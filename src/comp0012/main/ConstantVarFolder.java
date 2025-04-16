package comp0012.main;
/*
 * This code was tested and ran on NixOS x86 64 Linux
 * */
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

interface InstructionOptimizer {
    void optimize(InstructionHandle handle, InstructionList list, Stack<Number> constantStack,
                  HashMap<Integer, Number> vars, ConstantPoolGen cpgen);
}

interface ArithmeticOperation {
    Number perform(Number x, Number y);
}

abstract class InstructionHandler {
    protected final ConstantPoolGen cpgen;

    protected InstructionHandler(ConstantPoolGen cpgen) {
        this.cpgen = cpgen;
    }

    abstract boolean canHandle(Instruction instruction);

    abstract void handle(InstructionHandle handle, InstructionList list,
                         Stack<Number> constantStack, HashMap<Integer, Number> vars);
}

// Concrete arithmetic operations
class AddOperation implements ArithmeticOperation {
    @Override
    public Number perform(Number x, Number y) {
        System.out.println("Adding " + x + " and " + y);
        if (x instanceof Integer && y instanceof Integer) {
            return y.intValue() + x.intValue();
        } else if (x instanceof Long && y instanceof Long) {
            return y.longValue() + x.longValue();
        } else if (x instanceof Float && y instanceof Float) {
            return y.floatValue() + x.floatValue();
        } else if (x instanceof Double && y instanceof Double) {
            return y.doubleValue() + x.doubleValue();
        }
        throw new IllegalArgumentException("Unsupported number types");
    }
}

class MultiplyOperation implements ArithmeticOperation {
    @Override
    public Number perform(Number x, Number y) {
        System.out.println("Multiplying " + x + " and " + y);
        if (x instanceof Integer && y instanceof Integer) {
            return y.intValue() * x.intValue();
        } else if (x instanceof Long && y instanceof Long) {
            return y.longValue() * x.longValue();
        } else if (x instanceof Float && y instanceof Float) {
            return y.floatValue() * x.floatValue();
        } else if (x instanceof Double && y instanceof Double) {
            return y.doubleValue() * x.doubleValue();
        }
        throw new IllegalArgumentException("Unsupported number types");
    }
}

class SubtractOperation implements ArithmeticOperation {
    @Override
    public Number perform(Number x, Number y) {
        System.out.println("Subtracting " + x + " and " + y);
        if (x instanceof Integer && y instanceof Integer) {
            return y.intValue() - x.intValue();
        } else if (x instanceof Long && y instanceof Long) {
            return y.longValue() - x.longValue();
        } else if (x instanceof Float && y instanceof Float) {
            return y.floatValue() - x.floatValue();
        } else if (x instanceof Double && y instanceof Double) {
            return y.doubleValue() - x.doubleValue();
        }
        throw new IllegalArgumentException("Unsupported number types");
    }
}

class DivideOperation implements ArithmeticOperation {
    @Override
    public Number perform(Number x, Number y) {
        System.out.println("Dividing " + x + " and " + y);
        if (x instanceof Integer && y instanceof Integer) {
            return y.intValue() / x.intValue();
        } else if (x instanceof Long && y instanceof Long) {
            return y.longValue() / x.longValue();
        } else if (x instanceof Float && y instanceof Float) {
            return y.floatValue() / x.floatValue();
        } else if (x instanceof Double && y instanceof Double) {
            return y.doubleValue() / x.doubleValue();
        }
        throw new IllegalArgumentException("Unsupported number types");
    }
}

// Utility class for instruction-related operations
class InstructionUtils {
    static void deleteInstruction(InstructionHandle handle, InstructionList list) {
        try {
            // Get all instructions that target this handle
            InstructionTargeter[] targeters = handle.getTargeters();
            if (targeters != null) {
                for (InstructionTargeter targeter : targeters) {
                    if (targeter instanceof BranchInstruction) {
                        BranchInstruction branch = (BranchInstruction) targeter;
                        // Update the target to the next instruction
                        branch.setTarget(handle.getNext());
                    }
                }
            }

            list.delete(handle);
        } catch (TargetLostException e) {
            InstructionHandle[] targets = e.getTargets();
            for (InstructionHandle target : targets) {
                InstructionTargeter[] targeters = target.getTargeters();
                for (InstructionTargeter targeter : targeters) {
                    targeter.updateTarget(target, null);
                }
            }
        }
    }
}

// Concrete instruction handlers
class ArithmeticInstructionHandler extends InstructionHandler {
    private final ArithmeticOperation operation;

    public ArithmeticInstructionHandler(ConstantPoolGen cpgen, ArithmeticOperation operation) {
        super(cpgen);
        this.operation = operation;
    }

    @Override
    public boolean canHandle(Instruction instruction) {
        return instruction instanceof ArithmeticInstruction;
    }

    @Override
    public void handle(InstructionHandle handle, InstructionList list,
                       Stack<Number> constantStack, HashMap<Integer, Number> vars) {
        // Check if we have enough operands on the stack
        if (constantStack.size() < 2) {
            return;
        }

        try {
            Number x = constantStack.pop();
            Number y = constantStack.pop();

            // Skip if either operand is null
            if (x == null || y == null) {
                // Restore the stack state
                constantStack.push(y);
                constantStack.push(x);
                return;
            }

            Number result = operation.perform(x, y);
            if (result != null) {
                constantStack.push(result);

                // Replace the arithmetic instruction with LDC
                if (result instanceof Double) {
                    list.insert(handle, new LDC2_W(cpgen.addDouble((Double) result)));
                } else if (result instanceof Long) {
                    list.insert(handle, new LDC2_W(cpgen.addLong((Long) result)));
                } else if (result instanceof Integer) {
                    list.insert(handle, new LDC(cpgen.addInteger((Integer) result)));
                } else if (result instanceof Float) {
                    list.insert(handle, new LDC(cpgen.addFloat((Float) result)));
                }

                InstructionUtils.deleteInstruction(handle, list);
            } else {
                // Restore the stack state if operation failed
                constantStack.push(y);
                constantStack.push(x);
            }
        } catch (Exception e) {
            System.err.println("Error optimizing arithmetic instruction: " + e.getMessage());
        }
    }
}

// track variable state
class VariableState {
    private final Map<Integer, Number> constantValues;
    private final Set<Integer> modifiedVariables;

    public VariableState() {
        this.constantValues = new HashMap<>();
        this.modifiedVariables = new HashSet<>();
    }

    public void setConstant(int index, Number value) {
        if (!modifiedVariables.contains(index)) {
            constantValues.put(index, value);
        }
    }

    public void markModified(int index) {
        modifiedVariables.add(index);
        constantValues.remove(index);
    }

    public Number getValue(int index) {
        return constantValues.get(index);
    }

    public boolean isConstant(int index) {
        return constantValues.containsKey(index);
    }
}

class StoreInstructionHandler extends InstructionHandler {
    public StoreInstructionHandler(ConstantPoolGen cpgen) {
        super(cpgen);
    }

    @Override
    public boolean canHandle(Instruction instruction) {
        return instruction instanceof StoreInstruction;
    }

    @Override
    public void handle(InstructionHandle handle, InstructionList list,
                       Stack<Number> constantStack, HashMap<Integer, Number> vars) {
        if (constantStack.isEmpty()) {
            return;
        }

        Number value = constantStack.pop();
        int index = ((StoreInstruction) handle.getInstruction()).getIndex();

        // Only store if the value is a constant number
        if (value != null &&
                (value instanceof Integer || value instanceof Long ||
                        value instanceof Float || value instanceof Double)) {
            vars.put(index, value);
        }

        InstructionUtils.deleteInstruction(handle, list);
    }
}

class LoadInstructionHandler extends InstructionHandler {
    public LoadInstructionHandler(ConstantPoolGen cpgen) {
        super(cpgen);
    }

    @Override
    public boolean canHandle(Instruction instruction) {
        return instruction instanceof LoadInstruction && !(instruction instanceof ALOAD);
    }

    @Override
    public void handle(InstructionHandle handle, InstructionList list,
                       Stack<Number> constantStack, HashMap<Integer, Number> vars) {
        int index = ((LoadInstruction) handle.getInstruction()).getIndex();
        Number value = vars.get(index);

        if (value != null) {
            constantStack.push(value);

            // Replace load with constant
            if (value instanceof Double) {
                list.insert(handle, new LDC2_W(cpgen.addDouble((Double) value)));
            } else if (value instanceof Long) {
                list.insert(handle, new LDC2_W(cpgen.addLong((Long) value)));
            } else if (value instanceof Integer) {
                list.insert(handle, new LDC(cpgen.addInteger((Integer) value)));
            } else if (value instanceof Float) {
                list.insert(handle, new LDC(cpgen.addFloat((Float) value)));
            }

            InstructionUtils.deleteInstruction(handle, list);
        }
    }
}

class GotoInstructionHandler extends InstructionHandler {
    public GotoInstructionHandler(ConstantPoolGen cpgen) {
        super(cpgen);
    }

    @Override
    public boolean canHandle(Instruction instruction) {
        return instruction instanceof GotoInstruction;
    }

    @Override
    public void handle(InstructionHandle handle, InstructionList list,
                       Stack<Number> constantStack, HashMap<Integer, Number> vars) {
        GotoInstruction gotoInst = (GotoInstruction) handle.getInstruction();
        InstructionHandle target = gotoInst.getTarget();

        // If the GOTO target is the next instruction, it's redundant
        if (target == handle.getNext()) {
            InstructionUtils.deleteInstruction(handle, list);
        }
    }
}

public class ConstantVarFolder {
    private final ClassParser parser;
    private final ClassGen gen;
    private final JavaClass original;
    private JavaClass optimized;
    private final List<InstructionHandler> instructionHandlers;
    private final Map<Class<? extends Instruction>, ArithmeticOperation> arithmeticOperations;

    public ConstantVarFolder(String classFilePath) {
        try {
            this.parser = new ClassParser(classFilePath);
            this.original = this.parser.parse();
            this.gen = new ClassGen(this.original);

            // Initialize instruction handlers
            this.instructionHandlers = new ArrayList<>();
            this.arithmeticOperations = new HashMap<>();

            initOperations();
            initHandlers();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse class file: " + classFilePath, e);
        }
    }

    private void initOperations() {
        // Initialize arithmetic operations
        arithmeticOperations.put(IMUL.class, new MultiplyOperation());
        arithmeticOperations.put(LMUL.class, new MultiplyOperation());
        arithmeticOperations.put(FMUL.class, new MultiplyOperation());
        arithmeticOperations.put(DMUL.class, new MultiplyOperation());
        arithmeticOperations.put(IDIV.class, new DivideOperation());
        arithmeticOperations.put(LDIV.class, new DivideOperation());
        arithmeticOperations.put(FDIV.class, new DivideOperation());
        arithmeticOperations.put(DDIV.class, new DivideOperation());
        arithmeticOperations.put(ISUB.class, new SubtractOperation());
        arithmeticOperations.put(LSUB.class, new SubtractOperation());
        arithmeticOperations.put(FSUB.class, new SubtractOperation());
        arithmeticOperations.put(DSUB.class, new SubtractOperation());
        arithmeticOperations.put(IADD.class, new AddOperation());
        arithmeticOperations.put(LADD.class, new AddOperation());
        arithmeticOperations.put(FADD.class, new AddOperation());
        arithmeticOperations.put(DADD.class, new AddOperation());
    }

    private void initHandlers() {
        ConstantPoolGen cpgen = gen.getConstantPool();

        for (Map.Entry<Class<? extends Instruction>, ArithmeticOperation> entry : arithmeticOperations.entrySet()) {
            instructionHandlers.add(new ArithmeticInstructionHandler(cpgen, entry.getValue()));
        }

        instructionHandlers.add(new LoadInstructionHandler(cpgen));
        instructionHandlers.add(new StoreInstructionHandler(cpgen));

        instructionHandlers.add(new GotoInstructionHandler(cpgen));
    }

    private void optimizeMethod(ClassGen cgen, ConstantPoolGen cpgen, Method method) {
        Code methodCode = method.getCode();

        if (methodCode == null) {
            return;
        }

        Stack<Number> constantStack = new Stack<>();
        HashMap<Integer, Number> vars = new HashMap<>();
        VariableState varState = new VariableState();

        InstructionList instList = new InstructionList(methodCode.getCode());
        MethodGen methodGen = new MethodGen(method.getAccessFlags(), method.getReturnType(),
                method.getArgumentTypes(), null, method.getName(),
                cgen.getClassName(), instList, cpgen);

        InstructionList il = methodGen.getInstructionList();

        if (il == null)
            return;

        InstructionFinder finder = new InstructionFinder(il);
        boolean changed = false;

        // Patterns to match constant ops for all primitive types
        String[] patterns = {
                "LDC LDC IADD", "LDC LDC ISUB", "LDC LDC IMUL", "LDC LDC IDIV",
                "LDC2_W LDC2_W LADD", "LDC2_W LDC2_W LSUB", "LDC2_W LDC2_W LMUL", "LDC2_W LDC2_W LDIV",
                "LDC LDC FADD", "LDC LDC FSUB", "LDC LDC FMUL", "LDC LDC FDIV",
                "LDC2_W LDC2_W DADD", "LDC2_W LDC2_W DSUB", "LDC2_W LDC2_W DMUL", "LDC2_W LDC2_W DDIV"
        };

        for (String pattern : patterns) {
            for (Iterator<?> it = finder.search(pattern); it.hasNext();) {
                InstructionHandle[] handles = (InstructionHandle[]) it.next();
                Instruction op = handles[2].getInstruction();

                Number val1 = getNumber(handles[0].getInstruction(), cpgen);
                Number val2 = getNumber(handles[1].getInstruction(), cpgen);
                Number result = computeFoldedResult(val1, val2, op);

                if (result == null)
                    continue;

                InstructionHandle newInstr;
                if (result instanceof Integer) {
                    newInstr = il.insert(handles[0], new LDC(cpgen.addInteger(result.intValue())));
                } else if (result instanceof Long) {
                    newInstr = il.insert(handles[0], new LDC2_W(cpgen.addLong(result.longValue())));
                } else if (result instanceof Float) {
                    newInstr = il.insert(handles[0], new LDC(cpgen.addFloat(result.floatValue())));
                } else if (result instanceof Double) {
                    newInstr = il.insert(handles[0], new LDC2_W(cpgen.addDouble(result.doubleValue())));
                } else {
                    continue;
                }

                try {
                    il.delete(handles[0], handles[2]);
                } catch (TargetLostException e) {
                    for (InstructionHandle target : e.getTargets()) {
                        for (InstructionTargeter t : target.getTargeters()) {
                            t.updateTarget(target, newInstr);
                        }
                    }
                }

                changed = true;
            }

            for (InstructionHandle handle : instList.getInstructionHandles()) {
                Instruction instruction = handle.getInstruction();
                if (instruction == null)
                    continue;

                // Handle variable modifications
                if (instruction instanceof IINC) {
                    int index = ((IINC) instruction).getIndex();
                    varState.markModified(index);
                }

                // Find appropriate handler
                for (InstructionHandler handler : instructionHandlers) {
                    if (handler.canHandle(instruction)) {
                        handler.handle(handle, instList, constantStack, vars);
                        break;
                    }
                }
            }

            try {
                instList.setPositions(true);
            } catch (Exception e) {
                System.out.println("Problem setting positions");
            }

            methodGen.setMaxStack();
            methodGen.setMaxLocals();
            Method newMethod = methodGen.getMethod();
            cgen.replaceMethod(method, newMethod);
        }
    }

    public void optimize() {
        ClassGen cgen = new ClassGen(original);
        ConstantPoolGen cpgen = cgen.getConstantPool();

        Method[] methods = cgen.getMethods();
        for (Method m : methods) {
            optimizeMethod(cgen, cpgen, m);
        }

        this.optimized = cgen.getJavaClass();
    }

    public void write(String optimisedFilePath) {
        this.optimize();

        try {
            FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
            this.optimized.dump(out);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write optimized class file: " + optimisedFilePath, e);
        }
    }

    private Number getNumber(Instruction instr, ConstantPoolGen cpgen) {
        if (instr instanceof LDC) {
            Object val = ((LDC) instr).getValue(cpgen);
            if (val instanceof Integer || val instanceof Float)
                return (Number) val;
        } else if (instr instanceof LDC2_W) {
            Object val = ((LDC2_W) instr).getValue(cpgen);
            if (val instanceof Long || val instanceof Double)
                return (Number) val;
        }
        return null;
    }

    // Helper: Apply the arithmetic operation
    private Number computeFoldedResult(Number a, Number b, Instruction op) {
        try {
            if (op instanceof IADD)
                return a.intValue() + b.intValue();
            if (op instanceof ISUB)
                return a.intValue() - b.intValue();
            if (op instanceof IMUL)
                return a.intValue() * b.intValue();
            if (op instanceof IDIV && b.intValue() != 0)
                return a.intValue() / b.intValue();

            if (op instanceof LADD)
                return a.longValue() + b.longValue();
            if (op instanceof LSUB)
                return a.longValue() - b.longValue();
            if (op instanceof LMUL)
                return a.longValue() * b.longValue();
            if (op instanceof LDIV && b.longValue() != 0)
                return a.longValue() / b.longValue();

            if (op instanceof FADD)
                return a.floatValue() + b.floatValue();
            if (op instanceof FSUB)
                return a.floatValue() - b.floatValue();
            if (op instanceof FMUL)
                return a.floatValue() * b.floatValue();
            if (op instanceof FDIV && b.floatValue() != 0.0f)
                return a.floatValue() / b.floatValue();

            if (op instanceof DADD)
                return a.doubleValue() + b.doubleValue();
            if (op instanceof DSUB)
                return a.doubleValue() - b.doubleValue();
            if (op instanceof DMUL)
                return a.doubleValue() * b.doubleValue();
            if (op instanceof DDIV && b.doubleValue() != 0.0)
                return a.doubleValue() / b.doubleValue();
        } catch (Exception e) {
            // silently ignore division by zero or overflow
        }

        return null;
    }
}