package comp0012.main;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.generic.TargetLostException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class ConstantFolder {
	private final ClassParser parser;
	private final ClassGen gen;
	private final JavaClass original;
	private JavaClass optimized;

	public ConstantFolder(String classFilePath) {
		try {
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch (IOException e) {
			throw new RuntimeException("Failed to parse class file: " + classFilePath, e);
		}
	}

	public void optimize() {
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

		for (Method method : cgen.getMethods()) {
			Code code = method.getCode();
			if (code == null)
				continue;

			InstructionList il = new InstructionList(code.getCode());
			MethodGen mg = new MethodGen(method, cgen.getClassName(), cpgen);

			Map<Integer, Number> dynVars = new HashMap<>();
			Stack<Number> constantStack = new Stack<>();

			for (InstructionHandle handle : il.getInstructionHandles()) {
				Instruction inst = handle.getInstruction();

				if (inst instanceof ConstantPushInstruction) {
					Number val = ((ConstantPushInstruction) inst).getValue();
					constantStack.push(val);
				}
				else if (inst instanceof StoreInstruction && !(inst instanceof ASTORE)) {
					if (!constantStack.isEmpty()) {
						Number value = constantStack.pop();
						int index = ((StoreInstruction) inst).getIndex();
						dynVars.put(index, value);
					}
					deleteInstruction(handle, il);
				}
				else if (inst instanceof LoadInstruction && !(inst instanceof ALOAD)) {
					int index = ((LoadInstruction) inst).getIndex();
					if (dynVars.containsKey(index)) {
						Number value = dynVars.get(index);
						Instruction replacement = createLDCInstruction(value, cpgen);
						il.insert(handle, replacement);
						deleteInstruction(handle, il);
					}
				}
				else if (inst instanceof ArithmeticInstruction) {
					if (constantStack.size() >= 2) {
						Number op1 = constantStack.pop();
						Number op2 = constantStack.pop();
						Number result = computeFoldedResult(op2, op1, inst);
						if (result != null) {
							constantStack.push(result);
							Instruction replacement = createLDCInstruction(result, cpgen);
							il.insert(handle, replacement);
							deleteInstruction(handle, il);
						} else {
							constantStack.push(op2);
							constantStack.push(op1);
						}
					}
				}
				else {
					constantStack.clear();
				}
			}

			InstructionFinder finder = new InstructionFinder(il);
			boolean changed = false;
			String[] patterns = {
					"LDC LDC IADD", "LDC LDC ISUB", "LDC LDC IMUL", "LDC LDC IDIV",
					"LDC2_W LDC2_W LADD", "LDC2_W LDC2_W LSUB", "LDC2_W LDC2_W LMUL", "LDC2_W LDC2_W LDIV",
					"LDC LDC FADD", "LDC LDC FSUB", "LDC LDC FMUL", "LDC LDC FDIV",
					"LDC2_W LDC2_W DADD", "LDC2_W LDC2_W DSUB", "LDC2_W LDC2_W DMUL", "LDC2_W LDC2_W DDIV"
			};

			for (String pattern : patterns) {
				for (Iterator<?> it = finder.search(pattern); it.hasNext();) {
					InstructionHandle[] handles = (InstructionHandle[]) it.next();
					Instruction opInst = handles[2].getInstruction();

					Number val1 = getNumber(handles[0].getInstruction(), cpgen);
					Number val2 = getNumber(handles[1].getInstruction(), cpgen);
					Number result = computeFoldedResult(val1, val2, opInst);

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
			}

			if (changed) {
				mg.setMaxStack();
				mg.setMaxLocals();
				Method newMethod = mg.getMethod();
				cgen.replaceMethod(method, newMethod);
			}
			il.dispose();
		}
		this.optimized = cgen.getJavaClass();
	}

	private Instruction createLDCInstruction(Number value, ConstantPoolGen cpgen) {
		if (value instanceof Integer) {
			return new LDC(cpgen.addInteger((Integer) value));
		} else if (value instanceof Long) {
			return new LDC2_W(cpgen.addLong((Long) value));
		} else if (value instanceof Float) {
			return new LDC(cpgen.addFloat((Float) value));
		} else if (value instanceof Double) {
			return new LDC2_W(cpgen.addDouble((Double) value));
		}
		throw new IllegalArgumentException("Unsupported constant type: " + value);
	}

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
		}
		return null;
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

	private void deleteInstruction(InstructionHandle handle, InstructionList il) {
		try {
			il.delete(handle);
		} catch (TargetLostException e) {
			for (InstructionHandle target : e.getTargets()) {
				for (InstructionTargeter t : target.getTargeters()) {
					t.updateTarget(target, target.getNext());
				}
			}
		}
	}

	public void write(String optimizedFilePath) {
		optimize();
		try (FileOutputStream out = new FileOutputStream(new File(optimizedFilePath))) {
			this.optimized.dump(out);
		} catch (IOException e) {
			throw new RuntimeException("Failed to write optimized class file: " + optimizedFilePath, e);
		}
	}
}