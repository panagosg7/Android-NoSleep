package edu.ucsd.energy.analysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.CollectionFilter;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Acyclic;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphReachability;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.DFSPathFinder;

import edu.ucsd.energy.analysis.SpecialConditions.SpecialCondition;
import edu.ucsd.energy.components.Activity;
import edu.ucsd.energy.components.Application;
import edu.ucsd.energy.components.AsyncTask;
import edu.ucsd.energy.components.BaseAdapter;
import edu.ucsd.energy.components.BroadcastReceiver;
import edu.ucsd.energy.components.Callable;
import edu.ucsd.energy.components.ClickListener;
import edu.ucsd.energy.components.Component;
import edu.ucsd.energy.components.CompoundButton;
import edu.ucsd.energy.components.ContentProvider;
import edu.ucsd.energy.components.DialogInterface;
import edu.ucsd.energy.components.Handler;
import edu.ucsd.energy.components.Initializer;
import edu.ucsd.energy.components.LocationListener;
import edu.ucsd.energy.components.OnSharedPreferenceChangeListener;
import edu.ucsd.energy.components.PhoneStateListener;
import edu.ucsd.energy.components.RunnableThread;
import edu.ucsd.energy.components.SensorEventListener;
import edu.ucsd.energy.components.Service;
import edu.ucsd.energy.components.ServiceConnection;
import edu.ucsd.energy.components.TextView;
import edu.ucsd.energy.components.View;
import edu.ucsd.energy.components.WebViewClient;
import edu.ucsd.energy.interproc.SensibleExplodedInterproceduralCFG;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.GraphBottomUp;
import edu.ucsd.energy.util.SSAProgramPoint;
import edu.ucsd.energy.viz.GraphDotUtil;

@SuppressWarnings("deprecation")
public class ComponentManager {

  private  int DEBUG_LEVEL = 2;

  private  HashMap<TypeReference, Component> componentMap;

  private  AppCallGraph originalCG;

  private GraphReachability<CGNode> graphReachability;

  private int unresolvedInterestingCallBacks;

  private ArrayList<Result> results;

  private ProcessResults resultAnalysis;
  
  public HashMap<TypeReference, Component> getComponents() {
    return componentMap;
  }

  public ComponentManager(AppCallGraph cg) {
    originalCG = cg;
    componentMap = new HashMap<TypeReference, Component>();
  }
  private  void registerComponent(TypeReference declaringClass, Component comp) {
    componentMap.put(declaringClass, comp);
  }
  
  public Component getComponent(TypeReference c) {
	  return componentMap.get(c);
  }
  
  public Component getComponent(CGNode n) {
	  return componentMap.get(n.getMethod().getDeclaringClass().getReference());
  }
  
  public int getNUnresInterestingCBs() {
	  return unresolvedInterestingCallBacks;
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
  

  
  /****************************************************************************
   * 
   * 						RESOLVE COMPONENTS
   * 
   ****************************************************************************/
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
    unresolvedInterestingCallBacks = 0;
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

    
    if(! Opts.RUN_IN_PARALLEL) {
	    System.out.println();
	    System.out.println( "==========================================");
	    String fst = String.format("%-30s: %d", "Resolved classes", resolvedComponentCount);
	    System.out.println(fst);
	    fst = String.format("%-30s: %d", "Resolved implementors", resolvedImplementorCount);
	    System.out.println(fst);
	    fst = String.format("%-30s: %d", "Resolved constructors", resolvedConstructors);
	    System.out.println(fst);
	    fst = String.format("%-30s: %d (%.2f %%)", "Unresolved callbacks", unresolvedCallBacks,        
	        100 * ((double) unresolvedCallBacks / (double) totalCallBacks));
	    System.out.println(fst);
	    fst = String.format("%-30s: %d (%.2f %%)", "Interesting Unresolved cbs", unresolvedInterestingCallBacks, 
	        100 * ((double) unresolvedInterestingCallBacks / (double) totalCallBacks));
	    System.out.println(fst);
	    System.out.println("------------------------------------------");
	    
	    E.log(2, "Unresolved");
	    E.log(2, unresolvedSB);
	    E.log(2, "----------------------------------------------------");
	    
	    fst = String.format("%-30s: %d", "Total callbacks", totalCallBacks);
	    System.out.println(fst);
	    fst = String.format("%-30s: %d", "Resolved components", componentMap.size());
	    System.out.println(fst);
	    System.out.println("==========================================\n");
    }
    
  }

  private  void outputUnresolvedInfo(CGNode root, List<CGNode> path) {
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
  private  Component resolveComponent(CGNode root) {

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
        if (ancName.equals("Landroid/webkit/WebViewClient")) {
            comp = new WebViewClient(originalCG, klass, root);
        }
        if (ancName.equals("Landroid/telephony/PhoneStateListener")) {
            comp = new PhoneStateListener(originalCG, klass, root);
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
  private  ArrayList<IClass> getClassAncestors(IClass klass) {
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
  private  Component resolveKnownImplementors(CGNode root) {
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
        if (implName.equals("Ljava/util/concurrent/Callable")) {
            comp = new Callable(originalCG, klass, root);
          }
        if (implName.endsWith("ClickListener")) {
        	comp = new ClickListener(originalCG, klass, root);
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
        if (implName.startsWith("Landroid/content/ServiceConnection")) {
        	comp = new ServiceConnection(originalCG, klass, root);
        }
        if (implName.startsWith("Landroid/content/DialogInterface")) {
        	comp = new DialogInterface(originalCG, klass, root);
        }
        if (implName.startsWith("Landroid/widget/TextView")) {
        	comp = new TextView(originalCG, klass, root);
        }
        
        
        
        if (comp != null) break;
      }
      if (comp != null) {
    	  E.log(2, "PASS: " + root.getMethod().getSignature().toString() + " -> " + comp.toString() );
      }
      else {
    	  E.log(DEBUG_LEVEL, "FAIL: " + root.getMethod().getSignature().toString());
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
  private  HashSet<CGNode> getThreadStartSuccessors(CallGraph cg) {
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


  /****************************************************************************
   * 
   * 						PROCESS COMPONENTS
   * 
   ****************************************************************************/  
  /**
   * 2. Process the components that have been resolved
   */
  public void solveComponents() {
  
	//getGlobalIntents();

	//Initialize results
	resultAnalysis = new ProcessResults(this);
	  
    /* Build the constraints graph 
     * (threads should be analyzed before their parents)
     */
    Collection<Component> components = componentMap.values();    
    Graph<Component> constraintGraph = constraintGraph(components);
    Iterator<Component> bottomUpIterator = GraphBottomUp.bottomUpIterator(constraintGraph);
    
    /* And analyze the components based on this graph in bottom up order */
    while (bottomUpIterator.hasNext()) {
    	Component component = bottomUpIterator.next();
    	//E.log(1, component.toString());
        /* assert that dependencies are met */
    	Collection<Component> compDep = component.getThreadDependencies();
    	
    	com.ibm.wala.util.Predicate<Component> p =
    			new com.ibm.wala.util.Predicate<Component>() {
    		@Override
    		public boolean test(Component c) {
    			return c.isSolved;
    			}
    		};
		Assertions.productionAssertion(com.ibm.wala.util.collections.Util.forAll(compDep, p));
	
		if (Opts.ENFORCE_SPECIAL_CONDITIONS) {
		 /* gather special conditions */
			gatherSpecialConditions(component);
		}
	  
		if (Opts.OUTPUT_COMPONENT_CALLGRAPH) {
			component.outputNormalCallGraph();
		}

		if (Opts.ONLY_ANALYSE_LOCK_REACHING_CALLBACKS) { 
	        /* Use reachability results to see if we can actually get to a 
	         * wifi/wake lock call from here */
	        Predicate predicate = new Predicate() {      
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
      
	    //DEBUG
	      /*
	      
	      if (component.toString().equals("Runnable thread: Lcom/imo/android/imoim/ImoService$ConnectionThread")) {
	    	  HashSet<CallBack> callbacks = component.getCallbacks();
	    	  for(CallBack cb : callbacks) {
	    		  E.log(1, "\t" + cb.toString());
	    	  }
	    	  for (Iterator<CGNode> it = component.getCallgraph().iterator(); it.hasNext(); ) {
	    		CGNode next = it.next();
	    		E.log(1, "\tNode: " + next.getMethod().getSignature().toString());	    		  
	    	  } 
	    	  
	      }
	      */
		//DEBUG	      
	            
	    if (Opts.DO_CS_ANALYSIS) {
	    	component.solveCSCFG();
	    }
	    else {
	    	component.solveCICFG();      
	    }
	      
	    component.cacheStates();
	     
	    if(Opts.OUTPUT_COLOR_CFG_DOT) {
	    	component.outputColoredCFGs();
	    }      

	    if(Opts.OUTPUT_SOLVED_EICFG) {
	    	component.outputSolvedICFG();
	    }
    }    
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
  
  public AppCallGraph getCG() {
	  return originalCG;
  }

  
  /****************************************************************************
   * 
   * 					SPECIAL CONDITIONS
   * 
   ****************************************************************************/
  private SpecialConditions specialConditions = null;
  
  private HashMap<SSAProgramPoint,SpecialCondition> getGlobalSpecialConditions() {
	if (specialConditions == null) {
		specialConditions = new SpecialConditions(this);
	}
	return specialConditions.getSpecialConditions();
  }

  
  /****************************************************************************
   * 
   * 						INTENT STUFF
   * 
   ****************************************************************************/
  
  private IntentInvestigation intentCreation = null;

  
  //TODO: fix return statement
  private void getGlobalIntents() {
	if (intentCreation == null) {
		intentCreation = new IntentInvestigation(this);
	}
	intentCreation.prepare();
	
	//intentCreation.printIntents();
	
  }

  
  
  /****************************************************************************
   * 
   * 					THREAD INVOCATION STUFF
   * 
   ****************************************************************************/
  
  private RunnableInvestigation threadCreation = null;

  private HashMap<SSAProgramPoint,Component> getGlobalThreadInvocations() {
	if (threadCreation == null) {
		threadCreation = new RunnableInvestigation(this);
	}
	return threadCreation.getThreadInvocations();
  }
  
  private HashMap<Component, HashMap<BasicBlockInContext<IExplodedBasicBlock>, 
  	Component>> component2ThreadInvocations = null;
  
  private HashMap<Component, HashMap<BasicBlockInContext<IExplodedBasicBlock>, 
	SpecialCondition>> component2SpecConditions = null;

  
  /**
   * This should gather the thread invocation that are specific to a
   * particular component.
   * @param c
   * @return
   */
  public HashMap<BasicBlockInContext<IExplodedBasicBlock>, Component> getThreadInvocations(Component c) {	  	 	  
	  if (component2ThreadInvocations == null) {		  
		  component2ThreadInvocations = new HashMap<Component, 
				  		HashMap<BasicBlockInContext<IExplodedBasicBlock>,Component>>();
	  }
	  HashMap<BasicBlockInContext<IExplodedBasicBlock>, Component> compInv = component2ThreadInvocations.get(c);
	   
	  if (compInv == null) {
		  compInv = new HashMap<BasicBlockInContext<IExplodedBasicBlock>, Component>();
		  SensibleExplodedInterproceduralCFG icfg = c.getICFG();		  
		  //Iterate over the thread invocations
		  HashMap<SSAProgramPoint, Component> globalThrInv = getGlobalThreadInvocations();	  		  
		  //Get all the instructions ** from the exploded ** graph
		  for(Iterator<BasicBlockInContext<IExplodedBasicBlock>> it = icfg.iterator();
				  it.hasNext(); ) {
			
			  BasicBlockInContext<IExplodedBasicBlock> bbic = it.next();			  
			  IExplodedBasicBlock ebb = bbic.getDelegate();			  
			  CGNode node = icfg.getCGNode(bbic);			  
			  SSAInstruction instruction = ebb.getInstruction();
			  if (instruction instanceof SSAInvokeInstruction) {				  
				  SSAInvokeInstruction inv = (SSAInvokeInstruction) instruction;				  
				  SSAProgramPoint ssapp = new SSAProgramPoint(node, inv);				  				  
				  Component callee = globalThrInv.get(ssapp);
				  if (callee != null) {
					  compInv.put(bbic, callee);
					  E.log(2, "Found: " + ebb.toString());
				  }			
			  }
		  }		  
		  c.setThreadInvocations(compInv);		  
		  component2ThreadInvocations.put(c, compInv);
	  }
	  Assertions.productionAssertion(compInv != null);
	  return compInv;
  }
  
  
  /**
   * This should gather the thread invocation that are specific to a
   * particular component.
   * @param c
   * @return
   */
  public void gatherSpecialConditions(Component c) {	  	 	  
	  if (component2SpecConditions == null) {		  
		  component2SpecConditions = 
				  new HashMap<Component, HashMap<BasicBlockInContext<IExplodedBasicBlock>,SpecialCondition>>();
	  }	  
	  HashMap<BasicBlockInContext<IExplodedBasicBlock>, SpecialCondition> compCond = component2SpecConditions.get(c);
	   
	  if (compCond == null) {
		  compCond = new HashMap<BasicBlockInContext<IExplodedBasicBlock>, SpecialCondition>();
		  SensibleExplodedInterproceduralCFG icfg = c.getICFG();
		  //Iterate over the thread invocations
		  HashMap<SSAProgramPoint, SpecialCondition> globalSpecCond = getGlobalSpecialConditions();	
		  //E.log(1, "checking special cond: " + globalSpecCond);
		  //Check all the instructions ** from the exploded ** graph
		  for(Iterator<BasicBlockInContext<IExplodedBasicBlock>> it = icfg.iterator(); it.hasNext(); ) {			
			  BasicBlockInContext<IExplodedBasicBlock> bbic = it.next();			  
			  IExplodedBasicBlock ebb = bbic.getDelegate();
			  CGNode node = icfg.getCGNode(bbic);			  
			  SSAInstruction instruction = ebb.getInstruction();
			  if((node!= null) && (instruction != null)) {
				  SSAProgramPoint ssapp = new SSAProgramPoint(node, instruction);				  
				  SpecialCondition cond = globalSpecCond.get(ssapp);
				  if (cond != null) {
					  compCond.put(bbic, cond);
					  E.log(2, "Found: " + ebb.toString());
				  }			
			  }
		  }		  
		  c.setSpecialConditions(compCond);
		  component2SpecConditions.put(c, compCond);
	  }	  
  }
  
  
  
  public Collection<Component> getThreadConstraints(Component c) {
	HashMap<BasicBlockInContext<IExplodedBasicBlock>, Component> ti = getThreadInvocations(c);
	return ti.values();	  
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
		  Assertions.productionAssertion(threadDependencies != null);
		  
		  for (Component dst : threadDependencies) {
			  if ((src != null) && (dst != null)) {
				E.log(2, src + " --> " + dst);
				g.addEdge(src,dst);
			}
		  }
	  }
	
	  /* Assert that there are no cycles in the component constraint graph. */
	  com.ibm.wala.util.Predicate<Component> p = 
				new com.ibm.wala.util.Predicate<Component>() {		
			@Override
			public boolean test(Component c) {
				return Acyclic.isAcyclic(g, c);
			}
	  };
	  E.log(1, "Building constraint graph... " + GraphUtil.countEdges(g) + " edge(s).");
	  
	  boolean acyclic = com.ibm.wala.util.collections.Util.forAll(cc, p);
	  dumpConstraintGraph(g);	  
	  if (!acyclic) {
		  Assertions.productionAssertion(acyclic, 
		    "Cannot handle circular dependencies in thread calls. Dumping thread dependency graph");  
	  }
			
	  /* TODO: circular dependencies are just dropped */ 
	  return g;
  }

  private void dumpConstraintGraph(SparseNumberedGraph<Component> g) {
	try {
		E.log(1, "Dumping constraint graph... ");
		Properties p = null;
		p = WalaExamplesProperties.loadProperties();
		p.putAll(WalaProperties.loadProperties());
		String dotFile = edu.ucsd.energy.util.Util.getResultDirectory()
				+ File.separatorChar + "thread_dependencies.dot";
		String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
		GraphDotUtil.dotify(g, null, dotFile, null, dotExe);
		return;
	} catch (WalaException e) {
		e.printStackTrace();
		return;
	}
  }	

	public ArrayList<Result> getAnalysisResults() {
		return resultAnalysis.getResult();
	}
	
	public void processExitStates() {
		resultAnalysis.processExitStates();		
	}	  

}
