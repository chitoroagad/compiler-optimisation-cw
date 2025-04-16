package comp0012.main;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConstantFolder {
    ClassParser parser = null;
    ClassGen classGen = null;
    ConstantPoolGen constPoolGen = null;
    private Optimiser[] optimisers = null;

    JavaClass original = null;
    JavaClass optimized = null;

    public ConstantFolder(String classFilePath) {
        try {
            this.parser = new ClassParser(classFilePath);
            this.original = parser.parse();
            this.classGen = new ClassGen(original);
            this.constPoolGen = classGen.getConstantPool();
            this.optimisers = new Optimiser[] {
                    new SimpleFolder(classGen, constPoolGen),
                    new ConstVarFold(classGen, constPoolGen),
            };

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void optimize() {
        optimizeMethods(classGen.getMethods());
        classGen.setConstantPool(constPoolGen);
        optimized = classGen.getJavaClass();
    }

    // Applies optimisers from `optimisers` array until no changes
    public void optimizeMethods(Method[] methods) {
        for (Method originalMethod : methods) {
            Method method = originalMethod;
            Code code = method.getCode();
            if (code == null)
                continue;
            int iteration = 1;
            boolean optimisationPerformed;
            do {
                optimisationPerformed = false;
                for (Optimiser optimiser : this.optimisers) {
                    Method optimisedMethod = optimiser.optimiseMethod(method, iteration);
                    if (optimisedMethod != null) {
                        optimisationPerformed = true;
                        method = optimisedMethod;
                    }
                }
                iteration++;
            } while (optimisationPerformed);
            classGen.replaceMethod(originalMethod, method);
        }
    }

    public void write(String optimisedFilePath) {
        this.optimize();

        try {
            FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
            this.optimized.dump(out);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
