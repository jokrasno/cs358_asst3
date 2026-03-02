package visitor;

import syntaxtree.*;
import java.util.*;
import errorMsg.*;
// The purpose of this class is to:
// - link each variable reference to its corresponding VarDecl
//    (via its 'link' field)
//   - undefined variable names are reported
// - link each type reference to its corresponding ClassDecl
//   - undefined type names are reported
// - link each Break expression to its enclosing While or Case statement
//   - a break that is not inside any while loop or case is reported
// - report conflicting local variable names (including formal parameter names)
// - ensure that no instance variable has the name 'length'
public class Sem3Visitor extends Visitor
{
    // current class we're visiting
    ClassDecl currentClass;

    // environment for names of classes
    HashMap<String, ClassDecl> classEnv;

    // environment for names of variables
    HashMap<String, VarDecl> localEnv;

    // set of initialized variables
    HashSet<String> init;

    // set of unused classes
    HashSet<String> unusedClasses;

    // set of unused local variables
    HashSet<String> unusedLocals;

    // stack of while/switch
    Stack<BreakTarget> breakTargetStack;

    //error message object
    ErrorMsg errorMsg;

    // track variables in current switch chunk
    Stack<ArrayList<String>> chunkVars;

    // constructor
    public Sem3Visitor(HashMap<String,ClassDecl> env, ErrorMsg e)
    {
        errorMsg         = e;
        currentClass     = null;
        classEnv         = env;
        localEnv         = new HashMap<String,VarDecl>();
        breakTargetStack = new Stack<BreakTarget>();
        chunkVars        = new Stack<ArrayList<String>>();

        unusedClasses    = new HashSet<String>();
        unusedLocals     = new HashSet<String>();

        for (String className : classEnv.keySet())
        {
            if (!className.equals("Object") && !className.equals("String") && !className.equals("Lib") && !className.equals("RunMain"))
            {
                unusedClasses.add(className);
            }
        }
    }

    @Override
    public Object visit(Program p)
    {
        p.classDecls.accept(this);
        p.mainStmt.accept(this);
        
        // Report any unused classes
        for (String className : unusedClasses)
        {
            ClassDecl c = classEnv.get(className);
            if (c != null)
            {
                errorMsg.warning(c.pos, CompWarning.UnusedClass(className));
            }
        }
        
        return null;
    }

    // link variable refrences to declarations
    @Override
    public Object visit(IDExp n)
    {
        // System.err.println("DEBUG: Checking local environment for: " + n.name);  // TEMPORARY DEBUG

        if (localEnv.containsKey(n.name))
        {
            n.link = localEnv.get(n.name);

            // check if initialized
            if (!init.contains(n.name))
            {
                errorMsg.error(n.pos, CompError.UninitializedVariable(n.name));
            }

            // mark as used
            if (unusedLocals != null && unusedLocals.contains(n.name))
            {
                unusedLocals.remove(n.name);
            }
        }
        else if (currentClass != null)
        {
            // System.err.println("DEBUG: Checking field for: " + n.name);  // TEMPORARY DEBUG
            ClassDecl c = currentClass;
            while (c != null)
            {
                if (c.fieldEnv.containsKey(n.name))
                {
                    n.link = c.fieldEnv.get(n.name);
                    return null;
                }
                c = c.superLink;
            }
            // not found anywhere
            // System.err.println("DEBUG: Undefined variable: " + n.name);  // TEMPORARY DEBUG
            errorMsg.error(n.pos, CompError.UndefinedVariable(n.name));
        }

        return null;
    }

    @Override 
    public Object visit(IDType n)
    {
        if (classEnv.containsKey(n.name))
        {
            n.link = classEnv.get(n.name);
            // mark class as used
            if (unusedClasses != null && unusedClasses.contains(n.name))
            {
                unusedClasses.remove(n.name);
            }
        }
        
        else
        { 
            errorMsg.error(n.pos, CompError.UndefinedClass(n.name));
        }
        return null;
    }

    @Override
    public Object visit(ClassDecl n)
    {
        ClassDecl oldClass = currentClass;
        currentClass = n;
        n.decls.accept(this);
        currentClass = oldClass;
        return null;
    }

    @Override
    public Object visit(Break n)
    {
        if (breakTargetStack.isEmpty())
        {
            errorMsg.error(n.pos, CompError.TopLevelBreak());
            return null;
        }
        
        n.breakLink = breakTargetStack.peek();
        
        ArrayList<String> chunk = chunkVars.peek();
        for (String var : chunk)
        {
            localEnv.remove(var);
            init.remove(var);
        }
        chunk.clear();
        
        return null;
    }

    @Override
    public Object visit(While n)
    {
        breakTargetStack.push(n);
        n.exp.accept(this);
        n.body.accept(this);
        breakTargetStack.pop();
        return null;
    }

    @Override
    public Object visit(Switch n)
    {
        breakTargetStack.push(n);
        chunkVars.push(new ArrayList<String>());
        n.exp.accept(this);
        n.stmts.accept(this);
        chunkVars.pop();
        breakTargetStack.pop();
        return null;
    }

    @Override
    public Object visit(Case n)
    {
        n.enclosingSwitch = (Switch)breakTargetStack.peek();
        n.exp.accept(this);
        return null;
    }

    @Override
    public Object visit(Default n)
    {
        n.enclosingSwitch = (Switch)breakTargetStack.peek();
        return null;
    }

    @Override
    public Object visit(MethodDecl n)
    {
        HashMap<String,VarDecl> oldLocalEnv = localEnv;
        HashSet<String> oldInit = init;
        HashSet<String> oldUnusedLocals = unusedLocals;
        
        localEnv = new HashMap<String,VarDecl>();
        init = new HashSet<String>();
        unusedLocals = new HashSet<String>();
        
        n.params.accept(this);
        n.stmts.accept(this);
        
        for (String varName : unusedLocals)
        {
            VarDecl var = localEnv.get(varName);
            if (var != null)
            {
                errorMsg.warning(var.pos, CompWarning.UnusedVariable(varName));
            }
        }
        
        localEnv = oldLocalEnv;
        init = oldInit;
        unusedLocals = oldUnusedLocals;
        
        return null;
    }

    @Override
    public Object visit(ParamDecl n)
    {
        if (localEnv.containsKey(n.name))
        {
            errorMsg.error(n.pos, CompError.DuplicateVariable(n.name));
        }
        localEnv.put(n.name, n);
        unusedLocals.add(n.name);
        init.add(n.name);
        return null;
    }

    @Override
    public Object visit(LocalVarDecl n)
    {
        if (localEnv.containsKey(n.name))
        {
            errorMsg.error(n.pos, CompError.DuplicateVariable(n.name));
        }
        localEnv.put(n.name, n);
        n.initExp.accept(this);
        init.add(n.name);
        unusedLocals.add(n.name);
        return null;
    }

    @Override
    public Object visit(Assign n)
    {
        n.rhs.accept(this);
        n.lhs.accept(this);
        if (n.lhs instanceof IDExp)
        {
            IDExp id = (IDExp)n.lhs;
            if (id.link != null)
            {
                init.add(id.link.name);
            }
        }
        return null;
    }

    @Override
    public Object visit(LocalDeclStmt n)
    {
        n.localVarDecl.accept(this);
        // Track variables declared in switch chunks
        if (!chunkVars.isEmpty())
        {
            chunkVars.peek().add(n.localVarDecl.name);
        }
        return null;
    }

}
