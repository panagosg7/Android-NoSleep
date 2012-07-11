package edu.ucsd.energy.contexts;

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
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.HashSetMultiMap;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.intset.OrdinalSet;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.AbstractContext;
import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.component.SuperComponent;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.LifecycleGraph;
import edu.ucsd.energy.interproc.LifecycleGraph.SensibleCGNode;
import edu.ucsd.energy.interproc.SingleContextCFG;
import edu.ucsd.energy.util.Util;

public abstract class Context extends AbstractContext {

	private static final int DEBUG = 0;

	protected IClass klass;

	//These are the ACTUAL callbacks that get resolved
	private Map<Selector, CallBack> 	mActualCallback;

	//Nodes that belong to the class that created this component
	//Call Graph construction might bring in more nodes.
	private Set<CGNode> sNode;



	//Typical callbacks specified by API
	protected static Set<Selector> sTypicalCallback;

	//This is going to be needed for the construction of the sensible graph
	protected static HashSet<Pair<Selector, Selector>> callbackEdges;

	private Boolean callsInteresting = null;


	/**
	 * Add a set of nodes to this context
	 * @param nodes
	 */
	public void addNodes(Set<CGNode> nodes) {
		if(sNode == null) {
			sNode = new HashSet<CGNode>();
		}
		sNode.addAll(nodes);
	}


	public boolean isCallBack(CGNode n) {
		return mActualCallback.containsValue(CallBack.findOrCreateCallBack(n));	  
	}

	public boolean isCallBack(Selector s) {
		return mActualCallback.containsKey(s);	  
	}

	protected Context(IClass c) {
		super();
		klass = c;
		sTypicalCallback = new HashSet<Selector>();
		callbackEdges = new HashSet<Pair<Selector,Selector>>();
	}

	
	public CallGraph getContextCallGraph() {
		if (componentCallgraph == null) {
			HashSet<CGNode> set = new HashSet<CGNode>();    
			for (CGNode node : sNode ) {    	
				OrdinalSet<CGNode> reachableSet = originalCallgraph.getReachability().getReachableSet(node);
				Set<CGNode> desc = Util.iteratorToSet(reachableSet.iterator());
				set.addAll(desc);
			}    
			//sNode is not really the root set, but this should work,
			//cause they should work as entry points
			componentCallgraph = PartialCallGraph.make(originalCallgraph, sNode, set);
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

	private void requiresCallBacks() {
		if (mActualCallback == null) {
			mActualCallback = new HashMap<Selector, CallBack>();
			for (CGNode node : GraphUtil.inferRoots(getContextCallGraph())) {
				Selector selector = node.getMethod().getSelector();
				CallBack callback = CallBack.findOrCreateCallBack(node);
				mActualCallback.put(selector, callback);
			}
		}
	}

	public Collection<CallBack> getCallbacks() {
		requiresCallBacks();
		return mActualCallback.values();
	}

	public CallBack getCallBack(Selector sel) {
		requiresCallBacks();
		return mActualCallback.get(sel);
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
		//Keep a worklist with all the callbacks that need to be
		//checked for predecessors
		Queue<SensibleCGNode> worklist = new LinkedList<SensibleCGNode>();
		Set<SensibleCGNode> visited = new HashSet<SensibleCGNode>();
		Set<CallBack> returnSet = new HashSet<CallBack>();

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

	HashSetMultiMap<Context, SSAInstruction> sSeed;

	private SuperComponent containingSuperComponent;

	public void addSeed(Context c, SSAInstruction instr) {
		if (sSeed == null) {
			sSeed = new HashSetMultiMap<Context, SSAInstruction>();
		}
		sSeed.put(c, instr);
	}

	/**
	 * Get a mapping from the Contexts that call this one to the 
	 * specific instructions in their code the call is made.
	 * WARNING: this can be null!!
	 * @return
	 */
	public HashSetMultiMap<Context, SSAInstruction> getSeeds() {
		return sSeed;
	}


	public Iterator<CGNode> getNodes() {
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
	
	
	public Set<Context> getContainingContexts(CGNode node) {
		HashSet<Context> hashSet = new HashSet<Context>();
		hashSet.add(this);
		return hashSet;		
	}
	

}
