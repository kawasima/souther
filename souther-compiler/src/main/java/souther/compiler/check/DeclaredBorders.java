package souther.compiler.check;

import souther.compiler.types.TypeSymbol;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The lines one declaration draws, in the terms the declaration writes them.
 *
 * <p>What a report calls a line where it prints it under the declaration rather than under a
 * behavior. {@code UserId}'s clause draws {@code String.length(value) = 1}; the behaviors carrying
 * the type meet that line at {@code String.length(draft.owner)} and at
 * {@code String.length(activities[*]@CallTask.owner)}, and neither of those is what the author
 * wrote. A debt named after one of them would be named after whichever reading a report happened to
 * reach first (issue #1062).
 *
 * <p><b>Asked of the declaration, rather than carried from the readings.</b> Which number a clause
 * bounds is read from whatever value the reading started at — {@code Day}'s clause is about
 * {@code value} read from {@code Day} and about {@code d} read from the {@code Span} holding it —
 * so a coordinate that travelled here with a measurement would be one frame of several and nothing
 * would say which. Read from the declaration, there is one frame and it is the author's.
 *
 * <p>Where the declaration is written is read here too, because a report sends a reader to it and
 * a caller that looked it up separately would have a second way of finding one declaration — and a
 * policy of its own for the one that came back empty, which is a null place reaching a reader as a
 * switch over the arms a citation has.
 *
 * <p>Nothing here is an identity. What tells one authored line from another is the clause and which
 * of its conjuncts drew the end ({@link souther.compiler.partition.AuthoredLine}), and that is what
 * this is keyed by; what it hands back is what to call the line. Held as part of the identity,
 * the frames above would make one line two.
 */
public record DeclaredBorders(souther.compiler.diag.Citation at,
                              Map<Key, NumberAt<RuleKey>> forms) {

    /**
     * Which authored line: the part of the clause that placed the end.
     *
     * <p>A part of a {@code data}'s clause and of nothing else. These are the lines a declaration
     * wrote in its own terms, and a behavior's {@code ensures} writes none of them — so the kind of
     * clause is in the type rather than asked of a part that arrives. Written over parts of any
     * clause, this would take a behavior's and answer null, which is the word it has for a
     * declaration that drew no such line.
     */
    public record Key(PartId<RuleRef.Invariant> part) {}

    public DeclaredBorders {
        if (at == null) {
            throw new IllegalArgumentException("a declaration is written somewhere");
        }
        forms = Map.copyOf(forms);
    }

    /**
     * The lines {@code declaredOn} draws.
     *
     * <p>The declaration's own reading of its own rules, which is the reading whose frame is the
     * author's. One of these serves every debt of one declaration, so a caller printing a report
     * asks once per declaration rather than once per line.
     */
    public static DeclaredBorders of(TypeSymbol declaredOn, RuleReadingSource source,
                                     ReadingPolicy policy) {
        // Where the declaration is, read here with what it draws. A caller that asked one thing for
        // the name and another for the place would have two ways of finding one declaration, and a
        // policy of its own for the one that came back empty.
        if (!(source.symbols().declaredNode(declaredOn) instanceof souther.compiler.ast.Hir.Data
                data)) {
            throw new IllegalArgumentException(
                    "there is no declaration of " + declaredOn.name() + " to read");
        }
        souther.compiler.diag.Citation at = souther.compiler.diag.Citation.of(data.pos());
        Map<Key, NumberAt<RuleKey>> forms = new LinkedHashMap<>();
        for (FieldDomains.Placed placed : Rules.of(declaredOn, source, policy).bounds().placed()) {
            // A clause reaching this declaration through a spread is written on another one and is
            // that one's to name, the way a line is named by the rule that drew it (ADR-0090). Its
            // own reading answers for it.
            if (placed.part().rule().clause().id().declaredOn().equals(declaredOn)) {
                forms.put(new Key(placed.part()), placed.at());
            }
        }
        return new DeclaredBorders(at, forms);
    }

    /**
     * What the author wrote the line on, or null where this declaration draws no such line.
     *
     * <p>Null is an answer about the reading and not about the line: a clause whose end this could
     * not read is a clause with no form to print, and a caller handed one has nothing to call the
     * line but the rule's own name.
     */
    public NumberAt<RuleKey> at(PartId<RuleRef.Invariant> part) {
        return at(new Key(part));
    }

    /** The same, for a caller holding the key the rule handed it
     *  ({@code LineOrigin.declaredLine}). */
    public NumberAt<RuleKey> at(Key line) {
        return forms.get(line);
    }

    /**
     * What to call the line the author drew, in their own terms, or null where this declaration
     * draws no such line.
     *
     * <p>Here, where the lines a declaration wrote are, and nowhere else. The claim itself is the
     * question's vocabulary and is written about a place of any kind, so it has no word for a name
     * a value's own rules spell one way and an input's spell another; what a declaration calls its
     * own place is this reading's to say. Written at each surface that shows a line, the same line
     * came out in two spellings and neither was the other's.
     */
    public String nameOf(Key line) {
        NumberAt<RuleKey> at = forms.get(line);
        if (at == null) {
            return null;
        }
        // The value a newtype wraps is at no name, and the clause writing about it says `value`.
        String where = at.position().isTheValueItself() ? "value" : at.position().toString();
        return at.of() instanceof NumberAt.OfWhatNumber.OfWhatAnOperationAnswers taken
                ? taken.operation() + "(" + where + ")" : where;
    }

    /** The same, for a caller holding the part that drew the line. */
    public String nameOf(PartId<RuleRef.Invariant> part) {
        return nameOf(new Key(part));
    }
}
