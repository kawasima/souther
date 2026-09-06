package souther.compiler.check;

/**
 * The source a compilation reads {@code module}'s rules under: the one every reader that asks it
 * where to read is given, however many pairs are built to hand it over.
 *
 * <p>Not public and not written outside this package. What it says is that a reading made from a
 * source carrying it is the declaration's own as this compilation reads it, which is a thing only
 * whatever answers for the compilation can say — so {@link DeclarationReadings#theCompilationsOwn}
 * is where one is made, and a reader that could write one would be lending itself readings of
 * rules it was not reading.
 */
record AModulesRules(String module) implements RuleReadingSource.Origin {

    AModulesRules {
        if (module == null) {
            throw new IllegalArgumentException("a module's rules are read under its name");
        }
    }
}
