package net.unit8.souther.runtime;

/**
 * The failure case of integer division / remainder by zero (spec 18.2). A built-in data with no
 * fields; the only value is {@link #INSTANCE}.
 */
public final class DivisionByZero {

    public static final DivisionByZero INSTANCE = new DivisionByZero();

    public DivisionByZero() {}
}
