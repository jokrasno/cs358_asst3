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
    HashSet<VarDecl> init;

    // set of unused classes
    HashSet<String> unusedClasses;

    // set of unused local variables
    HashSet<VarDecl> unusedLocals;

    // stack of while/switch
    Stack<BreakTarget> breakTargetStack;

    //error message object
    ErrorMsg errorMsg;

    // track variables in current switch chunk
    Stack<ArrayList<VarDecl>> chunkVars;

    // stack of lexical local scopes
    Stack<ArrayList<VarDecl>> localScopes;

    // constructor
    public Sem3Visitor(HashMap<String,ClassDecl> env, ErrorMsg e)
    {
        errorMsg         = e;
        currentClass     = null;
        classEnv         = env;
        localEnv         = new HashMap<String,VarDecl>();
        breakTargetStack = new Stack<BreakTarget>();
        chunkVars        = new Stack<ArrayList<VarDecl>>();
        localScopes      = new Stack<ArrayList<VarDecl>>();

        unusedClasses    = new HashSet<String>();
        unusedLocals     = new HashSet<VarDecl>();
        init             = new HashSet<VarDecl>();

        for (String className : classEnv.keySet())
        {
            if (!isPredefinedClass(className))
            {
                unusedClasses.add(className);
            }
        }
    }

    private void pushLocalScope()
    {
        localScopes.push(new ArrayList<VarDecl>());
    }

    private boolean isPredefinedClass(String className)
    {
        return className.equals("Object") || className.equals("String") ||
               className.equals("Lib") || className.equals("RunMain");
    }

    private void popLocalScope()
    {
        if (localScopes.isEmpty())
        {
            return;
        }

        ArrayList<VarDecl> scope = localScopes.pop();
        for (VarDecl var : scope)
        {
            VarDecl active = localEnv.get(var.name);
            if (active == var)
            {
                localEnv.remove(var.name);
            }
            init.remove(var);
        }
    }

    private void declareLocal(VarDecl var)
    {
        localEnv.put(var.name, var);
        if (!localScopes.isEmpty())
        {
            localScopes.peek().add(var);
        }
    }

    private void markClassUsed(String className)
    {
        if (unusedClasses != null && className != null)
        {
            unusedClasses.remove(className);
        }
    }

    private Object analyzeMethod(MethodDecl n, Type rtnType, Exp rtnExp)
    {
        HashMap<String,VarDecl> oldLocalEnv = localEnv;
        HashSet<VarDecl> oldInit = init;
        HashSet<VarDecl> oldUnusedLocals = unusedLocals;
        Stack<ArrayList<VarDecl>> oldLocalScopes = localScopes;

        localEnv = new HashMap<String,VarDecl>();
        init = new HashSet<VarDecl>();
        unusedLocals = new HashSet<VarDecl>();
        localScopes = new Stack<ArrayList<VarDecl>>();
        pushLocalScope();

        // Check if this method belongs to a predefined class
        boolean isPredefined = currentClass != null && isPredefinedClass(currentClass.name);

        if (rtnType != null)
        {
            rtnType.accept(this);
        }

        n.params.accept(this);
        n.stmts.accept(this);

        if (rtnExp != null)
        {
            rtnExp.accept(this);
        }

        // Only report unused variables for non-predefined classes
        if (!errorMsg.anyErrors && !isPredefined)
        {
            for (VarDecl var : unusedLocals)
            {
                errorMsg.warning(var.pos, CompWarning.UnusedVariable(var.name));
            }
        }

        localEnv = oldLocalEnv;
        init = oldInit;
        unusedLocals = oldUnusedLocals;
        localScopes = oldLocalScopes;
        return null;
    }

    @Override
    public Object visit(Program p)
    {
        p.classDecls.accept(this);
        p.predefinedDecls.accept(this);
        p.mainStmt.accept(this);
        
        if (!errorMsg.anyErrors)
        {
            for (String className : unusedClasses)
            {
                // don't report warnings for predefined classes
                if (!isPredefinedClass(className))
                {
                    ClassDecl c = classEnv.get(className);
                    if (c != null)
                    {
                        errorMsg.warning(c.pos, CompWarning.UnusedClass(className));
                    }
                }
            }
        }
        
        return null;
    }

    // link variable refrences to declarations
    @Override
    public Object visit(IDExp n)
    {
        if (localEnv.containsKey(n.name))
        {
            n.link = localEnv.get(n.name);

            if (!init.contains(n.link))
            {
                errorMsg.error(n.pos, CompError.UninitializedVariable(n.name));
            }

            if (unusedLocals != null)
            {
                unusedLocals.remove(n.link);
            }
        }
        else if (currentClass != null)
        {
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
        markClassUsed(n.superName);
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

        if (n.breakLink instanceof Switch && !chunkVars.isEmpty())
        {
            ArrayList<VarDecl> chunk = chunkVars.peek();
            for (VarDecl var : chunk)
            {
                VarDecl active = localEnv.get(var.name);
                if (active == var)
                {
                    localEnv.remove(var.name);
                }
                init.remove(var);
            }
            chunk.clear();
        }
        
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
        chunkVars.push(new ArrayList<VarDecl>());
        pushLocalScope();
        n.exp.accept(this);
        n.stmts.accept(this);
        popLocalScope();
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
        return analyzeMethod(n, null, null);
    }

    @Override
    public Object visit(MethodDeclNonVoid n)
    {
        return analyzeMethod(n, n.rtnType, n.rtnExp);
    }

    @Override
    public Object visit(ParamDecl n)
    {
        n.type.accept(this);
        if (localEnv.containsKey(n.name))
        {
            errorMsg.error(n.pos, CompError.DuplicateVariable(n.name));
            return null;
        }
        declareLocal(n);
        unusedLocals.add(n);
        init.add(n);
        return null;
    }

    @Override
    public Object visit(LocalVarDecl n)
    {
        n.type.accept(this);
        boolean declared = false;
        if (localEnv.containsKey(n.name))
        {
            errorMsg.error(n.pos, CompError.DuplicateVariable(n.name));
        }
        else
        {
            declareLocal(n);
            unusedLocals.add(n);
            declared = true;
        }
        n.initExp.accept(this);
        if (declared)
        {
            init.add(n);
        }
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
                init.add(id.link);
            }
        }
        return null;
    }

    @Override
    public Object visit(Block n)
    {
        pushLocalScope();
        n.stmts.accept(this);
        popLocalScope();
        return null;
    }

    @Override
    public Object visit(LocalDeclStmt n)
    {
        n.localVarDecl.accept(this);
        // track variables declared in switch chunks
        if (!chunkVars.isEmpty())
        {
            chunkVars.peek().add(n.localVarDecl);
        }
        return null;
    }

}
