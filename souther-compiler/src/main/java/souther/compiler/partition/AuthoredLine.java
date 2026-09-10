package souther.compiler.partition;

import souther.compiler.check.RuleRef;
import souther.compiler.types.TypeSymbol;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One line of the model: which rule, and which of that rule's lines.
 *
 * <p>What the author wrote, with nothing about the reading that reached it. A rule is read once per
 * position of every behavior carrying it and once per call of every helper holding it; which
 * position, and which call of which helper, are {@link LineOrigin}'s, and this is what several
 * readings of one line share.
 *
 * <p><b>How far that reaches is the rule's own answer and is not restated here.</b> {@link RuleRef}
 * is what a rule is, and it does not tell every kind of rule apart the same way: a clause of a
 * {@code data} is the type's rule wherever the type is carried, and a comparison and an
 * {@code ensures} clause are written in a body and are that behavior's — so a helper's comparison
 * called from two behaviors is two rules and two lines, while a type's clause read in two behaviors
 * is one. Said again here as "no behavior", this would be a second answer to a question the rule
 * already answers, and the two would differ for whichever kind of rule was added next.
 *
 * <p><b>Not the rule alone.</b> One clause places as many lines as it has conjuncts with an end in
 * them: {@code invariant within = value >= 1 && value <= 10} is one {@link RuleRef.Invariant} and
 * two lines, and a row at the bottom of the range is no evidence about the top. So which conjunct
 * drew it is part of this, and it is the clause's own text rather than the number it was written
 * about ({@link souther.compiler.check.DeclaredBounds.Drawn}).
 *
 * <p><b>And what the line is.</b> Two lines of one rule at one value are told apart by what each
 * says about its own value ({@link LineFacts}) — {@code value >= 5 && value <= 5} places a minimum
 * and a maximum at 5, and they are two lines however few values are left between them. Whether some
 * row could show the two apart is not asked: what row is left over is a fact about every other rule
 * of the model, so an identity read off it would be worked back out of what the rules happened to
 * leave rather than read from what this rule says.
 *
 * @param which          which of that rule's lines this is, said as what named it: a part of a
 *                       declaration's clause, a statement of a part of a behavior's, or the rule
 *                       alone where a body's comparison drew it and there is nothing under the rule
 *                       to be one of ({@link WhichLine})
 * @param facts          what the rule says about its own line
 * @param narrowedWithin the declarations that took a bound's end in, kept so that a narrowed line
 *                       stays apart from the bare one it narrows: {@code MinuteOfDay}'s maximum is
 *                       the same rule whether or not {@code WorkInterval} moved where it lands, and
 *                       the line the two settled together is not the line the bound would have
 *                       drawn alone. These are also who owes the line, which the bound is not once
 *                       something took its end in ({@link #obligationOwners})
 */
public record AuthoredLine(WhichLine which, LineFacts facts,
                           List<TypeSymbol.AtModule> narrowedWithin) {

    public AuthoredLine {
        if (which == null || facts == null) {
            throw new IllegalArgumentException("a line of the model is some rule's, and says what it"
                    + " is: " + which + " " + facts);
        }
        narrowedWithin = List.copyOf(narrowedWithin);
        // An end is something a clause of a `data` places, so those are the only lines a declaration
        // can take in. Said here rather than left to whoever reads the pair: a line answering that
        // a body's rule was narrowed has an owner that owes a row for a comparison, and every reader
        // of it would be deciding what to do about a line the language cannot write.
        if (!narrowedWithin.isEmpty() && !(which.rule() instanceof RuleRef.Invariant)) {
            throw new IllegalArgumentException(
                    "a rule written in a body places no end for a declaration to take in: "
                            + which.rule() + " within " + narrowedWithin);
        }
        // One entry per declaration. Several of these are one answer about one end, so a
        // declaration written twice would be one owner counted twice — and what counts them is what
        // says how many places a finding about the line names.
        if (narrowedWithin.size() > 1
                && narrowedWithin.size() != Set.copyOf(narrowedWithin).size()) {
            throw new IllegalArgumentException(
                    "one declaration took this end in twice, which is once: " + narrowedWithin);
        }
    }

    /**
     * What a report calls this line where it has no place to point at.
     *
     * <p>The author's word for the rule where they wrote one and what the rule is where they did
     * not, with the declarations that took it in beside it. A narrowing is not part of the rule, so
     * the rule does not say it and this does.
     *
     * <p>Not a place, because a debt is not at one: a line an {@code invariant} drew is met wherever
     * the type is carried, so any place to print would be the position of whichever behavior a walk
     * reached first. Where a reading of it is being said rather than the line, the place is there to
     * point at and {@link LineOrigin#describe} says it.
     *
     * <p>The two halves of the seal, spelled here because this is the sentence being written. Which
     * of the two a rule is found by is the rule's own answer and it says nothing about what a
     * sentence with no place in it should read; a fold over both that lived on the rule would be one
     * word for a question only a caller writing a sentence has.
     */
    public String saidWithoutAPlace() {
        return said(switch (which.rule()) {
            case RuleRef.Named it -> it.citedName();
            case RuleRef.Written it -> "the " + it.whatItIs();
        });
    }

    /**
     * The same about a rule a reader is calling something else.
     *
     * <p>A comparison has no name, so a reading of one is said by where it is written
     * ({@link LineOrigin#describe}) — and what the narrowing adds is the same words either way. Said
     * in both places, the two spellings of one narrowing read as two.
     */
    public String said(String rule) {
        return rule + narrowing();
    }

    /**
     * What this reading adds after the rule, which is nothing where no declaration took an end in.
     *
     * <p>The words on their own, for a reader putting them after a handle it renders itself. A rule
     * has one spelling and it is not this one's to write, so what is offered here is the rest of the
     * sentence rather than the whole of it.
     */
    public String narrowing() {
        return narrowedWithin.isEmpty() ? "" : " within " + naming(narrowedWithin);
    }

    /**
     * Several declarations that answer for one thing together, as a report says them.
     *
     * <p>One place, because a report says such a list in more than one: which declarations took an
     * end in, and which of them a finding is about. They are not the same list — the second is of
     * one module, and a line nothing took in has an owner that is in neither — and the reason to
     * spell them alike is that a reader meets both about one line. Written twice, the two spellings
     * are free to come apart.
     *
     * <p>{@code or} rather than {@code and}, because that is what the list means: any one of them
     * is as much the answer as the others, and the reading does not know which.
     */
    public static String naming(List<TypeSymbol.AtModule> declarations) {
        return declarations.stream().map(TypeSymbol::name)
                .collect(java.util.stream.Collectors.joining(" or "));
    }

    /**
     * The declaration this line came from, where it is a declaration's line rather than a body's.
     *
     * <p>Where the line came from, and not who owes it. A clause of a {@code data} says something
     * about the type wherever the type is carried, so a row standing at the line is evidence about
     * the type and the behaviors carrying it have nothing to add — one line, read wherever the type
     * goes. A comparison and an {@code ensures} clause are written in a body and say something about
     * that body at that position, so they have no declaration here at all.
     *
     * <p>A narrowed end comes from the bound's declaration. What {@code MinuteOfDay} says is why the
     * position has an upper edge; the declarations that took it in are why the edge is 1439, and
     * they are the ones who owe a row at it — which is {@link #obligationOwners} and not this.
     */
    public Optional<TypeSymbol> owedToTheDeclaration() {
        return which.rule() instanceof RuleRef.Invariant i
                ? Optional.of(i.clause().id().declaredOn())
                : Optional.empty();
    }

    /**
     * Which authored line of a declaration this is, where it is a declaration's line.
     *
     * <p>The part that drew it, which is what a report reads the declaration's own words for the
     * line by ({@link souther.compiler.check.DeclaredBorders}). Taken off the line rather than put
     * together from a rule and a number: the part carries both, and a caller assembling the pair
     * would be naming a part nobody issued.
     */
    public Optional<souther.compiler.check.DeclaredBorders.Key> declaredLine() {
        return which instanceof WhichLine.OfAPart it
                ? Optional.of(new souther.compiler.check.DeclaredBorders.Key(it.drawnBy()))
                : Optional.empty();
    }

    /**
     * The declarations that owe a row at this line. Empty where no declaration does.
     *
     * <p>Who has to write it, which is not where the line came from. Ownership follows the authored
     * line: a bound nothing took in is owed by the declaration that wrote the clause, and a bound a
     * record took in is owed by the record — the distinction at 1439 rather than 1440 is one that
     * record's author wrote, and taking it out is theirs to do. So a module importing a type owes
     * nothing for the type's own lines, and owes the ones its own declarations drew round them.
     *
     * <p>Empty for a comparison and for an {@code ensures} clause, which are a body's and are owed
     * per behavior as they were. Asked of the line rather than matched on which kind of rule it is,
     * for the reason {@link #owedToTheDeclaration} is: read as "is this an invariant", the question
     * would be asked again at every report, refusal and editor, and a rule added later would be
     * answered by whichever arm it was written beside.
     *
     * <p>Several, and not one chosen from them. Each is one where taking any away leaves the end
     * where it is, so each is as much the author as the others; and they need not be one module's —
     * an inner record's clause and an outer record's reach one coordinate at one value from two
     * modules as readily as from one. What each module does with the ones that are its own is
     * {@link #ownersIn}.
     */
    public List<TypeSymbol.AtModule> obligationOwners() {
        if (!narrowedWithin.isEmpty()) {
            return narrowedWithin;
        }
        return which.rule() instanceof RuleRef.Invariant i
                ? List.of(i.clause().id().declaredOn())
                : List.of();
    }

    /**
     * Which of them {@code module} wrote, in the order the line names them.
     *
     * <p>What a module's account of this line is filed under, and what a finding about it names. A
     * line with none of these here is one this module has nothing to answer for: its values are held
     * to it, and a row at it is somebody else's to write.
     */
    public List<TypeSymbol.AtModule> ownersIn(String module) {
        return obligationOwners().stream().filter(each -> each.module().equals(module)).toList();
    }

    /**
     * Whether any declaration {@code module} wrote owes a row at this line.
     *
     * <p>A projection of {@link #ownersIn} and nothing more. It says the module has a declaration
     * that owes the line, not that the module holds a debt for it: a debt is what the readings of
     * the line came to, so a module that owes this and reads it nowhere holds none
     * ({@link souther.compiler.query.Adequacy.DeclaredBorders}).
     */
    public boolean owedIn(String module) {
        return !ownersIn(module).isEmpty();
    }
}
