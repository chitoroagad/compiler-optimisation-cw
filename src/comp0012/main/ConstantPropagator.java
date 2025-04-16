package comp0012.main;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

import java.util.HashMap;
import java.util.Map;

public class ConstantPropagator extends Optimiser {

    private Map<Integer, Number> varsByIndex = null;

    public ConstantPropagator(ClassGen classGen, ConstantPoolGen constPoolGen) {
        super(classGen, constPoolGen);
    }

    protected Method optimiseMethod(
            Method method,
            MethodGen methodGen,
            InstructionList list) {
        boolean optimisationPerformed = false;
        this.varsByIndex = new HashMap<>();
        for (InstructionHandle handle : list.getInstructionHandles()) {
            Instruction instruction = handle.getInstruction();
            InstructionHandle nextHandle = handle.getNext();
            if (nextHandle != null) {
                Instruction nextInstruction = nextHandle.getInstruction();
                if (nextInstruction instanceof StoreInstruction) {
                    updateConstantStore(instruction, (StoreInstruction) nextInstruction);
                }
            }
            if (Utils.isArithmeticLoadInstruction(instruction)) {
                optimisationPerformed = optimisationPerformed || attemptProp(list, handle);
            }
        }
        list.setPositions(true);
        return optimisationPerformed ? methodGen.getMethod() : null;
    }

    private boolean attemptProp(InstructionList list, InstructionHandle handle) {
        LoadInstruction load = (LoadInstruction) handle.getInstruction();
        int variableIndex = load.getIndex();
        if (isGotoTarget(handle) || !this.varsByIndex.containsKey(variableIndex))
            return false;
        if (isInLoopAndChanges(list, handle, variableIndex))
            return false;

        Number value = this.varsByIndex.get(variableIndex);
        Instruction insert = Utils.getConstantPushInstruction(value, constPoolGen);
        InstructionHandle newHandle = list.append(handle, insert);
        attemptDelete(list, handle, newHandle);
        return true;
    }

    private boolean isGotoTarget(InstructionHandle handle) {
        for (InstructionTargeter targeter : handle.getTargeters()) {
            if (targeter instanceof GotoInstruction) {
                return true;
            }
        }
        return false;
    }

    private boolean isInLoopAndChanges(InstructionList list, InstructionHandle handle, int variableIndex) {
        int position = handle.getPosition();
        InstructionHandle nextHandle = handle.getNext();
        boolean inLoop = false;
        int loopStart = -1;
        int loopEnd = -1;
        while (nextHandle != null) {
            Instruction nextInstruction = nextHandle.getInstruction();
            if (nextInstruction instanceof GotoInstruction) {
                GotoInstruction gotoInstruction = (GotoInstruction) nextInstruction;
                int targetPosition = gotoInstruction.getTarget().getPosition();
                if (targetPosition < position) {
                    inLoop = true;
                    loopStart = targetPosition;
                    loopEnd = nextHandle.getPosition();
                }
            }
            nextHandle = nextHandle.getNext();
        }

        if (!inLoop)
            return false;

        for (int i = loopStart; i < loopEnd; i++) {
            InstructionHandle loopHandle = list.findHandle(i);
            if (loopHandle == null)
                continue;
            Instruction instruction = loopHandle.getInstruction();
            if (instruction instanceof LocalVariableInstruction && !(instruction instanceof LoadInstruction)) {
                LocalVariableInstruction variableInstruction = (LocalVariableInstruction) instruction;
                if (variableInstruction.getIndex() == variableIndex) {
                    return true;
                }
            }
        }

        return false;
    }

    private void updateConstantStore(Instruction current, StoreInstruction next) {
        int index = next.getIndex();
        Number value = Utils.extractConstant(current, this.constPoolGen);
        if (value != null) {
            this.varsByIndex.put(index, value);
        } else {
            this.varsByIndex.remove(index);
        }
    }
}
