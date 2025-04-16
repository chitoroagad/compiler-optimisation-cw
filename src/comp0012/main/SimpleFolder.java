package comp0012.main;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ArithmeticInstruction;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.ConversionInstruction;
import org.apache.bcel.generic.DCMPG;
import org.apache.bcel.generic.DCMPL;
import org.apache.bcel.generic.FCMPG;
import org.apache.bcel.generic.FCMPL;
import org.apache.bcel.generic.GotoInstruction;
import org.apache.bcel.generic.IfInstruction;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LCMP;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.LDC2_W;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.TypedInstruction;

public class SimpleFolder extends Optimiser {

    public SimpleFolder(ClassGen classGen, ConstantPoolGen constPoolGen) {
        super(classGen, constPoolGen);
    }

    /*
     * Replaces arithmetic instructions with constants and converts constants
     */
    protected Method optimiseMethod(
            Method method,
            MethodGen methodGen,
            InstructionList list) {
        boolean optimisationPerformed = false;
        for (InstructionHandle handle : list.getInstructionHandles()) {
            if (handle == null)
                continue;
            Instruction instruction = handle.getInstruction();
            if (instruction instanceof ConversionInstruction) {
                optimisationPerformed = optimisationPerformed || this.handleConversion(list, handle);
            } else if (instruction instanceof ArithmeticInstruction) {
                optimisationPerformed = optimisationPerformed || this.handleArithmeticInstruction(list, handle);
            } else if (instruction instanceof IfInstruction) {
                optimisationPerformed = optimisationPerformed || this.handleIfInstruction(list, handle);
            }
        }
        return optimisationPerformed ? methodGen.getMethod() : null;
    }

    /**
     * @return bool is an optimisation has happened
     */
    private boolean handleConversion(InstructionList list, InstructionHandle handle) {
        ConversionInstruction instruction = (ConversionInstruction) handle.getInstruction();
        ArithType type = Util.extractArithmeticType(instruction.getType(this.constPoolGen));
        if (type == ArithType.OTHER)
            return false;

        InstructionHandle previousHandle = handle.getPrev();
        Number value = Util.extractConstant(previousHandle, constPoolGen);
        if (value == null)
            return false;

        Instruction pushInstruction;
        switch (type) {
            case INT:
                pushInstruction = Util.getConstantPushInstruction(value.intValue(), constPoolGen);
                break;
            case LONG:
                pushInstruction = Util.getConstantPushInstruction(value.longValue(), constPoolGen);
                break;
            case FLOAT:
                pushInstruction = Util.getConstantPushInstruction(value.floatValue(), constPoolGen);
                break;
            case DOUBLE:
                pushInstruction = Util.getConstantPushInstruction(value.doubleValue(), constPoolGen);
                break;
            default:
                return false;
        }

        InstructionHandle replacementHandle = list.insert(handle, pushInstruction);

        attemptDelete(list, handle, replacementHandle);
        attemptDelete(list, previousHandle, replacementHandle);
        return true;
    }

    /**
     * @return Determines whether an optimisation has been performed
     */
    private boolean handleArithmeticInstruction(InstructionList list, InstructionHandle handle) {
        ArithmeticInstruction instruction = (ArithmeticInstruction) handle.getInstruction();
        ArithType type = Util.extractArithmeticType(instruction.getType(this.constPoolGen));
        if (type == ArithType.OTHER)
            return false;

        InstructionHandle handle2 = handle.getPrev();
        InstructionHandle handle1 = handle2.getPrev();
        Number number1 = Util.extractConstant(handle1, constPoolGen);
        Number number2 = Util.extractConstant(handle2, constPoolGen);
        if (number1 == null || number2 == null)
            return false;

        ArithOpType operationType = Util.extractArithmeticOperationType(instruction);
        int constIndex = this.performArithmeticOperation(operationType, type, number1, number2);
        if (constIndex == -1)
            return false;

        Instruction replacementInstruction;

        if (type == ArithType.DOUBLE || type == ArithType.LONG) {
            replacementInstruction = new LDC2_W(constIndex);
        } else {
            replacementInstruction = new LDC(constIndex);
        }

        InstructionHandle replacementHandle = list.insert(handle, replacementInstruction);

        attemptDelete(list, handle, replacementHandle);
        attemptDelete(list, handle1, replacementHandle);
        attemptDelete(list, handle2, replacementHandle);

        return true;
    }

    /**
     * @return Index of the newly inserted constant
     */
    private int performArithmeticOperation(
            ArithOpType operationType,
            ArithType type,
            Number number1,
            Number number2) {
        switch (operationType) {
            case ADD:
                return performAddition(type, number1, number2);
            case SUB:
                return performSubtraction(type, number1, number2);
            case MUL:
                return performMultiplication(type, number1, number2);
            case DIV:
                return performDivision(type, number1, number2);
        }
        return -1;
    }

    /**
     * @return Index of the newly inserted constant
     */
    private int performAddition(ArithType type, Number number1, Number number2) {
        int index = -1;
        switch (type) {
            case INT:
                index = this.constPoolGen.addInteger((int) number1 + (int) number2);
                break;
            case LONG:
                index = this.constPoolGen.addLong((long) number1 + (long) number2);
                break;
            case FLOAT:
                index = this.constPoolGen.addFloat((float) number1 + (float) number2);
                break;
            case DOUBLE:
                index = this.constPoolGen.addDouble((double) number1 + (double) number2);
                break;
        }
        return index;
    }

    /**
     * @return Index of the newly inserted constant
     */
    private int performSubtraction(ArithType type, Number number1, Number number2) {
        int index = -1;
        switch (type) {
            case INT:
                index = this.constPoolGen.addInteger((int) number1 - (int) number2);
                break;
            case LONG:
                index = this.constPoolGen.addLong((long) number1 - (long) number2);
                break;
            case FLOAT:
                index = this.constPoolGen.addFloat((float) number1 - (float) number2);
                break;
            case DOUBLE:
                index = this.constPoolGen.addDouble((double) number1 - (double) number2);
                break;
        }
        return index;
    }

    /**
     * @return Index of the newly inserted constant
     */
    private int performMultiplication(ArithType type, Number number1, Number number2) {
        int index = -1;
        switch (type) {
            case INT:
                index = this.constPoolGen.addInteger((int) number1 * (int) number2);
                break;
            case LONG:
                index = this.constPoolGen.addLong((long) number1 * (long) number2);
                break;
            case FLOAT:
                index = this.constPoolGen.addFloat((float) number1 * (float) number2);
                break;
            case DOUBLE:
                index = this.constPoolGen.addDouble((double) number1 * (double) number2);
                break;
        }
        return index;
    }

    /**
     * @return Index of the newly inserted constant
     */
    private int performDivision(ArithType type, Number number1, Number number2) {
        int index = -1;
        switch (type) {
            case INT:
                index = this.constPoolGen.addInteger((int) number1 / (int) number2);
                break;
            case LONG:
                index = this.constPoolGen.addLong((long) number1 / (long) number2);
                break;
            case FLOAT:
                index = this.constPoolGen.addFloat((float) number1 / (float) number2);
                break;
            case DOUBLE:
                index = this.constPoolGen.addDouble((double) number1 / (double) number2);
                break;
        }
        return index;
    }

    private boolean handleIfInstruction(InstructionList list, InstructionHandle handle) {
        IfInstruction instruction = (IfInstruction) handle.getInstruction();
        CmpType cmpType = Util.extractComparisonType(instruction);
        if (cmpType == CmpType.OTHER)
            return false;
        Type numType1 = null;
        Type numType2 = null;
        InstructionHandle handle1 = handle.getPrev();
        boolean isOfOtherType = isComparisonOfOtherType(handle1.getInstruction());
        if (isOfOtherType) {
            cmpType = Util.adjustCmpTypeBecauseItsSpecial(cmpType);
            if (handle1.getInstruction() instanceof LCMP) {
                numType1 = Type.LONG;
                numType2 = Type.LONG;
            } else if (handle1.getInstruction() instanceof DCMPL
                    || handle1.getInstruction() instanceof DCMPG) {
                numType1 = Type.DOUBLE;
                numType2 = Type.DOUBLE;
            } else if (handle1.getInstruction() instanceof FCMPG
                    || handle1.getInstruction() instanceof FCMPL) {
                numType1 = Type.FLOAT;
                numType2 = Type.FLOAT;
            }
            handle1 = handle1.getPrev();
        }
        InstructionHandle handle2 = handle1.getPrev();
        Number number1 = Util.extractConstant(handle1, constPoolGen);
        if (number1 == null)
            return false;
        numType1 = numType1 != null ? numType1 : ((TypedInstruction) handle1.getInstruction()).getType(constPoolGen);
        Number number2 = null;
        if (Comparer.isZeroComparison(cmpType) && !isOfOtherType) {
            numType2 = null;
        } else {
            if (handle2 == null) {
                return false;
            } else {
                number2 = Util.extractConstant(handle2, constPoolGen);
                numType2 = numType2 != null ? numType2
                        : ((TypedInstruction) handle2.getInstruction()).getType(constPoolGen);
                if (number2 == null) {
                    return false;
                }
            }
        }
        try {
            boolean res = Comparer.performComparison(cmpType, numType1, numType2, number1, number2);
            InstructionHandle maybeUnusualComparisonHandle = handle.getPrev();
            InstructionHandle jumpTarget = instruction.getTarget();
            InstructionHandle elseJump = jumpTarget.getPrev();
            InstructionHandle replacementHandle;
            if (res) { // delete first branch, since we are jumping
                // delete if, delete everything from and including goto to and excluding the
                // point we would have jumped to
                replacementHandle = handle.getNext();
                try {
                    InstructionHandle elseDone = ((GotoInstruction) elseJump.getInstruction()).getTarget();
                    attemptDelete(list, handle, replacementHandle);
                    attemptDelete(list, elseJump, elseDone.getPrev(), replacementHandle);
                } catch (ClassCastException e) {
                    return false; // format does not work out --> abandon
                }
            } else {
                // look for goto, then delete everything from and including if to goto and
                // including
                attemptDelete(list, handle, elseJump, jumpTarget);
                replacementHandle = jumpTarget;
            }
            if (isOfOtherType) {
                attemptDelete(list, maybeUnusualComparisonHandle, replacementHandle);
            }
            attemptDelete(list, handle1, replacementHandle);
            if (!Comparer.isZeroComparison(cmpType) || isOfOtherType) {
                attemptDelete(list, handle2, replacementHandle);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isComparisonOfOtherType(Instruction instruction) {
        return instruction instanceof DCMPG
                || instruction instanceof DCMPL
                || instruction instanceof FCMPG
                || instruction instanceof FCMPL
                || instruction instanceof LCMP;
    }
}
