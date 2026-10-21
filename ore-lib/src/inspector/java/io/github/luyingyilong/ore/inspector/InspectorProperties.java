package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.debug.DebugProperty;
import io.github.luyingyilong.ore.debug.Inspectable;
import io.github.luyingyilong.ore.control.base.Control;
import net.minecraft.network.chat.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Cached public accessor discovery. No field access, setAccessible or reflective base writes.
 */
public final class InspectorProperties {
    private static final ClassValue<List<Accessor>> ACCESSORS = new ClassValue<>() {
        @Override
        protected List<Accessor> computeValue(Class<?> type) {
            List<Accessor> result = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            Method[] methods = type.getMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName)
                    .thenComparing(m -> m.getDeclaringClass().getName()));
            for (Method getter : methods) {
                Inspectable metadata = getter.getAnnotation(Inspectable.class);
                if (metadata == null || getter.isBridge() || getter.isSynthetic()) continue;
                if (Modifier.isStatic(getter.getModifiers()) || getter.getParameterCount() != 0)
                    throw invalid(getter, "Expected an instance getter with no arguments");
                String propertyName = propertyName(getter);
                String key = metadata.key().isEmpty() ? propertyName : metadata.key();
                if (key.isBlank() || !keys.add(key)) throw invalid(getter, "Duplicate or empty property key: " + key);
                if (!Double.isFinite(metadata.min()) || !Double.isFinite(metadata.max()) || metadata.min() > metadata.max())
                    throw invalid(getter, "Invalid numeric bounds");
                Class<?> valueType = getter.getReturnType();
                String kind;
                List<String> options = List.of();
                if (valueType == boolean.class) kind = "boolean";
                else if (valueType.isEnum()) {
                    kind = "enum";
                    options = Arrays.stream(valueType.getEnumConstants()).map(v -> ((Enum<?>) v).name()).toList();
                } else if (valueType == String.class || valueType == Component.class) kind = "text";
                else if (valueType == int.class || valueType == long.class || valueType == float.class || valueType == double.class)
                    kind = "number";
                else throw invalid(getter, "Unsupported value type: " + valueType.getName());
                String setterName = metadata.setter().isEmpty()
                        ? "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1)
                        : metadata.setter();
                Method setter;
                try {
                    setter = type.getMethod(setterName, valueType);
                } catch (NoSuchMethodException ex) {
                    throw invalid(getter, "Missing public typed setter: " + setterName);
                }
                if (Modifier.isStatic(setter.getModifiers()))
                    throw invalid(getter, "Setter must be an instance method");
                result.add(new Accessor(getter, setter, key, metadata, kind, options));
            }
            return List.copyOf(result);
        }
    };

    private InspectorProperties() {
    }

    private static String propertyName(Method getter) {
        String name = getter.getName();
        String suffix;
        if (name.startsWith("get") && name.length() > 3) suffix = name.substring(3);
        else if (name.startsWith("is") && name.length() > 2 && getter.getReturnType() == boolean.class)
            suffix = name.substring(2);
        else throw invalid(getter, "Inspectable properties require a get/is accessor");
        // Follow JavaBeans acronym handling without introducing a java.desktop dependency.
        if (suffix.length() > 1 && Character.isUpperCase(suffix.charAt(0)) && Character.isUpperCase(suffix.charAt(1)))
            return suffix;
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    public static List<DebugProperty> properties(Control target) {
        List<DebugProperty> result = new ArrayList<>();
        for (Accessor accessor : ACCESSORS.get(target.getClass())) result.add(accessor.bind(target));
        List<DebugProperty> explicit = new ArrayList<>();
        target.debugProperties(explicit);
        for (DebugProperty property : explicit) {
            // Explicit adapters win for the same target/key; forwarded internal parts stay distinct.
            result.removeIf(old -> old.target() == property.target() && old.key().equals(property.key()));
            result.add(property);
        }
        return List.copyOf(result);
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
            if (ex.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Inspector accessor failed: " + method.getName(), ex.getCause());
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Inspector accessor is not public: " + method.getName(), ex);
        }
    }

    private static IllegalArgumentException invalid(Method method, String message) {
        return new IllegalArgumentException(method.getDeclaringClass().getSimpleName() + "." + method.getName() + ": " + message);
    }

    private record Accessor(Method getter, Method setter, String key, Inspectable metadata,
                            String kind, List<String> options) {
        DebugProperty bind(Control target) {
            return new DebugProperty(target, key, metadata.group(), kind, options,
                    () -> invoke(getter, target), metadata.min(), metadata.max(),
                    value -> DebugBridge.validateSetter(target, key, value,
                            () -> invoke(setter, target, value)));
        }
    }
}
