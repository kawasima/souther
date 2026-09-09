package souther.architecture;


import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.constantpool.PoolEntry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may read what a predicate over a string means.
 *
 * <p>A rule tells the strings it admits from the rest, and that is a fact about a language. Whether
 * the position the rule is about is divided, restricted, or left where it was found is a different
 * fact, answered by what the rule is written in — and a reader that derives the second from the
 * first is deciding a question it does not own. That is what happened: the walk that draws lines
 * read the predicate a second time, called the two sides of the language two classes of the
 * position, and published "the model divides this position into values this measure draws no line
 * between" of a declaration that refuses to build anything on the other side.
 *
 * <p>So the edge is what is checked, and not the sentence that came out of it. Renaming the reason
 * closes one occurrence; what closes the shape is that nothing between the predicate's reading and
 * a word about a position exists to be used again. The reading that turns clauses into sets is the
 * one owner, and every later reader takes what it settled.
 *
 * <p>Read off the compiled classes and not off the source, so that an intermediary put between the
 * two — a class named for anything at all, holding what the language came to — arrives here as a
 * row. A walk over spellings would find the words a reader chose and not the edge it built.
 *
 * <p><b>Every way a class reaches the table, and not only the calls.</b> Naming a member of it is
 * one way; naming the type is another, and that is what {@code instanceof}, {@code checkcast} and
 * {@code new} put in a constant pool. A classifier asking only which answer it was handed calls
 * nothing and reads the table all the same — the exact shape this exists to refuse, and the one a
 * walk over member references passes over.
 *
 * <p>And each row says what it names, so a reader is licensed for the question it asks rather than
 * for the class it happens to sit in.
 *
 * <p>Its own tests are not read. A test builds a reading to look at it and publishes nothing, and a
 * list that moved whenever one was written is a list nobody keeps up.
 */
class WhoMayReadWhatAStringPredicateMeansTest {

    private static final String OWNER = "souther/compiler/check/StringPredicates";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * Every class that reads one, and what it reads it for.
     *
     * <p>Four, and each asks a different question of the same table.
     *
     * <p>{@code AdmissibleReading} turns the clause into the strings it admits, which is the one
     * place what a rule means about a position is worked out. Everything a later reader says about
     * the position comes from what this left.
     *
     * <p>{@code Partitions} asks what string would satisfy the rule, which is a witness to paste
     * into a row rather than an answer about the position. A format proposes a value the same way a
     * minimum does, and which of them the whole of the rules admits is settled elsewhere.
     *
     * <p>{@code Partitions} is licensed for the reading it takes and for nothing else, which is
     * what keeps the two apart: {@code statedByWritten} answers what a rule accepts as written, and
     * a witness is what that is for. The reading the analysis takes is the other entry point, and a
     * row saying {@code Partitions} names it would be a second reader of what a rule means about a
     * position.
     *
     * <p>A row for a class in {@code inputs} or {@code report} is the edge this exists to refuse:
     * those name what a position came to, and a rule's meaning reaching them except through the
     * values it left is a second answer to a question that has an owner. So is a row for
     * {@code InvariantChecker}, which is where the edge was.
     *
     * <p>The rows in {@code partition} are the walk that finds a rule in a body, what that walk
     * hands on, the crossing that turns it into sets, and the reading that offers a value. None of
     * them says what a rule means about a position. What each takes is written beside it, and a row
     * for anything in that package that draws lines, counts classes or writes a report is the same
     * edge one package over.
     */
    private static final String READS = "souther/compiler/check/AdmissibleReading -> " + OWNER;

    private static final String WITNESS = "souther/compiler/partition/Partitions -> " + OWNER;

    /**
     * The walk of a body reads one, because a rule about the strings at a position can be written
     * there and something has to recognise it.
     *
     * <p>Not a second answer to what such a rule means about a position, which is what the rows
     * above exist to keep to one. This reads which predicate was applied and which strings it
     * states, and stops — where the rule stands settles whether that restricts the position or
     * divides it, and the walk is what knows where it stands. So the two answers are still apart:
     * the table says what the rule accepts, and where it is written says what that does to the
     * position.
     *
     * <p>Its own entry point, and the difference between the three is how each reaches the text an
     * author wrote. A declaration's clauses are read against what that declaration binds and a
     * body's rules against what the walk has bound where it stands, so what is handed in is the
     * resolution and never a second reading of what a predicate means.
     */
    private static final String A_BODYS_RULES =
            "souther/compiler/partition/PredicateReadings -> " + OWNER;

    /** And what the walk hands on, which names the answer it carries. */
    private static final String A_BODYS_RULE =
            "souther/compiler/partition/PredicateReadings$Reading -> " + OWNER;

    /**
     * And what does something with that answer, which is the one thing left to do with it.
     *
     * <p>Not a fourth reader of what a rule means. Every outcome of the reading was settled by the
     * table before this holds one, and what happens here is the decision the outcomes were kept
     * apart for: a rule read as the strings it accepts becomes the two plans for either side of it,
     * and a rule read no further becomes what a reader is told instead. Handed only the strings, a
     * reader would have to work out for itself which rules it had not been given, under a word for
     * a limit nothing reached.
     *
     * <p>Which is why the outcomes reach here and the text does not. The strings a rule states are
     * taken as they came; what an author wrote them in, and how it was reached, are the walk's and
     * are gone by this point.
     */
    private static final String WHAT_A_RULE_STATES =
            "souther/compiler/partition/BehaviorSetStatements -> " + OWNER;

    /**
     * And the reading of what a behavior's own clauses state, which is the other place such a rule
     * is written.
     *
     * <p>A third entry point and not a third answer, for the reason the two above are two: what
     * reaches the text an author wrote differs. A clause is read against what the declaration binds
     * and under the statements the clause makes outright, which is that reader's own walk.
     *
     * <p>Here rather than in the walk that publishes it, because which reader a statement belongs to
     * is settled where the statements are. Asked later, the reader of comparisons and the reader of
     * predicates would each be deciding whether a statement is theirs, and a rule read as a set of
     * strings was also reported as a comparison whose form could not be read.
     */
    private static final String WHAT_A_CLAUSE_STATES =
            "souther/compiler/partition/ClauseStatements -> " + OWNER;

    /** And what that reading hands on, which names the answer it carries. */
    private static final String A_CLAUSES_RULE =
            "souther/compiler/partition/ClauseStatements$Statement$TellsStringsApart -> " + OWNER;

    private static final String IN_A_DESCRIPTOR = " (in a descriptor)";

    private static final List<String> READING_ONE = List.of(
            READS,
            READS + IN_A_DESCRIPTOR,
            READS + "#statedByChecked(Lsouther/compiler/core/Core;"
                    + "Lsouther/compiler/check/Symbols;Lsouther/compiler/check/Terms;"
                    + "Lsouther/compiler/check/Denotations;)L" + OWNER + "$Stated;",
            READS + "$Reading",
            READS + "$Reading$Accepting",
            READS + "$Reading$Accepting#accepts()Lsouther/compiler/regex/PatternSyntax;",
            READS + "$Reading$PatternNotRead",
            READS + "$Reading$PatternNotRead#why()Lsouther/compiler/regex/PatternRead$Unsupported;",
            READS + "$Reading$WrittenArgumentNotKnown",
            READS + "$Stated",
            READS + "$Stated#reading()L" + OWNER + "$Reading;",
            READS + "$Stated#subject()Lsouther/compiler/core/Core;",
            WHAT_A_RULE_STATES,
            WHAT_A_RULE_STATES + IN_A_DESCRIPTOR,
            WHAT_A_RULE_STATES + "$Reading",
            WHAT_A_RULE_STATES + "$Reading$Accepting",
            WHAT_A_RULE_STATES
                    + "$Reading$Accepting#accepts()Lsouther/compiler/regex/PatternSyntax;",
            WHAT_A_RULE_STATES + "$Reading$PatternNotRead",
            WHAT_A_RULE_STATES
                    + "$Reading$PatternNotRead#why()Lsouther/compiler/regex/PatternRead$Unsupported;",
            WHAT_A_RULE_STATES + "$Reading$WrittenArgumentNotKnown",
            WHAT_A_CLAUSE_STATES,
            WHAT_A_CLAUSE_STATES + IN_A_DESCRIPTOR,
            WHAT_A_CLAUSE_STATES + "#statedBy(Lsouther/compiler/core/Core;"
                    + "Lsouther/compiler/check/Symbols;L" + OWNER + "$WrittenText;)L"
                    + OWNER + "$Stated;",
            WHAT_A_CLAUSE_STATES + "$Stated",
            WHAT_A_CLAUSE_STATES + "$WrittenText",
            A_CLAUSES_RULE,
            A_CLAUSES_RULE + IN_A_DESCRIPTOR,
            A_CLAUSES_RULE + "$Stated",
            WITNESS,
            WITNESS + IN_A_DESCRIPTOR,
            WITNESS + "#statedByWritten(Lsouther/compiler/ast/Hir$Expr;"
                    + "Lsouther/compiler/check/Symbols;)L" + OWNER + "$Reading;",
            WITNESS + "$Reading",
            WITNESS + "$Reading$Accepting",
            WITNESS + "$Reading$Accepting#accepts()Lsouther/compiler/regex/PatternSyntax;",
            A_BODYS_RULES,
            A_BODYS_RULES + IN_A_DESCRIPTOR,
            A_BODYS_RULES + "#statedBy(Lsouther/compiler/core/Core;"
                    + "Lsouther/compiler/check/Symbols;L" + OWNER + "$WrittenText;)L"
                    + OWNER + "$Stated;",
            A_BODYS_RULES + "$Stated",
            A_BODYS_RULES + "$WrittenText",
            A_BODYS_RULE,
            A_BODYS_RULE + IN_A_DESCRIPTOR,
            A_BODYS_RULE + "$Reading",
            A_BODYS_RULE + "$Stated",
            A_BODYS_RULE + "$Stated#reading()L" + OWNER + "$Reading;",
            // What the rule states, which is what a class it makes is called. Read off the same
            // value the reading came in and never worked out again: a name for a class derived a
            // second time would be one that agrees with the rule until one of the two changes.
            A_BODYS_RULE + "$Stated#statement()Lsouther/compiler/check/PredicateStatement;",
            A_BODYS_RULE + "$Stated#subject()Lsouther/compiler/core/Core;");

    @Test
    void everyClassThatReadsOneIsWrittenDownWithWhatItReadsItFor() {
        assertEquals(READING_ONE, new ArrayList<>(readingOne()),
                "what a rule about a string means for a position is worked out once, by the reading"
                        + " that turns clauses into sets: a row that is not one of these is a reader"
                        + " deciding it a second time, and the two agree only until one of them"
                        + " changes");
    }

    /**
     * The walk reads every module's classes.
     *
     * <p>Asked of the modules the repository has and not of what a build happened to leave: a module
     * whose classes are missing is one whose reads this cannot see, and the rows from the rest would
     * match and this would pass while answering about fewer modules than it names.
     */
    @Test
    void andEveryModuleTheRepositoryHoldsWasRead() {
        assertTrue(modulesRead() > 1,
                "this walk goes over more than one module's classes, so a reader of the table"
                        + " written outside the one that declares it is one it would find");
    }

    /**
     * And the walk finds a reader that is there.
     *
     * <p>The rows above are what may read the table; this is that the reading which does read it is
     * one this walk can see. Matched on an owner nothing names, every row would be absent and the
     * list above would be empty and equal to itself.
     */
    @Test
    void andTheWalkSeesAReaderThatIsThere() {
        assertTrue(readingOne().stream()
                        .anyMatch(each -> each.startsWith("souther/compiler/check/AdmissibleReading")),
                "the reading that turns a predicate into the strings it admits reads the table, so a"
                        + " walk that cannot find it is finding nothing at all");
    }

    /** Every class whose constant pool reaches the table, with what it names of it. */
    private static Set<String> readingOne() {
        Set<String> found = new TreeSet<>();
        for (Path module : COMPILED.modules()) {
            for (ClassModel each : COMPILED.classesOf(module)) {
                String reader = each.thisClass().asInternalName();
                // The table and the answers it declares are the table. What each of them names of
                // the others is the reading being written down and taken apart where it is made,
                // and a row for one of them would be this walk reporting the owner as a reader of
                // itself.
                if (names(reader)) {
                    continue;
                }
                for (PoolEntry entry : each.constantPool()) {
                    String named = reached(entry);
                    if (named != null) {
                        found.add(reader + " -> " + named);
                    }
                }
            }
        }
        return found;
    }

    /**
     * What one constant reaches of the table, or null where it reaches none of it.
     *
     * <p>A member by the whole of what it is, and a type by its name. The two are different ways of
     * reaching the same table and a reader is licensed for whichever it uses, so they are one
     * vocabulary of rows rather than two lists that could disagree about who reads what.
     */
    private static String reached(PoolEntry entry) {
        if (entry instanceof MemberRefEntry member
                && names(member.owner().name().stringValue())) {
            return member.owner().name().stringValue() + "#" + member.name().stringValue()
                    + member.type().stringValue();
        }
        if (entry instanceof ClassEntry type && names(type.name().stringValue())) {
            return type.name().stringValue();
        }
        // And the type written into a descriptor or a signature, which is how a class holds one
        // without ever asking it anything: a method handing a reading along names the type in what
        // it takes and answers with, and needs no class constant to do it. Held by a class that
        // reads nothing, such a type is still the reading crossing a boundary it has no business
        // crossing, and a walk over classes and members alone passes over it.
        if (entry instanceof Utf8Entry text && text.stringValue().contains("L" + OWNER)) {
            return OWNER + " (in a descriptor)";
        }
        return null;
    }

    /** Whether a member's owner is the table or one of the answers it declares. */
    private static boolean names(String owner) {
        return owner.equals(OWNER) || owner.startsWith(OWNER + "$");
    }

    private static int modulesRead() {
        int read = 0;
        for (Path module : COMPILED.modules()) {
            if (!COMPILED.classesOf(module).isEmpty()) {
                read++;
            }
        }
        return read;
    }










}
