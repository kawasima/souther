package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Who may say that this check met a limit, and what each of them met.
 *
 * <p>The discharge check falls open on a limit and never on a failure, and which of the two
 * something is, is decided where it was met rather than at the boundary that records it. So what
 * matters is who may make one of these — a {@link WhatTheCheckCannotRead} — and that is a decision
 * about the analysis rather than something an access modifier settles: a factory a package can reach
 * is one anything in that package can call.
 *
 * <p><b>What this holds and what it does not.</b> It says who makes a limit today and what each of
 * them is for; a new maker lands here and has to be written down as one of them, which is where the
 * decision gets read. It does not say that a broad {@code catch} can never come back — a
 * {@code catch} of everything inside an existing maker would leave these numbers where they are.
 * That is held by the shape of the ways in instead: every one of them names what was met, and none
 * takes a {@code Throwable}, so wrapping an arbitrary failure means writing a way in that says it
 * does.
 */
class WhoMaySayThisCheckMetALimitIsWrittenDownTest {

    private static final String LIMIT = "souther/compiler/check/WhatTheCheckCannotRead";

    /**
     * What hands back a limit: the class it is declared on, its name, and what it takes.
     *
     * <p>The descriptor is part of it because it is part of what a method is. Told apart by name
     * alone, a second way in written beside an existing one under that name is not a second row
     * here — it collapses onto the first, and the counts below go on reading what they read while a
     * new thing an analysis may stop on has been added. There are two overloads named alike as it
     * is, so this is what the file is looking at rather than a case it might meet.
     */
    private record Producer(String owner, String name, String takes)
            implements Comparable<Producer> {

        String shown() {
            return owner.replace('/', '.').replace('$', '.') + "." + name + takes;
        }

        @Override
        public int compareTo(Producer other) {
            return shown().compareTo(other.shown());
        }
    }

    /** A way in, and what a caller of it has met. */
    private record Licence(String who, String why) { }

    /**
     * Every way to come by a limit, and what each of them says was met.
     *
     * <p>Declared because the assertions below are only as wide as this is. A new factory lands here
     * first, and writing it down is saying what an analysis may now fall open on.
     */
    private static final List<Licence> WAYS_IN = List.of(
            new Licence("souther.compiler.check.WhatTheCheckCannotRead"
                    + ".standingCallHasNoSignatureHere(Ljava/lang/String;Lsouther/compiler/diag/SourcePos;)",
                    "a call the expansion left standing that this reading has no signature for"),
            new Licence("souther.compiler.check.WhatTheCheckCannotRead"
                    + ".secondaryTypingDidNotFinish(Lsouther/compiler/diag/CompileException;)",
                    "a clause the reading below the authoritative check refused"),
            new Licence("souther.compiler.check.WhatTheCheckCannotRead"
                    + ".secondaryTypingDidNotFinish(Lsouther/compiler/check/Unanswerable;)",
                    "the same, of a clause whose meaning rests on a name that denotes nothing"),
            new Licence("souther.compiler.check.WhatTheCheckCannotRead"
                    + ".theWalkLeftAnAnswerUnmade(Ljava/lang/String;)",
                    "a walk that ran to the end and made none of the answers it is written to"
                            + " produce"));

    /**
     * The ones that hand on a limit somebody else made.
     *
     * <p>Held apart from the ways in because they are a different finding. One more of these is one
     * more reader of an answer already given; one more way in is a new thing an analysis may stop
     * on, which is what the assertions below are watching for.
     */
    private static final List<Licence> HANDS_ON = List.of(
            new Licence("souther.compiler.check.InvariantChecker.GaveUp.why()",
                    "what a recorded stop was, read back by a test in this package"),
            new Licence("souther.compiler.check.SecondaryClauseReading"
                    + ".standingCallNothingHereNames(Lsouther/compiler/check/ClauseAsExpanded;"
                    + "Lsouther/compiler/check/Scope;)",
                    "the limit a clause stops this reading on, where every call it cannot name was"
                            + " left standing on purpose"));

    /**
     * Who may say one was met. Adding to this is deciding that something else may make an analysis
     * stop without the compile failing.
     *
     * <p>Told apart the way the ways in are: by what a method is and not by what it is called. A
     * second reading written beside one of these under its name would otherwise be the same row,
     * and the licence granted to the first would cover it.
     */
    private static final List<Licence> MAY_SAY = List.of(
            new Licence("souther.compiler.check.SecondaryClauseReading"
                    + ".of(Lsouther/compiler/check/ClauseAsExpanded;Ljava/util/function/Supplier;"
                    + "Ljava/lang/String;)",
                    "types a clause below the check that answers for the program"),
            new Licence("souther.compiler.check.SecondaryClauseReading"
                    + ".standingCallNothingHereNames(Lsouther/compiler/check/ClauseAsExpanded;"
                    + "Lsouther/compiler/check/Scope;)",
                    "asks the expansion and the reading's own scope about every call it cannot"
                            + " name"),
            new Licence("souther.compiler.check.PathReachability"
                    + ".of(Lsouther/compiler/core/Core;Lsouther/compiler/check/Scope;"
                    + "Lsouther/compiler/coverage/CoverageSites$Plan;"
                    + "Lsouther/compiler/inputs/InputDomain;"
                    + "Lsouther/compiler/check/RuleReadingSource;"
                    + "Lsouther/compiler/check/ReadingPolicy;)",
                    "reads which of a plan's comparisons the walk settled nothing about"));

    @Test
    void everyMethodThatHandsBackALimitIsWrittenDownAsOneOfTheTwo() {
        assertEquals(declared(WAYS_IN, HANDS_ON), shown(producers()),
                "a method handing back a limit either makes one or hands on one that was made, and"
                        + " which it is has to be said before anything asks who may call it. What"
                        + " each of these is: " + why(WAYS_IN, HANDS_ON));
    }

    /** And that a limit is made only on the limit itself, so the ways in are all of them. */
    @Test
    void nothingMakesOneExceptTheWaysIn() {
        assertEquals(declared(WAYS_IN), shown(assembling()),
                "a limit made anywhere else is one whose making nothing decided");
    }

    /**
     * What the ways in are allowed to take, written out rather than a list of what they may not.
     *
     * <p>This is the half that keeps a {@code catch} of everything from becoming a limit again in
     * one line: a way in that takes a failure a caller merely caught lets any of them through while
     * the counts above stay exactly where they are. Written the other way round — the kinds nobody
     * may take — the guard is only as good as the names somebody thought of, and an overload taking
     * one more concrete exception passes it while saying nothing.
     *
     * <p>So a new parameter type has to be admitted here, and admitting one is saying that a
     * failure of that kind is something this reading met rather than something it was handed.
     */
    private static final Set<String> MAY_BE_TAKEN = Set.of(
            "Lsouther/compiler/diag/CompileException;",
            "Lsouther/compiler/check/Unanswerable;",
            "Ljava/lang/String;",
            "Lsouther/compiler/diag/SourcePos;");

    @Test
    void aWayInTakesWhatThisReadingMetAndNothingItWasHanded() {
        Map<String, String> taking = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            if (!model.thisClass().asInternalName().equals(LIMIT)) {
                continue;
            }
            for (MethodModel method : model.methods()) {
                if (method.methodName().stringValue().startsWith("<")) {
                    continue;
                }
                method.methodTypeSymbol().parameterList().forEach(each -> {
                    if (!MAY_BE_TAKEN.contains(each.descriptorString())) {
                        taking.put(method.methodName().stringValue(), each.descriptorString());
                    }
                });
            }
        }
        assertEquals(Map.of(), taking,
                "a way in takes what was met. What may be taken, and why each is not a failure a"
                        + " caller caught and passed on: " + MAY_BE_TAKEN);
    }

    @Test
    void onlyAReaderThatMetOneSaysThisCheckMetALimit() {
        assertEquals(declared(MAY_SAY), callers(assembling()),
                "a limit is made where one was met and nowhere else. What each of these met: "
                        + why(MAY_SAY));
    }

    /** And that something does make one, so the rule above is not read over no classes at all. */
    @Test
    void andSomethingDoesSaySo() {
        assertFalse(callers(assembling()).isEmpty(),
                "nothing makes a limit, so the rule above saw nothing");
    }

    @SafeVarargs
    private static Map<String, String> declared(List<Licence>... licences) {
        Map<String, String> out = new TreeMap<>();
        for (List<Licence> each : licences) {
            each.forEach(one -> out.put(one.who(), ""));
        }
        return out;
    }

    /** The ways in, which are the methods declared on the limit itself. */
    private static Set<Producer> assembling() {
        return new TreeSet<>(producers().stream()
                .filter(each -> each.owner().equals(LIMIT)).toList());
    }

    private static Map<String, String> shown(Set<Producer> producers) {
        Map<String, String> out = new TreeMap<>();
        producers.forEach(each -> out.put(each.shown(), ""));
        return out;
    }

    @SafeVarargs
    private static Map<String, String> why(List<Licence>... licences) {
        Map<String, String> out = new LinkedHashMap<>();
        for (List<Licence> each : licences) {
            each.forEach(one -> out.put(one.who(), one.why()));
        }
        return out;
    }

    /** Everything the compiler declares that hands one back. */
    private static Set<Producer> producers() {
        Set<Producer> out = new TreeSet<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String owner = model.thisClass().asInternalName();
            for (MethodModel method : model.methods()) {
                if (method.methodTypeSymbol().returnType().descriptorString()
                        .equals("L" + LIMIT + ";")) {
                    out.add(new Producer(owner, method.methodName().stringValue(),
                            takes(method)));
                }
            }
        }
        assertFalse(out.isEmpty(), "nothing hands one back at all, so this says nothing");
        return out;
    }

    /** Which methods call any of {@code watched}. */
    private static Map<String, String> callers(Set<Producer> watched) {
        assertFalse(watched.isEmpty(), "no producer was watched, so this says nothing");
        Map<String, String> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && watched.contains(new Producer(call.owner().asInternalName(),
                                    call.name().stringValue(),
                                    takesOf(call.typeSymbol().parameterList())))) {
                        calls.put(from + "." + method.methodName().stringValue()
                                + takes(method), "");
                    }
                }));
            }
        }
        return calls;
    }

    /** What a method takes, written the way a reader of a licence above writes it. */
    private static String takes(MethodModel method) {
        return takesOf(method.methodTypeSymbol().parameterList());
    }

    private static String takesOf(List<? extends java.lang.constant.ClassDesc> parameters) {
        StringBuilder out = new StringBuilder("(");
        parameters.forEach(each -> out.append(each.descriptorString()));
        return out.append(')').toString();
    }

}
