package net.unit8.souther.compiler;

import java.util.Map;

/**
 * Loads freshly generated classes from an in-memory binary-name → bytecode map, delegating anything
 * unknown to the parent loader. Used to run generated code without writing {@code .class} files:
 * the compiler's compile-time {@code $Ctfe.check} evaluation and {@link Runner}'s behavior driving.
 */
final class MemoryClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes;

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
}
