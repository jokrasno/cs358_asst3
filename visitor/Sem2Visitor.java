package visitor;

import syntaxtree.*;
import java.util.*;
import errorMsg.*;

// the purpose of this class is to
// - link each ClassDecl to the ClassDecl for its superclass 
//    (via its 'superLink')
// - link each ClassDecl to each of its subclasses 
//    (via the 'subclasses' instance variable)
// - ensure that there are no cycles in the inheritance hierarchy
// - ensure that no class has 'String' or 'RunMain' as its superclass
public class Sem2Visitor extends Visitor
{

    HashMap<String,ClassDecl> classEnv;
    ErrorMsg errorMsg;

    public Sem2Visitor(HashMap<String,ClassDecl> env, ErrorMsg e)
    {
        errorMsg = e;
        classEnv = env;
    }

    // Link each ClassDecl to the ClassDecl for its superclass via its 'superLink'
    @Override
    public Object visit(ClassDecl n)
    {
        // SuperClass checking
        if (n.superName.equals("Object"))
        {
            n.superLink = classEnv.get("Object");
            classEnv.get("Object").subclasses.add(n);
        }
        else if (n.superName.equals("String") || n.superName.equals("RunMain"))
        {
            errorMsg.error(n.pos, CompError.IllegalSuperclass(n.superName));
        }
        else if (classEnv.containsKey(n.superName))
        {
            n.superLink = classEnv.get(n.superName);
            classEnv.get(n.superName).subclasses.add(n);
        }
        else
        {
            errorMsg.error(n.pos, CompError.UndefinedSuperclass(n.superName));
        }

        // For each class C, follow superlink chain. If more than total clases before hitting null, there's a cycle
        ClassDecl current = n;
        int count = 0;
        while (current != null)
        {
            current = current.superLink;
            count++;
            if (count > classEnv.size())
            {
                errorMsg.error(n.pos, CompError.InheritanceCycle(n.name));
                break;
            }
        }
        

        return null;
    }

}
