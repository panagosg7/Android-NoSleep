package energy.components;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.CollectionFilter;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.IndiscriminateFilter;
import com.ibm.wala.util.graph.GraphReachability;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.traverse.DFSPathFinder;
import com.ibm.wala.util.intset.OrdinalSet;

import energy.analysis.ApplicationCallGraph;
import energy.analysis.Opts;
import energy.util.E;
import energy.util.LockingStats;
import energy.util.Util;

@SuppressWarnings("deprecation")
public class ComponentManager {

  private static int DEBUG_LEVEL = 2;

  private static HashMap<String, Component> components;

  private static ApplicationCallGraph originalCG;

  private GraphReachability<CGNode> graphReachability;

  public HashMap<String, Component> getComponents() {
    return components;
  }

  public ComponentManager(ApplicationCallGraph cg) {
    originalCG = cg;
    components = new HashMap<String, Component>();
  }

  private static void registerComponent(String name, Component comp) {
    components.put(name, comp);
  }

  
  

  /**
   * 1. Reachability results are goint to be useful later
   */
  public void prepareReachability() {    
    Filter<CGNode> filter = new CollectionFilter<CGNode>(
        originalCG.getTargetCGNodeHash().values());
    graphReachability = new GraphReachability<CGNode>(originalCG, filter);
    try { 
      graphReachability.solve(null);
    } catch (CancelException e) {
      e.printStackTrace();
    }
  }
  

  /**
   * 2. Resolve components based on the root methods of the call graph (Callbacks)
   */
  public void resolveComponents() {
    E.log(DEBUG_LEVEL, "Number of nodes: " + originalCG.getNumberOfNodes());
    // Counts
    int resolvedComponentCount = 0;
    int resolvedImplementorCount = 0;
    int totalCallBacks = 0;
    int resolvedConstructors = 0;
    int unresolvedCallBacks = 0;
    int unresolvedInterestingCallBacks = 0;
    List<String>  unresolvedSB = new ArrayList<String>();
    Collection<CGNode>  roots = GraphUtil.inferRoots(originalCG);
    
    for (CGNode root : roots) {
      /* Ignore the stand-alone target method nodes */
      if (!originalCG.isTargetMethod(root)) {
               
        /*
         * Get known components, e.g. Activity
         */
        Component component = resolveApplicationComponent(root);
        String className = root.getMethod().getDeclaringClass().getName().toString();

        if (component != null) {
          E.log(DEBUG_LEVEL, component.toString());
          registerComponent(className, component);

          if (component instanceof Initializer) {
            resolvedConstructors++;
          } else {
            resolvedComponentCount++;
          }
        } else {
          /* Get known implementors. e.g. Runnable */
          Component knownImpl = resolveKnownImplementors(root);

          if (knownImpl != null) {
            registerComponent(className, knownImpl);
            E.log(DEBUG_LEVEL, knownImpl.toString());
            resolvedImplementorCount++;
          } else {
            /* Unresolved */
            unresolvedCallBacks++;
            unresolvedSB.add(root.getMethod().getSignature().toString());
            /*
             * We can check now if we should actually care about this callback.
             */
            if (graphReachability.getReachableSet(root).size() > 0) {
              
              /* Get a path from the node to the target function */
              Filter<CGNode> filter = new Filter<CGNode>() {
                @Override 
                public boolean accepts(CGNode o) { 
                  return originalCG.isTargetMethod(o);
                }
              };
              
              //TODO: replace reachability with this?
              DFSPathFinder<CGNode> pf = new DFSPathFinder<CGNode>(originalCG, root, filter);
              List<CGNode> path = pf.find();
              
              /* Output unresolved info just for the interesting callbacks */
              outputUnresolvedInfo(root, path);
              
              unresolvedInterestingCallBacks++; // for which we care
            }                
          }
        }
        totalCallBacks++;
      }
    }

    E.log(0, "############################################################");
    String fst = String.format("%-30s: %d", "Resolved classes", resolvedComponentCount);
    E.log(0, fst);
    fst = String.format("%-30s: %d", "Resolved implementors", resolvedImplementorCount);
    E.log(0, fst);
    fst = String.format("%-30s: %d", "Resolved constructors", resolvedConstructors);
    E.log(0, fst);
    fst = String.format("%-30s: %d (%.2f %%)", "Unresolved callbacks", unresolvedCallBacks,        
        100 * ((double) unresolvedCallBacks / (double) totalCallBacks));
    E.log(0, fst);
    fst = String.format("%-30s: %d (%.2f %%)", "Interesting Unresolved cbs", unresolvedInterestingCallBacks, 
        100 * ((double) unresolvedInterestingCallBacks / (double) totalCallBacks));
    E.log(0, fst);
    E.log(0, "------------------------------------------------------------");
    E.log(2, "Unresolved");
    E.log(2, unresolvedSB);
    E.log(2, "------------------------------------------------------------");
    fst = String.format("%-30s: %d", "Total callbacks", totalCallBacks);
    E.log(0, fst);
    fst = String.format("%-30s: %d", "Resolved components", components.size());
    E.log(0, fst);
    E.log(0, "############################################################");

  }

  private static void outputUnresolvedInfo(CGNode root, List<CGNode> path) {
    IClass declaringClass = root.getMethod().getDeclaringClass();
    Collection<IClass> allImplementedInterfaces = declaringClass.getAllImplementedInterfaces();
    E.log(1, "#### UnResolved: " + root.getMethod().getSignature().toString());
    for (IClass c : getClassAncestors(declaringClass)) {
      E.log(1, "#### CL: " + c.getName().toString());
    }
    for (IClass c : allImplementedInterfaces) {
      E.log(1, "#### IF: " + c.getName().toString());
    }
    E.log(1, "==== Path to target:");
    for (CGNode n : path) {
      E.log(1, "---> " + n.getMethod().getSignature().toString());
    }   

  }

  /**
   * Find out what type of component this class belongs to. E.g. Activity,
   * Service, ...
   * 
   * @param root
   * @return
   * @throws IOException
   */
  private static Component resolveApplicationComponent(CGNode root) {

    IClass klass = root.getMethod().getDeclaringClass();
    String className = klass.getName().toString();
    String methName = root.getMethod().getName().toString();
    E.log(2, "Declaring class: " + className);

    Component comp = components.get(className);
    if (comp == null) {
      /* We haven't met this component so far */
      // find the super-classes until object
      ArrayList<IClass> classAncestors = getClassAncestors(klass);
      for (IClass anc : classAncestors) {
        String ancName = anc.getName().toString();
        if (ancName.equals("Landroid/app/Activity")) {
          comp = new Activity(klass, root);
        }
        if (ancName.equals("Landroid/app/Service")) {
          comp = new Service(klass, root);
        }
        if (ancName.equals("Landroid/content/ContentProvider")) {
          comp = new ContentProvider(klass, root);
        }
        if (ancName.equals("Landroid/content/BroadcastReceiver")) {
          comp = new BroadcastReceiver(klass, root);
        }
        if (ancName.equals("Landroid/os/AsyncTask")) {
          comp = new AsyncTask(klass, root);
        }
        if (ancName.equals("Landroid/widget/BaseAdapter")) {
          comp = new BaseAdapter(klass, root);
        }
        if (ancName.equals("Landroid/view/View")) {
          comp = new View(klass, root);
        }
      }

      /*
       * Important: check if this is a real component before going into the
       * rest.
       */
      if (comp != null) {
        E.log(DEBUG_LEVEL, comp.toString() + " : " + root.getMethod().getSignature().toString());
        return comp;
      }

      /* TODO Check to see if this is a constructor. 
       * Has to be <init> or somethind...
       * */
      /*if (methName.equals("<init>") || methName.equals("<clinit>")) {
        comp = new Initializer(klass, root);
        E.log(DEBUG_LEVEL, comp.toString() + " : " + root.getMethod().getSignature().toString());
      }*/
      
      return comp;
    } else {
      /* Existing component: just update the callback */
      E.log(DEBUG_LEVEL, comp.toString() + " : " + root.getMethod().getSignature().toString());
      comp.registerCallback(methName, root);
      // update the callgraph node set
      return comp;
    }
  }

  /**
   * Compute the class ancestors until Object
   * 
   * @param klass
   * @return
   */
  private static ArrayList<IClass> getClassAncestors(IClass klass) {
    ArrayList<IClass> classList = new ArrayList<IClass>();
    IClass currentClass = klass;
    IClass superClass;
    while ((superClass = currentClass.getSuperclass()) != null) {
      classList.add(superClass);
      currentClass = superClass;
    }
    return classList;
  }

  /**
   * Find out if this class implements a known class. E.g. Runnable
   * 
   * @param root
   * @return
   */
  private static Component resolveKnownImplementors(CGNode root) {

    IClass klass = root.getMethod().getDeclaringClass();
    String className = klass.getName().toString();
    String methName = root.getMethod().getName().toString();

    Component comp = components.get(className);
    if (comp == null) {
      Collection<IClass> allImplementedInterfaces = klass.getAllImplementedInterfaces();
      for (IClass iI : allImplementedInterfaces) {
        String implName = iI.getName().toString();
        if (implName.equals("Ljava/lang/Runnable")) {
          return new Thread(klass, root);
        }
        if (implName.equals("Landroid/widget/AdapterView$OnItemClickListener")) {
          return new AdapterViewOnItemClickListener(klass, root);
        }
        if (implName.equals("Landroid/view/View$OnClickListener")) {
          return new ViewOnClickListener(klass, root);
        }
        if (implName.equals("Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener")
            || implName.equals("Landroid/preference/Preference$OnPreferenceChangeListener")) {
          return new OnSharedPreferenceChangeListener(klass, root);
        }
        if (implName.equals("Landroid/location/LocationListener")) {
          return new LocationListener(klass, root);
        }
        if (implName.startsWith("Landroid/widget/CompoundButton")) {
          return new CompoundButton(klass, root);
        }
        if (implName.startsWith("Landroid/hardware/SensorEventListener")) {
          return new SensorEventListener(klass, root);
        }        
      }
      return null;
    } else {
      /**
       * Existing component: just update the callback
       */
      comp.registerCallback(methName, root);
      return comp;
    }
  }

  /**
   * Return the successors of Thread.start or null if its not found
   * 
   * @param cg
   * @return
   */
  @SuppressWarnings("unused")
  private static HashSet<CGNode> getThreadStartSuccessors(CallGraph cg) {
    Iterator<CGNode> iterator = cg.iterator();
    while (iterator.hasNext()) {
      CGNode node = iterator.next();
      String name = node.getMethod().getSignature().toString();
      if (name.contains("Thread.start")) {
        HashSet<CGNode> roots = new HashSet<CGNode>();
        Iterator<CGNode> itr = cg.getSuccNodes(node);
        while (itr.hasNext()) {
          roots.add(itr.next());
        }
        return roots;
      }
    }
    return null;
  }

  
  
  /****************************************************************************/
  
  /**
   * 2. Process the components that have been resolved
   */
  public Map<String, String> processComponents() {
     
    /* Gather locking statistics */
    LockingStats ls = new LockingStats();
    
    Map<String, String> result = new HashMap<String, String>();
    
    for (Entry<String, Component> entry : components.entrySet()) {
      Component component = entry.getValue();
      /*
       * Use reachability results to see if we can actually get to a 
       * wifi/wake lock call from here 
       */            
      Predicate predicate = new Predicate() {      
        @Override
        public boolean evaluate(Object c) {
          CGNode n = (CGNode) c;          
          return (graphReachability.getReachableSet(n).size() > 0);    
        }
      };
      boolean isInteresting =
          CollectionUtils.exists(component.getCallbacks(), predicate);      
      
      CallGraph componentCG = createComponentCG(component);
      component.setCallgraph(componentCG);
      if (Opts.OUTPUT_COMPONENT_CALLGRAPH) {
        component.outputNormalCallGraph();
      }      

      if (Opts.ONLY_ANALYSE_LOCK_REACHING_CALLBACKS && !isInteresting) {
        continue;
      }
      E.log(1, "");
      E.log(1, "Interesting component: " + component.toString());
     
      

      
      /* Create a sensible exploded interprocedural CFG 
       * (TODO: Wrap it up?) */
      component.createSensibleCG();
      
      if (Opts.DO_CS_ANALYSIS)  component.solveCSCFG();
      else                      component.solveCICFG();     
      
      component.cacheColors();
      
      if(Opts.OUTPUT_COLOR_CFG_DOT) {
        component.outputColoredCFGs();
      }
      
      component.outputSolvedICFG();
      
      /* Check the policy - defined for each type of component separately */
      if (Opts.CHECK_LOCKING_POLICY) {        
        Map<String, String> componentResult = component.checkLockingPolicy();
        result.putAll(componentResult);
      }
      /* Register the result - needs to be done after checking the policy */
      ls.registerComponent(component);      
    }
    
    /* Post-process */
    ls.dumpStats();
    
    return result;
    
  }
  

  private static CallGraph createComponentCG(Component component) {
    HashSet<CGNode> rootSet = new HashSet<CGNode>();
    E.log(DEBUG_LEVEL, "Creating callgraph: " + component.getKlass().getName().toString());
    HashSet<CGNode> set = new HashSet<CGNode>();
    for (CGNode node : component.getCallbacks()) {
      set.addAll(getDescendants(originalCG, node));
      rootSet.add(node);
    }
    PartialCallGraph pcg = PartialCallGraph.make(originalCG, rootSet, set);

    E.log(2, "Partial CG #nodes: " + pcg.getNumberOfNodes());
    return pcg;
  }

  /**
   * Get all the callgraph nodes that are reachable from @param node in the @param
   * cg
   * 
   * @return the set of these nodes
   */
  private static Set<CGNode> getDescendants(CallGraph cg, CGNode node) {
    Filter<CGNode> filter = IndiscriminateFilter.<CGNode> singleton();
    GraphReachability<CGNode> graphReachability = new GraphReachability<CGNode>(cg, filter);
    try {
      graphReachability.solve(null);
    } catch (CancelException e) {
      e.printStackTrace();
    }
    OrdinalSet<CGNode> reachableSet = graphReachability.getReachableSet(node);

    return Util.iteratorToSet(reachableSet.iterator());
  }
  

  @SuppressWarnings("unused")
  private List<String> getTargetFunctions(Component component) {
    List<String> list = new ArrayList<String>();
    Iterator<CGNode> iter = component.getCallgraph().iterator();
    while (iter.hasNext()) {
      CGNode node = iter.next();
      if (originalCG.isTargetMethod(node)) {
        list.add(node.getMethod().getDeclaringClass().getName().toString() + "." +
            node.getMethod().getName().toString
            ());        
      }
    }
    return list;
  }
  
}
