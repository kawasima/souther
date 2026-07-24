package net.unit8.souther.compiler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Loads freshly generated classes from an in-memory binary-name → bytecode map, delegating anything
 * unknown to the parent loader. Used to run generated code without writing {@code .class} files:
 * the compiler's compile-time {@code $Ctfe.check} evaluation and {@link Runner}'s behavior driving.
 */
final class MemoryClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes;
    /** Classes defined on the fly (a multi-argument fake's base subclass; issue #57), cached so a name
     * is never defined twice — which would be a {@code LinkageError}. */
    private final Map<String, Class<?>> defined = new HashMap<>();

    MemoryClassLoader(Map<String, byte[]> classes, ClassLoader parent) {
        super(parent);
        this.classes = classes;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] b = classes.get(name);
        if (b == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, b, 0, b.length);
    }

    /** Defines {@code name} once, generating the bytes lazily only on the first (cache-miss) call — a
     * second call with the same name returns the already-defined class without rebuilding the bytecode
     * (the bytes are identical; the fake body is injected at construction, not baked into the class). */
    synchronized Class<?> define(String name, Supplier<byte[]> bytes) {
        return defined.computeIfAbsent(name, n -> {
            byte[] b = bytes.get();
            return defineClass(n, b, 0, b.length);
        });
    }
}
