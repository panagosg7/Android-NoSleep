package edu.ucsd.energy.component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.HashSetMultiMap;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.intset.OrdinalSet;

import edu.ucsd.energy.apk.AppCallGraph;
import edu.ucsd.energy.apk.ClassHierarchyUtils;
import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.LifecycleGraph;
import edu.ucsd.energy.interproc.LifecycleGraph.SensibleCGNode;
import edu.ucsd.energy.interproc.SingleContextCFG;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.ComponentSummary;
import edu.ucsd.energy.results.IReportKey;
import edu.ucsd.energy.results.LockUsage;
import edu.ucsd.energy.results.Violation;
import edu.ucsd.energy.results.Violation.ViolationType;
import edu.ucsd.energy.util.Log;
import edu.ucsd.energy.util.Util;

public abstract class Component extends AbstractContext implements IReportKey {

	private static final int DEBUG = 0;

	protected IClass klass;

	/**
	 * These are the roots of the component's callgraph
	 * Not exaclty callbacks
	 */
	private Map<Selector, CallBack> 	mRoots;
	

	//Nodes that belong to the class that created this component
	//Call Graph construction might bring in more nodes.
	private Set<CGNode> sNode;

	//Typical callbacks specified by API
	protected Set<Selector> sTypicalCallback;

	//This is going to be needed for the construction of the sensible graph
	protected HashSet<Pair<Selector, Selector>> callbackEdges;

	private Boolean callsInteresting = null;


	public Set<CGNode> getClassNodes() {
		if(sNode == null) {
			sNode = new HashSet<CGNode>();			
			//Add the methods declared by this class or any of its super-classes to the 
			//relevant component. This takes care of the previous bug, where methods 
			//inherited from super-classes, were missing from the child components
			for (IMethod m : getKlass().getAllMethods()) {
				Set<CGNode> nodes = GlobalManager.get().getAppCallGraph().getNodes(m.getReference());
				sNode.addAll(nodes);
			}
		}
		return sNode;
	}
	
	

	public boolean isCallBack(CGNode n) {
		return mRoots.containsValue(CallBack.findOrCreateCallBack(n));	  
	}

	public boolean isCallBack(Selector s) {
		return mRoots.containsKey(s);	  
	}

	protected Component(IClass c) {
		super();
		klass = c;
		sTypicalCallback = new HashSet<Selector>();
		callbackEdges = new HashSet<Pair<Selector,Selector>>();
	}


	public CallGraph getContextCallGraph() {
		if (componentCallgraph == null) {
			HashSet<CGNode> set = new HashSet<CGNode>();    
			for (CGNode node : getClassNodes()) {    	
				OrdinalSet<CGNode> reachableSet = originalCallgraph.getReachability().getReachableSet(node);
				Set<CGNode> desc = Util.iteratorToSet(reachableSet.iterator());
				set.addAll(desc);
			}    
			//sNode is not really the root set, but this should work,
			//cause they should work as entry points
			componentCallgraph = PartialCallGraph.make(originalCallgraph, getClassNodes(), set);
		}
		return componentCallgraph;
	}



	/**
	 * Determines if this context calls any interesting methods.
	 * If it doesn't we don't really need to solve the component.
	 * 
	 * @return true is there are calls to interesting methods
	 */
	public boolean callsInteresting() {
		if (callsInteresting == null) {
			CallGraph cg = getContextCallGraph();
			Iterator<CGNode> iterator = cg.iterator();
			while(iterator.hasNext()) {
				CGNode next = iterator.next();
				if (next.getMethod().isNative()) continue;
				Iterator<CallSiteReference> it = next.getIR().iterateCallSites();
				while(it.hasNext()) {
					MethodReference target = it.next().getDeclaredTarget();
					if (Interesting.sInterestingMethods.contains(target)) {
						callsInteresting = new Boolean(true);
						return true;
					}
				}
			}
			callsInteresting = new Boolean(false);
			return false;
		}
		return callsInteresting .booleanValue();

	}


	public String toString() {
		StringBuffer b = new StringBuffer();
		String name = this.getClass().getName().toString();
		b.append(name.substring(name.lastIndexOf('.') + 1) + ": ");
		b.append(getKlass().getName().toString());
		return b.toString();
	}

	public IClass getKlass() {
		return klass;
	}


	/**
	 * Is this context defined in an abstract class? 
	 * @return
	 */
	public boolean isAbstract() {
		return getKlass().isAbstract();
	}

	private void requiresRoots() {
		if (mRoots == null) {
			mRoots = new HashMap<Selector, CallBack>();
			for (CGNode node : GraphUtil.inferRoots(getContextCallGraph())) {
				Selector selector = node.getMethod().getSelector();
				CallBack callback = CallBack.findOrCreateCallBack(node);
				mRoots.put(selector, callback);
			}
		}
	}

	public Collection<CallBack> getRoots() {
		requiresRoots();
		return mRoots.values();
	}

	/**
	 * Returns the root/callback corresponding to a selector based on the 
	 * components call graph - not the whole application's one. Note
	 * that there might be a method that is a callback in the component
	 * and not in the whole application.
	 * @param sel
	 * @return
	 */
	public CallBack getRoot(Selector sel) {
		requiresRoots();
		return mRoots.get(sel);
	}
	
	public boolean isRoot(Selector sel) {
		requiresRoots();
		return mRoots.containsKey(sel);
	}
	

	/**
	 * Return the callback that corresponds to this selector or a
	 * predecessor of it in the life-cycle if this is not overridden.
	 * 
	 * The callback that is returned is guaranteed to be overridden.
	 * 
	 * @param selector
	 * @param fwd true if you want to get a successor callback,
	 * 						false if you want to get a predecessor callback
	 * @return null if the selector does not correspond to any known
	 * callback
	 * 
	 * TODO: cache results (not so necessary - the graphs are small)
	 */
	public Set<CallBack> getNextCallBack(Selector selector, boolean fwd) {

		if (DEBUG > 0) {
			System.out.println("CallBack or predecessors for: " + selector.toString());
		}
		Set<CallBack> returnSet = new HashSet<CallBack>();
		
		
		//First try to see if the requested callback is present (overriden)
		//If that's true, we don't need to search harder
		CallBack callBack = getRoot(selector);
		if (callBack != null) {
			
			if (DEBUG > 0) {
				System.out.println("Got exact CallBack: " + callBack);
			}
			AppCallGraph appCallGraph = GlobalManager.get().getAppCallGraph();
			//If it is called by the system then it is _possibly_ a 
			//callback, so we treat it as one.
			returnSet.add(callBack);
			return returnSet;	
		}
		
		
		//If we haven't found anything so far this means that the method 
		//is probably not overridden. 
		//Keep a worklist with all the callbacks that need to be
		//checked for predecessors
		Queue<SensibleCGNode> worklist = new LinkedList<SensibleCGNode>();
		Set<SensibleCGNode> visited = new HashSet<SensibleCGNode>();

		SensibleCGNode initNode = getLifecycleGraph().find(selector);

		if (initNode != null) {
			worklist.add(initNode);
			if (DEBUG > 1) {
				System.out.println("Adding to worklist: " + initNode.toString());
			}
		}
		//if the first node is not a callback nothing will be returned

		while (!worklist.isEmpty()) {
			SensibleCGNode node = worklist.remove();
			if (visited.contains(node)) continue;		//ensure termination
			visited.add(node);

			if (node.isEmpty()) {
				//this node is not overridden, so we need to add 
				//the predecessor nodes to worklist
				//We need to get the predecessors from the full life-cycle
				//graph - not the pruned one. The pruned one will not work 
				//because it might even be missing the node we are looking for 
				//(it might not even be overridden)
				Iterator<SensibleCGNode> nextNodes = fwd? 
						getFullLifecycleGraph().getSuccNodes(node):
							getFullLifecycleGraph().getPredNodes(node);
						while(nextNodes.hasNext()) {
							SensibleCGNode succ = nextNodes.next();
							worklist.add(succ);
							if (DEBUG > 1) {
								System.out.println("Adding to worklist: " + succ.toString());
							}
						}
			}
			else {
				//this node is overridden, so it should be returned
				//and none of its predecessors
				returnSet.add(node.getCallBack());
			}
		}
		if (DEBUG > 0) {
			System.out.println("Returning: " + returnSet.toString());
		}
		return returnSet;
	}


	public HashSet<Pair<Selector, Selector>> getCallbackEdges() {
		return callbackEdges;
	}

	public Set<Selector> getTypicalCallbacks() {
		return sTypicalCallback;
	}

	private LifecycleGraph implicitGraph;

	public LifecycleGraph getLifecycleGraph() {
		if (implicitGraph == null) {
			implicitGraph = new LifecycleGraph(this);
		}
		return implicitGraph;
	}

	public SparseNumberedGraph<SensibleCGNode> getFullLifecycleGraph() {
		return getLifecycleGraph().getFullLifeCycleGraph();
	}

	private Set<Pair<CGNode, CGNode>> packedEdges;

	public Set<Pair<CGNode, CGNode>> getImplicitEdges() {
		if (packedEdges == null) {
			packedEdges = getLifecycleGraph().packEdges();
		}
		return packedEdges;
	}

	public SingleContextCFG makeCFG() {
		return new SingleContextCFG(this, getImplicitEdges());
	}


	/****************************************************
	 * These are the points in the code that might call 
	 * this component using an intent/thread/handler
	 ***************************************************/

	HashSetMultiMap<Component, SSAInstruction> sSeed;

	private SuperComponent containingSuperComponent;

	private Collection<IClass> implementedInterfaces;

	private Collection<IClass> classAncestors;

	protected Boolean extendsAndroid = null;


	public void addSeed(Component c, SSAInstruction instr) {
		if (sSeed == null) {
			sSeed = new HashSetMultiMap<Component, SSAInstruction>();
		}
		sSeed.put(c, instr);
	}

	/**
	 * Get a mapping from the Contexts that call this one to the 
	 * specific instructions in their code the call is made.
	 * WARNING: this can be null!!
	 * @return
	 */
	public HashSetMultiMap<Component, SSAInstruction> getSeeds() {
		return sSeed;
	}


	public Iterator<CGNode> getAllReachableNodes() {
		return getContextCallGraph().iterator();
	}

	public String toFileName() {
		return getKlass().getName().toString().replace("/", ".");
	}


	public void setContainingSuperComponent(SuperComponent superComponent) {
		containingSuperComponent = superComponent;		
	}

	public CompoundLockState getReturnState(CGNode cgNode) {
		if (cgNode.getMethod().isNative()) return null;
		if (icfg == null) {
			//This means that we have run the analysis on the supercomponent and get
			//the result from there
			return containingSuperComponent.getExitState(cgNode);
		}
		else {
			return getExitState(cgNode);
		}
	}


	public Set<Component> getContainingContexts(CGNode node) {
		HashSet<Component> hashSet = new HashSet<Component>();
		hashSet.add(this);
		return hashSet;		
	}



	/***
	 * Stuff imported by old Component
	 */
	public Set<Violation> assembleReport() {
		ComponentSummary ctxSum = new ComponentSummary(this);
		for(Iterator<CallBack> it = getRoots().iterator() ; it.hasNext(); ) {
			CallBack cb = it.next();
			CompoundLockState exitState = getReturnState(cb.getNode());
			if (exitState != null) {	//for the case of native methods 
				ctxSum.registerState(cb, exitState);
			}
			else {
				IMethod method = cb.getNode().getMethod();
				Assertions.productionAssertion(method.isNative(), 
						"Empty exit state from non-native method: " + method.toString());
			}
		}
		return gatherViolations(ctxSum);
	}

	abstract protected Set<Violation> gatherViolations(ComponentSummary ctx);

	protected boolean relevant(LockUsage st) {
		return st.relevant(this);
	}

	/**
	 * The set of entry and exit points might depend on the call method used 
	 * for this component call
	 */
	abstract public Set<Selector> getEntryPoints(Selector callSelector);
	abstract public Set<Selector> getExitPoints(Selector callSelector);



	public Set<Violation> gatherViolations(ComponentSummary summary, Selector sel, ViolationType res) {
		Set<LockUsage> stateForSelector = summary.getCallBackState(sel);
		if (DEBUG > 0) {
			System.out.println("States for :" + sel.toString());
			System.out.println("  " + stateForSelector);
		}
		Set<Violation> violations = new HashSet<Violation>();
		for (WakeLockInstance wli : summary.lockInstances()) {
			if (DEBUG > 0) {
				System.out.println("Checking policies for lock: " + wli.toShortString());
			}

			if (stateForSelector != null) {
				for (LockUsage st : stateForSelector) {

					boolean relevant = relevant(st);

					if (DEBUG > 0) {
						if (!relevant) {
							System.out.println("IRRELEVANT Examining: " + st.toString());
							System.out.println("Relevant ctxs: " + st.getRelevantCtxs());
						}
					}

					if (relevant && st.locking(wli)) {
						if (DEBUG > 0) {
							System.out.println("Adding violation: " + wli.toShortString());
							System.out.println();
						}
						violations.add(new Violation(res, sel.toString()));
					}	
				}
			}
			else {
				Assertions.UNREACHABLE("Cannot ask for a callback state and " +
						"not be able to find it in our results.");
			}
		}
		return violations;		
	}


	public Collection<IClass> getImplementedInterfaces() {
		if (implementedInterfaces == null) {
			implementedInterfaces = getKlass().getAllImplementedInterfaces();
		}
		return implementedInterfaces;
	}

	public Collection<IClass> getClassAncestors() {
		if (classAncestors == null) {
			classAncestors = ClassHierarchyUtils.getClassAncestors(getKlass());
		}
		return classAncestors;
	}

	
	/**
	 * Is this component a class extending one of Java's or Android's 
	 * API's or is it just a helper class in the application?
	 * @return
	 */
	public boolean extendsAndroid() {
		if (extendsAndroid == null) {
			for(IClass c : getClassAncestors()) {
				if (c.getClassLoader().toString().equals("Extension")) {	//should be ok...
					extendsAndroid = new Boolean(true);
					return true;
				}
			}
			for(IClass i : getImplementedInterfaces()) {
				if (i.getClassLoader().toString().equals("Extension")) {	//should be ok...
					extendsAndroid = new Boolean(true);
					return true;
				}
			}
			extendsAndroid = new Boolean(false);
		}
		return extendsAndroid.booleanValue();
	}
	
	
	public void outputParentInfo() {
		for (IClass anc : getClassAncestors()) {
			Log.println("#### CL: " + anc.getName().toString() + "\t" +
					((anc.getClassLoader().getReference().equals(ClassLoaderReference.Primordial) &&
					 (anc.getSuperclass() != null))?"Primordial":"") + 
					((anc.getClassLoader().getReference().equals(ClassLoaderReference.Extension) &&
					 (anc.getSuperclass() != null))?"ANDROID":""));
		}
		for (IClass intf : getImplementedInterfaces()) {
			Log.println("#### IF: " + intf.getName().toString() + "\t" + 
					((intf.getClassLoader().getReference().equals(ClassLoaderReference.Primordial) &&
					 (intf.getSuperclass() != null))?"Primordial":"") + 
					((intf.getClassLoader().getReference().equals(ClassLoaderReference.Extension) &&
					 (intf.getSuperclass() != null))?"ANDROID":""));
		}
	}
	
	
}
