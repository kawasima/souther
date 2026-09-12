package souther.compiler.report;

import souther.compiler.coverage.ArmProbe;
import souther.compiler.cst.CstError;
import souther.compiler.cst.CstParser;
import souther.compiler.cst.SyntaxKind;
import souther.compiler.diag.SourceRendering;
import souther.compiler.fmt.Formatter;
import souther.compiler.publish.PublishedIncompleteness;
import souther.compiler.publish.PublishedRuleHandle;
import souther.compiler.check.Requirements;
import souther.compiler.publish.RuleHandleProse;
import souther.compiler.query.Sites;
import souther.compiler.partition.BorderObligationPoint;
import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.GenerationReason;
import souther.compiler.partition.GenerationOutcome;
import souther.compiler.partition.Generator;
import souther.compiler.partition.StoodInAnswer;
import souther.compiler.query.About;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Db;
import souther.compiler.query.BorderAccount;
import souther.compiler.query.GenerationScope;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.query.OfferedRow;
import souther.compiler.query.Offering;
import souther.compiler.query.OfferingRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rows a person completes, printed rather than written.
 *
 * <p>The rows go out as rows. Each is written with {@code <?>} where its answer goes, which is what
 * the language writes for a row whose answer is owed: the row parses, the module goes on compiling,
 * and the checker reports the answer as work the author has left. So a block can be pasted whole and
 * answered a row at a time, and what was pasted is source — {@code souther fmt} runs over it, a
 * rename of a field a row names reaches it, and the report keeps saying which rows are still owed
 * an answer.
 *
 * <p>What is commented is the prose. A block carries lines that are not Souther — the {@code ensures}
 * a behavior states, a note over a row composed for more than one thing, a line for each point no row
 * could be written at — and those are written as comments so that the block around them stays
 * something a file can hold.
 *
 * <p>Nothing is written to a file. Where the rows belong is a question with a real answer — the
 * module's own source or an attached {@code examples for} file — and one this does not have to guess
 * at, because a person who can read the block can put it where they want it.
 */
public final class GeneratedRows {

    /** How the language writes a row's answer before anyone has written it. Read from the token
     * rather than spelt again: the parser, the formatter and this block say it one way. */
    private static final String UNANSWERED =
            SyntaxKind.UNANSWERED.fixedSpelling().orElseThrow();

    /**
     * A block, and how much of it is rows.
     *
     * <p>Two answers because callers ask two different things of this. What to print is the text; a
     * block of notes and no rows is worth printing, and {@code souther examples --generate} prints
     * it. Whether there is a row to write is {@link #rowCount}, and it is the generator's answer
     * rather than something read off the text: an editor offering "write the rows this does not cover"
     * asked whether the block was blank, and a block that holds only the reason nothing was composed
     * is not blank — so the action appeared, a person took it, and what it wrote into their source
     * was a comment (issue #955).
     *
     * @param rowCount how many rows the block offers, which is what a caller that is about to
     *                 change somebody's source has to ask
     */
    public record Block(String text, int rowCount) {

        public Block {
            Objects.requireNonNull(text, "a block is of something, even if it is empty");
            if (rowCount < 0) {
                throw new IllegalArgumentException(
                        "a block offering fewer than no rows: " + rowCount);
            }
        }
    }

    /**
     * The block for a finished compile, for the modules and behaviors the caller asked about.
     *
     * <p>Asked here and not with the rest of a compile's questions, because filling the combinations
     * searches the pair space, and nobody who only wanted the report should pay for that. The rows at
     * the edges cost nothing here: each was built where the boundary was measured, and this reads what
     * that attempt produced.
     *
     * <p>{@code rendering} is what the caller calls its sources, for the same reason the report beside
     * this block asks for it: a note here is read in the same terminal, and a source id is an
     * identity rather than a name.
     */
    public static Block of(Compilation compilation, String module, String behavior,
                           SourceRendering rendering) {
        StringBuilder out = new StringBuilder();
        int rows = 0;
        for (String name : compilation.modules()) {
            if (module != null && !module.equals(name)) {
                continue;
            }
            // What this run offers, asked as one question. Which rows go out is settled where both
            // searches are read ({@link Adequacy#offeredFor}) — a behavior's own and the ones a
            // declaration's line is owed are work for one person, and a renderer putting them
            // together would be deciding that where the layout is.
            Offering offering = Adequacy.offeredFor(compilation.db(),
                    new OfferingRequest(name, behavior == null ? new GenerationScope.Module()
                            : new GenerationScope.Behavior(behavior)));
            if (offering == null) {
                continue;
            }
            Block one = of(offering, WrittenEnsures.of(compilation.db(), name), rendering,
                    compilation.db());
            out.append(one.text());
            rows += one.rowCount();
        }
        return new Block(out.toString(), rows);
    }

    /**
     * What nothing offers a row for among the module's declarations.
     *
     * <p>Read off the same resolutions the rows above were, so the two cannot disagree: a line whose
     * row is printed two lines up is not one this says nothing offers a row for.
     *
     * <p>Which lines this is about was settled where the search was, and is not asked again here. A
     * renderer that filtered by what the behavior carries would be deciding the request's own
     * question a second time, and would be free to decide it differently.
     *
     * <p>Asked of what the walks came to and not of the findings. A finding stands where something
     * has shown a row can be written at the point; a line the search failed at with nothing yet
     * promising a row raises none, and the block would go quiet about work it had just tried — which
     * is the same rule the rows beside these are offered under.
     *
     * <p><b>Which sentence a line gets is the answer's shape and not this reader's choice.</b> What
     * a search of one reading came to is a fact about that reading and is said as one, naming the
     * behavior the way every other note here does. That no row can be written at the line is a
     * claim about every reading of it, and only an answer that passed the gate carries it — so it
     * is said once, under the declaration that drew the line, and cannot be written from evidence
     * that does not support it.
     */
    private static void declarations(StringBuilder out, Offering offering) {
        BorderAccount account = offering.account();
        Set<String> said = new LinkedHashSet<>();
        for (Map.Entry<BorderObligationPoint, BorderAccount.Unmet> each
                : account.unmet().entrySet()) {
            // Nothing about a point one of the rows above stands at. What is left to write is what
            // this says, and a line telling a person no row was composed for something they are
            // being handed a row for is work that is not left.
            if (offering.answered().contains(new ObligationIdentity.OfALine(each.getKey()))) {
                continue;
            }
            switch (each.getValue()) {
                // Said once, of the line, and in the declaration's own words. What each reading
                // proved it of is not repeated: they agree, which is what let this be said at all.
                case BorderAccount.Unmet.TheLineCannotBeWritten(var owedBy, var asked, var _) ->
                        say(out, said, String.format(
                                "// no row can be written at `%s` owed by `%s`: every reading of the"
                                        + " line was searched and the rules leave no value at it%n",
                                asked, owedBy));
                // One line per reading, because they are not one fact: a reading whose rules leave
                // nothing at its own position and one whose search stopped are different news, and
                // a line carrying whichever came first would carry the order the walk took.
                case BorderAccount.Unmet.WhatTheReadingsCameTo(var _, var asked, var came) ->
                        came.forEach(at -> say(out, said, switch (at) {
                            case BorderAccount.At.Searched(var reading, var why) -> String.format(
                                    "// no row for `%s` in `%s`: %s%n", why.subject(),
                                    reading.behavior(), saidOf(why));
                            // Said, because a reading that was asked about and could not be
                            // searched is a thing that happened to this run. Left out, a reader
                            // sees the readings that answered and no sign that another was asked.
                            case BorderAccount.At.CouldNotBeSearched(var reading) -> String.format(
                                    "// no row for `%s` in `%s`: the lines of that behavior could"
                                            + " not be searched, so nothing was looked for at it%n",
                                    asked, reading.behavior());
                        }));
                case BorderAccount.Unmet.NothingWasSearched(var owedBy, var asked) ->
                        say(out, said, String.format(
                                "// no row for `%s` owed by `%s`: %s%n", asked, owedBy,
                                why(Generator.UnresolvedCombination.Reason
                                        .NO_READING_OF_THE_LINE_COULD_BE_SEARCHED)));
            }
        }
    }

    /**
     * The block for one offering.
     *
     * <p>Which rows go out is settled before this is called and is not decided again here. What
     * this adds is what they are called and how they are laid out — an arm's name is the report's
     * word, and a heading, a note and a comment marker are not things a row carries.
     */
    public static Block of(Offering offering, Map<String, List<String>> ensures,
                           SourceRendering rendering, Db db) {
        PublishedRuleHandle.WhereARuleIs places = cited -> Sites.placeOf(db, cited);
        String module = offering.request().module();
        BorderAccount account = offering.account();
        // Written once and then read three times — printed, counted, and asked whether there is
        // anything to answer. Counting the candidates instead gives a number about work a reader
        // cannot see, and asking the candidates whether the block holds a hole prints the line
        // telling them to fill one over a block that has none.
        Map<String, List<Offered>> offered = named(offering);
        int rows = offering.count();
        StringBuilder out = new StringBuilder();
        if (rows > 0) {
            out.append(String.format(
                    "// generated by `souther examples --generate`: %d %s to fill what nothing covers.%n",
                    rows, rows == 1 ? "row" : "rows"));
            out.append(String.format(
                    "// Replace each `%s` with what the system actually answers.%n", UNANSWERED));
            out.append(stated(blocks(module, offered), ensures));
        }
        for (Map.Entry<String, Adequacy.Filling> behavior : offering.searched().entrySet()) {
            notes(out, behavior.getKey(), behavior.getValue(), rendering, offering, places);
        }
        // And what the module's declarations are owed that nothing composed a row for. Here rather
        // than after this returns, because what this builds is the block: a caller that rendered
        // through it and appended the rest itself would be a second place that knows what a block
        // holds, and the one that forgot would print rows with nothing said about the work beside
        // them.
        if (account != null) {
            declarations(out, offering);
        }
        // The count leaves with the text. It was worked out here and thrown away, and the one
        // caller that needed it read the text instead.
        return new Block(pastable(module, out.toString()), rows);
    }

    /**
     * The block, held to being something a file can take.
     *
     * <p>What the block holds is rows and prose about them, and the marker in front of the prose is
     * the only thing telling them apart. There is a writer for each thing there is to say — the
     * clauses a behavior states, the note over a row composed for more than one thing, a line for
     * each point no row could be written at, a sentence for each combination nothing was composed
     * for — and each of them writes its own marker. One that forgets sends prose out as source, and
     * the block stops compiling the moment somebody pastes it, which is the whole of what it is for.
     *
     * <p>Asked by parsing what is left when the prose is taken away, rather than by looking at how
     * a line starts. A shape read off the front of a line is a guess about what the language
     * admits, and the guess a reader would reach for lets exactly the writer this is here for
     * through: a clause is quoted indented, so prose that lost its marker looks like the lines a
     * wrapped row continues on. What the block promises is that a file can take it, and the thing
     * that answers that is the parser.
     *
     * <p>The header goes back on for the reading. It is taken off the block because where the rows
     * are pasted is the author's choice, and a bare {@code example} block is not a file — so the
     * question is asked of the file the block becomes rather than of a fragment nothing accepts.
     */
    private static String pastable(String module, String block) {
        StringBuilder source = new StringBuilder("examples for ").append(module).append("\n\n");
        for (String line : block.lines().toList()) {
            if (!line.startsWith("//")) {
                source.append(line).append("\n");
            }
        }
        List<CstError<?>> refused = CstParser.parse(source.toString()).errors();
        if (!refused.isEmpty()) {
            throw new IllegalStateException("a block goes out as something a file can take, and"
                    + " what is left of this one when its prose is taken away does not parse: "
                    + refused.getFirst() + " in\n" + source);
        }
        return block;
    }

    /**
     * The findings this block owes a reader a word about.
     *
     * <p>A point of a border is said like everything else. What a run offers is the account's, and
     * the points are obligations of it: a block that held them back read as though the arms and the
     * cases were all there was to write, and the report beside it went on naming what the block had
     * decided not to mention.
     *
     * <p>A finding row synthesis is not about is left out. This block is rows to write and notes
     * about rows that could not be written; a measure this compiler could not make has no row
     * waiting behind it, and a line here saying nothing offers one would put our own shortfall in
     * a list of the author's work. The report says those findings, which is where they belong.
     */
    private static List<Adequacy.GenerationDisposition> shown(Adequacy.Filling filling,
                                                              Offering offering) {
        return filling.generation().stream()
                .filter(each -> !(each.outcome() instanceof GenerationOutcome.NotApplicable))
                // And nothing about something one of the rows above stands at. What the search for
                // this finding came to is what it came to, and a person reading the block is being
                // told what is left to write — which a row in front of them is not.
                .filter(each -> each.item().stream()
                        .noneMatch(offering.answered()::contains))
                .toList();
    }

    /**
     * One row as it will be written, and everything it was composed for.
     *
     * <p>One name is a name and several are a note apiece over a row with none. Joining them into
     * one name is the way of being wrong that reads worst: {@code "a x b"} reads as one thing owed
     * at two positions at once, which is the shape this whole block was written against. The
     * language lets a row be written without a name and an author names it when they answer it;
     * what it is for is said in words above it, once per thing.
     *
     * @param inputs   the row's values, in the form they are written in
     * @param purposes what this layer calls the things it was composed for, in the order they were
     *                 taken
     */
    private record Offered(String inputs, String standsIn, List<String> purposes) {

        Offered {
            purposes = List.copyOf(purposes);
        }

        /** The same, and one more thing it turned out to answer. Kept in order and without
         *  repeats: two purposes with one name are one thing said twice. */
        Offered and(String purpose) {
            if (purpose == null || purposes.contains(purpose)) {
                return this;
            }
            List<String> both = new ArrayList<>(purposes);
            both.add(purpose);
            return new Offered(inputs, standsIn, both);
        }

        /** The row as it is written: named where one thing names it, and not otherwise. What a
         *  row with several is for is said over it ({@link #blocks}) rather than in it — the
         *  formatter parses what it is handed, and prose is not a row. */
        String written() {
            // The name as the language writes a string, because that is what it is. Written out as
            // it stands, a name holding a quote — which a class named after a rule about strings
            // does — closes the literal early and the rest of it becomes source.
            return purposes.size() == 1
                    ? "    | " + FixtureTemplate.quoted(purposes.get(0))
                            + " : (" + inputs + ")" + standsIn + " -> " + UNANSWERED
                    : "    | (" + inputs + ")" + standsIn + " -> " + UNANSWERED;
        }

        /** What to say over the row, which is nothing where its name already says it. */
        List<String> saidOver() {
            return purposes.size() == 1 ? List.of() : purposes;
        }
    }

    /**
     * What each arm of one behavior is called, by the probe the plan gave it.
     *
     * <p>Read off the findings the generation answers, which is where an arm's name is already
     * written. An arm the search composed a row for is one a finding named, the plan being made of
     * them, so there is a name here for every arm a row is offered at.
     */
    private static Map<ArmProbe, String> armNames(Adequacy.Filling filling) {
        Map<ArmProbe, String> out = new LinkedHashMap<>();
        if (filling == null) {
            // A behavior with no search of its own, which is one that composed a row a declaration
            // is owed and nothing else. A row at a line is offered without a name, so there is no
            // arm here to be called anything.
            return out;
        }
        for (Adequacy.GenerationDisposition each : filling.generation()) {
            if (each.finding().about() instanceof About.AnArmNoRowGoesThrough(var arm)) {
                out.put(arm.index(), ArmVocabulary.label(arm));
            }
        }
        return out;
    }

    /**
     * What a row is offered under, one name per thing it was composed for.
     *
     * <p>Every one of them and not a name made by joining them. A row that answers two arms answers
     * two things, and `a x b` spelt over the pair reads as an obligation nobody raised — the same
     * fault as naming a row for everything it turns out to settle, arriving from the other side.
     */
    private static List<String> named(List<Generator.Purpose> purposes,
                                      Map<ArmProbe, String> arms) {
        List<String> out = new ArrayList<>();
        for (Generator.Purpose purpose : purposes) {
            if (purpose instanceof Generator.Purpose.ForAnArm(ArmProbe probe)) {
                // Left unnamed where nothing named the arm, which is the state of a row nobody has
                // named yet and is what the language writes for one. A name invented here would be
                // a second vocabulary for an arm.
                if (arms.containsKey(probe)) {
                    out.add(arms.get(probe));
                }
            } else {
                out.addAll(purpose.labels());
            }
        }
        return out;
    }

    /**
     * The rows this run offers, each under the words this layer calls what it was composed for.
     *
     * <p>Which rows go out and what each was composed for is {@link Offering}'s; what those things
     * are called is this layer's. An arm is named by the report ({@link ArmVocabulary}) and a
     * generation spells no name for one, so the two are joined here — and a row whose arm nothing
     * named is offered without a name, which is the state of a row nobody has named yet and is what
     * the language writes for one.
     *
     * <p>Names without repeats. Two purposes with one name are one thing said twice, and what tells
     * them apart is the name rather than the purpose: a class of one position and a class of another
     * can be written the same way, and a reader handed both would be told the same fact twice.
     */
    private static Map<String, List<Offered>> named(Offering offering) {
        Map<String, List<Offered>> out = new LinkedHashMap<>();
        offering.rowsByBehavior().forEach((behavior, rows) -> {
            Map<ArmProbe, String> arms = armNames(offering.searched().get(behavior));
            List<Offered> here = new ArrayList<>();
            for (OfferedRow row : rows) {
                Offered offered = new Offered(row.key().inputs(),
                        standingIn(offering.request().module(), row), List.of());
                for (String name : named(row.namedFor(), arms)) {
                    offered = offered.and(name);
                }
                here.add(offered);
            }
            out.put(behavior, List.copyOf(here));
        });
        return out;
    }

    /**
     * The {@code with} clause a row carries, or nothing where it stands nothing in.
     *
     * <p>A projection and not a decision. Which of the two things that answer a dependency answers
     * this one was settled where the row was composed, and is read here rather than decided again.
     * A row leaning on the module's table writes nothing at that dependency — the table is already
     * in the file the row is pasted into, and a {@code with} put there would take the row out of
     * the environment every written row of the module runs in.
     *
     * <p>The dependency spelled the way a person writes one, asked of the rule that answers it
     * ({@link Requirements#writtenIn}). A behavior another module declares is reachable through
     * that module whether or not an import brought its bare name in, so a block that wrote the bare
     * name would hand a person a row naming a behavior nothing resolves — and the hint that says
     * what to type and the skeleton that types it would be two answers to one question.
     */
    private static String standingIn(String module, OfferedRow row) {
        List<String> written = new ArrayList<>();
        for (StoodInAnswer each : row.answers()) {
            if (each instanceof StoodInAnswer.OnTheRow(var dependency, var value)) {
                written.add(Requirements.writtenIn(module, dependency) + " = " + value.text());
            }
        }
        return written.isEmpty() ? "" : " with " + String.join(", ", written);
    }

    /**
     * The rows as source, in the form the formatter writes them.
     *
     * <p>Formatted rather than printed straight, because these lines are meant to be pasted into a
     * file that {@code souther fmt} then runs over. A block that came out in a shape the formatter
     * would change turns a paste into a diff on the next commit.
     */
    private static String blocks(String module, Map<String, List<Offered>> offered) {
        StringBuilder source = new StringBuilder();
        source.append("examples for ").append(module).append("\n");
        for (Map.Entry<String, List<Offered>> behavior : offered.entrySet()) {
            if (behavior.getValue().isEmpty()) {
                continue;
            }
            source.append("\n").append("example ").append(behavior.getKey()).append("\n");
            for (Offered row : behavior.getValue()) {
                source.append(row.written()).append("\n");
            }
        }
        String written = source.toString();
        String formatted;
        try {
            formatted = Formatter.format(written);
        } catch (RuntimeException _) {
            formatted = written;   // a row the formatter cannot read is still a row worth printing
        }
        // The header was there to make the rows parseable on their own. Where they are pasted is the
        // author's choice — the module's own file or an attached one — and only one of those wants it.
        return fills(formatted.replaceFirst("^examples for \\S+\\R+", ""), offered);
    }

    /**
     * What each row with more than one thing to its name is for, said over it.
     *
     * <p>Written as a comment, because it is prose and the rows around it are source. Put in after
     * the formatter has run, for the reason the {@code ensures} headings are: what {@link #blocks}
     * hands the formatter is the rows, and what a reader is told about them is not one of them.
     *
     * <p>Matched by position rather than by reading the line. The rows go in in one order and come
     * out in it, and a row the formatter wrapped is still one row — its continuations are indented
     * past the {@code |} that starts it, so what starts a row is what a row starts with.
     *
     */
    private static String fills(String rows, Map<String, List<Offered>> offered) {
        List<Offered> inOrder = new ArrayList<>();
        offered.values().forEach(inOrder::addAll);
        StringBuilder out = new StringBuilder();
        int at = 0;
        for (String line : rows.lines().toList()) {
            if (line.startsWith(ROW) && at < inOrder.size()) {
                for (String each : inOrder.get(at++).saidOver()) {
                    out.append("// fills ").append(each).append(System.lineSeparator());
                }
            }
            out.append(line).append(System.lineSeparator());
        }
        return out.toString();
    }

    /** How a row starts, which is how one is told from the lines a wrapped one continues on: those
     *  are indented past it. */
    private static final String ROW = "    | ";

    /**
     * The clauses each behavior carries, put over the rows they are about.
     *
     * <p>Over the rows and not beside each one. A clause is written on the behavior, so it says the
     * same thing about every row of it, and a copy per row would be the same words as many times as
     * the generator happened to offer questions.
     *
     * <p>Put in after {@link #blocks} and not inside it, because these lines are not rows. What that
     * builds is source, written so that {@code souther fmt} would leave it alone; a heading is prose
     * and is written as a comment, and the formatter parses what it is handed.
     */
    private static String stated(String source, Map<String, List<String>> ensures) {
        StringBuilder out = new StringBuilder();
        for (String line : source.lines().toList()) {
            // The heading a behavior's rows are written under, which is the one line of the block
            // that names a behavior. A row is written indented and under it, so nothing else here
            // can be read for one.
            String behavior =
                    line.startsWith("example ") ? line.substring("example ".length()) : null;
            List<String> clauses =
                    behavior == null ? List.of() : ensures.getOrDefault(behavior, List.of());
            if (!clauses.isEmpty()) {
                // The words the author put in the declaration, quoted whether or not the checker
                // could make a rule of them: what is claimed is that they are written, and nothing
                // further.
                out.append(String.format("// `ensures` written for `%s`:%n", behavior));
                for (String clause : clauses) {
                    for (String each : clause.lines().toList()) {
                        out.append("//     ").append(each).append(System.lineSeparator());
                    }
                }
                out.append(System.lineSeparator());
            }
            out.append(line).append(System.lineSeparator());
        }
        return out.toString();
    }

    /**
     * What could not be written, said rather than left out — and said once.
     *
     * <p>A block that printed only the rows it managed would read as though it had filled everything.
     * One that printed a line per combination would say the same thing hundreds of times: a position
     * nothing can write a value for makes every combination it takes part in unfillable, and the
     * position is the fact while the combinations are arithmetic on it.
     *
     * <p>A generation that ended and one that carried on without a position are said apart. This
     * printed {@code generation stopped} over whatever it was given, which is a claim about the run
     * taken from the presence of a reason — and where some positions were read and others were not,
     * the rows it was offering were printed two lines above the line saying it had stopped.
     */
    private static void notes(StringBuilder out, String behavior, Adequacy.Filling filling,
                              SourceRendering rendering, Offering offering,
                              PublishedRuleHandle.WhereARuleIs places) {
        Set<String> said = new LinkedHashSet<>();
        List<Generator.UnresolvedCombination> left =
                new ArrayList<>(filling.composed().unresolved());
        left.addAll(filling.boundaries().unresolved());
        for (Generator.UnresolvedCombination each : left) {
            say(out, said, String.format("// no row for `%s` in `%s`: %s%n",
                    each.subject(), behavior, saidOf(each)));
        }
        // Every finding a row could answer, and not only the ones a strategy took. One printed in
        // the report and left out of this block is one an author is told nothing about, while the
        // rows above it read as though they filled everything.
        for (Adequacy.GenerationDisposition each : shown(filling, offering)) {
            switch (each.outcome()) {
                case GenerationOutcome.Generated _ -> { }
                // Each of what was tried, because they are not one fact: a combination the model
                // refuses and one the search stopped at are different news, and a line carrying
                // whichever came first carried the order the cells were walked in.
                // Named for the finding and not for where the search went. A class's own search is
                // about the class either way; an arm's is looked for at the classes a way into it
                // leaves, and named for those it read as the class's line — the same words twice,
                // so the arm's news was dropped as a repeat of the class's (issue #1009).
                case GenerationOutcome.CannotGenerate cannot -> cannot.why().forEach(why ->
                        say(out, said, String.format("// no row for `%s` in `%s`: %s%n",
                                each.finding().about() instanceof About.AnArmNoRowGoesThrough
                                        ? about(each.finding(), rendering, places) : why.subject(),
                                behavior, saidOf(why))));
                // Told apart from the one above it in its own words. A strategy that tried and
                // composed nothing and a finding nothing takes are different pieces of news: the
                // first says what the attempt came to, and whether a row can be written at all is
                // its reason's to say; the second says no run of this will offer one until
                // something is written for it.
                // Each of what is missing, for the reason the attempts above are each said: a
                // thing that stands in two places is read at both, and what is missing at one of
                // them is not what is missing at the other.
                case GenerationOutcome.NotSupported none -> none.reasons().forEach(why ->
                        say(out, said,
                                String.format("// nothing offers a row for `%s` in `%s`: %s%n",
                                        about(each.finding(), rendering, places), behavior,
                                        why.said())));
                // Said rather than passed over, because the report counts this coordinate among
                // what is missing and no row is offered for it. Left out, an author reads a gap
                // above and no account of why nothing was written for it; the account is that the
                // line it is a coordinate of is owed one row and has one — written already, or
                // composed at another of the line's positions and offered above.
                case GenerationOutcome.ObligationAlreadySettled _ -> say(out, said,
                        String.format("// no row offered for `%s` in `%s`: this line is answered"
                                + " by a row elsewhere%n",
                                about(each.finding(), rendering, places), behavior));
                // Filtered out above, and listed here so that the switch stays exhaustive: an
                // answer added later has to be given words rather than falling silently into
                // whichever arm a default would have put it in.
                case GenerationOutcome.NotApplicable _ -> { }
            }
        }
        List<GenerationReason> stopped = new ArrayList<>(filling.composed().reasons());
        stopped.addAll(filling.boundaries().reasons());
        for (GenerationReason why : stopped) {
            // Through the same set the lines above went through. Two searches of one behavior stop
            // for one reason — nothing built to put a candidate through stops both — and a reader
            // told that twice reads two things having gone wrong.
            lines(out, said, switch (why) {
                case GenerationReason.PositionWithheld withheld -> String.format(
                        "// no rows offered at `%s`: a row's value there could not be read, so a"
                                + " row written for it may be one that is already here%n",
                        withheld.axis());
                case GenerationReason.SearchLimit limit -> String.format(
                        "// generation stopped for `%s`: %d %s past the row limit%n",
                        limit.behavior(), limit.owed(),
                        limit.owed() == 1 ? "class or arm" : "classes and arms");
                // Beside the line above rather than folded into it. That one is a budget an author
                // can raise; this is a walk that was never made, and a reader told the first where
                // the second happened would raise a limit that changes nothing.
                // What was not walked, and not what is missing from the block. A row through an arm
                // behind one of these comes from the way into the arm and is offered above, so a
                // line saying rows were not offered here says a row is owed where one is written.
                case GenerationReason.GroupsNotOffered held -> String.format(
                        "// the combinations of %d %s of `%s` were not looked in: each has more of"
                                + " them together than this walks%n",
                        held.groups(), held.groups() == 1 ? "group of decisions" : "groups of"
                                + " decisions", held.behavior());
                case GenerationReason.NothingToBuildAgainst none -> String.format(
                        "// generation stopped for `%s`: there was nothing to build a candidate"
                                + " against%n", none.behavior());
                case GenerationReason.NoValuesWereAskedFor none -> String.format(
                        "// no rows offered at the lines of `%s`: this build composed no values,"
                                + " and a row at a line is a value that went through the"
                                + " decoders%n", none.behavior());
                case GenerationReason.LinkageFailed failed -> String.format(
                        "// generation stopped for `%s`: the generated classes would not link, so"
                                + " the decoders a candidate is built through were out of reach%n",
                        failed.behavior());
                // Not a stop. The rows above it are there and are worth writing; what is said is
                // that nothing ran them, so what each is offered for is a reading of the body
                // rather than something anything watched.
                case GenerationReason.RowsNotConfirmed unconfirmed -> String.format(
                        "// rows offered for `%s` were not run, so which combination each reaches"
                                + " is read off the body rather than observed%n",
                        unconfirmed.behavior());
                // The reasons it rests on rather than a word of its own. What was not read is a
                // measurement's answer and is already said in those words; saying it again in the
                // generator's would be the same fact under two spellings, read side by side.
                case GenerationReason.RowsNotRead unread -> {
                    StringBuilder lines = new StringBuilder();
                    // In the order a document writes them in, which is the order every surface
                    // that says these facts writes them in. A block written from the account
                    // itself would come out in whatever that iterated in, and a person comparing
                    // two generations of one model would be reading the difference between two
                    // walks.
                    for (PublishedIncompleteness because
                            : PublishedIncompleteness.everyOne(unread.because()).written()) {
                        lines.append(String.format("// generation stopped for `%s`: %s%n",
                                unread.behavior(), Reasons.said(because.fact(), rendering)));
                    }
                    yield lines.toString();
                }
            });
        }
    }

    /** Each line of what a reason came to, and each of them once. */
    private static void lines(StringBuilder out, Set<String> said, String written) {
        for (String line : written.lines().toList()) {
            say(out, said, line + System.lineSeparator());
        }
    }

    /** One line, and once. Two gaps can rest on one fact, and a reader owed it is owed it once. */
    private static void say(StringBuilder out, Set<String> said, String line) {
        if (said.add(line)) {
            out.append(line);
        }
    }

    /**
     * What a gap is about, in the words its own finding carries.
     *
     * <p>Read off the value the finding was established with, so that a subject printed here and
     * a subject printed in the report are the same words about the same thing.
     */
    private static String about(Adequacy.Finding finding, SourceRendering rendering,
                                PublishedRuleHandle.WhereARuleIs places) {
        return switch (finding.about()) {
            // The point's own words, which is what the edge's own attempt is named by a few lines
            // above ({@code saidOf}). Spelled out here as well, the two vocabularies differed by
            // the role: a point away from the line was written as the value the line is at, which
            // is the one place in reach that such a point is not.
            case About.APointOfABorder(var point) ->
                    RuleHandleProse.said(point.said(places), rendering, null);
            // The same words on what the declaration wrote. Nothing composes a row for one of
            // these yet — the search walks one behavior's inputs and this line is owed once over
            // all of them — so what is printed beside it is that, in its own sentence.
            case About.APointOfADeclaredBorder(var debt) -> debt.said();
            // The arm's own short name, which is what the report writes and what the document's
            // `subject` joins on. The finding carries the arm rather than words about it, so that
            // the sentence a diagnostic says in the reader's language and the words written here
            // are two readings of one arm rather than one of them being handed the other's.
            case About.AnArmNoRowGoesThrough(var arm) -> ArmVocabulary.label(arm);
            case About.ACaseNoRowAppliesItTo(var _, var missing, var _) -> missing.name();
            case About.ACaseNoRowExpects(var missing) -> missing.name();
            // The class and the measure it is a class of, which a class name alone does not say:
            // two parameters of one type divide into classes of the same names, and one location is
            // measured at more than one number.
            case About.AClassNoRowIsIn(var missing) ->
                    missing.name() + " at " + missing.axis().name();
            // The behavior whose decision it is a rule of, which is as far as words about a rule
            // go. What tells one from another is the proposition each of its conditions is keyed
            // on, written the one way round that makes a comparison and its denial one column —
            // and printing that would show an author a comparison they did not write.
            case About.ARuleNoRowTakes(var behavior, var _) -> "a decision rule of " + behavior;
            // Findings row synthesis is not about, which `shown` leaves out and nothing here is
            // asked to name. Listed rather than defaulted so that a shape added later has to be
            // given words here.
            case About.ACaseNothingWasSeenToProduce _,
                    About.ARowAtAnArmAwaitsItsAnswer _, About.AnUnansweredRow _,
                    About.APositionNoLineDivides _, About.APositionThisCouldNotRead _,
                    About.ARuleWithoutALine _, About.ARuleNothingClassified _,
                    About.AQuestionNothingAnswered _,
                    About.APositionWhoseRulesWereNotReached _,
                    About.APositionReadWiderThanItsRules _ ->
                    throw new IllegalStateException("no row answers this finding: " + finding);
        };
    }

    /**
     * What to print about a combination nothing was written for: what the class said about itself
     * where it said anything, and the category of the answer otherwise.
     *
     * <p>The category is what a reader acts on and the sentence is which case of it this was. A
     * class that recorded why nothing was composed for it knows something the category does not,
     * and printing the category over it loses the one part an author can do anything with.
     */
    private static String saidOf(Generator.UnresolvedCombination left) {
        return left.said().orElseGet(() -> why(left.reason()));
    }

    private static String why(Generator.UnresolvedCombination.Reason reason) {
        return switch (reason) {
            case NOTHING_COMPOSES_ONE ->
                    "nothing here could build a representative for it, which does not make one"
                            + " unwritable";
            case ALL_CANDIDATES_REJECTED ->
                    "every value tried was refused at construction, which does not make the"
                            + " combination impossible";
            // What is missing is the stand-in and not the row's values, so an author reading this
            // is being told what to write beside the row rather than that no row exists.
            case NOTHING_STANDS_IN_FOR_A_DEPENDENCY ->
                    "nothing here could answer for a behavior the target depends on, and a row"
                            + " that stands none in is a row nothing applies";
            case A_TABLE_IS_WHAT_THIS_NEEDS ->
                    "it needs a behavior the target depends on to answer by what it was applied to,"
                            + " which is a table written for the module and not a line on a row";
            // As above: one of the two ways a search leaves something untried has a number in it
            // and the other has none, so neither is said as a halt here.
            case THE_SEARCH_LEFT_SOMETHING_UNTRIED -> "the search left something untried";
            case THE_GROUP_WAS_NOT_OFFERED ->
                    "the decisions that settle it have more combinations together than this offers"
                            + " a row for, so none of them was looked in";
            case NO_CERTIFIED_WITNESS ->
                    "no row composed for it was seen reaching it, which does not make it"
                            + " unreachable";
            case THE_WAY_IN_PLACES_AT_NO_CLASS ->
                    "the way to it holds a decision that no class of any position stands for, so"
                            + " nothing here can steer a row along it";
            // Nothing was left untried here and nothing is unwritable: the value is in hand and
            // the block is full. What lifts it is the number of rows a block offers, which is not
            // what any of the words above are about.
            case THE_BLOCK_IS_AS_LONG_AS_IT_MAY_BE ->
                    "a value was found for it and this block already offers as many rows as it may";
            case THE_RULES_LEAVE_NOTHING_THERE ->
                    "the rules leave no value here, and every combination they do leave was tried";
            case ONE_POSITION_CANNOT_BE_BOTH ->
                    "it would need one position to be two things at once, which no value is, so"
                            + " there is no row to write";
            case NOTHING_TO_BUILD_AGAINST ->
                    "the module's classes were not there to build a candidate against";
            case NO_VALUES_WERE_ASKED_FOR ->
                    "this build composed no values, so nothing was tried at it";
            case LINKAGE_FAILED ->
                    "the generated classes would not link, so the decoders a candidate is built"
                            + " through were out of reach";
            case THE_POSITION_WAS_WITHHELD ->
                    "a row's value at that position could not be read, so no class of it was looked"
                            + " for — one written for it may be one that is already here";
            case THE_ROWS_WERE_NOT_READ ->
                    "the rows were not read, so nothing was looked for; what stopped them being"
                            + " read is said above";
            case NO_CANDIDATE_WAS_OFFERED ->
                    "the walk over what could stand there put no value forward, so nothing was"
                            + " built and nothing was refused";
            case NO_READING_OF_THE_LINE_COULD_BE_SEARCHED ->
                    "no reading of the line this asked about could be searched, so nothing was"
                            + " looked for at it — which says nothing about whether a row stands"
                            + " there";
        };
    }


    private GeneratedRows() {}
}
