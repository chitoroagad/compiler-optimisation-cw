package comp0012.main;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

public abstract class Optimiser {
    protected ClassGen classGen;
    protected ConstantPoolGen constPoolGen;

    protected String debugString = null;

    public Optimiser(ClassGen classGen, ConstantPoolGen constPoolGen) {
        this.classGen = classGen;
        this.constPoolGen = constPoolGen;
    }

    public Method optimiseMethod(Method method, int iteration) {
        MethodGen methodGen = new MethodGen(method, this.classGen.getClassName(), this.constPoolGen);
        InstructionList list = methodGen.getInstructionList();
        String className = this.classGen.getClassName();
        String shortClass = className.substring(className.lastIndexOf('.') + 1).trim();
        String methodName = method.getName();

        if (list == null)
            return method;
        method = this.optimiseMethod(method, methodGen, list);
        return method;
    }

    protected void attemptDelete(InstructionList list, InstructionHandle handle) {
        attemptDelete(list, handle, null);
    }

    protected void attemptDelete(InstructionList list, InstructionHandle handle, InstructionHandle replacement) {
        if (handle == null)
            return;
        try {
            list.delete(handle);
        } catch (TargetLostException e) {
            resetTargets(replacement, e);
        }
        list.setPositions(true);
    }

    protected void attemptDelete(InstructionList list, InstructionHandle from,
            InstructionHandle to,
            InstructionHandle replacement) {
        if (from == null || to == null)
            return;
        try {
            list.delete(from, to);
        } catch (TargetLostException e) {
            resetTargets(replacement, e);
        }
        list.setPositions(true);
    }

    private void resetTargets(InstructionHandle replacement, TargetLostException e) {
        if (replacement != null) {
            for (InstructionHandle target : e.getTargets()) {
                for (InstructionTargeter targeter : target.getTargeters()) {
                    targeter.updateTarget(target, replacement);
                }
            }
        } else {
            System.err.println("Error: (" + debugString + ")");
            System.err.println(e.getClass() + e.getMessage());
            System.err.println();
        }

    }

    protected abstract Method optimiseMethod(
            Method method,
            MethodGen methodGen,
            InstructionList list);
}
