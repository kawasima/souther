package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;
import net.unit8.souther.runtime.Behavior;

/**
 * Reflective access to a compiled module's derived codecs and behaviors. Souther emits these
 * as static factories ({@code decoder()}/{@code encoder()}) and no-arg behavior classes whose
 * generic parameters are gone at the reflection boundary. This helper confines the unavoidable
 * unchecked casts to three private methods so the tests themselves stay free of raw types.
 */
final class Codecs {

    private Codecs() {}

    @SuppressWarnings("unchecked")
    private static Decoder<Object, ?> asDecoder(Object factoryResult) {
        return (Decoder<Object, ?>) factoryResult;
    }

    @SuppressWarnings("unchecked")
    private static Encoder<Object, ?> asEncoder(Object factoryResult) {
        return (Encoder<Object, ?>) factoryResult;
    }

    @SuppressWarnings("unchecked")
    private static Behavior<Object, Object> asBehavior(Object instance) {
        return (Behavior<Object, Object>) instance;
    }

    static Decoder<Object, ?> decoder(ClassLoader loader, String className, String factory) throws Exception {
        return asDecoder(loader.loadClass(className).getMethod(factory).invoke(null));
    }

    static Decoder<Object, ?> decoder(ClassLoader loader, String className) throws Exception {
        return decoder(loader, className, "decoder");
    }

    static Decoder<Object, ?> decoder(Class<?> clazz) throws Exception {
        return asDecoder(clazz.getMethod("decoder").invoke(null));
    }

    static Encoder<Object, ?> encoder(ClassLoader loader, String className) throws Exception {
        return asEncoder(loader.loadClass(className).getMethod("encoder").invoke(null));
    }

    static Encoder<Object, ?> encoder(Class<?> clazz) throws Exception {
        return asEncoder(clazz.getMethod("encoder").invoke(null));
    }

    /** Decode {@code raw} through the named class's {@code decoder} factory. */
    static Result<?> decode(ClassLoader loader, String className, Object raw) throws Exception {
        return decoder(loader, className).decode(raw, Path.ROOT);
    }

    /** Decode {@code raw} through a named factory ({@code jsonDecoder}, {@code recordDecoder}, ...). */
    static Result<?> decode(ClassLoader loader, String className, String factory, Object raw) throws Exception {
        return decoder(loader, className, factory).decode(raw, Path.ROOT);
    }

    /** Decode {@code raw} through a class's {@code decoder} factory. */
    static Result<?> decode(Class<?> clazz, Object raw) throws Exception {
        return decoder(clazz).decode(raw, Path.ROOT);
    }

    /** Decode and unwrap the success value; the cast fails if the result is an error. */
    static Object decoded(ClassLoader loader, String className, Object raw) throws Exception {
        return ((Ok<?>) decode(loader, className, raw)).value();
    }

    /** Decode and unwrap the success value; the cast fails if the result is an error. */
    static Object decoded(Class<?> clazz, Object raw) throws Exception {
        return ((Ok<?>) decode(clazz, raw)).value();
    }

    /** Encode {@code value} through the named class's {@code encoder} factory. */
    static Object encode(ClassLoader loader, String className, Object value) throws Exception {
        return encoder(loader, className).encode(value);
    }

    /** Encode {@code value} through a class's {@code encoder} factory. */
    static Object encode(Class<?> clazz, Object value) throws Exception {
        return encoder(clazz).encode(value);
    }

    /** Apply a reflectively-instantiated behavior to an input. */
    static Object apply(Object behavior, Object input) {
        return asBehavior(behavior).apply(input);
    }
}
