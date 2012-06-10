package edu.ucsd.energy.contexts;

import java.awt.peer.LightweightPeer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.analysis.SpecialConditions.SpecialCondition;
import edu.ucsd.energy.component.AbstractComponent;
import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.interproc.LifecycleGraph.SensibleCGNode;
import edu.ucsd.energy.interproc.SingleContextCFG;
import edu.ucsd.energy.interproc.LifecycleGraph;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.util.E;

public abstract class Context extends AbstractComponent {
  
	protected IClass klass;
  
	// These are the ACTUAL callbacks that get resolved
	//private HashSet<CallBack> callbacks;
	//callbacks be obtained by callbackMap.values 
	private Map<Selector, CallBack> 	mActualCallback;
  
	// 	Typical callbacks specified by API
	protected Set<Selector> sTypicalCallback;
  
	// 	This is going to be needed for the construction of the sensible graph
	protected HashSet<Pair<Selector, Selector>> callbackEdges;
  
	public void registerCallback(CGNode node) {
		Selector sel = node.getMethod().getSelector();
		E.log(2, "Registering callback: " + sel.toString() + " to " + this.toString());    
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

	public Collection<CallBack> getCallbacks() {
		return mActualCallback.values();
	}

	public CallBack getCallBack(Selector sel) {
		return mActualCallback.get(sel);
	}
	
	
	/**
	 * TODO: see if this is needed and fix this 
	 * @param sel
	 * @return
	 */
	public Set<CallBack> getCallBackOrPred(Selector sel) {
		if (sel == null) {
			return null;
		}
		CallBack cb = getCallBack(sel);
		if (cb == null) {
			SensibleCGNode n = SensibleCGNode.makeEmpty(sel);
			Iterator<SensibleCGNode> predNodes = implicitGraph.getPredNodes(n);
			Set<CallBack> res = new HashSet<CallBack>();
			SensibleCGNode predNode = predNodes.next();
			while(predNodes.hasNext()) {
				if (!predNode.isEmpty()) {
					res.add(predNode.getCallBack());
				}
			}
			return res;
		}
		return null;
	}
	

	public HashSet<Pair<Selector, Selector>> getCallbackEdges() {
		return callbackEdges;
	}

	public Set<Selector> getTypicalCallbacks() {
		return sTypicalCallback;
	}

	private LifecycleGraph implicitGraph;
	
	private LifecycleGraph getLifecyclegraph() {
		if (implicitGraph == null) {
			implicitGraph = new LifecycleGraph(this);
		}
		return implicitGraph;
	}
	
	private Set<Pair<CGNode, CGNode>> packedEdges;
  
	public Set<Pair<CGNode, CGNode>> getImplicitEdges() {
		if (packedEdges == null) {
			packedEdges = getLifecyclegraph().packEdges();
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
 		return originalCallgraph.iterator();
 	}
 	
 	public String toFileName() {
 		return getKlass().getName().toString().replace("/", ".");
 	}

	
}
