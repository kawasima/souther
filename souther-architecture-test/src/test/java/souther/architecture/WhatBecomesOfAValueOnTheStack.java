package souther.architecture;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.MonitorInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.NopInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a value one instruction pushed is used, for a rule about how a value is read.
 *
 * <p>A rule that says a constant may not be compared has to say what compares it, and what compares
 * a value is the instruction that takes it off the stack. Read as the instructions written near it,
 * the answer would be about where the source put the comparison and not about the comparison: a
 * constant written where a value is wanted stands beside whatever the method does next, and one
 * compared against something worked out in a conditional stands several jumps away from the
 * comparison that takes it.
 *
 * <p><b>An instruction is what it takes and what it leaves, and never the difference.</b> A call
 * that takes a receiver and returns something leaves the stack the height it found it, and what is
 * on it is not what was on it. Read as a height, {@code EMPTY.name() == text} is a constant that
 * came through a call and reached a comparison; read as what took what, the constant is what the
 * call took, and the comparison is about a string.
 *
 * <p>So what is followed is how far above the value the stack has come. Where that is further than
 * an instruction takes, the value is under everything the instruction is about and comes through
 * it; where it is not, that instruction is what took the value, and what it did with it is the
 * answer.
 *
 * <p><b>What it cannot follow it refuses, and never answers.</b> An instruction whose effect this
 * cannot say, one that takes the value and puts it back somewhere this does not model, a way round
 * that comes back to where the walk has already been, the beginning of an exception handler, and
 * the end of a method with the value still on the stack are each a place the value was lost rather
 * than taken — and every one of them ends the walk with a refusal. Answered, each would come out as
 * "nothing compares it", which is the one thing a rule written on top of this reads as a fact.
 *
 * <p>What it does follow is a value carried across the jumps that work something else out, across a
 * switch, which takes the number it switched on and leaves the value alone, and across a cast,
 * which leaves what it took. Each way the code may go is remembered with the stack it is reached
 * at, and picked up again where it arrives.
 */
final class WhatBecomesOfAValueOnTheStack {

    private WhatBecomesOfAValueOnTheStack() {
    }

    /**
     * What one instruction takes off the stack and what it leaves there.
     *
     * @param takes    how many slots come off
     * @param leaves   how many go back on
     * @param carrying whether what goes back on is what came off. A cast is the same value said
     *                 under another type; a call that returns something is not, however alike the
     *                 two look to a walk that only counts
     */
    private record Effect(int takes, int leaves, boolean carrying) {

        static Effect of(int takes, int leaves) {
            return new Effect(takes, leaves, false);
        }
    }

    /**
     * Whether the value the instruction at {@code at} pushes is taken by a comparison of two
     * references.
     *
     * <p>Followed forward until something takes the value. Where the code branches, the stack the
     * branch leaves is remembered against the place it jumps to and picked up again there, which is
     * what lets a value compared against a conditional's answer be followed past the jumps that
     * work the answer out.
     */
    static boolean isTakenByAReferenceComparison(List<CodeElement> elements, int at) {
        return followedFrom(elements, at, 0);
    }

    /**
     * The same, of a value put somewhere and taken out again.
     *
     * <p>A value in a local is followed to each place that reads the local before anything writes
     * it again, and is compared where any of them compares it. Answered without following, a
     * constant a clause wrote into a name would come out as one nothing compares, which is a rule
     * that anybody could get round by writing the name.
     */
    private static boolean followedFrom(List<CodeElement> elements, int at, int through) {
        if (through > 4) {
            throw new IllegalStateException("a value was put away and taken out too many times");
        }
        Set<Label> caught = new HashSet<>();
        Map<Label, Integer> placed = new HashMap<>();
        for (int where = 0; where < elements.size(); where++) {
            CodeElement each = elements.get(where);
            if (each instanceof ExceptionCatch handler) {
                caught.add(handler.handler());
            }
            if (each instanceof LabelTarget target) {
                placed.put(target.label(), where);
            }
        }
        Map<Label, Integer> above = new HashMap<>();
        // One, because the value has just been pushed and nothing else is on top of it. What is
        // followed is this number and never the depth of the stack, so where the method's code
        // began does not have to be known.
        int now = 1;
        boolean reached = true;
        for (int next = at + 1; next < elements.size(); next++) {
            CodeElement element = elements.get(next);
            if (element instanceof LabelTarget target) {
                if (caught.contains(target.label())) {
                    // A handler begins with what was thrown and with nothing else, so the stack
                    // there is not this stack and the value cannot be followed across it.
                    throw new IllegalStateException(
                            "a value was followed into the beginning of an exception handler");
                }
                Integer said = above.get(target.label());
                if (said != null) {
                    if (reached && said != now) {
                        throw new IllegalStateException(
                                "two ways to one place leave the stack at two heights");
                    }
                    now = said;
                    reached = true;
                }
                continue;
            }
            if (!(element instanceof Instruction instruction)) {
                continue;
            }
            if (!reached) {
                // Code no way reaches from here, which is what stands between a jump and the place
                // it jumps to. What it does to a stack is not what this value's stack is doing.
                continue;
            }
            Effect effect = effectOf(instruction);
            if (now <= effect.takes()) {
                // This instruction is what takes the value, so what it is is the answer.
                if (instruction instanceof BranchInstruction branch
                        && isAReferenceComparison(branch.opcode())) {
                    return true;
                }
                if (instruction instanceof StackInstruction && effect.leaves() > 0) {
                    // What a rearrangement leaves is the same values somewhere else, and which
                    // slot this one went to is not something this says. Answered, the answer would
                    // be about whatever ends up where the value was.
                    throw new IllegalStateException(
                            "a value was taken by a rearrangement of the stack");
                }
                if (instruction instanceof StoreInstruction put) {
                    return heldIn(elements, next, put.slot(), through);
                }
                if (effect.carrying()) {
                    // The same value under another name, so it goes on being followed as the one
                    // thing this instruction left.
                    now = 1;
                    reached = !ends(instruction);
                    continue;
                }
                return false;
            }
            for (Label target : whereItMayGo(instruction)) {
                Integer to = placed.get(target);
                if (to == null || to <= next) {
                    // A jump backwards, or to somewhere this walk never reaches. What the value
                    // meets on that way round is not read here, and a walk that carried on would be
                    // answering about the way it happened to take.
                    throw new IllegalStateException("a value was followed into a jump that returns");
                }
                above.merge(target, now - effect.takes() + effect.leaves(),
                        WhatBecomesOfAValueOnTheStack::agreeing);
            }
            now += effect.leaves() - effect.takes();
            reached = !ends(instruction);
        }
        // The value is still on the stack and there is no more code, so what took it was on a way
        // this did not follow. Said as "nothing compares it", that would be the one mistake this
        // whole reading is written to refuse.
        throw new IllegalStateException("a value was followed to the end of a method");
    }

    /**
     * Whether what is read out of a local this value was put into is compared.
     *
     * <p><b>A name is not the value, and which value is in it is each way round's own.</b> A name
     * written on one way through the code still holds what it held on the ways that missed the
     * writing, so what a place reads out of it is what any way here left there. Read as the last
     * writing before that place, a name given another value inside a condition would be read as
     * having it on every way, and a comparison after the condition would be about a value nothing
     * put there.
     *
     * <p>So the ways are followed as they are for a value on the stack, and put together where they
     * meet: the name holds the value where any way arriving holds it. A way that comes back to
     * where this has already been is walked again, until no way says anything new — which it
     * reaches, since a name that holds the value goes on holding it and no round takes that back.
     */
    private static boolean heldIn(List<CodeElement> elements, int from, int slot, int through) {
        Map<Label, Integer> placed = new HashMap<>();
        for (int where = 0; where < elements.size(); where++) {
            if (elements.get(where) instanceof LabelTarget target) {
                placed.put(target.label(), where);
            }
        }
        Map<Label, Boolean> arriving = new HashMap<>();
        Set<Integer> readWhileHeld = new LinkedHashSet<>();
        boolean anythingNew = true;
        int rounds = 0;
        while (anythingNew) {
            if (++rounds > 16) {
                throw new IllegalStateException("what a name holds would not settle");
            }
            anythingNew = false;
            boolean holds = true;
            boolean reached = true;
            for (int at = from + 1; at < elements.size(); at++) {
                CodeElement element = elements.get(at);
                if (element instanceof LabelTarget target) {
                    Boolean said = arriving.get(target.label());
                    if (said != null) {
                        holds = said || (reached && holds);
                        reached = true;
                    }
                    continue;
                }
                if (!(element instanceof Instruction instruction) || !reached) {
                    continue;
                }
                if (holds) {
                    if (element instanceof StoreInstruction put && put.slot() == slot) {
                        holds = false;
                    } else if (element instanceof IncrementInstruction added
                            && added.slot() == slot) {
                        holds = false;
                    } else if (element instanceof LoadInstruction got && got.slot() == slot
                            && readWhileHeld.add(at)) {
                        anythingNew = true;
                    }
                }
                for (Label target : whereItMayGo(instruction)) {
                    Boolean was = arriving.get(target);
                    boolean now = was != null ? was || holds : holds;
                    if (was == null || was != now) {
                        arriving.put(target, now);
                        anythingNew = true;
                    }
                }
                reached = !ends(instruction);
            }
        }
        for (int at : readWhileHeld) {
            if (followedFrom(elements, at, through + 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Everywhere the code may carry on to other than the instruction written after this one.
     *
     * <p>A switch is one of them and takes nothing but the number it switched on, so a value under
     * that number is followed across it the same way it is followed across a jump — each way with
     * the stack the switch leaves.
     */
    private static List<Label> whereItMayGo(Instruction instruction) {
        return switch (instruction) {
            case BranchInstruction it -> List.of(it.target());
            case TableSwitchInstruction it -> targetsOf(it.defaultTarget(),
                    it.cases().stream().map(SwitchCase::target).toList());
            case LookupSwitchInstruction it -> targetsOf(it.defaultTarget(),
                    it.cases().stream().map(SwitchCase::target).toList());
            default -> List.of();
        };
    }

    private static List<Label> targetsOf(Label otherwise, List<Label> cases) {
        List<Label> out = new ArrayList<>(cases);
        out.add(otherwise);
        return out;
    }

    private static int agreeing(int one, int other) {
        if (one != other) {
            throw new IllegalStateException("two ways to one place leave the stack at two heights");
        }
        return one;
    }

    private static boolean isAReferenceComparison(Opcode opcode) {
        return opcode == Opcode.IF_ACMPEQ || opcode == Opcode.IF_ACMPNE;
    }

    /** Whether nothing carries on to the instruction after this one. */
    private static boolean ends(Instruction instruction) {
        return instruction instanceof ReturnInstruction
                || instruction instanceof ThrowInstruction
                || instruction instanceof TableSwitchInstruction
                || instruction instanceof LookupSwitchInstruction
                || (instruction instanceof BranchInstruction branch
                        && (branch.opcode() == Opcode.GOTO || branch.opcode() == Opcode.GOTO_W));
    }

    /** What one instruction takes off the stack and what it leaves there. */
    private static Effect effectOf(Instruction instruction) {
        return switch (instruction) {
            case LoadInstruction it -> Effect.of(0, slotsOf(it.typeKind()));
            case ConstantInstruction it -> Effect.of(0, slotsOf(it.typeKind()));
            case StoreInstruction it -> Effect.of(slotsOf(it.typeKind()), 0);
            case FieldInstruction it -> fieldEffect(it);
            case InvokeInstruction it -> calling(it.typeSymbol().parameterList(),
                    it.typeSymbol().returnType(), it.opcode() != Opcode.INVOKESTATIC);
            case InvokeDynamicInstruction it -> calling(it.typeSymbol().parameterList(),
                    it.typeSymbol().returnType(), false);
            case ArrayLoadInstruction it -> Effect.of(2, slotsOf(it.typeKind()));
            case ArrayStoreInstruction it -> Effect.of(2 + slotsOf(it.typeKind()), 0);
            case StackInstruction it -> stackEffect(it.opcode());
            case OperatorInstruction it -> operatorEffect(it.opcode());
            case ConvertInstruction it -> Effect.of(slotsOf(it.fromType()), slotsOf(it.toType()));
            case BranchInstruction it -> branchEffect(it.opcode());
            // A cast leaves what it took, said under another type, so the value goes on being the
            // value. Asking whether something is of a type takes it and leaves an answer about it.
            case TypeCheckInstruction it -> it.opcode() == Opcode.CHECKCAST
                    ? new Effect(1, 1, true) : Effect.of(1, 1);
            case NewObjectInstruction _ -> Effect.of(0, 1);
            case NewPrimitiveArrayInstruction _ -> Effect.of(1, 1);
            case NewReferenceArrayInstruction _ -> Effect.of(1, 1);
            case NewMultiArrayInstruction it -> Effect.of(it.dimensions(), 1);
            case MonitorInstruction _ -> Effect.of(1, 0);
            case IncrementInstruction _ -> Effect.of(0, 0);
            case NopInstruction _ -> Effect.of(0, 0);
            case ThrowInstruction _ -> Effect.of(1, 0);
            case ReturnInstruction it -> Effect.of(slotsOf(it.typeKind()), 0);
            case TableSwitchInstruction _ -> Effect.of(1, 0);
            case LookupSwitchInstruction _ -> Effect.of(1, 0);
            default -> throw new IllegalStateException(
                    "the walk cannot say what " + instruction.opcode() + " takes off the stack");
        };
    }

    private static Effect fieldEffect(FieldInstruction field) {
        int held = slotsOf(TypeKind.from(field.typeSymbol()));
        return switch (field.opcode()) {
            case GETSTATIC -> Effect.of(0, held);
            case PUTSTATIC -> Effect.of(held, 0);
            case GETFIELD -> Effect.of(1, held);
            case PUTFIELD -> Effect.of(1 + held, 0);
            default -> throw new IllegalStateException(
                    "the walk cannot say what " + field.opcode() + " does to a field");
        };
    }

    private static Effect calling(List<ClassDesc> takes, ClassDesc gives, boolean onAReceiver) {
        int taken = onAReceiver ? 1 : 0;
        for (ClassDesc each : takes) {
            taken += slotsOf(TypeKind.from(each));
        }
        return Effect.of(taken, slotsOf(TypeKind.from(gives)));
    }

    private static Effect stackEffect(Opcode opcode) {
        return switch (opcode) {
            case POP -> Effect.of(1, 0);
            case POP2 -> Effect.of(2, 0);
            case DUP -> Effect.of(1, 2);
            case DUP_X1 -> Effect.of(2, 3);
            case DUP_X2 -> Effect.of(3, 4);
            case DUP2 -> Effect.of(2, 4);
            case DUP2_X1 -> Effect.of(3, 5);
            case DUP2_X2 -> Effect.of(4, 6);
            case SWAP -> Effect.of(2, 2);
            default -> throw new IllegalStateException(
                    "the walk cannot say what " + opcode + " does to the stack");
        };
    }

    private static Effect branchEffect(Opcode opcode) {
        return switch (opcode) {
            case GOTO, GOTO_W -> Effect.of(0, 0);
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> Effect.of(1, 0);
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE -> Effect.of(2, 0);
            default -> throw new IllegalStateException(
                    "the walk cannot say what " + opcode + " does before it jumps");
        };
    }

    private static Effect operatorEffect(Opcode opcode) {
        return switch (opcode) {
            case ARRAYLENGTH, INEG, FNEG -> Effect.of(1, 1);
            case LNEG, DNEG -> Effect.of(2, 2);
            case IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR,
                 ISHL, ISHR, IUSHR,
                 FADD, FSUB, FMUL, FDIV, FREM,
                 FCMPL, FCMPG -> Effect.of(2, 1);
            case LSHL, LSHR, LUSHR -> Effect.of(3, 2);
            case LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR,
                 DADD, DSUB, DMUL, DDIV, DREM -> Effect.of(4, 2);
            case LCMP, DCMPL, DCMPG -> Effect.of(4, 1);
            default -> throw new IllegalStateException(
                    "the walk cannot say what " + opcode + " leaves of its operands");
        };
    }

    private static int slotsOf(TypeKind kind) {
        return kind.slotSize();
    }
}
