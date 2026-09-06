package souther.compiler.check;

/**
 * The source a compilation reads {@code module}'s rules under: the one every reader that asks it
 * where to read is given, however many pairs are built to hand it over.
 *
 * <p>Whose it is, and then which module's. A reading made from a source carrying this is handed to
 * any reader whose source carries the same, so what it says has to be a thing only whatever answers
 * for a compilation can say — and a module's name is not that, because a name is a thing anybody
 * can write. So it says which sources it came from as well: two mints are two mints, and what one
 * of them stamps is nothing the other's readers are handed, whatever name is written on it.
 *
 * <p>Not public and not written outside this package. Between them those two say the whole of it: a
 * reader cannot write one, and a reader that builds sources of its own gets what its own sources
 * mint, which nobody else's readers share.
 *
 * @param sources what minted this, which is what makes it that compilation's and no other's
 * @param module the module whose rules a source carrying this reads
 */
record AModulesRules(TheCompilationsSources sources, String module)
        implements RuleReadingSource.Origin {

    AModulesRules {
        if (sources == null || module == null) {
            throw new IllegalArgumentException(
                    "a module's rules are read under its name, and under whoever answers for them");
        }
    }
}
