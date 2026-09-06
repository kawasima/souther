package souther.compiler.check;

/**
 * What reading a module's declarations as a static analysis takes: the scope the names resolve in,
 * and where the clauses in the representation it reads come from.
 *
 * <p>The two together because a reading takes both, and for no reason beyond that. Most of what
 * carries this reads only the scope and hands the pair on; separated, those would thread two
 * arguments where they thread one, which is plumbing rather than a distinction anyone makes.
 *
 * <p><b>It states no relation between them.</b> Not that they are the same module's, not that one
 * answers for the other, not that a declaration reached through the scope is one the lookup has.
 * There is nothing of that kind to state: a scope belongs to the module being read, and a clause
 * belongs to the declaration that wrote it, wherever that was. This pair used to require that the
 * two named one module, which is exactly the claim that made an imported declaration's clauses come
 * back in whatever representation the reader happened to hold, so the requirement is gone and
 * nothing here or in a constructor puts it back.
 *
 * <p>Nothing else goes in here. What a reading may spend is a bound on the work and not part of what
 * is being read ({@link ReadingPolicy}), and it stays a separate argument: joined, how much a
 * declaration may cost and where its clauses come from would be one value, and a caller changing
 * either would be changing both.
 *
 * @param symbols    the module's resolved scope
 * @param invariants where a declaration's clauses in the representation this reads are answered from
 */
public record RuleReadingSource(Symbols symbols, ExpandedClauseLookup invariants) {

    public RuleReadingSource {
        if (symbols == null || invariants == null) {
            throw new IllegalArgumentException(
                    "reading a declaration's rules takes a scope and somewhere to read clauses from");
        }
    }
}
