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
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.CollectionFilter;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.graph.GraphReachability;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.DFSPathFinder;

import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.apk.AppCallGraph;
import edu.ucsd.energy.apk.AppClassHierarchy;
import edu.ucsd.energy.component.AbstractComponent;
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

	private static final int DEBUG = 0;

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

	private void registerComponent(TypeReference declaringClass, Context comp) {
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
	int resolvedClasses = 0;
	int unresolvedClasses = 0;
	int totalClassesChecked = 0;
	int resolvedConstructors = 0;
	
	public void resolveComponents() {

		ClassHierarchy ch = global.getClassHierarchy();
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
				ArrayList<IClass> classAncestors = AppClassHierarchy.getClassAncestors(c);
				for (IClass anc : classAncestors) {
					String ancName = anc.getName().toString();
					if (ancName.equals("Landroid/app/Activity")) {
						context = new Activity(global, c);
					}
					if (ancName.equals("Landroid/app/Service")) {
						context = new Service(global, c);
					}
					if (ancName.equals("Landroid/content/ContentProvider")) {
						context = new ContentProvider(global, c);
					}
					if (ancName.equals("Landroid/content/BroadcastReceiver")) {
						context = new BroadcastReceiver(global, c);
					}
					if (ancName.equals("Landroid/os/AsyncTask")) {
						context = new AsyncTask(global, c);
					}
					if (ancName.equals("Landroid/view/View")) {
						context = new View(global, c);
					}
					if (ancName.equals("Landroid/app/Application")) {
						context = new Application(global, c);
					}
					if (ancName.equals("Landroid/os/Handler")) {
						context = new Handler(global, c);
					}
					if (ancName.equals("Landroid/webkit/WebViewClient")) {
						context = new WebViewClient(global, c);
					}
					if (ancName.equals("Landroid/telephony/PhoneStateListener")) {
						context = new PhoneStateListener(global, c);
					}
				}
			}

			if (context == null) {
				//Since a class can implement multiple interfaces we should keep an order of 
				//in which to decide on which one should be used. The only restriction at 
				//this moment is Runnable, which should be tested on first. 
				if (implementsInterface(c, "Ljava/lang/Runnable")) {
					context = new RunnableThread(global, c);
				}
				else if (implementsInterface(c, "Ljava/util/concurrent/Callable")) {
					context = new Callable(global, c);
				}
				else if (implementsInterface(c,"ClickListener")) {
					context = new ClickListener(global, c);
				}
				else if (implementsInterface(c, "Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener")
						|| implementsInterface(c, "Landroid/preference/Preference$OnPreferenceChangeListener")) {
					context = new OnSharedPreferenceChangeListener(global, c);
				}
				else if (implementsInterface(c, "Landroid/location/LocationListener")) {
					context = new LocationListener(global, c);
				}
				else if (implementsInterface(c, "Landroid/widget/")) {
					context = new Widget(global, c);
				}
				else if (implementsInterface(c, "Landroid/hardware/SensorEventListener")) {
					context = new SensorEventListener(global, c);
				}
				else if (implementsInterface(c, "Landroid/content/ServiceConnection")) {
					context = new ServiceConnection(global, c);
				}
				else if (implementsInterface(c, "Landroid/content/DialogInterface")) {
					context = new DialogInterface(global, c);
				}
				else if (implementsInterface(c, "Landroid/media/MediaPlayer$OnCompletionListener")) {
					context = new OnCompletionListener(global, c);
				}
			}
			
			if (context != null) {
				//Add the methods declared by this class or any of its super-classes to the 
				//relevant component. This takes care of the previous bug, where methods 
				//inherited from super-classes, were missing from the child components
				for (IMethod m : c.getAllMethods()) {
					context.addNodes(originalCG.getNodes(m.getReference()));
				}
				registerComponent(c.getReference(), context);
				resolvedClasses++;

				if (DEBUG > 0) {
					System.out.println("Resolved  : " + context.toString());
				}
			}
			else {
				if (DEBUG > 0) {
					E.yellow();
					System.out.println("Unresolved: " + c.getName().toString());
					if (DEBUG > 1) {
						outputUnresolvedInfo(c);
					}
					
					E.resetColor();
				}
				unresolvedClasses++;
			}
		} // for IClass c
		
		resolutionStats();

	}


	/**
	 * A class that does not get resolved does not mean necessarily that we're missing
	 * stuff. But if it is a callback that actually makes sense it will be included 
	 * in those missing ones.
	 */
	private void resolutionStats() {
		totalClassesChecked = resolvedClasses + unresolvedClasses;
		System.out.println();
		System.out.println( "==========================================");
		String fst = String.format("%-30s: %d (%.2f %%)", "Resolved classes", resolvedClasses,        
				100 * ((double) resolvedClasses / (double) totalClassesChecked));
		System.out.println(fst);
		fst = String.format("%-30s: %d (%.2f %%)", "Resolved classes", unresolvedClasses,        
				100 * ((double) unresolvedClasses / (double) totalClassesChecked));
		System.out.println(fst);
		System.out.println("------------------------------------------");

		fst = String.format("%-30s: %d", "Total nodes", originalCG.getNumberOfNodes());
		System.out.println(fst);
		System.out.println("==========================================\n");

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


	private  void outputUnresolvedInfo(IClass c) {
		Collection<IClass> allImplementedInterfaces = c.getAllImplementedInterfaces();
		for (IClass anc :  AppClassHierarchy.getClassAncestors(c)) {
			System.out.println("#### CL: " + anc.getName().toString());
		}
		for (IClass intf : allImplementedInterfaces) {
			System.out.println("#### IF: " + intf.getName().toString());
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
