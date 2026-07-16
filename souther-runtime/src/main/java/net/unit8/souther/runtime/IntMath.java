package net.unit8.souther.runtime;

/**
 * Overflow-checked {@code Int} arithmetic (spec 18.2). {@code Int} is signed 64-bit; when a sum,
 * difference, or product leaves that range the computation aborts rather than wrapping. Overflow
 * is a model bug, not a business result, so — like an invariant violation — it throws
 * {@link ConstraintViolation} (spec 7.3, 9.4, 19.7), which Souther code cannot catch.
 *
 * <p>Zero division is different: it is a possible input (the divisor is 0), so it returns a
 * {@code DivisionByZero} arm rather than aborting (spec 18.2). It is not handled here.
 */
public final class IntMath {

    private IntMath() {}

    public static long addExact(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            throw new ConstraintViolation("Int overflow: " + a + " + " + b);
        }
    }

    public static long subtractExact(long a, long b) {
        try {
            return Math.subtractExact(a, b);
        } catch (ArithmeticException e) {
            throw new ConstraintViolation("Int overflow: " + a + " - " + b);
        }
    }

    public static long multiplyExact(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            throw new ConstraintViolation("Int overflow: " + a + " * " + b);
        }
    }
}
