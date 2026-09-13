package souther.compiler.execute;

import souther.compiler.ast.Hir;
import souther.compiler.core.Contract;
import souther.compiler.check.BehaviorRequirement;
import souther.compiler.check.Prepared;
import souther.compiler.check.Sig;
import souther.compiler.check.Symbols;
import souther.compiler.observe.FieldTypes;
import souther.compiler.source.SourceId;
import souther.compiler.types.ValueName;

import java.util.List;
import java.util.Map;

/**
 * Everything an evaluation of one module's examples reads, as this compile settled it.
 *
 * <p>Not a second model of a module. What an output reads of a checked program is Core, lowered,
 * and an example is not there: a row's inputs and its expected value are expressions the checker
 * read and never lowered, and the definitions a fixture reaches are the ones a call expands rather
 * than the ones the module emits. So this is not the checked program under another name — it is the
 * stage the checked program does not have, and nothing here is a second answer to a question the
 * checked program already answers.
 *
 * <p>It carries and does not decide. Every part of it is some other question's answer, taken whole:
 * what the names mean, what each behavior's shape is, what it needs supplied, what the module
 * defines, and what its behaviors declare of what they answer. Nothing here re-derives one of those
 * from the others — a carrier that started answering would be the second model this is not.
 *
 * <p>What it does hold together is the correlation. The rows and the module they are rows of come
 * from one preparation and cannot be paired wrongly, which is the condition a run of them is
 * correct under; and the terms the rows are held to are this compile's, read once, so two runs of
 * one module are not two budgets.
 *
 * <p>Module-wide, with the source asked for at the question. Which file a row is written in decides
 * which rows a question is about, not what the module means, and a copy of this per source would
 * make "all of them" and "one file's" two states of one type.
 */
public final class ExampleExecution {

    private final Prepared prepared;
    private final Symbols symbols;
    /** What the declarations the rows name say about themselves. */
    private final souther.compiler.check.PublishedDeclarations published;
    /** Which form each of those declarations was written in. */
    private final souther.compiler.check.DeclarationKinds kinds;
    private final FieldTypes fields;
    private final Map<ValueName.Behavior, Sig> signatures;
    private final Map<String, List<BehaviorRequirement>> requirements;
    private final Map<String, Hir.FnDef> definitions;
    private final Map<ValueName.Behavior, Contract> contracts;
    private final EvaluationPolicy policy;
    private final Map<String, ExampleExecution> declaring;

    public ExampleExecution(Prepared prepared, Symbols symbols,
                            souther.compiler.check.PublishedDeclarations published,
                            souther.compiler.check.DeclarationKinds kinds,
                            FieldTypes fields,
                            Map<ValueName.Behavior, Sig> signatures,
                            Map<String, List<BehaviorRequirement>> requirements,
                            Map<String, Hir.FnDef> definitions,
                            Map<ValueName.Behavior, Contract> contracts,
                            EvaluationPolicy policy,
                            Map<String, ExampleExecution> declaring) {
        this.prepared = prepared;
        this.symbols = symbols;
        this.published = published;
        this.kinds = kinds;
        this.fields = fields;
        this.declaring = declaring;
        // Taken as they are and not copied. Each is another question's settled answer, and this is
        // put together afresh every time it is asked for — it cannot be memoised, because a
        // `Symbols` closes over the store it resolves against — so copying four maps here would be
        // a cost paid on every reading to defend against a caller that does not exist.
        this.signatures = signatures;
        this.requirements = requirements;
        this.definitions = definitions;
        this.contracts = contracts;
        this.policy = policy;
    }

    /** What the module is called. */
    public String module() {
        return prepared.name();
    }

    /** The module projected for its every example block, from its own file and from every file
     *  naming it. */
    public Prepared.ForExamples forExamples() {
        return prepared.forExamples();
    }

    /** The same over the blocks written in one source, projected the way the preparation projects
     *  them. */
    public Prepared.ForExamples forExamplesWrittenIn(SourceId source) {
        return prepared.forExamplesWrittenIn(source);
    }

    /** What a name written here means. */
    public Symbols symbols() {
        return symbols;
    }

    /** What the declarations the rows name say about themselves. */
    public souther.compiler.check.PublishedDeclarations published() {
        return published;
    }

    /** Which form each of those declarations was written in. */
    public souther.compiler.check.DeclarationKinds kinds() {
        return kinds;
    }

    /**
     * What a value of a declaration is made of, as the check settled it.
     *
     * <p>Every declaration this compile resolved and not this module's own: a row here may state a
     * value of a data another module declares, and what its fields hold is that declaration's
     * check to say. Read rather than worked out — the places a comparison reads a value's parts at
     * come from this same answer, so a fixture typed by it and a row compared against it cannot
     * come apart.
     */
    public FieldTypes fieldTypes() {
        return fields;
    }

    /**
     * The shape of every behavior the rows may name: this module's own, and the ones it borrows.
     *
     * <p>Keyed by the declaration and not by the spelling, because a stand-in may name a dependency
     * another module declares and this module may declare one of that name too.
     */
    public Map<ValueName.Behavior, Sig> signatures() {
        return signatures;
    }

    /**
     * The reading of each module this one writes a stand-in for a behavior of.
     *
     * <p>What a {@code fake} states and what the rows recorded for that behavior state are two
     * statements about one thing, and the second lives where the behavior is declared — a row names
     * a behavior of its own module, and a stand-in may name another module's. So reading the two
     * against each other takes both, and the second is here.
     *
     * <p>The rows of the other module as that module writes them: its own values and its own names,
     * because a fixture is read in the scope it was written in. What is not taken from it is the
     * execution — the values are built and compared in this module's, so nothing crosses a loader
     * and the comparison is the language's own equality rather than a second one that would agree
     * with it until a value's identity mattered.
     *
     * <p>One level. What the other module borrows is that module's own reading to make, and
     * following it further would read a statement nothing here writes about.
     */
    public Map<String, ExampleExecution> declaring() {
        return declaring;
    }

    /** What each behavior needs supplied to it. */
    public Map<String, List<BehaviorRequirement>> requirements() {
        return requirements;
    }

    /** The definitions a row can reach by name, which is what a fixture applies. */
    public Map<String, Hir.FnDef> definitions() {
        return definitions;
    }

    /** What each behavior declares of what it answers, which is what holds a row's values to
     *  something. */
    public Map<ValueName.Behavior, Contract> contracts() {
        return contracts;
    }

    /**
     * What a run of these rows is held to.
     *
     * <p>The terms and not a way of keeping them. How an execution arranges to stop a row that has
     * spent them is its own — a worker and a wall clock here, something else elsewhere — and saying
     * it here would put one implementation's arrangement in what every implementation is asked.
     */
    public EvaluationPolicy policy() {
        return policy;
    }
}
