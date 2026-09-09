package souther.compiler.check;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.MonitorInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NopInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Everywhere an operator can be, and what each of them wants one for.
 *
 * <p>Beside {@link AnOperatorIsAskedWhatItPlacesInOnePlaceTest}, which says how often an operator
 * may be read for what it places. That one holds the crossing from how a comparison is written to
 * what it means to one call; this one is the ground it stands on, so that an operator reaching
 * somewhere new is a line that has to be written down and given a reason. There is no crossing back:
 * what a comparison places is taken from a relation without an operator in between
 * ({@link ComparisonClaim#stating}), so a relation never becomes one.
 *
 * <p><b>And no line here is one a reader has because it holds a comparison.</b> Every reader below
 * a recognition once had the node it was recognised from, which is one call from the operator, and
 * what kept it out of this list was that nobody had made that call yet — a fact about the code and
 * not about the type. {@link souther.compiler.check.Comparison} holds no node now: what it carries
 * is what the operator placed and the two sides it placed it on, and which comparison a reader is
 * talking about is a name of its own. So a line arriving here for that reason is a reader that has
 * gone back to the tree, and that is what it would have to say.
 *
 * <p><b>One list, and the rule it comes from is the machine's.</b> A method that has an operator
 * names the type: to use a value as one, the type has to be established, and a symbolic reference
 * is how that is written down. Which instructions can carry one is a closed set the class file
 * format decides, and {@link #namesTheOperator} is held to that set by javac rather than by
 * anybody's memory of it. So what is here is every method whose code names the operator, together
 * with every method whose signature does and every field of that type — not a list of the ways
 * somebody thought of.
 *
 * <p><b>And nothing here works out what was done with it.</b> A switch, a constant compared
 * against, a set asked for membership and a map asked for an answer are four spellings of one act,
 * and a check that knew three of them would be a check somebody could write the fourth past. So
 * what is fixed is the having, and what each holder does with it is the reason written beside it.
 *
 * <p>Which puts the copiers in the list. A pass that rebuilds a tree meets every operator it walks
 * over and writes each one into the node it is making, and there is nothing to say about that
 * beyond saying it — a line reading "copies it into the node it is rebuilding" is a line anyone who
 * made it do more would have to edit.
 *
 * <p><b>What it does not see, said rather than left to be found.</b> An operator reached by name
 * through reflection is a string, and a string is not a reference to the type. And what is read is
 * this module's classes, so a reader of an operator compiled elsewhere is not here. Both are things
 * to be told about rather than things this can be widened to cover.
 *
 * <p><b>A list and not a count.</b> A number stays right while one line goes and another arrives,
 * and the arriving one is exactly what this is for.
 */
class WhereAnOperatorMayStillBeHeldIsWrittenDownTest {

    private static final String BIN_OP = "Lsouther/compiler/types/BinOp;";

    private static final String OPERATOR = "souther/compiler/types/BinOp";

    /** Something that has an operator, and what it has one for. */
    private record Held(String what, String why) { }

    /**
     * What is handed an operator: a field it is stored in, and a method a caller passes one to.
     *
     * <p>Written {@code owner#field} and {@code owner.method}.
     */
    private static final List<Held> HANDED_IN = List.of(
            new Held("souther.compiler.ast.Hir.Binary#op",
                    "the resolved tree, which is where an operator is written down"),
            new Held("souther.compiler.core.Core.Binary#op",
                    "the tree a check produces, which carries what the source wrote"),
            new Held("souther.compiler.coverage.Settled.Operator#op",
                    "what a comparison of a body compares by, as part of saying what that body"
                            + " does: two bodies alike but for an operator do different things, so"
                            + " a numbering of one is not a numbering of the other. Copied out of"
                            + " the tree rather than read for anything — nothing here asks what the"
                            + " operator means, only whether two of them are the same"),

            new Held("souther.compiler.check.ComparisonPlacement.of",
                    "the one reading of an operator for what it places"),
            new Held("souther.compiler.semantics.ConditionJoin.of",
                    "the one reading of an operator for what a connective composes"),

            new Held("souther.compiler.check.ArithmeticCheck.of",
                    "which operands an operator takes and what it answers, which is a question"
                            + " about the operator itself"),
            new Held("souther.compiler.check.BinaryElaborator.operandBeside",
                    "what the operator asks of one operand, given the one beside it"),
            new Held("souther.compiler.check.HelperParams.BodyTyping.visitOperand",
                    "types an operand under the operator it stands beside"),
            new Held("souther.compiler.check.ConstEval.arith",
                    "what the operator computes of two constants"),
            new Held("souther.compiler.reading.Meetings.run",
                    "walks the operands under the operator they are written with"),

            new Held("souther.compiler.semantics.Arithmetic.TheOperator#op",
                    "a library operation declared to compute what an operator computes"),
            new Held("souther.compiler.semantics.NumericResult.TheOtherCaseWhen#op",
                    "a library fact stating the case an operation answers something else in, as a"
                            + " comparison against a number: an operator standing for what it"
                            + " means, in a table nothing recognises a comparison out of"),

            new Held("souther.compiler.check.NumericMeaning.Operator#op",
                    "an arithmetic expression keyed by the operator written in it — an operator"
                            + " answering no number is refused where one of these is made, so a"
                            + " comparison is not among the operators this holds"),
            new Held("souther.compiler.check.Terms.written",
                    "makes the term a written binary is, off the operator it was written with,"
                            + " which is an arithmetic one: what reaches it is a divide it names"
                            + " itself and the operator a NumericMeaning.Operator was made with"),
            new Held("souther.compiler.check.Term.Interner.operator",
                    "makes the term an operator over two operands is, and refuses a comparison:"
                            + " what a comparison of two values comes to is decided from what it"
                            + " placed and arrives already stated"),
            new Held("souther.compiler.coverage.SourceOutcome.Compared#op",
                    "the operator an outcome was written with, which is what a report shows"),

            // And what puts each of those where it is: a value is handed its operator when it is
            // made, which is the one call that decides what it will hold for as long as it lives.
            new Held("souther.compiler.ast.Hir.Binary.<init>",
                    "makes the node the resolved tree holds"),
            new Held("souther.compiler.core.Core.Binary.<init>",
                    "makes the node a check produces"),
            new Held("souther.compiler.check.NumericMeaning.Operator.<init>",
                    "makes the arithmetic meaning keyed by an operator, and is what limits which"
                            + " operators one of them may be keyed by to those answering a number"),
            new Held("souther.compiler.coverage.SourceOutcome.Compared.<init>",
                    "makes the outcome a report shows an operator for"),
            new Held("souther.compiler.coverage.Settled.Operator.<init>",
                    "makes the word a numbering says a comparison of a body compares by"),
            new Held("souther.compiler.semantics.Arithmetic.TheOperator.<init>",
                    "makes the fact that a library operation computes what an operator computes,"
                            + " and is what limits that to the operators answering a number"),
            new Held("souther.compiler.semantics.NumericResult.TheOtherCaseWhen.<init>",
                    "makes the fact stating an operation's other case as a comparison"));

    /** What fetches an operator: answered by a call, read out of a field, or named as a constant. */
    private static final List<Held> FETCHED = List.of(
            // Recognising a comparison, which is what carries the claim to everything below.
            new Held("souther.compiler.check.Comparison.of",
                    "asks what the operator places, for a binary of a checked body"),
            new Held("souther.compiler.check.ClauseComparison.of",
                    "the same, for a clause of a data"),
            new Held("souther.compiler.inputs.ComparedNumber.of",
                    "the same, for any binary a walk over the input space met"),
            // Reading a term for what it says, where the operator is part of what it says.
            new Held("souther.compiler.check.TermMeaning.project",
                    "puts it among what two readings of a term are compared by: two comparisons"
                            + " differing in it say different things"),
            // Rebuilding a tree, which carries the operator across unchanged.
            new Held("souther.compiler.ast.Hir.atSlots",
                    "copies it into the node it is rebuilding"),
            new Held("souther.compiler.ast.Hir.withRegion", "the same, under a region"),
            new Held("souther.compiler.core.Core.atSlots",
                    "copies it into the node it is rebuilding"),
            new Held("souther.compiler.check.HelperInliner.inline",
                    "copies it into the node a spliced helper becomes"),
            new Held("souther.compiler.check.HelperInliner.rename",
                    "the same, under a renaming of what the body bound"),
            new Held("souther.compiler.check.NewtypeDesugar.go",
                    "copies it into the node a newtype's rule becomes"),
            new Held("souther.compiler.check.BinaryElaborator.arithmetic",
                    "writes it into the checked node, and into the one inside a construction where"
                            + " the answer is a newtype"),
            new Held("souther.compiler.check.Terms.asWrittenValue",
                    "writes it back into the syntax a value is rendered as"),
            new Held("souther.compiler.check.Resolve.expr",
                    "writes what that answered into the node the resolved tree holds"),

            // Handing it on to something that answers about it.
            new Held("souther.compiler.check.HelperParams.BodyTyping.visit",
                    "hands it to what types an operand standing beside it"),
            new Held("souther.compiler.check.Terms.numericMeaningOf",
                    "asks whether it computes a number, and keys the meaning by it"),
            new Held("souther.compiler.check.Terms.namedByRule", "asks whether it computes a number"),
            new Held("souther.compiler.check.Terms.lambda$binary$1",
                    "hands it to the interner, for a binary the recognition beside it found to be"
                            + " no comparison"),
            new Held("souther.compiler.check.Terms.asOperator",
                    "reads the operator an arithmetic meaning was keyed by"),
            new Held("souther.compiler.check.Terms.theOneOf", "the same, for the meaning it interns"),
            new Held("souther.compiler.check.Terms.openedKey", "the same, for the term it keys"),
            new Held("souther.compiler.check.Terms.recipeFor", "the same, for the recipe it names"),
            new Held("souther.compiler.check.NumericMeanings.of",
                    "keys an arithmetic meaning by the operator a library operation computes"),
            new Held("souther.compiler.check.DischargeRules.formOperations",
                    "asks which operator a library operation is defined as"),
            new Held("souther.compiler.coverage.CoverageSites.Walk.number",
                    "records the operator an outcome was written with, which is what a report shows"),
            new Held("souther.compiler.coverage.ExecutableIdentity.say",
                    "what a comparison does, written into what says whether two bodies do the same"
                            + " thing: two bodies alike but for which way they compare are two"
                            + " bodies, and a run recorded against one says nothing about the"
                            + " other"),
            new Held("souther.compiler.report.ArmVocabulary.label",
                    "writes the operator into the words a report shows for an arm"),
            new Held("souther.compiler.reading.Meetings.run",
                    "gathers the operands one operator reaches, which is what it is asked about"),
            new Held("souther.compiler.check.PathReachability.unansweredAt",
                    "names the operator in what it says went unanswered"),
            new Held("souther.compiler.check.TheOtherCase.conditionAt",
                    "composes the comparison a library fact states its other case by, out of the"
                            + " operator that fact holds: what is written there is read back as a"
                            + " comparison by everything downstream, and nothing recognised it"),

            // Handing the operator to the one reading of what a connective composes. Each of these
            // has the operator only as far as that call: what it asks afterwards is of the
            // composition, and where a polarity is carried it is applied to the composition.
            new Held("souther.compiler.check.ClauseExpr.of",
                    "the composition under the polarity the tree is read with, which the shape it"
                            + " makes carries so that nothing below it holds the operator"),
            new Held("souther.compiler.check.ClauseHelpers.expandedOver",
                    "writes a clause back into the nodes its author joined its parts with, which is"
                            + " the operator copied out of the node it was written at. Nothing here"
                            + " asks what it means: the shape being walked already said these two"
                            + " are parts of one clause"),
            new Held("souther.compiler.check.ClauseHelpers.shaped",
                    "the shape an author wrote a clause in, which is walking into what composes"
                            + " both halves. The one walk that says what the parts are; everything"
                            + " else about them is read off the shape it leaves"),
            new Held("souther.compiler.check.FieldDomains.lambda$projection$6",
                    "walks into both halves for the clause that bounds a field"),
            new Held("souther.compiler.partition.Condition.of",
                    "the composition, which the shape it makes carries"),
            new Held("souther.compiler.partition.ClauseStatements.walk",
                    "walks into both halves, and stops where the connective composes either of"
                            + " them because what such a rule states is neither of its sides"),

            // Which operand runs when, asked of the operator itself rather than of what it
            // composes: the two are answered by the same two operators and are not one question.
            new Held("souther.compiler.partition.ComparisonReadings.walk",
                    "walks both sides of a conjunction and of a disjunction, each under what the"
                            + " other side leaves"),

            // Which of a condition's parts decide the fork, which the two connectives are what
            // divides: either side coming out the wrong way is the whole answer, so a rule about
            // either is a rule the fork tests.
            new Held("souther.compiler.partition.ConditionSkeleton.cut",
                    "asks whether the operator joins two conditions, for cutting a condition into"
                            + " the parts a rule can be about"),

            // Which operand runs when, which is the enum's own answer.
            new Held("souther.compiler.core.Evaluated.inOrder",
                    "asks the enum which way the left has to come out for the right to run"),
            new Held("souther.compiler.flow.ValueArrivals.reading",
                    "asks the enum whether the right operand runs on every run"),
            new Held("souther.compiler.flow.ValueArrivals.through", "and which way it runs under"),
            new Held("souther.compiler.reading.CoverageRead.descend",
                    "asks the enum whether the right operand runs on every run"),
            new Held("souther.compiler.reading.CoverageRead.rightOf", "and which way it runs under"),
            new Held("souther.compiler.reading.Meetings.meetingAt",
                    "asks the enum whether the operator stops early"),
            new Held("souther.compiler.check.PathReachability.walk",
                    "asks the enum whether there is a right side that runs on some runs and not"
                            + " others, and which way the left has to come out for it to"),

            // What the operator computes, is typed as, or is emitted as.
            new Held("souther.compiler.check.AffineForms.composed",
                    "which arithmetic an expression composes into a form"),
            new Held("souther.compiler.check.BinaryElaborator.elaborateBinary",
                    "what the operator makes of its operands' types"),
            new Held("souther.compiler.check.BinaryElaborator.operand",
                    "asks whether the operator joins two conditions, which is the one thing an"
                            + " operand can be held to before the one beside it has been read"),
            new Held("souther.compiler.check.ConstEval.binary",
                    "what the operator computes of two constants, which operand it needs to compute"
                            + " it, and — for the six that compare — what it placed, which is what"
                            + " the fold is asked for instead of the operator"),
            new Held("souther.compiler.check.DischargeRules.noSmallerThan",
                    "which operands a string joined by another is no shorter than"),
            new Held("souther.compiler.core.GrowingFold.appended",
                    "what a fold appends, which is what joining strings is"),
            new Held("souther.compiler.examples.FixtureReader.fold",
                    "what the operator computes of two numbers a fixture wrote"),
            new Held("souther.compiler.codegen.BodyGen.binary",
                    "which instructions the operator is emitted as, and — for the six it emits a"
                            + " comparison for — the recognition everything below it holds"),
            new Held("souther.compiler.codegen.BodyGen.lambda$binary$0",
                    "names the operator in what it says of a comparison that placed nothing"),

            // And whether it compares at all, asked of the one place that says so — by a reader
            // recognising a comparison, and by no reader below one.
            new Held("souther.compiler.check.Relates.twoPositions",
                    "asks the enum whether the operator compares, for a rule that stands one"
                            + " position against another"),

            // Handing back the operator a value of one's own holds.
            new Held("souther.compiler.ast.Hir.Binary.op", "hands back what the node holds"),
            new Held("souther.compiler.core.Core.Binary.op", "hands back what the node holds"),
            new Held("souther.compiler.check.NumericMeaning.Operator.op",
                    "hands back the operator an arithmetic meaning is keyed by"),
            new Held("souther.compiler.coverage.SourceOutcome.Compared.op",
                    "hands back the operator an outcome was written with"),
            new Held("souther.compiler.coverage.Settled.Operator.op",
                    "hands back the operator a body's comparison compares by"),
            new Held("souther.compiler.semantics.Arithmetic.TheOperator.op",
                    "hands back the operator a library operation computes"),
            new Held("souther.compiler.semantics.NumericResult.TheOtherCaseWhen.op",
                    "hands back the operator a library fact states its other case by"),
            new Held("souther.compiler.check.DischargeRules.operator",
                    "reads the operator a library operation is declared to compute"),

            // Naming a constant, which is the other way to come by one.
            new Held("souther.compiler.check.Resolve.binOp",
                    "names the constant the parsed tree's own operator denotes, which is the one"
                            + " place the two vocabularies are held together: both sides written"
                            + " out, so an operator added to what may be written stops the compile"
                            + " until somebody says what it means here"),
            new Held("souther.compiler.check.Conditions.asSizeComparison",
                    "writes the equality an emptiness check means: a size stood against nought"),
            new Held("souther.compiler.check.ClauseExpr.againstATruthValue",
                    "asks whether the operator is the equality or the disequality, for a comparison"
                            + " one side of which is a written truth value: which of the two it is"
                            + " and which value was written are together what says whether such a"
                            + " comparison states the other side or denies it"),
            new Held("souther.compiler.check.Conditions.AsPolar.theSameValue",
                    "writes the equality a fact is keyed by, which the claim said this comparison"
                            + " states"),
            new Held("souther.compiler.check.Conditions.AsPolar.below",
                    "writes the order a fact is keyed by, with the sides the way the claim wanted"
                            + " them"),
            new Held("souther.compiler.check.Conditions.AsPolar.canonical",
                    "the one place a canonical comparison is spelled as a node: written over"
                            + " nothing the source holds, because a statement stands nowhere"),
            new Held("souther.compiler.check.Term.Interner.AsTerms.below",
                    "the one order a term is written in, which the claim exchanged the sides for"),
            new Held("souther.compiler.check.Terms.repeating",
                    "writes the multiplication a repeated accumulation comes to"),
            new Held("souther.compiler.semantics.OperationFacts.declared",
                    "the library's own table, naming the operator each arithmetic operation"
                            + " computes"),
            new Held("souther.compiler.semantics.OperationFacts.computesInTheCaseCarrying",
                    "the same, for an operation whose other case is stated as a comparison"),
            new Held("souther.compiler.check.OperationFactBinder.holdNumericResult",
                    "carries the operator that fact states its other case by across into the"
                            + " bound fact, unchanged: what changes in the binding is the argument"
                            + " it compares, not how"),
            new Held("souther.compiler.check.ArithmeticCheck.of",
                    "names the constants it has rules for, against the operator it was handed"),
            new Held("souther.compiler.check.BinaryElaborator.operandBeside",
                    "names the two that scale a newtype"),
            new Held("souther.compiler.check.Term.Interner.operator",
                    "names the constants the canonical terms are keyed by, which is the"
                            + " six-into-three the reading of a guard now takes from what was"
                            + " placed"));

    @Test
    void everythingWithAnOperatorIsWrittenDownWithAReason() {
        assertEquals(declared(everything()), found(hasAnOperator()),
                "an operator reaching somewhere new is a line to be written here with what it is"
                        + " wanted for. What each of these has one for: " + why(everything()));
    }

    /** The two parts above as the one list they are, since a method is often both. */
    private static List<Held> everything() {
        Map<String, Held> out = new LinkedHashMap<>();
        HANDED_IN.forEach(each -> out.putIfAbsent(each.what(), each));
        FETCHED.forEach(each -> out.putIfAbsent(each.what(), each));
        return List.copyOf(out.values());
    }

    /**
     * Everything with an operator: a field of that type, and a method whose signature names one or
     * whose code does.
     */
    private static List<String> hasAnOperator() {
        List<String> out = new ArrayList<>();
        forEachClass((owner, model) -> {
            for (MethodModel method : model.methods()) {
                boolean has = method.methodType().stringValue().contains(BIN_OP);
                for (CodeElement element
                        : method.code().map(code -> code.elementList()).orElse(List.of())) {
                    has |= element instanceof Instruction one && namesTheOperator(one);
                }
                if (has) {
                    out.add(owner + "." + method.methodName().stringValue());
                }
            }
            for (FieldModel field : model.fields()) {
                if (field.fieldType().stringValue().contains(BIN_OP)) {
                    out.add(owner + "#" + field.fieldName().stringValue());
                }
            }
        });
        return out;
    }

    /**
     * Whether one instruction names the operator.
     *
     * <p>An instruction either carries a symbolic reference or it does not, and which ones do is the
     * class file format's answer rather than anybody's. <b>Written without a default, so that it is
     * javac's answer here too.</b> The kinds are sealed: an arm left out is a compile error, and an
     * instruction the format grows later is a compile error the day the runtime is raised. Read off
     * a list somebody typed, this said there was no other way to name a type four times over, and
     * there was one each time.
     *
     * <p>What each arm says is only whether that kind of instruction can name a type. The ones that
     * cannot are the ones that move, compute or jump: what they work on is already on the stack, and
     * where it was put there is the arm that answers.
     */
    private static boolean namesTheOperator(Instruction instruction) {
        return switch (instruction) {
            // Carries one.
            case InvokeInstruction call -> names(call.owner().asInternalName())
                    || names(call.type().stringValue());
            case FieldInstruction field -> names(field.owner().asInternalName())
                    || names(field.type().stringValue());
            case TypeCheckInstruction cast -> names(cast.type().asInternalName());
            case NewObjectInstruction made -> names(made.className().asInternalName());
            case NewReferenceArrayInstruction made -> names(made.componentType().asInternalName());
            case NewMultiArrayInstruction made -> names(made.arrayType().asInternalName());
            case ConstantInstruction constant -> names(String.valueOf(constant.constantValue()));
            case InvokeDynamicInstruction dynamic -> names(dynamic.type().stringValue())
                    || dynamic.bootstrapArgs().stream()
                            .anyMatch(each -> names(String.valueOf(each)));

            // Names a type that is never one of ours: an array of a primitive.
            case NewPrimitiveArrayInstruction _ -> false;

            // Carries no reference at all. Each works on what is already on the stack, on a number,
            // or on where to go next — and where what it works on was put there is an arm above.
            case ArrayLoadInstruction _, ArrayStoreInstruction _, BranchInstruction _,
                    ConvertInstruction _, DiscontinuedInstruction _, IncrementInstruction _,
                    LoadInstruction _, LookupSwitchInstruction _, MonitorInstruction _,
                    NopInstruction _, OperatorInstruction _, ReturnInstruction _,
                    StackInstruction _, StoreInstruction _, TableSwitchInstruction _,
                    ThrowInstruction _ -> false;
        };
    }

    /** Whether a name or a descriptor is the operator's, an array of them and a member of it
     *  included. */
    private static boolean names(String what) {
        return what.contains(BIN_OP) || what.equals(OPERATOR) || what.startsWith(OPERATOR + ".");
    }

    private static Map<String, String> declared(List<Held> held) {
        Map<String, String> out = new TreeMap<>();
        held.forEach(each -> out.put(each.what(), ""));
        return out;
    }

    private static Map<String, String> why(List<Held> held) {
        Map<String, String> out = new LinkedHashMap<>();
        held.forEach(each -> out.put(each.what(), each.why()));
        return out;
    }

    /** What was found, as a map, so that a missing line reads beside the reasons rather than as a
     *  place in a list. */
    private static Map<String, String> found(List<String> what) {
        Map<String, String> out = new TreeMap<>();
        what.forEach(each -> out.put(each, ""));
        return out;
    }

    private interface EachClass {
        void read(String owner, ClassModel model);
    }

    private static void forEachClass(EachClass each) {
        int read = 0;
        try {
            for (Path path : classes()) {
                ClassModel model = ClassFile.of().parse(Files.readAllBytes(path));
                String owner = model.thisClass().asInternalName()
                        .replace('/', '.').replace('$', '.');
                if (owner.equals("souther.compiler.types.BinOp")) {
                    // The operator itself, whose own members are what an enum is made of.
                    continue;
                }
                if (model.flags().has(java.lang.reflect.AccessFlag.SYNTHETIC)) {
                    // A class the compiler wrote, not a place anybody reads an operator: what
                    // javac emits to switch over an enum holds one, on behalf of the method that
                    // does the switching — and that method is in the list under its own name. An
                    // anonymous class is not one of these; it holds what somebody wrote.
                    continue;
                }
                read++;
                each.read(owner, model);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        assertFalse(read == 0, "no compiled class was read at all, so this says nothing");
    }

    private static List<Path> classes() throws IOException {
        Path root = Path.of("target", "classes").toAbsolutePath();
        try (Stream<Path> walk = Files.walk(root)) {
            return new ArrayList<>(walk.filter(p -> p.toString().endsWith(".class")).toList());
        }
    }
}
