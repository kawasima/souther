package souther.architecture;

import souther.compiler.ast.ConstructionOrigin;
import souther.compiler.ast.Hir;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Who may settle what a construction was read as, and where.
 *
 * <p>Two checks beside this one read the caller: nothing outside {@code souther.compiler.ast} hands
 * an origin in, and nothing outside names which of its fields a construction had to write. Both see
 * an answer only where a call site spells one, so a member of the owning package that fills one in
 * for whoever calls it leaves every caller clean — which is what a constructor answering
 * {@code own} was, and what a factory answering {@code optionals may be omitted} would be.
 *
 * <p>What settles an answer is a member and the calls that reach it, so both are read here. The
 * members of the owning package that name an answer are listed, each by the whole of what it is, so
 * a second one of a name is a second row. And the calls that reach the members which <em>settle</em>
 * one are listed by the method that makes them and how many it makes, because a settling member is
 * public and being unable to reach its arguments is a thing about how the passes are written rather
 * than one the language holds.
 *
 * <p>The method rather than the class it is in, because a class holds passes that do different
 * things and a row naming the class says one thing about all of them. How many rather than whether,
 * because a second call beside a first is a second place settling an answer.
 *
 * <p>And nothing outside the package builds one of these forms at all. The list of callers says who
 * reaches a named way in; it says nothing about a pass that reaches past them, and a form here is a
 * record whose constructor answers whatever it is handed. The two are separate checks because they
 * answer different questions — which way in was used, and whether a way in was used.
 *
 * <p>No list here says what a rewrite is. The first is every member of a package, read off its
 * classes; the others are the calls that reach them, read off the whole reactor. A pass added
 * tomorrow is in them or it does not settle an answer.
 */
class EveryPlaceAnAnswerAboutAConstructionIsNamedTest {

    private static final String THEIRS = "souther/compiler/ast/";

    /** The same package, as a call's owner says it — exactly, so one under it is not it. */
    private static final String THEIR_PACKAGE = "souther/compiler/ast";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * A member a settling call may name, and the word the edges below are written with.
     *
     * <p>The word is what a reader of an edge wants: which act a caller reached for. A descriptor
     * says the same thing and says it in forty characters of type names, so an edge written with one
     * is read by working out what it is rather than by reading it.
     *
     * <p>The whole signature is here beside the word, because that is what an edge is matched on. A
     * word standing for a member is only as good as the member it stands for being the one it was
     * written for: matched by name alone, an overload added tomorrow would be written with a word
     * that means the other one.
     */
    private record Settler(String word, String owner, String name, String descriptor) {

        /** The member, whichever of its overloads a call names — what says an invocation settles. */
        String member() {
            return owner + "#" + name;
        }

        /** The one overload, which is what an edge is written from. */
        String signature() {
            return member() + descriptor;
        }
    }

    /**
     * The members that settle an answer rather than carrying or asking one: a source read, the
     * translation of an application into the construction it means, a pass composing one, and the
     * two crossings that make one answer out of another.
     *
     * <p>Every overload is here, the ones that settle nothing themselves included. {@code synthetic}
     * taking a spelling hands its arguments to the one taking an expression, so it is not in
     * {@link #NAMING} — and a caller naming it is still a caller reaching a settle, which the edge
     * says by the door it came through. Whether an overload settles is what the other list answers;
     * this one answers what a call reaches.
     */
    private static final List<Settler> SETTLERS = List.of(
            new Settler("NewData.read", "souther/compiler/ast/Hir$NewData", "read",
                    "(Lsouther/compiler/ast/Ast$NewData;Lsouther/compiler/ast/Hir$Name;"
                            + "Ljava/util/List;Ljava/util/List;Lsouther/compiler/ast/Reading;)"
                            + "Lsouther/compiler/ast/Hir$NewData;"),
            new Settler("NewData.fromApply", "souther/compiler/ast/Hir$NewData", "fromApply",
                    "(Lsouther/compiler/ast/Hir$Apply;Lsouther/compiler/ast/Hir$Name;"
                            + "Ljava/util/List;)Lsouther/compiler/ast/Hir$NewData;"),
            new Settler("NewData.syntheticWithEveryFieldWritten",
                    "souther/compiler/ast/Hir$NewData", "syntheticWithEveryFieldWritten",
                    "(Lsouther/compiler/ast/Hir$Name;Ljava/util/List;Ljava/util/List;"
                            + "Lsouther/compiler/diag/SourcePos;Lsouther/compiler/diag/Region;)"
                            + "Lsouther/compiler/ast/Hir$NewData;"),
            new Settler("NewData.publishedBy", "souther/compiler/ast/Hir$NewData", "publishedBy",
                    "(Ljava/lang/String;)Lsouther/compiler/ast/Hir$NewData;"),
            new Settler("NewData.carriedByValue", "souther/compiler/ast/Hir$NewData",
                    "carriedByValue", "()Lsouther/compiler/ast/Hir$NewData;"),
            new Settler("Apply.read", "souther/compiler/ast/Hir$Apply", "read",
                    "(Lsouther/compiler/ast/Ast$Apply;Lsouther/compiler/ast/Hir$AppliedCallee;"
                            + "Lsouther/compiler/ast/Hir$Expr;Ljava/util/List;)"
                            + "Lsouther/compiler/ast/Hir$Apply;"),
            new Settler("Apply.synthetic(Expr)", "souther/compiler/ast/Hir$Apply", "synthetic",
                    "(Lsouther/compiler/ast/Hir$Expr;Ljava/util/List;"
                            + "Lsouther/compiler/types/ApplicationOrigin;"
                            + "Lsouther/compiler/diag/SourcePos;Lsouther/compiler/diag/Region;)"
                            + "Lsouther/compiler/ast/Hir$Apply;"),
            new Settler("Apply.synthetic(String)", "souther/compiler/ast/Hir$Apply", "synthetic",
                    "(Ljava/lang/String;Lsouther/compiler/types/ReachName;"
                            + "Lsouther/compiler/types/ReferenceOrigin;"
                            + "Lsouther/compiler/types/ApplicationOrigin;Ljava/util/List;"
                            + "Lsouther/compiler/diag/SourcePos;Lsouther/compiler/diag/Region;)"
                            + "Lsouther/compiler/ast/Hir$Apply;"),
            new Settler("Apply.carriedByValue", "souther/compiler/ast/Hir$Apply", "carriedByValue",
                    "()Lsouther/compiler/ast/Hir$Apply;"),
            new Settler("Apply.with", "souther/compiler/ast/Hir$Apply", "with",
                    "(Lsouther/compiler/ast/Hir$AppliedCallee;Lsouther/compiler/ast/Hir$Expr;"
                            + "Ljava/util/List;Lsouther/compiler/diag/SourcePos;"
                            + "Lsouther/compiler/diag/Region;)Lsouther/compiler/ast/Hir$Apply;"));

    /**
     * Every member of the owning package that names an answer, by the whole of what it is — owner,
     * member, what it takes and what it answers with. A second member of a name is a second row, so
     * an overload that settles an answer of its own cannot stand behind one that already does.
     *
     * <p>Four settle one: {@code read} on each form is a source spelling it, {@code fromApply}
     * moves to a construction what the application it means already answered, and
     * {@code syntheticWithEveryFieldWritten} and {@code synthetic} are a pass writing one where no
     * source did. Three move an answer along the crossings a construction has:
     * {@code publishedBy}, {@code carriedByValue} and the {@code Origins} members that say what
     * each crossing does. The rest carry or ask — {@code with}, {@code withArgs} and
     * {@code replacedBy} put back what they were handed and
     * {@code atSlots} and {@code withRegion} are the rewrites that go through them, while
     * {@code mayOmitOptionalFields}, {@code wasCarried}, {@code wasCarriedByValue} and
     * {@code Origins#carried} are the questions a check puts to a node. The accessors and the
     * enum's own members are here because naming an answer is what an accessor does.
     *
     * <p>An overload that only hands its arguments to one of these is not here, and needs not be:
     * it settles nothing itself, and a call to it is a call to what it delegates to, which the
     * other list holds.
     */
    private static final List<String> NAMING = List.of(
            "souther/compiler/ast/Hir#atSlots(Lsouther/compiler/ast/Hir$Expr;Ljava/util/function/UnaryOperator;Ljava/util/function/UnaryOperator;)Lsouther/compiler/ast/Hir$Expr;",
            "souther/compiler/ast/Hir#withRegion(Lsouther/compiler/ast/Hir$Expr;Lsouther/compiler/diag/Region;)Lsouther/compiler/ast/Hir$Expr;",
            "souther/compiler/ast/Hir$Apply#carriedByValue()Lsouther/compiler/ast/Hir$Apply;",
            "souther/compiler/ast/Hir$Apply#origin()Lsouther/compiler/ast/ConstructionOrigin;",
            "souther/compiler/ast/Hir$Apply#read(Lsouther/compiler/ast/Ast$Apply;Lsouther/compiler/ast/Hir$AppliedCallee;Lsouther/compiler/ast/Hir$Expr;Ljava/util/List;)Lsouther/compiler/ast/Hir$Apply;",
            "souther/compiler/ast/Hir$Apply#replacedBy(Lsouther/compiler/ast/Hir$Expr;Ljava/util/List;)Lsouther/compiler/ast/Hir$Apply;",
            "souther/compiler/ast/Hir$Apply#synthetic(Lsouther/compiler/ast/Hir$Expr;Ljava/util/List;Lsouther/compiler/types/ApplicationOrigin;Lsouther/compiler/diag/SourcePos;Lsouther/compiler/diag/Region;)Lsouther/compiler/ast/Hir$Apply;",
            "souther/compiler/ast/Hir$Apply#wasCarriedByValue()Z",
            "souther/compiler/ast/Hir$Apply#with(Lsouther/compiler/ast/Hir$AppliedCallee;Lsouther/compiler/ast/Hir$Expr;Ljava/util/List;Lsouther/compiler/diag/SourcePos;Lsouther/compiler/diag/Region;)Lsouther/compiler/ast/Hir$Apply;",
            "souther/compiler/ast/Hir$Apply#withArgs(Ljava/util/List;)Lsouther/compiler/ast/Hir$Apply;",
            "souther/compiler/ast/Hir$Fields#$values()[Lsouther/compiler/ast/Hir$Fields;",
            "souther/compiler/ast/Hir$Fields#values()[Lsouther/compiler/ast/Hir$Fields;",
            "souther/compiler/ast/Hir$NewData#carriedByValue()Lsouther/compiler/ast/Hir$NewData;",
            "souther/compiler/ast/Hir$NewData#fields()Lsouther/compiler/ast/Hir$Fields;",
            "souther/compiler/ast/Hir$NewData#fromApply(Lsouther/compiler/ast/Hir$Apply;Lsouther/compiler/ast/Hir$Name;Ljava/util/List;)Lsouther/compiler/ast/Hir$NewData;",
            "souther/compiler/ast/Hir$NewData#mayOmitOptionalFields()Z",
            "souther/compiler/ast/Hir$NewData#origin()Lsouther/compiler/ast/ConstructionOrigin;",
            "souther/compiler/ast/Hir$NewData#publishedBy(Ljava/lang/String;)Lsouther/compiler/ast/Hir$NewData;",
            "souther/compiler/ast/Hir$NewData#read(Lsouther/compiler/ast/Ast$NewData;Lsouther/compiler/ast/Hir$Name;Ljava/util/List;Ljava/util/List;Lsouther/compiler/ast/Reading;)Lsouther/compiler/ast/Hir$NewData;",
            "souther/compiler/ast/Hir$NewData#syntheticWithEveryFieldWritten(Lsouther/compiler/ast/Hir$Name;Ljava/util/List;Ljava/util/List;Lsouther/compiler/diag/SourcePos;Lsouther/compiler/diag/Region;)Lsouther/compiler/ast/Hir$NewData;",
            "souther/compiler/ast/Hir$NewData#wasCarried(Lsouther/compiler/types/TypeSymbol$AtModule;)Z",
            "souther/compiler/ast/Hir$NewData#with(Ljava/util/List;Ljava/util/List;Lsouther/compiler/diag/SourcePos;Lsouther/compiler/diag/Region;)Lsouther/compiler/ast/Hir$NewData;",
            "souther/compiler/ast/Origins#carried(Lsouther/compiler/ast/ConstructionOrigin;Lsouther/compiler/types/TypeSymbol$AtModule;)Z",
            "souther/compiler/ast/Origins#carriedByValue(Lsouther/compiler/ast/ConstructionOrigin;)Lsouther/compiler/ast/ConstructionOrigin;",
            "souther/compiler/ast/Origins#publishedIn(Lsouther/compiler/ast/ConstructionOrigin;Ljava/lang/String;)Lsouther/compiler/ast/ConstructionOrigin;",
            "souther/compiler/ast/Origins$Published#module()Ljava/lang/String;");

    /**
     * Every call that settles an answer, by the method that makes it and how many it makes.
     *
     * <p>A source is read in one place: {@code Resolve} is what reads one, and it is the only caller
     * of either {@code read}. The crossings are {@code HelperNames}', which is what carries a body
     * into a reader. {@code NewtypeDesugar} is where an application means a construction. The rest
     * compose an application or a fixture no source spells, and each says so where it calls.
     *
     * <p>A row whose caller is not one of those is a pass answering for something it did not read.
     * Being able to call one of these is not what stops it — the forms are public and a parsed node
     * is a record anyone can build — so what stops it is this list.
     *
     * <p>The method and not the class, and how many and not whether. A class holds passes that do
     * different things: {@code HelperInliner} both writes a call for a name used as a value and
     * rewrites an application a source wrote, and a row naming the class says one thing about both.
     * The count is the same question a step in: a second call added beside a first is a second place
     * settling an answer, and a row that says only that its caller settles one somewhere would not
     * move for it.
     */
    private static final List<String> SETTLING = List.of(
            "souther/compiler/ast/Hir$Apply#synthetic(Ljava/lang/String;Lsouther/compiler/types/ReachName;Lsouther/compiler/types/ReferenceOrigin;Lsouther/compiler/types/ApplicationOrigin;Ljava/util/List;Lsouther/compiler/diag/SourcePos;Lsouther/compiler/diag/Region;)Lsouther/compiler/ast/Hir$Apply; -> Apply.synthetic(Expr) x1",
            "souther/compiler/check/Elaborator#fromList(Ljava/lang/String;Lsouther/compiler/ast/Hir$Expr;Lsouther/compiler/ast/Hir$RowCollection;)Lsouther/compiler/ast/Hir$Expr; -> Apply.synthetic(String) x1",
            "souther/compiler/check/HelperInliner#etaExpand(Lsouther/compiler/ast/Hir$Var;ILjava/util/function/IntFunction;)Lsouther/compiler/ast/Hir$Block; -> Apply.synthetic(Expr) x1",
            "souther/compiler/check/HelperInliner#rename(Lsouther/compiler/ast/Hir$Expr;Lsouther/compiler/check/HelperInliner$Renaming;)Lsouther/compiler/ast/Hir$Expr; -> Apply.with x1",
            "souther/compiler/check/HelperNames#carriedByValue(Lsouther/compiler/ast/Hir$Expr;)Lsouther/compiler/ast/Hir$Expr; -> Apply.carriedByValue x1",
            "souther/compiler/check/HelperNames#carriedByValue(Lsouther/compiler/ast/Hir$Expr;)Lsouther/compiler/ast/Hir$Expr; -> NewData.carriedByValue x1",
            "souther/compiler/check/HelperNames#publishedBy(Lsouther/compiler/ast/Hir$Expr;Ljava/lang/String;)Lsouther/compiler/ast/Hir$Expr; -> NewData.publishedBy x1",
            "souther/compiler/check/NewtypeDesugar#go(Lsouther/compiler/ast/Hir$Expr;Lsouther/compiler/check/Symbols;)Lsouther/compiler/ast/Hir$Expr; -> NewData.fromApply x1",
            "souther/compiler/check/Resolve#applied(Lsouther/compiler/ast/Ast$Apply;Lsouther/compiler/ast/Ast$Var;Lsouther/compiler/check/Resolve$InForce;)Lsouther/compiler/ast/Hir$Expr; -> Apply.read x1",
            "souther/compiler/check/Resolve#expr(Lsouther/compiler/ast/Ast$Expr;Lsouther/compiler/check/Resolve$InForce;)Lsouther/compiler/ast/Hir$Expr; -> Apply.read x1",
            "souther/compiler/check/Resolve#expr(Lsouther/compiler/ast/Ast$Expr;Lsouther/compiler/check/Resolve$InForce;)Lsouther/compiler/ast/Hir$Expr; -> NewData.read x1",
            "souther/compiler/check/Terms#asWrittenValue(Lsouther/compiler/core/Core;Lsouther/compiler/check/Denotations;Ljava/util/Map;)Lsouther/compiler/ast/Hir$Expr; -> Apply.synthetic(String) x2",
            "souther/compiler/partition/FixtureTemplate#newtype(Lsouther/compiler/types/TypeReachName$Written;Lsouther/compiler/partition/FixtureTemplate;)Lsouther/compiler/partition/FixtureTemplate; -> Apply.synthetic(String) x1",
            "souther/compiler/partition/FixtureTemplate#record(Lsouther/compiler/types/TypeReachName$Written;Ljava/util/Map;)Lsouther/compiler/partition/FixtureTemplate; -> NewData.syntheticWithEveryFieldWritten x1",
            "souther/compiler/partition/FixtureTemplate#spreading(Lsouther/compiler/types/TypeReachName$Written;Lsouther/compiler/partition/FixtureTemplate;Ljava/util/Map;)Lsouther/compiler/partition/FixtureTemplate; -> NewData.syntheticWithEveryFieldWritten x1",
            "souther/compiler/partition/FixtureTemplate#temporal(Ljava/lang/String;Ljava/lang/String;)Lsouther/compiler/partition/FixtureTemplate; -> Apply.synthetic(String) x1");

    @Test
    void everyMemberOfTheOwningPackageThatNamesAnAnswerIsWrittenDown() {
        assertEquals(NAMING, new ArrayList<>(namingAnAnswer()),
                "a row here is a way to answer what a construction was read as, or a reader of one:"
                        + " say which it is and why it is not the node's own answer carried");
    }

    /** The words the edges are written with stand for the members they say they do — the table an
     *  edge is rendered through, checked against the package before anything is rendered. */
    @Test
    void andEachWordTheEdgesAreWrittenWithStandsForOneMember() {
        assertEquals(SETTLERS.stream().map(Settler::signature).sorted().toList(),
                SETTLERS.stream().map(Settler::signature).distinct().sorted().toList(),
                "two rows of the settlers table name one member");
        assertEquals(SETTLERS.stream().map(Settler::word).sorted().toList(),
                SETTLERS.stream().map(Settler::word).distinct().sorted().toList(),
                "two members of the settlers table are written with one word");
        assertEquals(List.of(), SETTLERS.stream().map(Settler::signature)
                        .filter(each -> !declaredInThatPackage().contains(each)).toList(),
                "a settlers row names a member this package does not declare");
        // And the other way round, which is what makes the table the members rather than the ones
        // that happen to be called. An overload nobody calls yet settles what its siblings settle,
        // and a table that waited for a caller would be answered about it by whoever wrote one.
        assertEquals(everyOverloadOfASettler(),
                SETTLERS.stream().map(Settler::signature).sorted().toList(),
                "an overload of a settling member is a way in whether or not anything uses it yet");
    }

    @Test
    void andEveryCallThatSettlesOneIsWrittenDownWithWhoMakesIt() {
        assertEquals(SETTLING, settlingAnAnswer(),
                "settling an answer is the reading's to do and the crossings': a pass that rewrites"
                        + " a body carries what it was handed, and a row here that is not a reading"
                        + " or a crossing is a pass answering for a construction it did not read");
    }

    /**
     * And nobody outside the package that declares these forms builds one without going through
     * them.
     *
     * <p>The list above says who reaches a named way in. It says nothing about a pass that reaches
     * past them: a form here is a record, its canonical constructor is as accessible as the record,
     * and one called with an origin in hand answers whatever the caller put there. The way in being
     * named is what the list is about, and this is what makes it the way.
     *
     * <p>Inside the package the constructor is how a form is made — a reading builds one, a crossing
     * builds the next, a rewrite puts back what it was handed — so the boundary is the package and
     * not the record. What a member of it may answer is the {@link #NAMING} list's to say, which is
     * why one rule does not do for both.
     */
    @Test
    void andNobodyOutsideThatPackageBuildsOneOfItsFormsDirectly() {
        assertEquals(List.of(), buildingAFormDirectly(),
                "a form of this package is built where nothing names what it answers: reach it"
                        + " through the reading, the crossing or the rewrite that says which");
    }

    /** The control: the walk reads the whole reactor and not only the package it is about, which is
     *  where the calls it lists are made. */
    @Test
    void andTheWalkReadsEveryModulesClasses() {
        assertFalse(COMPILED.all().stream()
                        .allMatch(each -> each.thisClass().asInternalName().startsWith(THEIRS)),
                "the calls this reads are made outside the package that declares what they reach");
    }

    /** Every method of the owning package whose code names an answer, by owner, name and what it
     *  takes and answers with — so a second member of a name is a second row. */
    private static Set<String> namingAnAnswer() {
        Set<String> found = new TreeSet<>();
        for (ClassModel each : COMPILED.all()) {
            if (!each.thisClass().asInternalName().startsWith(THEIRS)) {
                continue;
            }
            ClassModel model = each;
            for (MethodModel method : model.methods()) {
                if (namesAnAnswer(method)) {
                    found.add(model.thisClass().name().stringValue() + "#"
                            + method.methodName().stringValue()
                            + method.methodType().stringValue());
                }
            }
        }
        return found;
    }

    /** Every call to a settling member, as the method that makes it, what it settles and how many
     *  of them that method makes. */
    private static List<String> settlingAnAnswer() {
        Map<String, Integer> counted = new TreeMap<>();
        walkEveryCall((caller, invoked) -> {
            if (!settlingMembers().contains(memberOf(invoked))) {
                return;
            }
            counted.merge(caller + " -> " + wordFor(invoked), 1, Integer::sum);
        });
        return counted.entrySet().stream().map(row -> row.getKey() + " x" + row.getValue()).toList();
    }

    /**
     * The word {@code invoked} is written with, and a failure where nothing here holds one for it.
     *
     * <p>Not a spelling worked out from the descriptor. An overload nobody has written down is an
     * overload nobody has said settles what: rendered by falling back to its signature it would join
     * the list as one more row, which reads as a caller to look at rather than as a way in that
     * nobody has ruled on.
     */
    private static String wordFor(InvokeInstruction invoked) {
        for (Settler settler : SETTLERS) {
            if (settler.signature().equals(signatureOf(invoked))) {
                return settler.word();
            }
        }
        throw new AssertionError("`" + signatureOf(invoked) + "` settles an answer and is written"
                + " with no word: add it to the settlers table, saying which act it is");
    }

    /** Every call this package's forms are built by, from outside the package that declares them. */
    private static List<String> buildingAFormDirectly() {
        Set<String> found = new TreeSet<>();
        walkEveryCall((caller, invoked) -> {
            if (!"<init>".equals(invoked.name().stringValue())
                    || !theForms().contains(invoked.owner().name().stringValue())
                    || THEIR_PACKAGE.equals(packageOf(caller))) {
                return;
            }
            found.add(caller + " -> " + invoked.owner().name().stringValue() + "#<init>");
        });
        return new ArrayList<>(found);
    }

    /** The forms whose answers this is about, as a call names their class. */
    private static Set<String> theForms() {
        Set<String> forms = new LinkedHashSet<>();
        for (Settler settler : SETTLERS) {
            forms.add(settler.owner());
        }
        return forms;
    }

    /** The members a settling call names, whichever overload it names. */
    private static Set<String> settlingMembers() {
        Set<String> members = new LinkedHashSet<>();
        for (Settler settler : SETTLERS) {
            members.add(settler.member());
        }
        return members;
    }

    private static String memberOf(InvokeInstruction invoked) {
        return invoked.owner().name().stringValue() + "#" + invoked.name().stringValue();
    }

    private static String signatureOf(InvokeInstruction invoked) {
        return memberOf(invoked) + invoked.typeSymbol().descriptorString();
    }

    /** Where a class is declared: the whole of its internal name but the class, so a package under
     *  this one is a package under it and not this one. */
    private static String packageOf(String member) {
        int hash = member.indexOf('#');
        String owner = hash < 0 ? member : member.substring(0, hash);
        int last = owner.lastIndexOf('/');
        return last < 0 ? "" : owner.substring(0, last);
    }

    /** Every method this reactor compiles, and every call it makes, as the method that makes it. */
    private static void walkEveryCall(BiConsumer<String, InvokeInstruction> to) {
        for (ClassModel each : COMPILED.all()) {
            ClassModel model = each;
            for (MethodModel method : model.methods()) {
                String caller = each.thisClass().asInternalName() + "#" + method.methodName().stringValue()
                        + method.methodType().stringValue();
                method.code().ifPresent(code -> {
                    for (CodeElement element : code) {
                        if (element instanceof InvokeInstruction invoked) {
                            to.accept(caller, invoked);
                        }
                    }
                });
            }
        }
    }

    /** Every overload the package declares of a member the table names, by the whole of what it is
     *  — what the table is required to hold, read off the classes rather than off the calls. */
    private static List<String> everyOverloadOfASettler() {
        return declaredInThatPackage().stream()
                .filter(each -> settlingMembers().contains(each.substring(0, each.indexOf('('))))
                .sorted().toList();
    }

    /** Every method the package that declares these forms holds, by the whole of what it is. */
    private static Set<String> declaredInThatPackage() {
        Set<String> found = new TreeSet<>();
        for (ClassModel each : COMPILED.all()) {
            if (!each.thisClass().asInternalName().startsWith(THEIRS)) {
                continue;
            }
            ClassModel model = each;
            for (MethodModel method : model.methods()) {
                found.add(model.thisClass().name().stringValue() + "#"
                        + method.methodName().stringValue() + method.methodType().stringValue());
            }
        }
        return found;
    }

    /** Whether {@code method}'s code names an answer: a constant of one, or a member that takes one
     *  or answers with one. A class initialiser is what makes the constants and is not one. */
    private static boolean namesAnAnswer(MethodModel method) {
        if (method.methodName().stringValue().startsWith("<")) {
            return false;
        }
        return method.code().map(code -> {
            for (CodeElement element : code) {
                if (element instanceof FieldInstruction field
                        && (isAnAnswer(field.owner().name().stringValue())
                                || mentionsAnAnswer(field.typeSymbol().descriptorString()))) {
                    return true;
                }
                if (element instanceof InvokeInstruction invoked
                        && (isAnAnswer(invoked.owner().name().stringValue())
                                || mentionsAnAnswer(invoked.typeSymbol().descriptorString()))) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private static boolean isAnAnswer(String internalName) {
        return anAnswer().contains("L" + internalName + ";");
    }

    private static boolean mentionsAnAnswer(String descriptor) {
        return anAnswer().stream().anyMatch(descriptor::contains);
    }

    /** What an answer is, read off the types: which of its fields a construction had to write, and
     *  where it came from with each of the arms that says so. */
    private static Set<String> anAnswer() {
        Set<String> descriptors = new LinkedHashSet<>();
        descriptors.add(descriptorOf(Hir.Fields.class));
        descriptors.add(descriptorOf(ConstructionOrigin.class));
        for (Class<?> arm : ConstructionOrigin.class.getPermittedSubclasses()) {
            descriptors.add(descriptorOf(arm));
        }
        return descriptors;
    }

    private static String descriptorOf(Class<?> type) {
        return "L" + type.getName().replace('.', '/') + ";";
    }






}
