package comp0012.main;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

public class ConstantFolder {
    private final ClassParser parser;
    private final ClassGen gen;
    private final JavaClass original;
    private JavaClass optimized;
    private final List<InstructionHandler> instructionHandlers;
    private final Map<Class<? extends Instruction>, ArithmeticOperation> arithmeticOperations;

    public ConstantFolder(String classFilePath) {
        try {
            this.parser = new ClassParser(classFilePath);
            this.original = this.parser.parse();
            this.gen = new ClassGen(this.original);

            // Initialize instruction handlers
            this.instructionHandlers = new ArrayList<>();
            this.arithmeticOperations = new HashMap<>();

            initializeOperations();
            initializeHandlers();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse class file: " + classFilePath, e);
        }
    }

    private void initializeOperations() {
        // Initialize arithmetic operations
        arithmeticOperations.put(IADD.class, new AddOperation());
        arithmeticOperations.put(LADD.class, new AddOperation());
        arithmeticOperations.put(FADD.class, new AddOperation());
        arithmeticOperations.put(DADD.class, new AddOperation());
        arithmeticOperations.put(IMUL.class, new MultiplyOperation());
        arithmeticOperations.put(LMUL.class, new MultiplyOperation());
        arithmeticOperations.put(FMUL.class, new MultiplyOperation());
        arithmeticOperations.put(DMUL.class, new MultiplyOperation());
        arithmeticOperations.put(ISUB.class, new SubtractOperation());
        arithmeticOperations.put(LSUB.class, new SubtractOperation());
        arithmeticOperations.put(FSUB.class, new SubtractOperation());
        arithmeticOperations.put(DSUB.class, new SubtractOperation());
        arithmeticOperations.put(IDIV.class, new DivideOperation());
        arithmeticOperations.put(LDIV.class, new DivideOperation());
        arithmeticOperations.put(FDIV.class, new DivideOperation());
        arithmeticOperations.put(DDIV.class, new DivideOperation());
    }

    private void initializeHandlers() {
        ConstantPoolGen cpgen = gen.getConstantPool();

        // Add arithmetic instruction handlers
        for (Map.Entry<Class<? extends Instruction>, ArithmeticOperation> entry : arithmeticOperations.entrySet()) {
            instructionHandlers.add(new ArithmeticInstructionHandler(cpgen, entry.getValue()));
        }

        // Add load and store instruction handlers
        instructionHandlers.add(new LoadInstructionHandler(cpgen));
        instructionHandlers.add(new StoreInstructionHandler(cpgen));

        // Add GOTO instruction handler
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
}
