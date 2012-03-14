package energy.components;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.CollectionFilter;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.graph.Acyclic;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphReachability;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.BFSIterator;
import com.ibm.wala.util.graph.traverse.DFSPathFinder;

import energy.analysis.ApplicationCallGraph;
import energy.analysis.Opts;
import energy.analysis.ThreadCreation;
import energy.util.E;
import energy.util.GraphBottomUp;
import energy.util.LockingStats;
import energy.util.SSAProgramPoint;

@SuppressWarnings("deprecation")
public class ComponentManager {

  private static int DEBUG_LEVEL = 2;

  private static HashMap<TypeReference, Component> componentMap;

  private static ApplicationCallGraph originalCG;

  private GraphReachability<CGNode> graphReachability;

  public HashMap<TypeReference, Component> getComponents() {
    return componentMap;
  }

  public ComponentManager(ApplicationCallGraph cg) {
    originalCG = cg;
    componentMap = new HashMap<TypeReference, Component>();
  }
  private static void registerComponent(TypeReference declaringClass, Component comp) {
    componentMap.put(declaringClass, comp);
  }
  
  public Component getComponent(TypeReference c) {
	  return componentMap.get(c);
  }
  
  public Component getComponent(CGNode n) {
	  return componentMap.get(n.getMethod().getDeclaringClass().getReference());
  }
  

  /**
   * 1. Reachability results are going to be useful later
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
               
        /* Get known components, e.g. Activity */
        Component component = resolveComponent(root);        
        IClass declaringClass = root.getMethod().getDeclaringClass();        
        
        if (component != null) {
          E.log(DEBUG_LEVEL, component.toString());
          registerComponent(declaringClass.getReference(), component);

          if (component instanceof Initializer) {
            resolvedConstructors++;
          } else {
            resolvedComponentCount++;
          }
        } else {
          /* Get known implementors. e.g. Runnable */
          Component knownImpl = resolveKnownImplementors(root);

          if (knownImpl != null) {
            registerComponent(declaringClass.getReference(), knownImpl);
            E.log(DEBUG_LEVEL, knownImpl.toString());
            resolvedImplementorCount++;
          } else {
            /* Unresolved */
            unresolvedCallBacks++;
            unresolvedSB.add(root.getMethod().getSignature().toString());
            /* We can check now if we should actually care about this callback. */
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
    fst = String.format("%-30s: %d", "Resolved components", componentMap.size());
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
  private static Component resolveComponent(CGNode root) {

    IClass klass = root.getMethod().getDeclaringClass();
    TypeReference reference = klass.getReference();
    String methName = root.getMethod().getName().toString();
    E.log(2, "Declaring class: " + klass.getName().toString());

    Component comp = componentMap.get(reference);
    if (comp == null) {
      /* We haven't met this component so far */
      // find the super-classes until object
      ArrayList<IClass> classAncestors = getClassAncestors(klass);
      for (IClass anc : classAncestors) {
        String ancName = anc.getName().toString();
        if (ancName.equals("Landroid/app/Activity")) {
          comp = new Activity(originalCG, klass, root);
        }
        if (ancName.equals("Landroid/app/Service")) {
          comp = new Service(originalCG, klass, root);
        }
        if (ancName.equals("Landroid/content/ContentProvider")) {
          comp = new ContentProvider(originalCG, klass, root);
        }
        if (ancName.equals("Landroid/content/BroadcastReceiver")) {
          comp = new BroadcastReceiver(originalCG, klass, root);
        }
        if (ancName.equals("Landroid/os/AsyncTask")) {
          comp = new AsyncTask(originalCG, klass, root);
        }
        if (ancName.equals("Landroid/widget/BaseAdapter")) {
          comp = new BaseAdapter(originalCG, klass, root);
        }
        if (ancName.equals("Landroid/view/View")) {
          comp = new View(originalCG, klass, root);
        }
        if (ancName.equals("Landroid/app/Application")) {
            comp = new Application(originalCG, klass, root);
        }
        if (ancName.equals("Landroid/os/Handler")) {
            comp = new Handler(originalCG, klass, root);
        }
        if (comp != null) break;
      }

      /* Important: check if this is a real component before going into the
       * rest. */
      if (comp != null) {
        E.log(2, "PASS: " + root.getMethod().getSignature().toString() + " -> " + comp.toString());
        return comp;
      }

      /* TODO Check to see if this is a constructor. 
       * Has to be <init> or somethind...
       * */
      /*if (methName.equals("<init>") || methName.equals("<clinit>")) {
        comp = new Initializer(klass, root);       
      }*/
      
      return comp;
    } else {
      /* Existing component: just update the callback */
      E.log(2, "OLD:  " + root.getMethod().getSignature().toString() + " -> " + comp.toString() );
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
    TypeReference reference = klass.getReference();
    String methName = root.getMethod().getName().toString();

    Component comp = componentMap.get(reference);
    if (comp == null) {
      Collection<IClass> allImplementedInterfaces = klass.getAllImplementedInterfaces();
      for (IClass iI : allImplementedInterfaces) {
        String implName = iI.getName().toString();
        if (implName.equals("Ljava/lang/Runnable")) {
          comp = new RunnableThread(originalCG, klass, root);
        }
        if (implName.equals("Landroid/widget/AdapterView$OnItemClickListener")) {
          comp = new AdapterViewOnItemClickListener(originalCG, klass, root);
        }
        if (implName.equals("Landroid/view/View$OnClickListener")) {
        	comp = new ViewOnClickListener(originalCG, klass, root);
        }
        if (implName.equals("Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener")
            || implName.equals("Landroid/preference/Preference$OnPreferenceChangeListener")) {
        	comp = new OnSharedPreferenceChangeListener(originalCG, klass, root);
        }
        if (implName.equals("Landroid/location/LocationListener")) {
        	comp = new LocationListener(originalCG, klass, root);
        }
        if (implName.startsWith("Landroid/widget/CompoundButton")) {
        	comp = new CompoundButton(originalCG, klass, root);
        }
        if (implName.startsWith("Landroid/hardware/SensorEventListener")) {
        	comp = new SensorEventListener(originalCG, klass, root);
        }
        if (comp != null) break;
      }
      if (comp != null) {
    	  E.log(2, "PASS: " + root.getMethod().getSignature().toString() + " -> " + comp.toString() );
      }
      else {
    	  E.log(1, "FAIL: " + root.getMethod().getSignature().toString());
      }      
      return comp;
    } else {
      /**
       * Existing component: just update the callback
       */
      E.log(2, "OLD:  " + root.getMethod().getSignature().toString() + " -> " + comp.toString() );
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
  public void processComponents() {
     
    /* Gather locking statistics */
    LockingStats ls = new LockingStats();
    
    /* Build the constraints graph 
     * (threads should be analyzed before their parents)*/
    Collection<Component> components = componentMap.values();    
    Graph<Component> constraintGraph = constraintGraph(components);
    BFSIterator<Component> bottomUpIterator = GraphBottomUp.bottomUpIterator(constraintGraph);
    
    /* And analyze the components based on this graph in bottom up order */
    while (bottomUpIterator.hasNext()) {
    	Component component = bottomUpIterator.next();
    	
        /* assert that dependencies are met */
    	Collection<Component> compDep = 
      		  component.getThreadDependencies();
    	com.ibm.wala.util.Predicate<Component> p =
    	  new com.ibm.wala.util.Predicate<Component>() {    		
			@Override
			public boolean test(Component c) {
				return c.isSolved;
			}
		  };
	  assert com.ibm.wala.util.collections.Util.forAll(compDep, p);
      
      if (Opts.OUTPUT_COMPONENT_CALLGRAPH) {
        component.outputNormalCallGraph();
      }

      if (Opts.ONLY_ANALYSE_LOCK_REACHING_CALLBACKS) { 
          /* Use reachability results to see if we can actually get to a 
           * wifi/wake lock call from here */
          Predicate predicate = new Predicate() {      
            @Override
            public boolean evaluate(Object c) {
              CGNode n = (CGNode) c;          
              return (graphReachability.getReachableSet(n).size() > 0);    
            }
          };
          boolean isInteresting =
              CollectionUtils.exists(component.getCallbacks(), predicate);
          component.setInteresting(isInteresting);   	  
    	  if (!isInteresting) {
    		  continue;
    	  }    	 
      }       
      E.log(1, component.toString());
      
      if(Opts.OUTPUT_CFG_DOT) {
    	  component.outputCFGs();
      }
            
      if (Opts.DO_CS_ANALYSIS) {
    	  component.solveCSCFG();
      }
      else {
    	  component.solveCICFG();      
      }
      
      component.cacheStates();
      
      if(Opts.OUTPUT_COLOR_CFG_DOT) {
        component.outputColoredCFGs();
        //component.outputSolvedICFG();
      }
      
      
      
      /* Check the policy - defined for each type of component separately */
      if (Opts.CHECK_LOCKING_POLICY) {        
        component.checkLockingPolicy();
      }
      /* Register the result - needs to be done after checking the policy */
      ls.registerComponent(component);      
    }
    
    /* Post-process */
    ls.dumpStats();
    
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
  
  public ApplicationCallGraph getCG() {
	  return originalCG;
  }
  
  /****************************************************************************/

  /****************************************************************************
   * Gather thread invocation info
   */
  private ThreadCreation threadCreation = null;
  
  private HashMap<SSAProgramPoint,Component> getGlobalThreadInvocations() {
	if (threadCreation == null) {
		threadCreation = new ThreadCreation(this);
	}
	return threadCreation.getThreadInvocations();
  }
  
  private static HashMap<Component,HashMap<SSAProgramPoint,Component>> component2ThreadInvocations = 
		  new HashMap<Component, HashMap<SSAProgramPoint,Component>>();
  
  public HashMap<SSAProgramPoint,Component> getThreadInvocations(Component c) {
	  HashMap<SSAProgramPoint, Component> compInv = component2ThreadInvocations.get(c);
	  if (compInv == null) {
		compInv = new HashMap<SSAProgramPoint, Component>();
		HashMap<SSAProgramPoint, Component> threadInvocations = getGlobalThreadInvocations();
		for(Entry<SSAProgramPoint, Component> ti : threadInvocations.entrySet()) {
			CallGraph cg = c.getCallgraph();
			if (cg == null) {
			/* Create and dump the component's callgraph */
			  c.createComponentCG();
			  cg = c.getCallgraph();
			}			
			if (cg.containsNode(ti.getKey().getCGNode())) {
			  compInv.put(ti.getKey(), ti.getValue());
			}
		}
		component2ThreadInvocations.put(c, compInv);
		c.setThreadInvocations(threadInvocations);
	  }
	  return compInv;
  }
  
  public Collection<Component> getThreadConstraints(Component c) {
	  return getThreadInvocations(c).values();	  
  }

  /**
   * The graph of constraints based on thread creation
   * @param cc
   * @return
   */
  public Graph<Component> constraintGraph(Collection<Component> cc) {
	  final SparseNumberedGraph<Component> g = new SparseNumberedGraph<Component>(1);			
	  for(Component c : cc) {
			g.addNode(c);
			E.log(2, "Adding: " + g.getNumber(c) + " : " + c ); 
	  }
	  for (Component src : cc) {
		  Collection<Component> threadDependencies = getThreadConstraints(src);
		  for (Component dst : threadDependencies) {
			  if ((src != null) && (dst != null)) {
				E.log(2, "adding: " + src + " --> " + dst);
				g.addEdge(src,dst);
			}
		  }
	  }
	
	/* For now assert that there are no cycles in the component 
	 * constraint graph. */
	com.ibm.wala.util.Predicate<Component> p = 
			new com.ibm.wala.util.Predicate<Component>() {		
		@Override
		public boolean test(Component c) {
			return Acyclic.isAcyclic(g, c);				
		}
	};
	assert com.ibm.wala.util.collections.Util.forAll(cc, p);
	/* TODO: not sure how to deal with circular dependencies */ 
	//System.out.println("\n\nComponent dependence graph\n");
	//System.out.println(g.toString());
	return g;
  }
	  
	  
  
  /****************************************************************************
   * TODO: Maybe at some point create a merge function for components
   */
  
}
