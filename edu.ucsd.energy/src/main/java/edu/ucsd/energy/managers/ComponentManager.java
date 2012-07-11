package edu.ucsd.energy.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.CollectionFilter;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.HashSetMultiMap;
import com.ibm.wala.util.collections.Iterator2Collection;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.GraphReachability;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.DFSPathFinder;

import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.apk.AppCallGraph;
import edu.ucsd.energy.apk.ClassHierarchyUtils;
import edu.ucsd.energy.component.AbstractContext;
import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.component.ComponentPrinter;
import edu.ucsd.energy.component.SuperComponent;
import edu.ucsd.energy.contexts.Activity;
import edu.ucsd.energy.contexts.Application;
import edu.ucsd.energy.contexts.AsyncTask;
import edu.ucsd.energy.contexts.BroadcastReceiver;
import edu.ucsd.energy.contexts.Callable;
import edu.ucsd.energy.contexts.ContentProvider;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.contexts.IntentService;
import edu.ucsd.energy.contexts.RunnableThread;
import edu.ucsd.energy.contexts.Service;
import edu.ucsd.energy.contexts.UnresolvedContext;
import edu.ucsd.energy.results.ProcessResults;
import edu.ucsd.energy.results.ViolationReport;
import edu.ucsd.energy.util.GraphUtils;
import edu.ucsd.energy.util.Log;


/**
 * This manager resolves classes of the app class hierearchy 
 * to Android Contexts/Components.
 * 
 * Classes that cannot be resolved are NOT necessarily indeed 
 * Android sensible Contexts, as they might be just classes that 
 * have no particular meaning for Android.
 * 
 *  TODO: So we need to find a way to determine if there are classes 
 *  that cannot be resolved, but still are meaningful for Android.
 *  If these are not resolved, this means that there are going to 
 *  be callbacks that are never explored. *  
 * 
 * @author pvekris
 *
 */
public class ComponentManager {

	private static final int SOLVE_DEBUG = 0;
	static final int RESOLVE_DEBUG = 0;

	private  HashMap<TypeName, Context> componentMap;

	private List<SuperComponent> superComponents = new ArrayList<SuperComponent>(); 

	private GlobalManager global;

	private GraphReachability<CGNode> graphReachability = null;

	private AppCallGraph originalCG;


	public ComponentManager() {
		global = GlobalManager.get();
		originalCG = global.getAppCallGraph();
		componentMap = new HashMap<TypeName, Context>();
	}

	public Collection<Context> getComponents() {
		return componentMap.values();
	}

	private void registerComponent(TypeReference declaringClass, Context comp) {
		componentMap.put(declaringClass.getName(), comp);
	}

	public Context getComponent(TypeName c) {
		return componentMap.get(c);
	}

	public GraphReachability<CGNode> getGraphReachability() {
		if (graphReachability == null) {
			Filter<CGNode> filter = new CollectionFilter<CGNode>(originalCG.getTargetCGNodeHash().values());
			try { 
				graphReachability = new GraphReachability<CGNode>(originalCG, filter);
				graphReachability.solve(null);
			} catch (CancelException e) {
				e.printStackTrace();
			}
		}
		return graphReachability;
	}


	/****************************************************************************
	 * 
	 * 						RESOLVE COMPONENTS
	 * 
	 ****************************************************************************/

	// Counts
	int resolvedClasses = 0;
	int unresolvedClasses = 0;
	int totalClassesChecked = 0;
	int resolvedConstructors = 0;

	int danglingMethods = 0;

	public void resolveComponents() {

		ClassHierarchy ch = global.getClassHierarchy();

		//Keep the nodes that belong to some context
		Set<CGNode> sNodes = new HashSet<CGNode>();
		int counter = 0;
		for(IClass c : ch) {

			//Avoid primordial (system) classes
			if(c.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)) {
				continue;
			}

			TypeName type = c.getName();
			//This is probably going to fail
			Context context = componentMap.get(type);
			if (context == null) {
				// A class can only extend one class so order does not matter
				ArrayList<IClass> classAncestors = ClassHierarchyUtils.getClassAncestors(c);
				for (IClass anc : classAncestors) {
					String ancName = anc.getName().toString();
					
					if (ancName.equals("Landroid/app/Activity")) {
						context = new Activity(c);
						break;
					}
					if (ancName.equals("Landroid/app/IntentService")) {
						context = new IntentService(c);
						break;
					}					
					if (ancName.equals("Landroid/app/Service")) {
						context = new Service(c);
						break;
					}
					if (ancName.equals("Landroid/content/ContentProvider")) {
						context = new ContentProvider(c);
						break;
					}
					if (ancName.equals("Landroid/content/BroadcastReceiver")) {
						context = new BroadcastReceiver(c);
						break;
					}
					if (ancName.equals("Landroid/os/AsyncTask")) {
						context = new AsyncTask(c);
						break;
					}
					if (ancName.equals("Landroid/app/Application")) {
						context = new Application(c);
						break;
					}
					/*
					//It does not offer us anything to treat these cases separately 
					//we can just unify them in unresolved context
					if (ancName.equals("Landroid/view/View")) {
						context = new View(c);
						break;
					}
					if (ancName.equals("Landroid/os/Handler")) {
						context = new Handler(c);
						break;
					}
					if (ancName.equals("Landroid/webkit/WebViewClient")) {
						context = new WebViewClient(c);
						break;
					}
					if (ancName.equals("Landroid/telephony/PhoneStateListener")) {
						context = new PhoneStateListener(c);
						break;
					}
					if (ancName.equals("Landroid/database/sqlite/SQLiteOpenHelper")) {
						context = new SQLiteOpenHelper(c);
						break;
					}
					*/
				}
			}

			if (context == null) {
				//Since a class can implement multiple interfaces we should keep an order of 
				//in which to decide on which one should be used. The only restriction at 
				//this moment is Runnable, which should be tested on first. 
				if (implementsInterface(c, "Ljava/lang/Runnable")) {
					context = new RunnableThread(c);
				}
				else if (implementsInterface(c, "Ljava/util/concurrent/Callable")) {
					context = new Callable(c);
				}
				/*
				else if (implementsInterface(c,"ClickListener")) {
					context = new ClickListener(c);
				}
				else if (implementsInterface(c, "Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener")
						|| implementsInterface(c, "Landroid/preference/Preference$OnPreferenceChangeListener")) {
					context = new OnSharedPreferenceChangeListener(c);
				}
				else if (implementsInterface(c, "Landroid/location/LocationListener")) {
					context = new LocationListener(c);
				}
				else if (implementsInterface(c, "Landroid/widget/")) {
					context = new Widget(c);
				}
				else if (implementsInterface(c, "Landroid/hardware/SensorEventListener")) {
					context = new SensorEventListener(c);
				}
				else if (implementsInterface(c, "Landroid/content/ServiceConnection")) {
					context = new ServiceConnection(c);
				}
				else if (implementsInterface(c, "Landroid/content/DialogInterface")) {
					context = new DialogInterface(c);
				}
				else if (implementsInterface(c, "Landroid/media/MediaPlayer$OnCompletionListener")) {
					context = new OnCompletionListener(c);
				}
				*/
			}
			
			
			//If all else fails register the class as an unresolved context
			if (context == null) {
				context = new UnresolvedContext(c);
				unresolvedClasses++;
				if (RESOLVE_DEBUG > 1) {
					Log.yellow();
					Log.println("Unresolved: " + c.getName().toString());
					if (RESOLVE_DEBUG > 1) {
						outputUnresolvedInfo(c);
					}
					Log.resetColor();
				}
			}

			if (context != null) {
				//Add the methods declared by this class or any of its super-classes to the 
				//relevant component. This takes care of the previous bug, where methods 
				//inherited from super-classes, were missing from the child components
				for (IMethod m : c.getAllMethods()) {
					Set<CGNode> nodes = originalCG.getNodes(m.getReference());
					context.addNodes(nodes);
				}
				
				sNodes.addAll(Iterator2Collection.toSet(context.getContextCallGraph().iterator()));
				registerComponent(c.getReference(), context);
				resolvedClasses++;

				if (RESOLVE_DEBUG > 1) {
					Log.println("Resolved  : " + context.toString());
					for(CallBack cb : context.getCallbacks()) {
						Log.println("  " + cb.toString()); 
					}
				}
			}
			else {	//This will not be executed when using unresolved contexts
				if (RESOLVE_DEBUG > 1) {
					Log.yellow();
					Log.println("Unresolved: " + c.getName().toString());
					if (RESOLVE_DEBUG > 1) {
						outputUnresolvedInfo(c);
					}

					Log.resetColor();
				}
				
			}
		} // for IClass c
		
		
		//Find the dangling nodes - these nodes might actually not be reachable at all ...
		Collection<CGNode> allNodes = Iterator2Collection.toSet(global.getAppCallGraph().iterator());
		for (CGNode n : allNodes ) {
			if (!sNodes.contains(n)) {
				danglingMethods++;
				if (RESOLVE_DEBUG > 1) {
					Log.red("Not in any context: " + n.getMethod().getSignature());
				}
			}
		}
		if (RESOLVE_DEBUG > 1 && danglingMethods == 0) {
			Log.green("No dangling methods.");
		}

		
		resolutionStats();

	}


	/**
	 * A class that does not get resolved does not mean necessarily that we're missing
	 * stuff. But if it is a callback that actually makes sense it will be included 
	 * in those missing ones.
	 */
	private void resolutionStats() {
		totalClassesChecked = resolvedClasses + unresolvedClasses;
		Log.println();
		Log.println( "==========================================");
		String fst = String.format("%-30s: %d (%.2f %%)", "Resolved classes", resolvedClasses,        
				100 * ((double) resolvedClasses / (double) totalClassesChecked));
		Log.println(fst);
		fst = String.format("%-30s: %d (%.2f %%)", "UnResolved classes", unresolvedClasses,        
				100 * ((double) unresolvedClasses / (double) totalClassesChecked));
		Log.println(fst);
		Log.println("------------------------------------------");

		fst = String.format("%-30s: %d", "Dangling nodes", danglingMethods);
		Log.println(fst);
		fst = String.format("%-30s: %d", "Total nodes", originalCG.getNumberOfNodes());
		Log.println(fst);
		Log.println("==========================================\n");

	}


	private void getPathToTarget(CGNode root) {
		Filter<CGNode> filter = new Filter<CGNode>() {
			public boolean accepts(CGNode o) { 
				return originalCG.isTargetMethod(o);
			}
		};
		//TODO: replace reachability with this?
		DFSPathFinder<CGNode> pf = new DFSPathFinder<CGNode>(originalCG, root, filter);
		List<CGNode> path = pf.find();
	}

	private Map<MethodReference, Set<Context>> method2Component; 

	private void fillMethod2Component() {
		method2Component =  new HashMap<MethodReference, Set<Context>>();
		for (Context comp : getComponents()) {
			for (Iterator<CGNode> it = comp.getContextCallGraph().iterator(); it.hasNext(); ) {
				CGNode node = it.next();
				MethodReference mr = node.getMethod().getReference();
				Set<Context> sComponents = method2Component.get(mr);
				if (sComponents == null) {
					sComponents = new HashSet<Context>();
				}
				sComponents.add(comp);
				method2Component.put(mr, sComponents);
				Log.log(3, mr.toString() + " < " + sComponents.toString());	//Huge output
			}
		}
	}

	public Set<Context> getContainingComponents(MethodReference mr) {
		if (method2Component == null) {
			fillMethod2Component();
		}
		return method2Component.get(mr);
	}


	private  void outputUnresolvedInfo(IClass c) {
		Collection<IClass> allImplementedInterfaces = c.getAllImplementedInterfaces();
		for (IClass anc :  ClassHierarchyUtils.getClassAncestors(c)) {
			Log.println("#### CL: " + anc.getName().toString());
		}
		for (IClass intf : allImplementedInterfaces) {
			Log.println("#### IF: " + intf.getName().toString());
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
		if (SOLVE_DEBUG > 0) {
			Log.println("\nSolving components...");
		}
		//Build the constraints' graph 
		RunnableManager runnableManager = global.getRunnableManager();
		SparseNumberedGraph<Context> rCG = runnableManager.getConstraintGraph();

		IntentManager intentManager = global.getIntentManager();
		SparseNumberedGraph<Context> iCG = intentManager.getConstraintGraph();

		SparseNumberedGraph<Context> constraintGraph = GraphUtils.merge(rCG,iCG);
		GraphUtils.dumpConstraintGraph(constraintGraph, "all_constraints");

		Collection<? extends AbstractContext> componentSet;

		if (!Opts.ANALYZE_SUPERCOMPONENTS) {
			//Run the analysis just on the Components
			Iterator<Context> bottomUpIterator = GraphUtils.topDownIterator(constraintGraph);
			// Analyze the components based on this graph
			while (bottomUpIterator.hasNext()) {
				Context ctx = bottomUpIterator.next();
				solveComponent(ctx);
			}
			componentSet = getComponents();
		}
		else {
			//Create SuperComponents based on component constraints
			Iterator<Set<Context>> scItr = GraphUtils.connectedComponentIterator(constraintGraph);
			//SuperComponents: the sequence does not matter
			while(scItr.hasNext()) {
				Set<Context> sCtx = scItr.next();	//The set of components that construct this supercomponent
				SuperComponent superComponent = new SuperComponent(sCtx);
				if (SOLVE_DEBUG > 0) {
					superComponent.dumpContainingComponents();
				}
				ComponentPrinter<SuperComponent> printer = new ComponentPrinter<SuperComponent>(superComponent);
				printer.outputSupergraph();
				superComponents.add(superComponent);			
				solveComponent(superComponent);

			}
			componentSet = getSuperComponents();
		}

		//************************************************************************************
		//Sanity check:
		//Populate unresolved Intent and Runnable calls that are made while a resource is held
		mUnresIntents = new HashSetMultiMap<MethodReference, SSAInstruction>();
		Collection<Pair<MethodReference, SSAInvokeInstruction>> unresolvedInstructions = 
				new HashSet<Pair<MethodReference,SSAInvokeInstruction>>(); 
		
		unresolvedInstructions.addAll(global.getIntentManager().getUnresolvedCallSites());
		unresolvedInstructions.addAll(global.getRunnableManager().getUnresolvedCallSites());

		//Warning: Be careful to use the correct set of contexts (super or regular)	
		for (AbstractContext c : componentSet) {
			for (Pair<MethodReference, SSAInvokeInstruction> p :
				c.getHightStateUnresolvedIntents(unresolvedInstructions)) {
				mUnresIntents.put(p.fst, p.snd);
			}
		}
		//************************************************************************************

	}


	private <T extends AbstractContext> void solveComponent(T component) {

		if (SOLVE_DEBUG > 0) {
			Log.println("Solving: " + component.toString());
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
					return (getGraphReachability().getReachableSet(n).size() > 0);    
				}
			};
			if (component instanceof Context) {
				Context context = (Context) component;
				if (!CollectionUtils.exists(context.getCallbacks(), predicate))
					return;
			}
		}	//Lock reaching callbacks
		
		if (!component.callsInteresting()) {
			if (SOLVE_DEBUG > 0) {
				Log.grey(component.toString() + " does not deal with resource management. Moving on ...");
			}
			return;
		}
		
		component.solve();

		if(Opts.OUTPUT_COLOR_CFG_DOT) {
			componentPrinter.outputColoredCFGs();
		}      
		if(Opts.OUTPUT_COLORED_SUPERGRAPHS) {
			componentPrinter.outputColoredSupergraph();
		}
	}


	private HashSetMultiMap<MethodReference, SSAInstruction> mUnresIntents;

	public HashSetMultiMap<MethodReference, SSAInstruction> getCriticalUnresolvedAsyncCalls() {
		if (mUnresIntents == null) {
			solveComponents();
		}
		return mUnresIntents;
	}
	

	public ViolationReport getAnalysisResults() {
		return new ProcessResults(this).processExitStates();
	}

	//SupperComponents need to have been resolved first
	public List<SuperComponent> getSuperComponents() {
		return superComponents;
	}
	
	public void setGraphReachability(GraphReachability<CGNode> graphReachability) {
		this.graphReachability = graphReachability;
	}

}
