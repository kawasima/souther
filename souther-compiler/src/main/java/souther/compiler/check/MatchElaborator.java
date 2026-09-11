package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.msg.MatchMessage;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.CaseSelector;
import souther.compiler.types.Refinement;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Typing a `match`: over a sum's cases, over an `Option`, and over a newtype's layers, plus the
 * exhaustiveness and unwrap checks each form is subject to.
 */
public final class MatchElaborator {

    private MatchElaborator() {}

    /**
     * Types a {@code match}. What the subject can be selected as is asked once, of {@link CaseSpace};
     * what the surface admits is asked here, and the two forms differ in what they admit rather than
     * in what a case means.
     *
     * <p>An optional keeps its own reading. An or-pattern is refused over one, {@code Some(x)} opens
     * the element rather than a newtype layer of its own, and an arm naming neither carrier is
     * reported as not a case of an optional rather than as a case of some other sum. Those are rules
     * about what may be written, so they stay where the text is read; which cases exist and what each
     * one binds comes from the space either way.
     */
    static Core elaborateMatch(Hir.Match m, Scope env, CheckContext ctx,
                                       Type expected) {
        Core scrutinee = Elaborator.elaborate(m.scrutinee(), env, ctx);
        Type st = scrutinee.type();
        // Which form the subject is was answered where its cases were worked out. Read as a form
        // rather than by asking the type again, so a form the space gains has to be answered here
        // too rather than falling into the general reading with nobody the wiser.
        return switch (CaseSpace.of(st, ctx.symbols())) {
            case CaseSpace.Plain ignored -> throw CompileException.of(Diagnostic.at(m.pos(), 5)
                    .say(new MatchMessage.TheSubjectIsNotASum(Type.show(st))).build());
            case CaseSpace.Optional option ->
                    elaborateOptionMatch(m, scrutinee, option, env, ctx, expected);
            case CaseSpace.Cases cases ->
                    elaborateCasesMatch(m, scrutinee, cases, st, env, ctx, expected);
        };
    }

    /**
     * An arm names something that is not a case of what is being matched. When that name is a case of
     * another sum, say which — the author wrote an arm for a different match.
     *
     * <p>Raised as something the matches around this one can still answer about. Which match an arm
     * belongs to is settled by the layout rule (&lt;&lt;match&gt;&gt;): a {@code |} indented past the
     * arms of the match around it belongs to the inner one, which every {@code |} on the line the
     * inner match was opened on is. So an arm meant for an outer match lands in an inner one, and
     * what says that is happening is not how the two are laid out — it is that the name this arm
     * gives is a case of a match this one is written inside of.
     */
    static NotACaseOfThisMatch notCase(Hir.Name written, String what, Hir.Case c,
                                       Set<TypeSymbol> cases, Symbols symbols) {
        String caseName = written.written();
        String otherSum = null;
        for (TypeSymbol name : symbols.scope().visibleNames()) {
            if (!(symbols.declaredNode(name) instanceof Hir.SumData sum)) {
                continue;
            }
            List<TypeSymbol> others = TypeOps.caseNames(sum);
            if (written.answered() != null && others.contains(written.answered().type())
                    && !cases.containsAll(others)) {
                otherSum = sum.name();
                break;
            }
        }
        return new NotACaseOfThisMatch(written.answered() == null ? null : written.answered().type(),
                caseName, what, c, otherSum, false);
    }

    /**
     * An arm naming something the match it was written in has no case of, on its way out past the
     * matches that match is written inside.
     *
     * <p>It carries what the arm named, because the answer to whether an inner match took an arm
     * meant for an outer one is held by the outer one: the arm is here, the case-space it makes
     * sense in is there, and neither half can be read from where the other is. Each match around
     * this one asks {@link #takenFromTheMatchAround}, and the nearest one that has the case is the
     * one the arm was written for.
     */
    static final class NotACaseOfThisMatch extends CompileException {

        private static final long serialVersionUID = 1L;

        private final transient TypeSymbol named;

        private final transient String caseName;

        private final transient String what;

        private final transient Hir.Case at;

        private final transient String otherSum;

        private NotACaseOfThisMatch(TypeSymbol named, String caseName, String what, Hir.Case at,
                                    String otherSum, boolean takenFromAMatchAround) {
            super(said(caseName, what, at, otherSum, takenFromAMatchAround));
            this.named = named;
            this.caseName = caseName;
            this.what = what;
            this.at = at;
            this.otherSum = otherSum;
        }

        private static Diagnostic said(String caseName, String what, Hir.Case at, String otherSum,
                                       boolean takenFromAMatchAround) {
            Diagnostic.Builder d = Diagnostic.at(at.pos())
                    .say(new MatchMessage.NotACaseOf(caseName, what));
            if (otherSum != null) {
                d = d.hint(new MatchMessage.ItIsACaseOfAnotherSum(caseName, otherSum));
            }
            if (takenFromAMatchAround) {
                d = d.hint(new MatchMessage.AMatchInAnArmTakesTheArmsAfterIt());
            }
            return d.build();
        }

        /**
         * This error, said as the arm having been taken from {@code around} — where what it names is
         * a case of that match.
         *
         * <p>Answered once. The nearest match that has the case is the one the arm was written for,
         * so a match further out that also has it is not told, and what comes back from here is no
         * longer something any of them will answer about.
         */
        CompileException takenFromTheMatchAround(CaseSpace.Cases around, Symbols symbols) {
            if (named == null || !around.holds(named, symbols)) {
                return this;
            }
            return CompileException.of(said(caseName, what, at, otherSum, true));
        }
    }

    /** Match over a fixed set of data cases (a named sum's cases, or an anonymous union's members).
     * A single-case case binds that case's type; an or-pattern ({@code A | B}) binds {@code scrutinee}
     * (the sum type), since no one case type fits all its alternatives. Every case must be covered
     * exactly once (E1201; a second cover is an overlap error). */
    static Core elaborateCasesMatch(Hir.Match m, Core scrutineeCore, CaseSpace.Cases space,
                                        Type scrutinee,
                                        Scope env, CheckContext ctx, Type expected) {
        Set<TypeSymbol> cases = new HashSet<>(space.names());
        String what = space.described();
        // Divided over what a value can be and not over what the subject listed: a case that is
        // itself a sum is transparent as a value (spec §sum-data), so an arm naming it answers for
        // the leaves under it and an arm naming one of those leaves answers for that one. The two
        // are the same kind of arm, and what tells a match apart from what it left out is which
        // atoms nobody took (#966).
        CasePartition partition = CasePartition.of(AtomSpace.subjectAtoms(scrutinee, ctx.symbols()));
        List<Core.Case> arms = new ArrayList<>();
        Type branchType = null;
        for (int armIndex = 0; armIndex < m.cases().size(); armIndex++) {
            Hir.Case c = m.cases().get(armIndex);
            List<TypeSymbol> answersFor = new ArrayList<>();
            List<ResolvedCase> alternatives = new ArrayList<>();
            for (Hir.Name written : c.caseTypes()) {
                TypeSymbol caseName = names(written);
                ResolvedCase resolved = space.selector(caseName, ctx.symbols());
                if (resolved == null) {
                    throw notCase(written, what, c, cases, ctx.symbols());
                }
                alternatives.add(resolved);
                answersFor.addAll(resolved.atoms());
            }
            // What the arm's own alternatives say about each other, before what they say about the
            // arms before them. An alternative that adds nothing is a mistake inside this arm, and
            // reporting it as this arm overlapping another one would name the wrong two lines.
            CasePartition.Duplicate twice = CasePartition.namedTwiceIn(alternatives);
            if (twice != null) {
                throw CompileException.of(Diagnostic.at(c.pos())
                        .say(new MatchMessage.ThisArmNamesOneCaseTwice(
                                c.caseTypes().get(twice.again()).written()))
                        .build());
            }
            CasePartition.Redundant redundant = CasePartition.redundantIn(alternatives);
            if (redundant != null) {
                throw CompileException.of(Diagnostic.at(c.pos())
                        .say(new MatchMessage.AnAlternativeAddsNothingToThisArm(
                                c.caseTypes().get(redundant.adds()).written(),
                                c.caseTypes().get(redundant.covering()).written()))
                        .build());
            }
            CasePartition.Overlap overlap = partition.take(answersFor, armIndex);
            if (overlap != null) {
                throw CompileException.of(Diagnostic.at(c.pos())
                        .secondary(souther.compiler.diag.Region.point(m.cases().get(overlap.earlier()).pos()),
                                new MatchMessage.AnEarlierArmAnswersForIt(overlap.value().name()))
                        .say(new MatchMessage.MatchedByMoreThanOneCase(overlap.value().name()))
                        .build());
            }
            // Carried on as resolved. What a case covers is read off the declarations, which no
            // reader below this holds, so a pass that handed on the selector alone would leave
            // every question about which case of the subject an arm picked to be answered from its
            // name — and a case that is itself a sum reaches several leaves under one name.
            Core.ResolvedPattern pattern = alternatives.size() == 1
                    ? new Core.ResolvedPattern.Single(alternatives.get(0))
                    : new Core.ResolvedPattern.AnyOf(alternatives, scrutinee);
            Type bindType = pattern.bindType();
            if (c.unwrapAsserts() != null) {
                if (!(pattern instanceof Core.ResolvedPattern.Single)) {
                    throw CompileException.of(Diagnostic.at(c.pos()).say(new MatchMessage.AnOrPatternOpensNothing()).build());
                }
                checkUnwrapAsserts(c, ctx.symbols());
            }
            // What this arm's body says, and what a match inside it could not answer on its own. An
            // arm written for this match that the layout rule gave to an inner one arrives as a case
            // that match has no case of, and this is where there is a space to read it against.
            Core body;
            try {
                body = Elaborator.liftIntoOption(
                        Elaborator.elaborate(c.body(), bound(env, c.binding(), bindType), ctx,
                                expected),
                        expected, ctx.symbols());
            } catch (NotACaseOfThisMatch inner) {
                throw inner.takenFromTheMatchAround(space, ctx.symbols());
            }
            arms.add(new Core.Case(pattern, CoreBinders.of(c.binding()), body, c.pos()));
            branchType = mergeBranch(m, branchType, body.type(), c, expected);
        }
        List<TypeSymbol> unanswered = partition.unanswered();
        if (!unanswered.isEmpty()) {
            // Said in the model's own names, which is a second reading of a question already
            // answered and is why it happens here rather than in the check. Left in the order the
            // subject states them: sorted, a report of a nesting would read in an order nothing
            // wrote.
            throw nonExhaustive(m.pos(), what,
                    CoveringNames.of(scrutinee, unanswered, ctx.symbols()));
        }
        if (branchType == null) {
            throw CompileException.of(Diagnostic.at(m.pos(), 5).say(new MatchMessage.ThisMatchHasNoCases()).build());
        }
        return new Core.Match(scrutineeCore, arms,
                new Core.ForkPlace(ctx.occurrenceOf(m.origin()), ctx.within()), branchType,
                m.pos());
    }

    /** Match over {@code Option<element>}: cases are {@code Some} (binds the element) and
     * {@code None}; both must be present (spec §match). */
    static Core elaborateOptionMatch(Hir.Match m, Core scrutineeCore, CaseSpace.Optional space,
                                          Scope env, CheckContext ctx, Type expected) {
        Set<TypeSymbol> covered = new HashSet<>();
        List<Core.Case> arms = new ArrayList<>();
        Type branchType = null;
        for (Hir.Case c : m.cases()) {
            if (c.caseTypes().size() != 1) {
                throw CompileException.of(Diagnostic.at(c.pos()).say(new MatchMessage.AnOptionMatchTakesNoOrPattern()).build());
            }
            Hir.Name arm = c.caseTypes().get(0);
            String caseType = arm.written();
            TypeSymbol armName = names(arm);
            ResolvedCase resolved = space.selector(armName, ctx.symbols());
            if (resolved == null) {
                throw CompileException.of(Diagnostic.at(c.pos()).say(new MatchMessage.NotACaseOfAnOptional(caseType)).build());
            }
            CaseSelector selector = resolved.selector();
            Type bind = selector.bound();
            if (c.unwrapAsserts() != null) {
                // Only the carrier that holds something has something to open.
                if (!(selector.refinement() instanceof Refinement.OptionPresent wrapped)) {
                    throw CompileException.of(Diagnostic.at(c.pos()).say(new MatchMessage.TheCaseHasNoValueToOpen(caseType)).build());
                }
                checkOptionUnwrapAsserts(c, wrapped.bound(), ctx.symbols());
            }
            if (!covered.add(armName)) {
                throw CompileException.of(Diagnostic.at(c.pos()).say(new MatchMessage.MatchedByMoreThanOneCase(caseType)).build());
            }
            Core body = Elaborator.liftIntoOption(
                    Elaborator.elaborate(c.body(), bound(env, c.binding(), bind), ctx, expected),
                    expected, ctx.symbols());
            arms.add(new Core.Case(new Core.ResolvedPattern.Single(resolved),
                    CoreBinders.of(c.binding()), body, c.pos()));
            branchType = mergeBranch(m, branchType, body.type(), c, expected);
        }
        List<String> missing = new ArrayList<>();
        for (TypeSymbol caseName : space.names()) {
            if (!covered.contains(caseName)) {
                missing.add(caseName.name());
            }
        }
        if (!missing.isEmpty()) {
            throw nonExhaustive(m.pos(), "Option", missing);
        }
        return new Core.Match(scrutineeCore, arms,
                new Core.ForkPlace(ctx.occurrenceOf(m.origin()), ctx.within()), branchType,
                m.pos());
    }

    /** What each arm name denotes — what a {@code Core} arm dispatches on. */
    static List<TypeSymbol> denoted(List<Hir.Name> names) {
        List<TypeSymbol> out = new ArrayList<>();
        for (Hir.Name n : names) {
            out.add(names(n));
        }
        return out;
    }

    /**
     * The case an arm names, or the abandonment of the definition it is written in where nothing
     * declares it.
     *
     * <p>Reported where it is written. What follows from it — that the arm is not a case of what is
     * matched, that it opens a type other than the one under it, that the match does not cover its
     * cases — is that one mistake seen from another angle, which is what {@link Unanswerable} is
     * for.
     */
    private static TypeSymbol names(Hir.Name arm) {
        if (!(arm instanceof Hir.Name.Denoting named)) {
            throw new Unanswerable(arm.pos());
        }
        return named.type();
    }

    /** A constructor-destructuring pattern {@code X(Y(s))} opens one newtype per layer: {@code X}
     * (the matched case) then each written inner name. Every opened layer MUST be a newtype, and each
     * inner name MUST equal the newtype the previous layer wraps, or it is a compile error (Elm/F#
     * parity — the constructor in the pattern is type-checked, and a non-newtype cannot be opened). */
    static void checkUnwrapAsserts(Hir.Case c, Symbols symbols) {
        List<Hir.Name> opened = new ArrayList<>();
        opened.add(c.caseTypes().get(0));
        opened.addAll(c.unwrapAsserts());
        checkOpenedLayers(c, opened, symbols, true);
    }

    /** {@code Some(X(v))} opens the Option's element in the pattern. Unlike a user case, {@code Some}
     * is not itself a newtype — codegen has already unwrapped it — so the first written layer opens the
     * element directly and MUST name the element type; the rest is the same layer check as a user case. */
    static void checkOptionUnwrapAsserts(Hir.Case c, Type element, Symbols symbols) {
        List<Hir.Name> layers = c.unwrapAsserts();
        if (layers.isEmpty()) {
            return;   // `Some(v)` binds the whole element, opening nothing
        }
        Hir.Name first = layers.get(0);
        // the layer is compared as a type: `Some(billing.金額(v))` opens the same newtype an
        // imported bare `金額` names
        if (!(element instanceof Type.Ref r) || !r.name().equals(names(first))) {
            String elementName = element instanceof Type.Ref r2 ? r2.name().name() : Type.show(element);
            throw CompileException.of(Diagnostic.at(c.pos())
                    .say(new MatchMessage.TheNewtypeWrapsAnotherType("Some", elementName,
                            first.written()))
                    .build());
        }
        checkOpenedLayers(c, layers, symbols, false);
    }

    /** {@code firstOpensTheCase} says the first opened name is the arm's own case, which is where
     * {@code | X as v} is the spelling to reach for when X turns out not to be a newtype. An inner
     * layer (and Option's element) is reached through the case, so no such binding replaces it. */
    static void checkOpenedLayers(Hir.Case c, List<Hir.Name> opened, Symbols symbols,
                                  boolean firstOpensTheCase) {
        for (int i = 0; i < opened.size(); i++) {
            String name = opened.get(i).written();
            TypeSymbol layer = names(opened.get(i));
            Type inner = TypeOps.newtypeInner(layer, symbols);
            if (inner == null) {
                Diagnostic.Builder d = Diagnostic.at(c.pos())
                        .say(new MatchMessage.NotANewtypeToOpen(name));
                if (i == 0 && firstOpensTheCase) {
                    d = d.hint(new MatchMessage.BindTheMatchedValueInstead(name));
                }
                throw CompileException.of(d.build());
            }
            if (i + 1 < opened.size()) {
                Hir.Name next = opened.get(i + 1);
                // the layer a pattern claims is compared as a type, so an inner newtype named
                // through its module opens the same one an imported bare name does
                if (!(inner instanceof Type.Ref ir) || !ir.name().equals(names(next))) {
                    String innerName = inner instanceof Type.Ref r ? r.name().name() : Type.show(inner);
                    throw CompileException.of(Diagnostic.at(c.pos())
                            .say(new MatchMessage.TheNewtypeWrapsAnotherType(name, innerName,
                                    next.written()))
                            .build());
                }
            }
        }
    }

    /** Extends {@code env} with {@code binding} when both it and its type are present; otherwise
     * returns it as is. */
    static Scope bound(Scope env, Hir.Binder binding, Type type) {
        return binding == null || type == null ? env : env.with(binding, type);
    }

    static Type mergeBranch(Hir.Match m, Type branchType, Type bt, Hir.Case c, Type expected) {
        if (branchType == null) {
            return bt;
        }
        // arms merge by the join `if` branches use — equal types collapse, data-like ones widen to
        // their union, and both hold under a collection or a tuple as well (spec §if). An arm
        // answering a primitive joins only where the output written for this behavior says so.
        Type joined = TypeOps.join(branchType, bt);
        if (joined == null) {
            joined = TypeOps.joinAt(expected, branchType, bt);
        }
        if (joined != null) {
            return joined;
        }
        throw CompileException.of(Diagnostic.at(c.pos())
                .say(new MatchMessage.TheBranchesDisagree(Type.show(branchType), Type.show(bt)))
                .diff(Type.show(bt, branchType), Type.show(branchType, bt)).build());
    }

    /** A non-exhaustive-match error (E1201) listing every missing case. The legacy message names the
     * first missing case, as it did before, so callers reading the text are unchanged. */
    static CompileException nonExhaustive(SourcePos pos, String what, List<String> missing) {
        return CompileException.of(Diagnostic
                        .at(pos, 5)
                        
                        .hint(new DeclarationMessage.AddACaseFor(String.join(", ", missing)))
                        .say(new DeclarationMessage.TheMatchDoesNotCoverEveryCase(what)).build());
    }
}
