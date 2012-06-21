package edu.ucsd.energy.contexts;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;

import edu.ucsd.energy.component.AbstractComponent;
import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.component.SuperComponent;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.LifecycleGraph;
import edu.ucsd.energy.interproc.LifecycleGraph.SensibleCGNode;
import edu.ucsd.energy.interproc.SingleContextCFG;
import edu.ucsd.energy.managers.GlobalManager;

public abstract class Context extends AbstractComponent {

	private static final int DEBUG = 0;

	protected IClass klass;

	//These are the ACTUAL callbacks that get resolved
	private Map<Selector, CallBack> 	mActualCallback;

	//Typical callbacks specified by API
	protected Set<Selector> sTypicalCallback;

	//This is going to be needed for the construction of the sensible graph
	protected HashSet<Pair<Selector, Selector>> callbackEdges;


	public void registerCallback(CGNode node) {
		Selector sel = node.getMethod().getSelector();
		if (DEBUG > 1) {
			System.out.println("Registering callback: " + sel.toString() + " to " + this.toString());    
		}
		if (mActualCallback == null) {
			mActualCallback = new HashMap<Selector, CallBack>();
		}
		CallBack callBack = new CallBack(node);    
		mActualCallback.put(sel, callBack);
	}


	public boolean isCallBack(CGNode n) {
		return mActualCallback.containsValue(CallBack.findOrCreateCallBack(n));	  
	}


	protected Context(GlobalManager gm, CGNode root) {
		super(gm);
		klass = root.getMethod().getDeclaringClass();
		sTypicalCallback = new HashSet<Selector>();
		callbackEdges = new HashSet<Pair<Selector,Selector>>();
		registerCallback(root);
	}

	public void makeCallGraph() {	  
		HashSet<CGNode> rootSet = new HashSet<CGNode>();    
		HashSet<CGNode> set = new HashSet<CGNode>();    
		for (CallBack cb : getCallbacks()) {    	
			set.addAll(getDescendants(originalCallgraph, cb.getNode()));      
			rootSet.add(cb.getNode());      
		}    
		componentCallgraph = PartialCallGraph.make(originalCallgraph, rootSet, set);
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
	
	public Collection<CallBack> getCallbacks() {
		return mActualCallback.values();
	}

	public CallBack getCallBack(Selector sel) {
		return mActualCallback.get(sel);
	}

	/**
	 * Return the callback that corresponds to this selector or a
	 * predecessor of it in the life-cycle if this is not overridden.
	 * 
	 * The callback that is returned is guaranteed to be overridden.
	 * 
	 * @param selector
	 * @return null if the selector does not correspond to any known
	 * callback
	 * 
	 * TODO: cache results (not so necessary - the graphs are small)
	 */
	public Set<CallBack> getMostRecentCallBack(Selector selector) {

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
				Iterator<SensibleCGNode> predNodes = 
						getFullLifecycleGraph().getPredNodes(node);
				while(predNodes.hasNext()) {
					SensibleCGNode pred = predNodes.next();
					worklist.add(pred);
					if (DEBUG > 1) {
						System.out.println("Adding to worklist: " + pred.toString());
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
			System.out.println(returnSet.toString());
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

	Map<Context, Set<SSAInstruction>> sSeed;

	private SuperComponent containingSuperComponent;

	public void addSeed(Context c, SSAInstruction instr) {
		if (sSeed == null) {
			sSeed = new HashMap<Context, Set<SSAInstruction>>();
		}
		Set<SSAInstruction> cInstr = sSeed.get(c);
		if (cInstr == null) {
			cInstr = new HashSet<SSAInstruction>();
		}
		cInstr.add(instr);
		sSeed.put(c, cInstr);
	}

	/**
	 * Get a mapping from the Components that call this one to the 
	 * specific instructions in their code the call is made.
	 * WARNING: this can be null!!
	 * @return
	 */
	public Map<Context, Set<SSAInstruction>> getSeeds() {
		return sSeed;
	}

	/**
	 * Whoever really needs these will have to override it.
	 */
	public Set<Selector> getEntryPoints() {
		return null;
	}

	public Set<Selector> getExitPoints() {
		return null;
	}


	public Iterator<CGNode> getNodes() {
		return getCallGraph().iterator();
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
		/*
		Iterator<BasicBlockInContext<IExplodedBasicBlock>> predNodes = icfg.getPredNodes(exitBB);
		HashSet<CompoundLockState> set = new HashSet<CompoundLockState>();
		while(predNodes.hasNext()) {
			BasicBlockInContext<IExplodedBasicBlock> bb = predNodes.next();
			if (!icfg.isExceptionalEdge(bb, exitBB)) {
				IExplodedBasicBlock ebb = bb.getDelegate();
				set.add(mEBBState.get(ebb));
			}
		}
		CompoundLockState merged = CompoundLockState.merge(set);
		 */
		//IExplodedBasicBlock exit = exitBB.getDelegate();

		//Do not search by instruction cause that might be null
	}




}
