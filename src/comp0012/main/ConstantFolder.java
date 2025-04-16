package comp0012.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;

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

		for (Method method : cgen.getMethods()) {
			MethodGen mg = new MethodGen(method, cgen.getClassName(), cpgen);
			InstructionList il = mg.getInstructionList();

			if (il == null) continue;

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
				for (Iterator<?> it = finder.search(pattern); it.hasNext(); ) {
					InstructionHandle[] handles = (InstructionHandle[]) it.next();
					Instruction op = handles[2].getInstruction();

					Number val1 = getNumber(handles[0].getInstruction(), cpgen);
					Number val2 = getNumber(handles[1].getInstruction(), cpgen);
					Number result = computeFoldedResult(val1, val2, op);

					if (result == null) continue;

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
		}

		this.optimized = cgen.getJavaClass();
	}

	public void write(String optimisedFilePath) {
		this.optimize();

		try (FileOutputStream out = new FileOutputStream(new File(optimisedFilePath))) {
			this.optimized.dump(out);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Helper: Extract constant values from instructions
	private Number getNumber(Instruction instr, ConstantPoolGen cpgen) {
		if (instr instanceof LDC) {
			Object val = ((LDC) instr).getValue(cpgen);
			if (val instanceof Integer || val instanceof Float) return (Number) val;
		} else if (instr instanceof LDC2_W) {
			Object val = ((LDC2_W) instr).getValue(cpgen);
			if (val instanceof Long || val instanceof Double) return (Number) val;
		}
		return null;
	}

	// Helper: Apply the arithmetic operation
	private Number computeFoldedResult(Number a, Number b, Instruction op) {
		try {
			if (op instanceof IADD) return a.intValue() + b.intValue(); if (op instanceof ISUB) return a.intValue() - b.intValue();
			if (op instanceof IMUL) return a.intValue() * b.intValue(); if (op instanceof IDIV && b.intValue() != 0) return a.intValue() / b.intValue();

			if (op instanceof LADD) return a.longValue() + b.longValue(); if (op instanceof LSUB) return a.longValue() - b.longValue();
			if (op instanceof LMUL) return a.longValue() * b.longValue(); if (op instanceof LDIV && b.longValue() != 0) return a.longValue() / b.longValue();

			if (op instanceof FADD) return a.floatValue() + b.floatValue(); if (op instanceof FSUB) return a.floatValue() - b.floatValue();
			if (op instanceof FMUL) return a.floatValue() * b.floatValue(); if (op instanceof FDIV && b.floatValue() != 0.0f) return a.floatValue() / b.floatValue();

			if (op instanceof DADD) return a.doubleValue() + b.doubleValue(); if (op instanceof DSUB) return a.doubleValue() - b.doubleValue();
			if (op instanceof DMUL) return a.doubleValue() * b.doubleValue(); if (op instanceof DDIV && b.doubleValue() != 0.0) return a.doubleValue() / b.doubleValue();
		} catch (Exception e) {
			// silently ignore division by zero or overflow
		}

		return null;
	}
}