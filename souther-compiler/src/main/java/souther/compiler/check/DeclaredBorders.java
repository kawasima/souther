package souther.compiler.check;

import souther.compiler.diag.Citation;
import souther.compiler.numeric.Endpoint;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>Where the declaration's code is, is read here too, because a report carries it and a caller
 * that found the declaration a second time to get it would have two ways of finding one — and a
 * policy of its own for the one that came back empty. That is a rule about how a declaration is
 * found, and not about what may be asked of it once it has been: the address is settled first, and
 * what the declaration says and where its code is are two questions put to that one address. What
 * comes back for a name nothing declares is then one answer and not two, because there was one
 * lookup.
 *
 * <p>Nothing here is an identity. What tells one authored line from another is which line of the
 * clause it is ({@link souther.compiler.partition.AuthoredLine}), and that is what
 * this is keyed by; what it hands back is what to call the line. Held as part of the identity,
 * the frames above would make one line two.
 */
public record DeclaredBorders(souther.compiler.diag.Citation at,
                              Map<Key, NumberAt<RuleKey>> forms) {

    /**
     * Which authored line: what the reading knows about the clause that placed the end.
     *
     * <p>A part of a {@code data}'s clause and of nothing else. These are the lines a declaration
     * wrote in its own terms, and a behavior's {@code ensures} writes none of them — so the kind of
     * clause is in the type rather than asked of a part that arrives. Written over parts of any
     * clause, this would take a behavior's and answer null, which is the word it has for a
     * declaration that drew no such line.
     *
     * <p>The line and not the conjunct alone, because this is a pairing key and a conjunct does not
     * name one line. A conjunct written under a denial places an end on each of two numbers, and
     * keyed by the conjunct the second overwrites the first — leaving one of the two lines named
     * after the other's number.
     */
    public record Key(DeclaredLine line) {}

    /**
     * One end of one of a declaration's numbers, which is what the evidence about it is gathered
     * under.
     *
     * <p>All three, because an end is where a number stops on one side: two rules stopping one
     * number at one value are evidence about one end, and the same rules stopping it at two values
     * are not.
     *
     * @param on    which number of the declaration
     * @param lower whether this is where its values start; otherwise where they stop
     * @param at    the value the end sits at
     */
    private record AnEnd(NumberAt<RuleKey> on, boolean lower, Endpoint at) {}

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
    public static DeclaredBorders of(TypeSymbol declaredOn, PublishedDeclarations declarations,
                                     DeclarationCitations citations, RuleReadingSource source,
                                     ReadingPolicy policy) {
        return of(declaredOn, declarations, citations, source, policy, DeclarationReadings.NONE);
    }

    /** The same, asking {@code machines} for what somebody has already made of the declaration. */
    public static DeclaredBorders of(TypeSymbol declaredOn, PublishedDeclarations declarations,
                                     DeclarationCitations citations, RuleReadingSource source,
                                     ReadingPolicy policy, DeclarationReadings machines) {
        // One address, and two questions put to it. What kind of declaration this is decides whether
        // there are lines to read at all; where its code is decides what a report calls the place.
        // Neither is looked up by the name a second time, which is what would give one declaration
        // two ways of being found and each of them a policy for coming back empty.
        if (!(declaredOn instanceof TypeSymbol.AtModule named)) {
            throw new IllegalArgumentException(
                    "there is no declaration of " + declaredOn.name() + " to read");
        }
        if (!(declarations.of(named.key()) instanceof DeclarationMeaning.Product)) {
            throw new IllegalArgumentException(
                    "there is no declaration of " + declaredOn.name() + " to read");
        }
        Citation at = citations.of(named.key());
        // The evidence gathered by the end it is about, because which lines an end is owed to is a
        // question about the end and not about one piece of what was established there
        // ({@link DeclaredBounds.End#drawn}). Asked of each piece as it arrives, this named lines
        // the reading of cuts does not draw, and a report would hold words for one of them.
        Map<AnEnd, List<LineProvenance>> byEnd = new LinkedHashMap<>();
        for (FieldDomains.Placed placed
                : Rules.of(declaredOn, source, policy, machines).bounds().placed()) {
            // A clause reaching this declaration through a spread is written on another one and is
            // that one's to name, the way a line is named by the rule that drew it (ADR-0090). Its
            // own reading answers for it.
            if (!placed.part().rule().clause().id().declaredOn().equals(declaredOn)) {
                continue;
            }
            byEnd.computeIfAbsent(new AnEnd(placed.at(), placed.lower(), placed.end()),
                    _ -> new ArrayList<>()).add(placed.from());
        }
        Map<Key, NumberAt<RuleKey>> forms = new LinkedHashMap<>();
        byEnd.forEach((end, found) -> new DeclaredBounds.End(end.at(), found).drawn()
                .forEach(line -> forms.put(new Key(line), end.on())));
        return new DeclaredBorders(at, forms);
    }

    /**
     * What the author wrote the line on, or null where this declaration draws no such line.
     *
     * <p>Null is an answer about the reading and not about the line: a clause whose end this could
     * not read is a clause with no form to print, and a caller handed one has nothing to call the
     * line but the rule's own name.
     */
    public NumberAt<RuleKey> at(DeclaredLine line) {
        return at(new Key(line));
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

    /** The same, for a caller holding the line as the clause drew it. */
    public String nameOf(DeclaredLine line) {
        return nameOf(new Key(line));
    }
}
