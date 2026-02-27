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

    // constructor
    public Sem3Visitor(HashMap<String,ClassDecl> env, ErrorMsg e)
    {
        errorMsg         = e;
        currentClass     = null;
        classEnv         = env;
        localEnv         = new HashMap<String,VarDecl>();
        breakTargetStack = new Stack<BreakTarget>();
    }

    // link variable refrences to declarations
    @Override
    public Object visit(IDExp n)
    {
        // check local environment
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
            errorMsg.error(n.pos, CompError.UndefinedVariable(n.name));
        }

        return null;
    }

    // @Override 
    // public Object visit(IDType n)
    // {
    //     if (classEnv.containsKey(n.name))
    //     {
    //         n.link = classEnv.get(n.name);

    //         // mark class as used
    //         if (unusedClasses != null && unusedClasses.contains(n.name))
    //         {
    //             unusedClasses.remove(n.name);
    //         }
    //     }

        
    //     else
    //     { 
    //         errorMsg.error(n.pos, CompError.UndefinedClass(n.name));
    //     }
    //     return null;
    // }
}
