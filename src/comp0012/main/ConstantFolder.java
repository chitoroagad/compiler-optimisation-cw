package comp0012.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ArithmeticInstruction;
import org.apache.bcel.generic.BIPUSH;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.DCONST;
import org.apache.bcel.generic.FCONST;
import org.apache.bcel.generic.IADD;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.LCONST;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.LDC2_W;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.ICONST;
import org.apache.bcel.generic.IDIV;
import org.apache.bcel.generic.IMUL;
import org.apache.bcel.generic.ISUB;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.SIPUSH;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.generic.TargetLostException;

public class ConstantFolder {
    ClassParser parser = null;
    ClassGen gen = null;

    JavaClass original = null;
    JavaClass optimized = null;

    public ConstantFolder(String classFilePath) {
        try {
            this.parser = new ClassParser(classFilePath);
            this.original = this.parser.parse();
            this.gen = new ClassGen(this.original);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void optimize() {
        ClassGen cgen = new ClassGen(original);
        ConstantPoolGen cpgen = cgen.getConstantPool();

        // Implement your optimization here

        Method[] methods = cgen.getMethods();

        for (Method method : methods) {
            optimizeMethod(cgen, method, cpgen);
        }

        this.optimized = cgen.getJavaClass();
    }

    public void write(String optimisedFilePath) {
        this.optimize();

        try {
            FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
            this.optimized.dump(out);
        } catch (FileNotFoundException e) {
            // Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void optimizeMethod(ClassGen cgen, Method method, ConstantPoolGen cpgen) {
        if (method.isAbstract() || method.isNative()) {
            return;
        }

        Code code = method.getCode();
        if (code == null) {
            return;
        }

        MethodGen methodGen = new MethodGen(method, cgen.getClassName(), cpgen);

        InstructionList instructionList = methodGen.getInstructionList();
        if (instructionList == null) {
            return;
        }

        // track constant local variables
        Map<Integer, Number> constantVariables = new HashMap<>();

        // First pass: identify constant variables
        InstructionHandle handle = instructionList.getStart();
        while (handle != null) {
            Instruction instruction = handle.getInstruction();

            if (instruction instanceof BIPUSH || instruction instanceof SIPUSH || instruction instanceof LDC
                    || instruction instanceof LDC2_W) {
                Number val = getConstantValue(instruction, cpgen);
                InstructionHandle next = handle.getNext();

                if (next != null && isStoreInstruction(next.getInstruction())) {
                    int idx = getStoreIndex(next.getInstruction());
                    constantVariables.put(idx, val);
                }
            }

            // check is const variable is being reassigned
            if (isStoreInstruction(instruction)) {
                int idx = getStoreIndex(instruction);
                InstructionHandle prev = handle.getPrev();
                if (prev == null || !(prev.getInstruction() instanceof BIPUSH)
                        || prev.getInstruction() instanceof SIPUSH || prev.getInstruction() instanceof LDC
                        || prev.getInstruction() instanceof LDC2_W) {
                    constantVariables.remove(idx);
                }
            }
            handle = handle.getNext();
        }

        // Second pass: replace variables lead with const vals
        handle = instructionList.getStart();
        while (handle != null) {
            InstructionHandle nextHandle = handle.getNext();
            Instruction instruction = handle.getInstruction();

            // if loading replace it with constant
            if (isLoadInstruction(instruction)) {
                int idx = getLoadIndex(instruction);

                if (constantVariables.containsKey(idx)) {
                    Number val = constantVariables.get(idx);
                    Instruction newInstruction = createConstantLoadInstruction(val, cpgen);

                    try {
                        instructionList.insert(handle, newInstruction);
                        instructionList.delete(handle);
                    } catch (TargetLostException e) {
                        InstructionHandle[] targets = e.getTargets();
                        for (InstructionHandle target : targets) {
                            for (InstructionTargeter targeter : target.getTargeters()) {
                                targeter.updateTarget(target, handle.getPrev());
                            }
                        }
                    }
                }
            }

            else if (isArithmeticInstruction(instruction) && nextHandle != null) {
                handle = tryFoldConstantOperation(instructionList, handle, cpgen);
            }
            handle = nextHandle;
        }
        instructionList.setPositions();

        methodGen.setMaxStack();
        methodGen.setMaxLocals();
        Method optimizedMethod = methodGen.getMethod();
        cgen.replaceMethod(method, optimizedMethod);
    }

    private Number getConstantValue(Instruction instruction, ConstantPoolGen cpgen) {
        if (instruction instanceof BIPUSH) {
            return ((BIPUSH) instruction).getValue();
        }
        if (instruction instanceof SIPUSH) {
            return ((SIPUSH) instruction).getValue();
        }
        if (instruction instanceof LDC) {
            LDC ldc = (LDC) instruction;
            Object val = ldc.getValue(cpgen);
            if (val instanceof Number) {
                return (Number) val;
            }
        } else if (instruction instanceof LDC2_W) {
            LDC2_W ldc = (LDC2_W) instruction;
            Object val = ldc.getValue(cpgen);
            if (val instanceof Number) {
                return (Number) val;
            }
        }
        return null;
    }

    private boolean isStoreInstruction(Instruction instruction) {
        return instruction instanceof StoreInstruction;
    }

    private boolean isLoadInstruction(Instruction instruction) {
        return instruction instanceof LoadInstruction;
    }

    private boolean isArithmeticInstruction(Instruction instruction) {
        return instruction instanceof ArithmeticInstruction;
    }

    private int getStoreIndex(Instruction instruction) {
        if (instruction instanceof StoreInstruction) {
            return ((StoreInstruction) instruction).getIndex();
        }
        return -1;
    }

    private int getLoadIndex(Instruction instruction) {
        if (instruction instanceof LoadInstruction) {
            return ((LoadInstruction) instruction).getIndex();
        }
        return -1;
    }

    private Instruction createConstantLoadInstruction(Number val, ConstantPoolGen cpgen) {
        if (val instanceof Integer) {
            int intVal = val.intValue();
            if (intVal >= -1 && intVal <= 5) {
                return new ICONST(intVal);
            }
            if (intVal >= Byte.MIN_VALUE && intVal <= Byte.MAX_VALUE) {
                return new BIPUSH((byte) intVal);
            }
            if (intVal >= Short.MIN_VALUE && intVal <= Short.MAX_VALUE) {
                return new SIPUSH((short) intVal);
            }
            return new LDC(cpgen.addInteger(intVal));
        }
        if (val instanceof Float) {
            float floatVal = val.floatValue();
            if (floatVal == 0.0f || floatVal == 1.0f || floatVal == 2.0f) {
                return new FCONST(floatVal);
            }
            return new LDC(cpgen.addFloat(floatVal));
        }
        if (val instanceof Long) {
            long longVal = val.longValue();
            if (longVal == 0L || longVal == 1L) {
                return new LCONST(longVal);
            }
            return new LDC2_W(cpgen.addLong(longVal));
        }
        if (val instanceof Double) {
            double doubleVal = val.doubleValue();
            if (doubleVal == 0.0d || doubleVal == 1.0d) {
                return new DCONST(doubleVal);
            }
            return new LDC2_W(cpgen.addDouble(doubleVal));
        }

        return null;
    }

    private InstructionHandle tryFoldConstantOperation(InstructionList instructionList, InstructionHandle handle,
            ConstantPoolGen cpgen) {
        // TODO: make this real, only folds where both operands are const
        Instruction instruction = handle.getInstruction();

        if (instruction instanceof IADD || instruction instanceof ISUB || instruction instanceof IMUL
                || instruction instanceof IDIV) {
            InstructionHandle first = handle.getPrev();
            InstructionHandle second = null;

            if (first != null) {
                second = first.getPrev();
            }

            if (first != null && second != null) {
                Number val1 = null;
                Number val2 = null;

                if (first.getInstruction() instanceof LDC) {
                    Object v = ((LDC) first.getInstruction()).getValue(cpgen);
                    if (v instanceof Integer) {
                        val1 = (Integer) v;
                    }
                } else if (first.getInstruction() instanceof BIPUSH) {
                    val1 = ((BIPUSH) first.getInstruction()).getValue();
                } else if (first.getInstruction() instanceof SIPUSH) {
                    val1 = ((SIPUSH) first.getInstruction()).getValue();
                }

                if (second.getInstruction() instanceof LDC) {
                    Object v = ((LDC) second.getInstruction()).getValue(cpgen);
                    if (v instanceof Integer) {
                        val2 = (Integer) v;
                    }
                } else if (second.getInstruction() instanceof BIPUSH) {
                    val2 = ((BIPUSH) second.getInstruction()).getValue();
                } else if (second.getInstruction() instanceof SIPUSH) {
                    val2 = ((SIPUSH) second.getInstruction()).getValue();
                }

                if (val1 != null && val2 != null) {
                    int res = 0;

                    if (instruction instanceof IADD) {
                        res = val2.intValue() + val1.intValue();
                    } else if (instruction instanceof ISUB) {
                        res = val2.intValue() - val1.intValue();
                    } else if (instruction instanceof IMUL) {
                        res = val2.intValue() * val1.intValue();
                    } else if (instruction instanceof IDIV) {
                        res = val2.intValue() / val1.intValue();
                    } else {
                        return handle;
                    }

                    Instruction newInstruction = createConstantLoadInstruction(res, cpgen);

                    try {
                        instructionList.delete(second);
                        instructionList.delete(first);
                        instructionList.insert(handle, newInstruction);
                        instructionList.delete(handle);

                        return handle.getPrev();
                    } catch (TargetLostException e) {
                        InstructionHandle[] targets = e.getTargets();
                        for (InstructionHandle target : targets) {
                            for (InstructionTargeter targeter : target.getTargeters()) {
                                targeter.updateTarget(target, handle.getPrev());
                            }
                        }
                    }
                }
            }
        }
        return handle;
    }
}
