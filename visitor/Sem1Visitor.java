package visitor;

import syntaxtree.*;
import java.util.*;
import errorMsg.*;
// The purpose of the Sem1Visitor class is to:
// - enter each class declaration into the global environment
//   - duplicate class names are detected
// - enter each method declaration into the method environment for its class
//   - duplicate method names for a class are detected
// - enter each field declaration into the field environment for its class
//   - duplicate instance variable names for a class are detected
// - all of the above are also done for the predefined classes
//   (Object, String, Lib and RunMain)
public class Sem1Visitor extends Visitor
{

    HashMap<String,ClassDecl> classEnv;
    ClassDecl currentClass;
    ClassDeclList classes;
    ErrorMsg errorMsg;

    public Sem1Visitor(ErrorMsg e)
    {
        errorMsg     = e;
        currentClass = null;
        classes      = new ClassDeclList();
        classEnv     = InitPredefined.initEnv(classes);
    }

    public HashMap<String,ClassDecl> getEnv()
    {
        return classEnv;
    }

    /**
     * Inserts the predefined class declarations into the Program
     * object so that they can be displayed, and so that the Sem1-,
     * Sem2-, and Sem3-visitors will traverse them.
     *
     * **** CS 358 Students should NOT modify this method ****
     */
    @Override
    public Object visit(Program p)
    {
        p.setPredefined(classes);
        p.classDecls.accept(this);
        return null;
    }

    @Override
    public Object visit(ClassDecl n)
    {
        // put name into class environment if it doesn't exist
        if (classEnv.containsKey(n.name))
        {
            errorMsg.error(n.pos, CompError.DuplicateClass(n.name));
        }
        else
        {
            classEnv.put(n.name, n);
        }
        currentClass = n;
        n.decls.accept(this);

        return null;
    }

    @Override
    public Object visit(MethodDecl n)
    {
        if (currentClass.methodEnv.containsKey(n.name))
        {
            errorMsg.error(n.pos, CompError.DuplicateMethod(n.name));
        }
        currentClass.methodEnv.put(n.name, n);
        n.params.accept(this);
        n.stmts.accept(this);
        return null;
    }

    @Override
    public Object visit(FieldDecl n)
    {
        if (currentClass.fieldEnv.containsKey(n.name))
        {
            errorMsg.error(n.pos, CompError.DuplicateField(n.name));
        }
        // check if field name is "length"
        if (n.name.equals("length"))
        {
            errorMsg.error(n.pos, CompError.IllegalLength());
        }
        currentClass.fieldEnv.put(n.name, n);
        n.type.accept(this);
        return null;
    }
}
