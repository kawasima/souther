package net.unit8.souther.compiler;

import java.util.Map;

/**
 * Loads Souther-generated classes from an in-memory byte map. Runtime classes
 * (Raw, Result, Decoder, ...) resolve through the parent, so a cast of a generated
 * value to a runtime interface is type-compatible across the two loaders.
 */
final class BytesClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes;

    BytesClassLoader(Map<String, byte[]> classes, ClassLoader parent) {
        super(parent);
        this.classes = classes;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }
}
