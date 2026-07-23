package net.unit8.souther.runtime;

/**
 * The failure case of {@code String.toInt}: the string is not a decimal integer. A built-in data with
 * no fields; the only value is {@link #INSTANCE}. The Souther-idiomatic case return (a named case,
 * not a surface {@code Maybe}), mirroring {@link DivisionByZero}.
 */
public final class NotANumber {

    public static final NotANumber INSTANCE = new NotANumber();

    public NotANumber() {}
}
