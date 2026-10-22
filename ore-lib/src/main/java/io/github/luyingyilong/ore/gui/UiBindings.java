package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.Signal0;
import io.github.luyingyilong.ore.signal.SignalOwner;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Registered Java callbacks; XML cannot resolve methods by name or invoke arbitrary code.
 */
public final class UiBindings {
    private final Map<String, Handler<?>> handlers = new LinkedHashMap<>();
    private final Map<String, Value<?>> values = new LinkedHashMap<>();

    /**
     * Borrowed Java data; XML never creates inventory sessions or owns shared sources.
     */
    public <T> UiBindings value(String name, Class<T> type, T value) {
        if (Objects.requireNonNull(name).isBlank() || values.containsKey(name))
            throw new IllegalArgumentException("Duplicate/blank UI value: " + name);
        values.put(name, new Value<>(Objects.requireNonNull(type), type.cast(Objects.requireNonNull(value))));
        return this;
    }

    Object requireValue(String name, Class<?> expected) {
        Value<?> value = values.get(name);
        if (value == null || !expected.isAssignableFrom(value.type()))
            throw new IllegalArgumentException("Missing or incompatible UI value " + name + ": " + expected.getTypeName());
        return value.value();
    }

    public UiBindings action(String name, Runnable action) {
        Objects.requireNonNull(action);
        put(name, new Handler<Void>(null, ignored -> action.run()));
        return this;
    }

    public <T> UiBindings handler(String name, Class<T> type, Consumer<? super T> slot) {
        put(name, new Handler<T>(Objects.requireNonNull(type), Objects.requireNonNull(slot)));
        return this;
    }

    private void put(String name, Handler<?> handler) {
        if (Objects.requireNonNull(name).isBlank() || handlers.putIfAbsent(name, handler) != null)
            throw new IllegalArgumentException("Duplicate/blank UI handler: " + name);
    }

    Handler<?> require(String name, Class<?> payload) {
        Handler<?> handler = handlers.get(name);
        if (handler == null) throw new IllegalArgumentException("Unknown UI handler: " + name);
        if (payload == null ? handler.type != null : handler.type == null || !handler.type.isAssignableFrom(payload))
            throw new IllegalArgumentException("Signal payload does not match handler " + name + ": " + payload);
        return handler;
    }

    private record Value<T>(Class<T> type, T value) {
    }

    record Handler<T>(Class<T> type, Consumer<? super T> slot) {
        @SuppressWarnings("unchecked")
        void accept(Object value) {
            slot.accept(type == null ? null : type.cast(value));
        }
    }

    record SignalAccessor(Method getter, Class<?> payload) {
        static SignalAccessor find(Object target, String name, Map<String, Class<?>> declaredPayloads) {
            Method found = null;
            for (Method method : target.getClass().getMethods()) {
                if (method.isBridge() || method.isSynthetic()) continue;
                UiSignal declaration = method.getAnnotation(UiSignal.class);
                for (Class<?> base = target.getClass().getSuperclass(); base != null && declaration == null; base = base.getSuperclass())
                    try {
                        declaration = base.getMethod(method.getName(), method.getParameterTypes()).getAnnotation(UiSignal.class);
                    } catch (NoSuchMethodException ignored) {
                    }
                if (declaration == null) continue;
                String key = declaration.value().isEmpty() ? method.getName() : declaration.value();
                if (!key.equals(name)) continue;
                if (found != null) throw new IllegalArgumentException("Ambiguous signal: " + name);
                found = method;
            }
            if (found == null || found.getParameterCount() != 0 || Modifier.isStatic(found.getModifiers()))
                throw new IllegalArgumentException("Unknown signal: " + name);
            if (found.getReturnType() == Signal0.class) return new SignalAccessor(found, null);
            if (found.getReturnType() != Signal.class)
                throw new IllegalArgumentException("Only read-only signals can be exposed");
            Type generic = found.getGenericReturnType();
            if (generic instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments()[0] instanceof Class<?> type)
                return new SignalAccessor(found, type);
            Class<?> payload = declaredPayloads.get(name);
            if (payload == null) throw new IllegalArgumentException("Factory must declare generic payload: " + name);
            return new SignalAccessor(found, payload);
        }

        @SuppressWarnings("unchecked")
        void connect(Object target, SignalOwner owner, String label, Handler<?> handler, java.util.function.BooleanSupplier active) {
            Object signal = UiPropertySchema.invoke(getter, target);
            if (signal instanceof Signal0 zero) zero.connect(owner, label, () -> {
                if (active.getAsBoolean()) handler.accept(null);
            });
            else ((Signal<Object>) signal).connect(owner, label, value -> {
                if (active.getAsBoolean()) handler.accept(value);
            });
        }
    }
}
