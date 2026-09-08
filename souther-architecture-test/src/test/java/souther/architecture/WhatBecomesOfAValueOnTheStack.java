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
 * <p>So this follows the value. How far the stack is above where the value sits is worked out
 * instruction by instruction, and the value is gone as soon as that reaches the value's own place.
 * What the value's place is is never needed — only how far above it the stack has come — so nothing
 * here has to know the depth a method's code begins at.
 *
 * <p><b>What it cannot follow it refuses, and never answers.</b> An instruction whose effect this
 * cannot say, a way round that comes back to where the walk has already been, the beginning of an
 * exception handler, and the end of a method with the value still on the stack are each a place the
 * value was lost rather than taken — and every one of them ends the walk with a refusal. Answered,
 * each would come out as "nothing compares it", which is the one thing a rule written on top of
 * this reads as a fact.
 *
 * <p>What it does follow is a value carried across the jumps that work something else out, and
 * across a switch, which takes the number it switched on and leaves the value alone. Each way the
 * code may go is remembered with the stack it is reached at, and picked up again where it arrives.
 */
final class WhatBecomesOfAValueOnTheStack {

    private WhatBecomesOfAValueOnTheStack() {
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
        Set<Label> caught = new HashSet<>();
        elements.forEach(each -> {
            if (each instanceof ExceptionCatch handler) {
                caught.add(handler.handler());
            }
        });
        Map<Label, Integer> placed = new HashMap<>();
        for (int where = 0; where < elements.size(); where++) {
            if (elements.get(where) instanceof LabelTarget target) {
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
            if (instruction instanceof BranchInstruction branch
                    && isAReferenceComparison(branch.opcode())) {
                // Both operands are what the stack holds above the value and the one below them, so
                // the value is one of the two exactly where the stack has come no further than two
                // above it.
                return now <= 2;
            }
            for (Label target : whereItMayGo(instruction)) {
                Integer to = placed.get(target);
                if (to == null || to <= next) {
                    // A jump backwards, or to somewhere this walk never reaches. What the value
                    // meets on that way round is not read here, and a walk that carried on would be
                    // answering about the way it happened to take.
                    throw new IllegalStateException("a value was followed into a jump that returns");
                }
                above.merge(target, now + effectOf(instruction),
                        WhatBecomesOfAValueOnTheStack::agreeing);
            }
            now += effectOf(instruction);
            if (now <= 0) {
                // Something other than a comparison of two references took the value, which is an
                // answer and not a place the walk gave up at.
                return false;
            }
            reached = !ends(instruction);
        }
        // The value is still on the stack and there is no more code, so what took it was on a way
        // this did not follow. Said as "nothing compares it", that would be the one mistake this
        // whole reading is written to refuse.
        throw new IllegalStateException("a value was followed to the end of a method");
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

    /** How many slots one instruction leaves on the stack, less what it took. */
    private static int effectOf(Instruction instruction) {
        return switch (instruction) {
            case LoadInstruction it -> slotsOf(it.typeKind());
            case ConstantInstruction it -> slotsOf(it.typeKind());
            case StoreInstruction it -> -slotsOf(it.typeKind());
            case FieldInstruction it -> fieldEffect(it);
            case InvokeInstruction it -> calling(it.typeSymbol().parameterList(),
                    it.typeSymbol().returnType(), it.opcode() != Opcode.INVOKESTATIC);
            case InvokeDynamicInstruction it -> calling(it.typeSymbol().parameterList(),
                    it.typeSymbol().returnType(), false);
            case ArrayLoadInstruction it -> slotsOf(it.typeKind()) - 2;
            case ArrayStoreInstruction it -> -slotsOf(it.typeKind()) - 2;
            case StackInstruction it -> stackEffect(it.opcode());
            case OperatorInstruction it -> operatorEffect(it.opcode());
            case ConvertInstruction it -> slotsOf(it.toType()) - slotsOf(it.fromType());
            case BranchInstruction it -> branchEffect(it.opcode());
            case TypeCheckInstruction _ -> 0;
            case NewObjectInstruction _ -> 1;
            case NewPrimitiveArrayInstruction _ -> 0;
            case NewReferenceArrayInstruction _ -> 0;
            case NewMultiArrayInstruction it -> 1 - it.dimensions();
            case MonitorInstruction _ -> -1;
            case IncrementInstruction _ -> 0;
            case NopInstruction _ -> 0;
            case ThrowInstruction _ -> -1;
            case ReturnInstruction it -> -slotsOf(it.typeKind());
            case TableSwitchInstruction _ -> -1;
            case LookupSwitchInstruction _ -> -1;
            default -> throw new IllegalStateException(
                    "the walk cannot say what " + instruction.opcode() + " leaves on the stack");
        };
    }

    private static int fieldEffect(FieldInstruction field) {
        int held = slotsOf(TypeKind.from(field.typeSymbol()));
        return switch (field.opcode()) {
            case GETSTATIC -> held;
            case PUTSTATIC -> -held;
            case GETFIELD -> held - 1;
            case PUTFIELD -> -held - 1;
            default -> throw new IllegalStateException(
                    "the walk cannot say what " + field.opcode() + " does to a field");
        };
    }

    private static int calling(List<ClassDesc> takes, ClassDesc gives, boolean onAReceiver) {
        int taken = onAReceiver ? 1 : 0;
        for (ClassDesc each : takes) {
            taken += slotsOf(TypeKind.from(each));
        }
        return slotsOf(TypeKind.from(gives)) - taken;
    }

    private static int stackEffect(Opcode opcode) {
        return switch (opcode) {
            case POP -> -1;
            case POP2 -> -2;
            case DUP, DUP_X1, DUP_X2 -> 1;
            case DUP2, DUP2_X1, DUP2_X2 -> 2;
            case SWAP -> 0;
            default -> throw new IllegalStateException(
                    "the walk cannot say what " + opcode + " does to the stack");
        };
    }

    private static int branchEffect(Opcode opcode) {
        return switch (opcode) {
            case GOTO, GOTO_W -> 0;
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> -1;
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE -> -2;
            default -> throw new IllegalStateException(
                    "the walk cannot say what " + opcode + " does before it jumps");
        };
    }

    private static int operatorEffect(Opcode opcode) {
        return switch (opcode) {
            case ARRAYLENGTH, INEG, LNEG, FNEG, DNEG -> 0;
            case IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR,
                 FADD, FSUB, FMUL, FDIV, FREM,
                 ISHL, ISHR, IUSHR, LSHL, LSHR, LUSHR,
                 FCMPL, FCMPG -> -1;
            case LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR,
                 DADD, DSUB, DMUL, DDIV, DREM -> -2;
            case LCMP, DCMPL, DCMPG -> -3;
            default -> throw new IllegalStateException(
                    "the walk cannot say what " + opcode + " leaves of its operands");
        };
    }

    private static int slotsOf(TypeKind kind) {
        return kind.slotSize();
    }
}
