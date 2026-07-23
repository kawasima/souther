package net.unit8.souther.runtime;

/**
 * Overflow-checked {@code Int} arithmetic (spec 18.2). {@code Int} is signed 64-bit; when a sum,
 * difference, or product leaves that range the computation aborts rather than wrapping. Overflow
 * is a model bug, not a business result, so — like an invariant violation — it throws
 * {@link ConstraintViolation} (spec 7.3, 9.4, 19.7), which Souther code cannot catch.
 *
 * <p>Zero division is different: it is a possible input (the divisor is 0), so it returns a
 * {@code DivisionByZero} case rather than aborting (spec 18.2). It is not handled here.
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

    /**
     * The {@code /} operator on Int: truncating division that aborts on a zero divisor (and on the
     * {@code Long.MIN_VALUE / -1} overflow), like the other arithmetic operators (spec 18.1). Code
     * that wants a zero divisor as a case uses the {@code Int.divide} function, which returns
     * {@code Int | DivisionByZero} instead.
     */
    public static long divideExact(long a, long b) {
        if (b == 0) {
            throw new ConstraintViolation("division by zero: " + a + " / 0");
        }
        try {
            return Math.divideExact(a, b);
        } catch (ArithmeticException e) {
            throw new ConstraintViolation("Int overflow: " + a + " / " + b);
        }
    }

    /** {@code Int.compare(a, b)}: -1, 0, or 1. The function form of the comparison operators. */
    public static long compare(long a, long b) {
        return Long.compare(a, b);
    }

    /**
     * {@code Int.modBy(divisor, n)}: Elm-style floored modulo (spec 18.2). The result takes the sign
     * of the divisor, unlike {@code Int.remainder}, which truncates toward zero. A zero divisor is a
     * model bug — like the {@code /} operator it aborts with {@link ConstraintViolation} rather than
     * returning a case, so the result is a plain {@code Int} that reads cleanly in an invariant.
     */
    public static long modBy(long divisor, long n) {
        if (divisor == 0) {
            throw new ConstraintViolation("modulo by zero: modBy 0");
        }
        return Math.floorMod(n, divisor);
    }
}
