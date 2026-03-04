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

    @Override
    public Object visit(Program n)
    {
        // Pass 1: link each class to its superclass.
        n.classDecls.accept(this);

        // Pass 2: detect cycles and report each class that participates.
        HashMap<ClassDecl, Integer> visitState = new HashMap<ClassDecl, Integer>();
        HashSet<ClassDecl> cycleParticipants = new HashSet<ClassDecl>();

        for (ClassDecl cls : n.classDecls)
        {
            if (!visitState.containsKey(cls))
            {
                markCycleParticipants(cls, visitState, cycleParticipants);
            }
        }

        for (ClassDecl cls : n.classDecls)
        {
            if (cycleParticipants.contains(cls))
            {
                errorMsg.error(cls.pos, CompError.InheritanceCycle(cls.name));
            }
        }

        n.mainStmt.accept(this);
        return null;
    }

    // Link each ClassDecl to the ClassDecl for its superclass via its 'superLink'
    @Override
    public Object visit(ClassDecl n)
    {
        n.superLink = null;

        // SuperClass checking
        if (n.superName.equals("Object"))
        {
            ClassDecl objectClass = classEnv.get("Object");
            n.superLink = objectClass;
            addSubclassLink(objectClass, n);
        }
        else if (n.superName.equals("String") || n.superName.equals("RunMain"))
        {
            errorMsg.error(n.pos, CompError.IllegalSuperclass(n.superName));
        }
        else if (classEnv.containsKey(n.superName))
        {
            ClassDecl superClass = classEnv.get(n.superName);
            n.superLink = superClass;
            addSubclassLink(superClass, n);
        }
        else
        {
            errorMsg.error(n.pos, CompError.UndefinedSuperclass(n.superName));
        }

        return null;
    }

    private void addSubclassLink(ClassDecl superClass, ClassDecl subClass)
    {
        if (superClass != null && !superClass.subclasses.contains(subClass))
        {
            superClass.subclasses.add(subClass);
        }
    }

    private void markCycleParticipants(ClassDecl start,
                                       HashMap<ClassDecl, Integer> visitState,
                                       HashSet<ClassDecl> cycleParticipants)
    {
        ArrayList<ClassDecl> path = new ArrayList<ClassDecl>();
        HashMap<ClassDecl, Integer> indexInPath = new HashMap<ClassDecl, Integer>();
        ClassDecl current = start;

        while (current != null)
        {
            Integer state = visitState.get(current);
            if (state != null)
            {
                if (state == 1 && indexInPath.containsKey(current))
                {
                    int cycleStart = indexInPath.get(current);
                    for (int i = cycleStart; i < path.size(); i++)
                    {
                        cycleParticipants.add(path.get(i));
                    }
                }
                break;
            }

            visitState.put(current, 1);
            indexInPath.put(current, path.size());
            path.add(current);
            current = current.superLink;
        }

        for (ClassDecl cls : path)
        {
            visitState.put(cls, 2);
        }
    }

}
