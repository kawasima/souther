package souther.compiler;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.reflect.AccessFlag;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In the packages that read a program, going on from a failure of the platform is somebody's leave.
 *
 * <p>What an analysis may be quiet about is a limit it named, and a limit is a value something with
 * the standing to say so made. A failure with no such value behind it is this compiler contradicting
 * itself, and the way that goes wrong is silent: a subject the analysis fell over on comes back with
 * nothing to report, which is what a subject that passed comes back as. Nothing fails while it does,
 * which is why a person reading the diff will not find it either.
 *
 * <p><b>What is watched is the way out of a handler, not the catch.</b> Catching widely is ordinary
 * and sometimes the only thing on offer — a class file the JDK refuses to parse, a message the
 * formatter will not take — and most of the ones in these packages end by throwing: the emitter says
 * what it could not read back, the writer says which limit of the class file was reached, a term
 * says which of the values it holds did not answer. None of those turns a failure into an answer.
 * What does is a handler a caller can return through, so that is what this asks: from where the
 * handler begins, following only the edges an ordinary run takes, is there a {@code return} to
 * arrive at.
 *
 * <p><b>And the ones that can are written down one place at a time.</b> Not the wide types — a type
 * is not what earns the right to go on. {@link StackOverflowError} out of a matcher handed a budget
 * is the engine answering that it did not finish inside it, and the same error out of anywhere else
 * is this compiler recursing without end; a licence written against the type would cover both, and
 * the second would be quiet from the day somebody wrote it. So what is granted is a place, and what
 * is written beside it is the question that place put and what an answer to it is
 * ({@link #PLATFORM_QUESTIONS}).
 *
 * <p>That is a list, and it is not the list this was written to avoid. Nothing that throws is in it,
 * however wide it catches, so it is not a copy of where the broad catches are. What it holds is the
 * decisions — the places allowed to turn a host's failure into something this compiler says — and a
 * new one lands here and is read before it is granted.
 *
 * <p>Read off the compiled classes, because what a handler does is a fact about the bytecode. A
 * {@code throw} of a value some other method computed is an {@code athrow} here like any other, and
 * a handler that branches before deciding is walked rather than read off its first instruction.
 *
 * <p><b>Most of what is examined is not written down anywhere.</b> A {@code switch} over a sealed
 * type compiles to a handler catching {@link Throwable} and rethrowing it as a
 * {@link MatchException}, and this compiler is written in those, so they outnumber the handlers a
 * person typed many times over. They are left in rather than recognised: telling them apart means
 * knowing how one version of javac writes them, and there is nothing to be had by it — every one of
 * them ends by throwing, which is the answer this asks for. What it costs is that the population
 * being non-empty no longer says much on its own, which is what the second test below is for.
 */
class NoBroadFailureBecomesAnAnswerInTheAnalysisCoreTest {

    /**
     * Where a program is read.
     *
     * <p>Not every package of this compiler. A run has to be able to go on from things that are not
     * about a program at all — a jar it cannot parse, a document it cannot format, an editor session
     * outliving a request — and those adapters answer with what they had before. These three are
     * where a program is turned into what is said about it, and there an answer that came out of a
     * failure is indistinguishable from an answer.
     */
    private static final Set<String> READ_A_PROGRAM = Set.of(
            "souther/compiler/check/",
            "souther/compiler/codegen/",
            "souther/compiler/query/");

    /**
     * Whether catching {@code type} says nothing about what was met.
     *
     * <p>Asked of the type rather than looked up in a list of the wide ones somebody thought of.
     * Such a list is the shape this whole change is about: the failures nobody has named yet go
     * through it, and nothing says so while they do. A handler catching {@link NullPointerException}
     * is as wide as one catching {@link IllegalStateException}, and it is wide here without anybody
     * having said so first.
     *
     * <p>Two things make it wide. Nothing in this build declares it, which is what says this
     * compiler gave it no meaning of its own — a refusal declared here is a subclass of
     * {@link RuntimeException} like any other, and catching {@code NotOneClause} is catching one
     * thing. Read off the name, because that is what ownership is: which loader a class arrived
     * through says where it was found, and a library's own runtime exception arrives the same way
     * this compiler's does.
     *
     * <p>And it is an unchecked failure: a {@link RuntimeException} or an {@link Error}, or
     * something above them that takes either in. A checked exception is not one — {@code IOException}
     * is a condition a method declared, and a handler naming it says what it met.
     *
     * <p>Nothing about which types are all right is decided here. A type is not what earns the right
     * to turn a failure into an answer; a place that asked a question is, and that is
     * {@link #PLATFORM_QUESTIONS}.
     */
    private static boolean isWide(String internalName) {
        if (internalName.startsWith("souther/")) {
            return false;   // declared by this build, so it names what it catches
        }
        String binary = internalName.replace('/', '.');
        Class<?> type;
        try {
            type = Class.forName(binary, false,
                    NoBroadFailureBecomesAnAnswerInTheAnalysisCoreTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("a handler catches " + binary
                    + ", which this cannot load — so whether it is wide was not decided", e);
        }
        return RuntimeException.class.isAssignableFrom(type)
                || Error.class.isAssignableFrom(type)
                || type.isAssignableFrom(RuntimeException.class);
    }

    /**
     * A method, told apart the way a method is, and one of the types it catches.
     *
     * <p>The descriptor is part of it because it is part of what a method is: two overloads under
     * one name are two places, and a licence given to the first would cover the second the day
     * somebody wrote it. The caught type is held whole for the same reason — two libraries may
     * spell an exception alike, and the one this compiler decided about is the one it decided
     * about. Both are shortened when they are shown and never when they are compared.
     */
    private record Site(String owner, String method, String descriptor, String caught)
            implements Comparable<Site> {

        @Override
        public String toString() {
            String simple = caught.substring(caught.lastIndexOf('.') + 1);
            return owner + "." + method + descriptor + " catches " + simple;
        }

        /**
         * Over the parts, and never over what {@link #toString} makes of them.
         *
         * <p>The sets and maps this is filed in take their identity from here, so ordering by the
         * shown form would put back exactly what holding the parts whole was for: two exceptions of
         * two libraries spelled alike are shown alike, and the licence given to one would be a
         * licence for the other.
         */
        @Override
        public int compareTo(Site other) {
            int by = owner.compareTo(other.owner);
            if (by == 0) {
                by = method.compareTo(other.method);
            }
            if (by == 0) {
                by = descriptor.compareTo(other.descriptor);
            }
            if (by == 0) {
                by = caught.compareTo(other.caught);
            }
            return by;
        }
    }

    /**
     * And what tells two of those apart is every part of them.
     *
     * <p>Held here rather than left to a reading of the comparison. What files a site under a key is
     * the ordering, so a part left out of it is a part two sites may differ in and be one — and
     * which parts those are is the whole of what the permissions above address.
     */
    @Test
    void twoSitesThatDifferInAnyPartAreTwoSites() {
        Site one = new Site("a.Owner", "run", "()V", "com.alpha.TimeoutException");

        assertTrue(one.compareTo(new Site("a.Other", "run", "()V", "com.alpha.TimeoutException")) != 0,
                "two owners are two places");
        assertTrue(one.compareTo(new Site("a.Owner", "walk", "()V", "com.alpha.TimeoutException")) != 0,
                "two methods are two places");
        assertTrue(one.compareTo(new Site("a.Owner", "run", "(I)V", "com.alpha.TimeoutException")) != 0,
                "two overloads under one name are two places");
        assertTrue(one.compareTo(new Site("a.Owner", "run", "()V", "com.beta.TimeoutException")) != 0,
                "two exceptions spelled alike in two packages are two things to catch");
        assertEquals(0, one.compareTo(new Site("a.Owner", "run", "()V", "com.alpha.TimeoutException")),
                "and the same site in every part is one site");
    }

    /**
     * What the walk found: every wide handler, and how many of the ones at each site a run can
     * return through.
     *
     * <p>A count and not a set. Two handlers written in one method for one type are two places, and
     * a set of what they are called keeps one of them — so a second catch added beside a licensed
     * one would be licensed by the first without anybody saying so. What is counted is the distinct
     * places a handler begins at, because one {@code catch} can be entered from several stretches of
     * a method and is still the one place a person wrote.
     */
    private record Scan(Set<Site> examined, Map<Site, Integer> returning) {}

    /** A place that put a question to the platform, and what an answer to it is. */
    private record Permission(String owner, String method, String descriptor, String caught,
                              String asks) {

        Site site() {
            return new Site(owner, method, descriptor, caught);
        }
    }

    /**
     * Who may turn a failure of the platform into an answer of this compiler's, one place at a time.
     *
     * <p>Not a list of types. A type is not what earns this: {@link StackOverflowError} out of a
     * matcher given a budget is the engine saying it did not finish inside it, and the same error
     * out of anywhere else is this compiler recursing without end. Written against the type, one
     * fold's licence would cover both, and the second would be quiet from the day it was written.
     * So what is written down is the place, what it asked, and what an answer to it is — and it is
     * read by a person, because that is a decision and not something a walk can settle.
     *
     * <p><b>Only the ones that return.</b> A handler that ends by throwing needs no line here
     * however wide it is, and there are more of those than of these. What is being granted is
     * exactly the thing this whole change is about — going on from a failure — so it is granted one
     * place at a time and every grant is a sentence somebody wrote.
     */
    private static final List<Permission> PLATFORM_QUESTIONS = List.of(
            new Permission("souther.compiler.check.Cardinality$Standing", "times",
                    "(Lsouther/compiler/check/Cardinality$Standing;)"
                            + "Lsouther/compiler/check/Cardinality$Standing;",
                    "java.lang.ArithmeticException",
                    "whether the product of two counts fits, asked of multiplyExact — answered as a"
                            + " count nothing here can name"),
            new Permission("souther.compiler.check.Cardinality$Standing", "choose",
                    "(J)Lsouther/compiler/check/Cardinality$Standing;",
                    "java.lang.ArithmeticException",
                    "the same, of the ways of choosing from a count"),
            new Permission("souther.compiler.check.Cardinality$Standing", "toThe",
                    "(J)Lsouther/compiler/check/Cardinality$Standing;",
                    "java.lang.ArithmeticException",
                    "the same, of the lists of one length over a count"),
            new Permission("souther.compiler.check.OccurrenceValues", "wholeValuesAt",
                    "(Lsouther/compiler/check/RuleKey;)Lsouther/compiler/check/Cardinality;",
                    "java.lang.ArithmeticException",
                    "whether the whole numbers between two ends fit in a count, asked of"
                            + " longValueExact — answered as a count nothing here can name"),
            new Permission("souther.compiler.check.ConstEval", "arith",
                    "(Lsouther/compiler/types/BinOp;Ljava/lang/Object;Ljava/lang/Object;)"
                            + "Ljava/util/Optional;",
                    "java.lang.ArithmeticException",
                    "whether an addition, subtraction or multiplication of two written numbers is"
                            + " one the run time computes, asked of the exact methods the operators"
                            + " emit — answered as a fold that does not settle it, so the run time"
                            + " is what refuses it"),
            new Permission("souther.compiler.check.ScaleRange", "receivedUnchanged",
                    "(Ljava/math/BigDecimal;)Ljava/lang/Integer;",
                    "java.lang.ArithmeticException",
                    "whether a scale is a number the run time can be handed as the number it is,"
                            + " asked of intValueExact — answered as no place count"),
            new Permission("souther.compiler.check.ConstEval", "matches",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/util/Optional;",
                    "java.util.regex.PatternSyntaxException",
                    "whether the platform's engine will compile a pattern an author wrote —"
                            + " answered as a fold that does not settle the match, which the"
                            + " run-time check does"),
            new Permission("souther.compiler.check.ConstEval", "matches",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/util/Optional;",
                    "java.lang.StackOverflowError",
                    "whether the engine finishes matching inside the budget it was handed. The"
                            + " budget is what makes this a question: a matcher given one answers"
                            + " by not finishing, and the fold declines the same way it declines a"
                            + " pattern the engine refused"),
            new Permission("souther.compiler.query.Adequacy$ARowBuiltAndRun", "attempt",
                    "(Ljava/lang/String;Ljava/util/Map;"
                            + "Lsouther/compiler/partition/Reachability$Reaching;"
                            + "Lsouther/compiler/partition/AnswersDemanded;)"
                            + "Lsouther/compiler/partition/Generator$BoundaryAttempt;",
                    "java.lang.LinkageError",
                    "whether the classes generated for this model link, asked by building a row"
                            + " out of them — answered as nothing tried, which is not everything"
                            + " tried being refused"),
            new Permission("souther.compiler.query.Adequacy$ARowBuiltAndRun", "read",
                    "(Lsouther/compiler/partition/RowToRun;)Lsouther/compiler/query/RowAsRead;",
                    "java.lang.LinkageError",
                    "the same, asked by reading a row through them — answered as a row nothing"
                            + " built, which is not a row seen to stand somewhere else"),
            new Permission("souther.compiler.query.Adequacy$Generated", "compute",
                    "(Lsouther/compiler/query/Db;)Lsouther/compiler/query/Answer;",
                    "java.lang.LinkageError",
                    "the same, asked by assembling what a model admits — answered by reporting no"
                            + " combination rather than reporting them impossible"));

    @Test
    void everyWideHandlerThatReturnsWasGivenLeaveToOnePlaceAtATime() {
        Map<Site, Integer> allowed = new TreeMap<>();
        PLATFORM_QUESTIONS.forEach(each -> allowed.merge(each.site(), 1, Integer::sum));

        assertEquals(allowed, scan().returning(),
                "a run through one of these leaves a handler and goes on to answer. Where nobody"
                        + " gave that place leave, a failure nobody named comes back as what this"
                        + " compiler has to say about the program — and a subject it fell over on"
                        + " reads as a subject that passed. A count on each side, so a second catch"
                        + " written beside a licensed one is not covered by it. What each of the"
                        + " places below asked: " + why());
    }

    private static Map<String, String> why() {
        Map<String, String> out = new TreeMap<>();
        PLATFORM_QUESTIONS.forEach(each -> out.put(each.site().toString(), each.asks()));
        return out;
    }

    /**
     * And the handlers somebody wrote are among them.
     *
     * <p>Without this the question above is answered by a walk that read nothing — a package renamed,
     * a directory not built, a way of writing a handler the reader does not recognise — or by one
     * that read only what javac put there. A handler naming anything but {@link Throwable} is one
     * somebody typed, since the wrapper a pattern switch compiles to always names that; so this says
     * the walk reached the code rather than only its desugaring.
     */
    @Test
    void theHandlersSomebodyWroteAreAmongThem() {
        assertTrue(scan().examined().stream()
                        .anyMatch(site -> !site.caught().equals("java.lang.Throwable")),
                "every wide handler found names Throwable, which is what a pattern switch compiles"
                        + " to — so this walk reached the desugaring and not the code, and the"
                        + " check above passed over nothing anybody wrote");
    }

    private static Scan scan() {
        Set<Site> examined = new TreeSet<>();
        // Where each site's returning handlers begin. A set of the places rather than a count of
        // the table rows: one `catch` can be entered from several stretches of a method and is one
        // place a person wrote, while two written beside each other are two.
        Map<Site, Set<Integer>> beginningAt = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String owner = model.thisClass().asInternalName();
            if (READ_A_PROGRAM.stream().noneMatch(owner::startsWith)) {
                continue;
            }
            // A class nobody wrote. A switch over an enum compiles to a synthetic holder whose
            // initializer catches a NoSuchFieldError per constant and carries on — a handler that
            // does return, and one no line of this repository asked for. What a person wrote is in
            // the class they wrote it in.
            if (model.flags().has(AccessFlag.SYNTHETIC)) {
                continue;
            }
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> {
                    Walk walk = Walk.of(code);
                    for (ExceptionCatch caught : code.exceptionHandlers()) {
                        // No type named is not a narrow handler. It is the widest there is — the
                        // entry catches everything, which is what a `finally` compiles to — so it
                        // is asked the same question under the name of what it takes.
                        String type = caught.catchType()
                                .map(entry -> entry.asInternalName())
                                .orElse("java/lang/Throwable");
                        if (!isWide(type)) {
                            continue;
                        }
                        Site site = new Site(owner.replace('/', '.'),
                                method.methodName().stringValue(),
                                method.methodType().stringValue(), type.replace('/', '.'));
                        examined.add(site);
                        if (walk.canReturnFrom(caught.handler())) {
                            beginningAt.computeIfAbsent(site, _ -> new TreeSet<>())
                                    .add(walk.beginsAt(caught.handler()));
                        }
                    }
                });
            }
        }
        Map<Site, Integer> returning = new TreeMap<>();
        beginningAt.forEach((site, places) -> returning.put(site, places.size()));
        return new Scan(examined, returning);
    }

    /**
     * One method's instructions and the edges an ordinary run takes between them.
     *
     * <p>Exception edges are left out on purpose. A handler reached from inside another handler is
     * itself one of the handlers this asks about, so following the throw would answer for it twice
     * and would say of a handler that rethrows that it can return.
     *
     * <p><b>Nothing here has a quiet arm.</b> A label nothing binds and a step off the end of a
     * method are this reader failing to read a class file, not facts about the code — and answered
     * quietly they are always answered on the safe-looking side: an edge dropped is a return not
     * arrived at, and a handler skipped is a handler nobody asked about. That is the shape this
     * whole check exists to refuse, so it is refused here first.
     */
    private record Walk(List<Instruction> instructions, Map<Label, Integer> at) {

        static Walk of(CodeModel code) {
            List<Instruction> instructions = new ArrayList<>();
            Map<Label, Integer> at = new IdentityHashMap<>();
            for (CodeElement element : code) {
                if (element instanceof LabelTarget target) {
                    at.put(target.label(), instructions.size());
                } else if (element instanceof Instruction instruction) {
                    instructions.add(instruction);
                }
            }
            return new Walk(instructions, at);
        }

        /** Which instruction {@code label} is bound to. */
        int beginsAt(Label label) {
            Integer where = at.get(label);
            if (where == null) {
                throw new IllegalStateException(
                        "a label this method's code refers to is bound nowhere in it, so what a run"
                                + " does from there was not read");
            }
            return where;
        }

        boolean canReturnFrom(Label handler) {
            Set<Integer> seen = new HashSet<>();
            Deque<Integer> todo = new ArrayDeque<>();
            todo.add(beginsAt(handler));
            while (!todo.isEmpty()) {
                int here = todo.removeFirst();
                if (!seen.add(here)) {
                    continue;
                }
                if (here >= instructions.size()) {
                    throw new IllegalStateException("a run walked off the end of a method, so the"
                            + " edges out of its last instruction were not read");
                }
                Instruction instruction = instructions.get(here);
                if (instruction instanceof ReturnInstruction) {
                    return true;
                }
                todo.addAll(nextAfter(here, instruction));
            }
            return false;
        }

        private List<Integer> nextAfter(int here, Instruction instruction) {
            List<Integer> out = new ArrayList<>();
            switch (instruction) {
                case BranchInstruction branch -> {
                    add(out, branch.target());
                    if (branch.opcode() != Opcode.GOTO && branch.opcode() != Opcode.GOTO_W) {
                        out.add(here + 1);
                    }
                }
                case TableSwitchInstruction table -> {
                    add(out, table.defaultTarget());
                    for (SwitchCase one : table.cases()) {
                        add(out, one.target());
                    }
                }
                case LookupSwitchInstruction lookup -> {
                    add(out, lookup.defaultTarget());
                    for (SwitchCase one : lookup.cases()) {
                        add(out, one.target());
                    }
                }
                default -> {
                    // athrow ends a way out and has no ordinary successor; a return was answered
                    // above. Everything else falls through to what is written after it.
                    if (instruction.opcode() != Opcode.ATHROW) {
                        out.add(here + 1);
                    }
                }
            }
            return out;
        }

        private void add(List<Integer> out, Label label) {
            out.add(beginsAt(label));
        }
    }

}
