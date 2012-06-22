package edu.ucsd.energy.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.eclipse.core.internal.utils.Queue;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.CollectionFilter;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.graph.GraphReachability;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.DFSPathFinder;

import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.apk.AppCallGraph;
import edu.ucsd.energy.component.AbstractComponent;
import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.component.ComponentPrinter;
import edu.ucsd.energy.component.SuperComponent;
import edu.ucsd.energy.contexts.Activity;
import edu.ucsd.energy.contexts.Application;
import edu.ucsd.energy.contexts.AsyncTask;
import edu.ucsd.energy.contexts.BroadcastReceiver;
import edu.ucsd.energy.contexts.Callable;
import edu.ucsd.energy.contexts.ClickListener;
import edu.ucsd.energy.contexts.ContentProvider;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.contexts.DialogInterface;
import edu.ucsd.energy.contexts.Handler;
import edu.ucsd.energy.contexts.Initializer;
import edu.ucsd.energy.contexts.LocationListener;
import edu.ucsd.energy.contexts.OnCompletionListener;
import edu.ucsd.energy.contexts.OnSharedPreferenceChangeListener;
import edu.ucsd.energy.contexts.PhoneStateListener;
import edu.ucsd.energy.contexts.RunnableThread;
import edu.ucsd.energy.contexts.SensorEventListener;
import edu.ucsd.energy.contexts.Service;
import edu.ucsd.energy.contexts.ServiceConnection;
import edu.ucsd.energy.contexts.View;
import edu.ucsd.energy.contexts.WebViewClient;
import edu.ucsd.energy.contexts.Widget;
import edu.ucsd.energy.results.ProcessResults;
import edu.ucsd.energy.results.ViolationReport;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.GraphUtils;

public class ComponentManager {

	private static final int DEBUG = 0;

	private static final int UNRES = 2;

	private  HashMap<TypeName, Context> componentMap;

	private List<SuperComponent> superComponents = new ArrayList<SuperComponent>(); 

	private GlobalManager global;

	private GraphReachability<CGNode> graphReachability;

	private AppCallGraph originalCG;


	public ComponentManager(GlobalManager gm) {
		global = gm;
		originalCG = gm.getAppCallGraph();
		componentMap = new HashMap<TypeName, Context>();
	}

	public Collection<Context> getComponents() {
		return componentMap.values();
	}

	private  void registerComponent(TypeReference declaringClass, Context comp) {
		componentMap.put(declaringClass.getName(), comp);
	}

	public Context getComponent(TypeName c) {
		return componentMap.get(c);
	}

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

	
	// Counts
	int resolvedComponentCount = 0;
	int totalCallBacks = 0;
	int resolvedConstructors = 0;
	int unresolvedCallBacks = 0;
	List<String> unresolvedSB = new ArrayList<String>();
	
	public void resolveComponents() {
		System.out.println("Number of nodes: " + originalCG.getNumberOfNodes());
		Collection<CGNode>  roots = GraphUtil.inferRoots(originalCG);
		Context context;
		Set<CGNode> sRemain = new HashSet<CGNode>(); 
		for (CGNode root : roots) {
			//Ignore the stand-alone target method nodes
			if (originalCG.isTargetMethod(root)) continue;
			
			context = resolveFromClass(root);        
			if (context == null) {
				context = resolveFromInterface(root);
			}
			
			updateConstructorCount(context);
			
			if (context != null) {
				context.registerCallback(root);
				IClass declaringClass = root.getMethod().getDeclaringClass();
				registerComponent(declaringClass.getReference(), context);
			} else {
				sRemain.add(root);
			}
			totalCallBacks++;

		}

		//Second chance to unresolved -- TODO: run fixpoint??
		for(CGNode root : sRemain) {
			IClass klass = root.getMethod().getDeclaringClass();
			TypeName type = klass.getReference().getName();
			Context comp = componentMap.get(type);
			if (comp!= null) {
				//Existing component: just update the callback
				comp.registerCallback(root);
			}
			else {
				// Unresolved
				unresolvedCallBacks++;
				unresolvedSB.add(root.getMethod().getSignature().toString());
				dumpUnresolvedInfo(root);
			}
		}
		
		fixInheritedMethods();
		
		if(! Opts.RUN_IN_PARALLEL) {
			resolutionStats();
		}

	}

	
	/**
	 * A callback from a parent class must be inherited to the child class
	 */
	private void fixInheritedMethods() {
		ClassHierarchy classHierarchy = global.getClassHierarchy();
		LinkedList<IClass> worklist = new LinkedList<IClass>();
		
		IClass rootClass = classHierarchy.getRootClass();
		worklist.add(rootClass);
		if(DEBUG > 0) {
			System.out.println("Worklist add: " + rootClass.toString());
		}
		while (!worklist.isEmpty()) {
			
			IClass c = worklist.remove();
			
			Collection<IClass> subs = classHierarchy.getImmediateSubclasses(c);
			Context parent = componentMap.get(c.getName());
			if (parent != null) {
				if(DEBUG > 0) {
					System.out.println("Doing: " + c.toString());
				}
				for(IClass sub : subs) {
					Context child = componentMap.get(sub.getName());
					if (child != null) {
						if(DEBUG > 0) {
							System.out.println("  child: " + sub.toString());
						}
						//transfer all callback methods that are in the parent and are not
						//in the child component
						for (CallBack cb : parent.getCallbacks()) {
							//If the callback is not overridden by the child class
							if (!child.isCallBack(cb.getSelector())) {
								child.registerCallback(cb.getNode());
								if(DEBUG > 0) {
									System.out.println("Inheriting: " + cb.getName() + " to " + child.toString());
								}
							}							
						}						
					}					
				}
			}
			worklist.addAll(subs);
		}
	}

	private void resolutionStats() {
		System.out.println();
		System.out.println( "==========================================");
		String fst = String.format("%-30s: %d", "Resolved classes", resolvedComponentCount);
		System.out.println(fst);
		fst = String.format("%-30s: %d", "Resolved constructors", resolvedConstructors);
		System.out.println(fst);
		fst = String.format("%-30s: %d (%.2f %%)", "Unresolved callbacks", unresolvedCallBacks,        
				100 * ((double) unresolvedCallBacks / (double) totalCallBacks));
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

	private void updateConstructorCount(Context component) {
		if (component != null) {
			if (component instanceof Initializer) {
				resolvedConstructors++;
			}
		}		
	}

	private void dumpUnresolvedInfo(CGNode root) {
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
	}

	private Map<MethodReference, Set<Context>> method2Component; 

	private void fillMethod2Component() {
		method2Component =  new HashMap<MethodReference, Set<Context>>();
		for (Context comp : getComponents()) {
			for (Iterator<CGNode> it = comp.getCallGraph().iterator(); it.hasNext(); ) {
				CGNode node = it.next();
				MethodReference mr = node.getMethod().getReference();
				Set<Context> sComponents = method2Component.get(mr);
				if (sComponents == null) {
					sComponents = new HashSet<Context>();
				}
				sComponents.add(comp);
				method2Component.put(mr, sComponents);
				E.log(3, mr.toString() + " < " + sComponents.toString());	//Huge output
			}
		}
	}

	public Set<Context> getContainingComponents(MethodReference mr) {
		if (method2Component == null) {
			fillMethod2Component();
		}
		return method2Component.get(mr);
	}


	private  void outputUnresolvedInfo(CGNode root, List<CGNode> path) {
		IClass declaringClass = root.getMethod().getDeclaringClass();
		Collection<IClass> allImplementedInterfaces = declaringClass.getAllImplementedInterfaces();
		E.log(UNRES, "#### UnResolved: " + root.getMethod().getSignature().toString());
		for (IClass c :  originalCG.getAppClassHierarchy().getClassAncestors(declaringClass)) {
			E.log(UNRES, "#### CL: " + c.getName().toString());
		}
		for (IClass c : allImplementedInterfaces) {
			E.log(UNRES, "#### IF: " + c.getName().toString());
		}
		if(path != null) {
			E.log(1, "==== Path to target:");
			for (CGNode n : path) {
				E.log(UNRES, "---> " + n.getMethod().getSignature().toString());
			}
		}
	}

	/**
	 * Find out what type of context this class belongs to. E.g. Activity,
	 * Service, ...
	 */
	private Context resolveFromClass(CGNode root) {

		IMethod method = root.getMethod();
		IClass klass = method.getDeclaringClass();
		TypeName type = klass.getReference().getName();
		String methName = method.getName().toString();
		E.log(2, "Declaring class: " + klass.getName().toString());

		Context context = componentMap.get(type);
		if (context != null) return context;
			// A class can only extend one class so order does not matter
			ArrayList<IClass> classAncestors = originalCG.getAppClassHierarchy().getClassAncestors(klass);
			for (IClass anc : classAncestors) {
				String ancName = anc.getName().toString();
				if (ancName.equals("Landroid/app/Activity")) {
					context = new Activity(global, root);
				}
				if (ancName.equals("Landroid/app/Service")) {
					context = new Service(global, root);
				}
				if (ancName.equals("Landroid/content/ContentProvider")) {
					context = new ContentProvider(global, root);
				}
				if (ancName.equals("Landroid/content/BroadcastReceiver")) {
					context = new BroadcastReceiver(global, root);
				}
				if (ancName.equals("Landroid/os/AsyncTask")) {
					context = new AsyncTask(global, root);
				}
				if (ancName.equals("Landroid/view/View")) {
					context = new View(global, root);
				}
				if (ancName.equals("Landroid/app/Application")) {
					context = new Application(global, root);
				}
				if (ancName.equals("Landroid/os/Handler")) {
					context = new Handler(global, root);
				}
				if (ancName.equals("Landroid/webkit/WebViewClient")) {
					context = new WebViewClient(global, root);
				}
				if (ancName.equals("Landroid/telephony/PhoneStateListener")) {
					context = new PhoneStateListener(global, root);
				}
				if (context != null) break;
			}
			// Important: check if this is a real component before going into the rest.
			if ((context == null) && methName.equals("<init>") || methName.equals("<clinit>")) {
				context = new Initializer(global, root);       
			}
		
		//else {
			// Existing component: just update the callback
			//E.log(2, "OLD:  " + method.getSignature().toString() + " -> " + context.toString() );
			//context.registerCallback(root);
			// update the callgraph node set
			//return context;
		//}
		return context;
	}

	/**
	 * Find out if this class implements a known class. E.g. Runnable
	 * Since a class can implement multiple interfaces we should keep an order of 
	 * in which to decide on which one should be used. 
	 * The only restriction at this moment is Runnable, which should be tested
	 * on first. 
	 * 
	 * @param root
	 * @return
	 */
	private  Context resolveFromInterface(CGNode root) {
		IClass klass = root.getMethod().getDeclaringClass();
		TypeReference reference = klass.getReference();
		Context comp = componentMap.get(reference);
		if (comp != null) {
			//Existing component: just update the callback
			E.log(2, "OLD:  " + root.getMethod().getSignature().toString() + " -> " + comp.toString() );
			comp.registerCallback(root);
			return comp;
		}
		else {
			//Runnable needs to stay in the first place of the check.
			if (implementsInterface(klass, "Ljava/lang/Runnable")) {
				comp = new RunnableThread(global, root);
			}
			else if (implementsInterface(klass, "Ljava/util/concurrent/Callable")) {
				comp = new Callable(global, root);
			}
			else if (implementsInterface(klass,"ClickListener")) {
				comp = new ClickListener(global, root);
			}
			else if (implementsInterface(klass, "Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener")
					|| implementsInterface(klass, "Landroid/preference/Preference$OnPreferenceChangeListener")) {
				comp = new OnSharedPreferenceChangeListener(global, root);
			}
			else if (implementsInterface(klass, "Landroid/location/LocationListener")) {
				comp = new LocationListener(global, root);
			}
			else if (implementsInterface(klass, "Landroid/widget/")) {
				comp = new Widget(global, root);
			}
			else if (implementsInterface(klass, "Landroid/hardware/SensorEventListener")) {
				comp = new SensorEventListener(global, root);
			}
			else if (implementsInterface(klass, "Landroid/content/ServiceConnection")) {
				comp = new ServiceConnection(global, root);
			}
			else if (implementsInterface(klass, "Landroid/content/DialogInterface")) {
				comp = new DialogInterface(global, root);
			}
			else if (implementsInterface(klass, "Landroid/media/MediaPlayer$OnCompletionListener")) {
				comp = new OnCompletionListener(global, root);
			}
			if (comp != null) {
				E.log(2, "PASS: " + root.getMethod().getSignature().toString() + " -> " + comp.toString() );
			}
			else {
				E.log(2, "FAIL: " + root.getMethod().getSignature().toString());
			}      
			return comp;
		}
	}

	public boolean implementsInterface(IClass klass, String string) {
		for (IClass iI : klass.getAllImplementedInterfaces()) {
			String implName = iI.getName().toString();
			if (implName.contains(string)) {
				return true;
			}
		}
		return false;
	}


	/****************************************************************************
	 * 
	 * 						PROCESS COMPONENTS
	 * 
	 ****************************************************************************/
	public void solveComponents() {
		if (DEBUG > 0) {
			System.out.println("\nSolving components...");
		}
		//Build the constraints' graph 
		RunnableManager runnableManager = global.getRunnableManager();
		SparseNumberedGraph<Context> rCG = runnableManager.getConstraintGraph();

		IntentManager intentManager = global.getIntentManager();
		SparseNumberedGraph<Context> iCG = intentManager.getConstraintGraph();

		SparseNumberedGraph<Context> constraintGraph = GraphUtils.merge(rCG,iCG);
		GraphUtils.dumpConstraintGraph(constraintGraph, "all_constraints");

		if (!Opts.ANALYZE_SUPERCOMPONENTS) {
			//Components
			Iterator<Context> bottomUpIterator = GraphUtils.topDownIterator(constraintGraph);
			// Analyze the components based on this graph
			while (bottomUpIterator.hasNext()) {
				Context ctx = bottomUpIterator.next();
				solveComponent(ctx);
			}
		}
		else {
			//Create SuperComponents based on component constraints
			Iterator<Set<Context>> scItr = GraphUtils.connectedComponentIterator(constraintGraph);
			//SuperComponents: the sequence does not matter
			while(scItr.hasNext()) {
				Set<Context> next = scItr.next();	//The set of components that construct this supercomponent
				SuperComponent superComponent = new SuperComponent(global, next);
				if (DEBUG > 0) {
					superComponent.dumpContainingComponents();
				}
				ComponentPrinter<SuperComponent> printer = new ComponentPrinter<SuperComponent>(superComponent);
				printer.outputSupergraph();
				superComponents.add(superComponent);			
				solveComponent(superComponent);
			}
		}
	}


	private <T extends AbstractComponent> void solveComponent(T component) {

		if (DEBUG > 0) {
			System.out.println("Solving: " + component.toString());
		}

		ComponentPrinter<T> componentPrinter = new ComponentPrinter<T>(component);
		if (Opts.OUTPUT_COMPONENT_CALLGRAPH) {			
			componentPrinter.outputNormalCallGraph();
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
			if (component instanceof Context) {
				Context context = (Context) component;
				if (!CollectionUtils.exists(context.getCallbacks(), predicate))
					return;
			}
		}
		component.solve();
		if(Opts.OUTPUT_COLOR_CFG_DOT) {
			componentPrinter.outputColoredCFGs();
		}      
		if(Opts.OUTPUT_COLORED_SUPERGRAPHS) {
			componentPrinter.outputColoredSupergraph();
		}
	}


	public ViolationReport getAnalysisResults() {
		return new ProcessResults(this).processExitStates();
	}

	//SupperComponents need to have been resolved first
	public List<SuperComponent> getSuperComponents() {
		return superComponents;
	}

}
