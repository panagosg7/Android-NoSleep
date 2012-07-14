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
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.component.ComponentPrinter;
import edu.ucsd.energy.component.SuperComponent;
import edu.ucsd.energy.contexts.Activity;
import edu.ucsd.energy.contexts.Application;
import edu.ucsd.energy.contexts.AsyncTask;
import edu.ucsd.energy.contexts.BroadcastReceiver;
import edu.ucsd.energy.contexts.Callable;
import edu.ucsd.energy.contexts.IntentService;
import edu.ucsd.energy.contexts.RunnableThread;
import edu.ucsd.energy.contexts.Service;
import edu.ucsd.energy.contexts.UnresolvedComponent;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.results.ProcessResults;
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
 *  be callbacks that are never explored.
 * 
 * @author pvekris
 *
 */
public class ComponentManager {

	private static	final int 		SOLVE_DEBUG = 0;
	static 					final int		RESOLVE_DEBUG = 0;

	private  HashMap<TypeName, Component> componentMap;

	private List<SuperComponent> superComponents = new ArrayList<SuperComponent>(); 

	private GlobalManager global;

	private GraphReachability<CGNode> graphReachability = null;

	private AppCallGraph originalCG;


	public ComponentManager() {
		global = GlobalManager.get();
		originalCG = global.getAppCallGraph();
		componentMap = new HashMap<TypeName, Component>();
	}

	public Collection<Component> getComponents() {
		return componentMap.values();
	}

	private void registerComponent(TypeReference declaringClass, Component comp) {
		componentMap.put(declaringClass.getName(), comp);
	}

	public Component getComponent(TypeName c) {
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

		for(IClass c : ch) {
			//Avoid primordial (java language classes) and extension (android.jar) classes
			if(c.getClassLoader().getReference().equals(ClassLoaderReference.Primordial) || 
					c.getClassLoader().getReference().equals(ClassLoaderReference.Extension)) {
				continue;
			}

			TypeName type = c.getName();
			//This is probably going to fail
			Component component = componentMap.get(type);
			if (component == null) {
				// A class can only extend one class so order does not matter
				ArrayList<IClass> classAncestors = ClassHierarchyUtils.getClassAncestors(c);
				for (IClass anc : classAncestors) {
					String ancName = anc.getName().toString();

					if (ancName.equals("Landroid/app/Activity")) {
						component = new Activity(c);
						break;
					}
					if (ancName.equals("Landroid/app/IntentService")) {
						component = new IntentService(c);
						break;
					}					
					if (ancName.equals("Landroid/app/Service")) {
						component = new Service(c);
						break;
					}
					//Some time, we might need to include this case
					//					if (ancName.equals("Landroid/content/ContentProvider")) {
					//						context = new ContentProvider(c);
					//						break;
					//					}
					if (ancName.equals("Landroid/content/BroadcastReceiver")) {
						component = new BroadcastReceiver(c);
						break;
					}
					if (ancName.equals("Landroid/os/AsyncTask")) {
						component = new AsyncTask(c);
						break;
					}
					if (ancName.equals("Landroid/app/Application")) {
						component = new Application(c);
						break;
					}
				}
			}

			if (component == null) {
				//Since a class can implement multiple interfaces we should keep an order of 
				//in which to decide on which one should be used. The only restriction at 
				//this moment is Runnable, which should be tested on first. 
				if (implementsInterface(c, "Ljava/lang/Runnable")) {
					component = new RunnableThread(c);
				}
				else if (implementsInterface(c, "Ljava/util/concurrent/Callable")) {
					component = new Callable(c);
				}
			}

			//If it has been resolved till now - it's one of the known categories
			if (component != null) {
				resolvedClasses++;
				if (RESOLVE_DEBUG > 1) {
					Log.println("Resolved: " + component.toString() +	" interacts with Android: " + component.extendsAndroid());
					if (RESOLVE_DEBUG > 2) {
						for(CallBack cb : component.getRoots()) {
							Log.println("  " + cb.toString()); 
						}
					}
				}
			}

			//If all else fails register the class as an unresolved context
			if (component == null) {
				component = new UnresolvedComponent(c);
				unresolvedClasses++;
			}
			//Done resolving this class --

			//This might not be needed
			sNodes.addAll(Iterator2Collection.toSet(component.getAllReachableNodes()));
			registerComponent(c.getReference(), component);


			if (component instanceof UnresolvedComponent) {
				if (RESOLVE_DEBUG > 0) {
					Log.yellow();
					Log.println("Unresolved: " + c.getName().toString() + 
							" interacts with Android: " + component.extendsAndroid());
					if (RESOLVE_DEBUG > 0) {
						component.outputParentInfo();
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

	private Map<MethodReference, Set<Component>> method2Component; 

	private void fillMethod2Component() {
		method2Component =  new HashMap<MethodReference, Set<Component>>();
		for (Component comp : getComponents()) {
			for (Iterator<CGNode> it = comp.getContextCallGraph().iterator(); it.hasNext(); ) {
				CGNode node = it.next();
				MethodReference mr = node.getMethod().getReference();
				Set<Component> sComponents = method2Component.get(mr);
				if (sComponents == null) {
					sComponents = new HashSet<Component>();
				}
				sComponents.add(comp);
				method2Component.put(mr, sComponents);
				Log.log(3, mr.toString() + " < " + sComponents.toString());	//Huge output
			}
		}
	}

	public Set<Component> getContainingComponents(MethodReference mr) {
		if (method2Component == null) {
			fillMethod2Component();
		}
		return method2Component.get(mr);
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
		SparseNumberedGraph<Component> rCG = runnableManager.getConstraintGraph();

		IntentManager intentManager = global.getIntentManager();
		SparseNumberedGraph<Component> iCG = intentManager.getConstraintGraph();

		SparseNumberedGraph<Component> constraintGraph = GraphUtils.merge(rCG,iCG);
		GraphUtils.dumpConstraintGraph(constraintGraph, "all_constraints");

		Collection<? extends AbstractContext> componentSet;

		if (!Opts.ANALYZE_SUPERCOMPONENTS) {
			//Run the analysis just on the Components
			Iterator<Component> bottomUpIterator = GraphUtils.topDownIterator(constraintGraph);
			// Analyze the components based on this graph
			while (bottomUpIterator.hasNext()) {
				Component ctx = bottomUpIterator.next();
				solveComponent(ctx);
			}
			componentSet = getComponents();
		}
		else {
			//Create SuperComponents based on component constraints
			Iterator<Set<Component>> scItr = GraphUtils.connectedComponentIterator(constraintGraph);
			//SuperComponents: the sequence does not matter
			while(scItr.hasNext()) {
				Set<Component> sCtx = scItr.next();	//The set of components that construct this supercomponent
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
			if (!c.solved()) continue;
			for (Pair<MethodReference, SSAInvokeInstruction> p :
				c.getHightStateUnresolvedIntents(unresolvedInstructions)) {
				mUnresIntents.put(p.fst, p.snd);
			}
		}
		//************************************************************************************

	}


	private <T extends AbstractContext> void solveComponent(T c) {
		ComponentPrinter<T> componentPrinter = new ComponentPrinter<T>(c);
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
			if (c instanceof Component) {
				Component context = (Component) c;
				if (!CollectionUtils.exists(context.getRoots(), predicate))
					return;
			}
		}	//Lock reaching callbacks

		//This (super)component does not interact with the Android system,
		//so it cannot be called by Android and therefore it can only be 
		//called by another (super)component, which we have already 
		//accounted for.
		if (!c.extendsAndroid()) {
			if (SOLVE_DEBUG > 0) {
				Log.grey(c.toString() + " does not extend Android API. Moving on ...");
			}
			return;
		}

		if (!c.callsInteresting()) {
			if (SOLVE_DEBUG > 0) {
				Log.grey(c.toString() + " does not deal with resource management. Moving on ...");
			}
			return;
		}
		
		if (SOLVE_DEBUG > 0) {
			Log.println("Solving: " + c.toString());
		}
		c.solve();

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


	public IReport getAnalysisResults() {
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
