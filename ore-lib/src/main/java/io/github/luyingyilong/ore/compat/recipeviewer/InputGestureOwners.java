package io.github.luyingyilong.ore.compat.recipeviewer;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/** A protected press owns its release even if the popup closes or the Screen changes. */
final class InputGestureOwners<T> {
    record Token(boolean pointer, int code, int scan) {
        static Token key(int key, int scan) { return new Token(false, key, scan); }
        static Token button(int button) { return new Token(true, button, 0); }
    }

    private final Map<Token, WeakReference<T>> owners = new HashMap<>();

    boolean contains(Token token) { return owners.containsKey(token); }
    T getOwner(Token token) {
        WeakReference<T> owner = owners.get(token);
        return owner == null ? null : owner.get();
    }
    void claim(Token token, T owner) { owners.putIfAbsent(token, new WeakReference<>(owner)); }
    void release(Token token) { owners.remove(token); }
    void clear() { owners.clear(); }

    boolean hasKeys() { return owners.keySet().stream().anyMatch(token -> !token.pointer()); }
}
