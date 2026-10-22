package io.github.luyingyilong.ore.gui;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Only explicitly annotated public bean accessors are discoverable. Never writes fields.
 */
public final class UiPropertySchema {
    private static final ClassValue<Map<String, Property>> CACHE = new ClassValue<>() {
        @Override
        protected Map<String, Property> computeValue(Class<?> type) {
            return discover(type);
        }
    };

    private UiPropertySchema() {
    }

    public static Map<String, Property> getProperties(Class<?> type) {
        return CACHE.get(type);
    }

    private static Map<String, Property> discover(Class<?> type) {
        Map<String, Property> result = new TreeMap<>();
        for (Method method : type.getMethods()) {
            if (method.isBridge() || method.isSynthetic()) continue;
            UiProperty annotation = method.getAnnotation(UiProperty.class);
            // Java overrides need not repeat the declaration annotation.
            if (annotation == null) {
                for (Class<?> base = type.getSuperclass(); base != null && annotation == null; base = base.getSuperclass())
                    try {
                        annotation = base.getMethod(method.getName(), method.getParameterTypes()).getAnnotation(UiProperty.class);
                    } catch (NoSuchMethodException ignored) {
                    }
            }
            if (annotation == null) continue;
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || method.getReturnType() == void.class)
                throw new IllegalArgumentException("UiProperty requires a public instance getter: " + method);
            String suffix = method.getName().startsWith("get") ? method.getName().substring(3) : "";
            String key = annotation.value().isEmpty() ? java.beans.Introspector.decapitalize(suffix) : annotation.value();
            if (key.isEmpty()) throw new IllegalArgumentException("UiProperty needs a name: " + method);
            Method setter = setter(type, annotation.setter().isEmpty() ? "set" + suffix : annotation.setter(), method.getReturnType());
            Method silent = annotation.silentSetter().isEmpty() ? setter : setter(type, annotation.silentSetter(), method.getReturnType());
            if (result.putIfAbsent(key, new Property(key, method, setter, silent, annotation.initialState())) != null)
                throw new IllegalArgumentException("Ambiguous UI property: " + key);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Method setter(Class<?> type, String name, Class<?> value) {
        List<Method> matches = Arrays.stream(type.getMethods()).filter(m -> m.getName().equals(name)
                && !Modifier.isStatic(m.getModifiers()) && Arrays.equals(m.getParameterTypes(), new Class<?>[]{value})).toList();
        // Covariant bridge methods are synthetic duplicates, never additional API choices.
        matches = matches.stream().filter(m -> !m.isBridge()).toList();
        if (matches.size() != 1)
            throw new IllegalArgumentException("Missing/ambiguous setter " + type.getName() + "." + name);
        return matches.get(0);
    }

    static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            if (error.getCause() instanceof Error fatal) throw fatal;
            throw new IllegalStateException(error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    public record Property(String name, Method getter, Method setter, Method silentSetter, boolean initialState) {
        public Class<?> getValueType() {
            return getter.getReturnType();
        }

        public Object get(Object target) {
            return io.github.luyingyilong.ore.property.PropertyAccess.readBase(() -> invoke(getter, target));
        }

        public void set(Object target, Object value) {
            invoke(silentSetter, target, value);
        }
    }
}
