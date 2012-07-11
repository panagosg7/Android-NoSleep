package edu.ucsd.energy.interproc;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.ProgramCounter;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.ExplodedControlFlowGraph;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.component.AbstractContext;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.util.Log;

/**
 * This is an exploded inter-procedural CFG with extra edges connecting 
 * methods specified at the creation. 
 */
abstract public class AbstractContextCFG extends ExplodedInterproceduralCFG {

	private static final int DEBUG = 0;

	protected AbstractContext absCtx;

	protected CallGraph callgraph;

	protected Map<String,CGNode> callbacks;


	//We need to keep these nodes so that the tabulation solver know that he has to 
	//propagate the state (these are going to be unbalanced seeds)
	protected Set<BasicBlockInContext<IExplodedBasicBlock>> sLifecycleEdge = 
			new HashSet<BasicBlockInContext<IExplodedBasicBlock>>();


	public AbstractContextCFG(CallGraph cg) {
		super(cg);
	}

	
	/**
	 * True if this node leads to the next method in the lifecycle
	 */
	public boolean isLifecycleExit(BasicBlockInContext<IExplodedBasicBlock> a) {
		return sLifecycleEdge.contains(a);
	}
	
	abstract public boolean isContextExit(BasicBlockInContext<IExplodedBasicBlock> a);
	
	
	

	/**
	 * The adjacent nodes to the packed edges are the interesting callbacks.
	 * So we will cache them and apply our policies later.
	 * @param packedEdges 
	 */
	protected void cacheCallbacks(Set<Pair<CGNode, CGNode>> packedEdges) {
		for (Pair<CGNode, CGNode> e : packedEdges) {
			getCallbacks().put(e.fst.getMethod().getName().toString(), e.fst);
			getCallbacks().put(e.snd.getMethod().getName().toString(), e.snd);
		}
	}

	protected  void addReturnToEntryEdge(Set<Pair<CGNode, CGNode>> packedEdges) {
		for(Pair<CGNode, CGNode> edge : packedEdges) {
			CGNode startNode = edge.fst;      
			ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> startCFG = getCFG(startNode);      
			CGNode stopNode = edge.snd;
			ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> stopCFG = getCFG(stopNode);
			IExplodedBasicBlock startBB = startCFG.exit();
			IExplodedBasicBlock stopBB = stopCFG.entry();
			BasicBlockInContext<IExplodedBasicBlock> p = 
					new BasicBlockInContext<IExplodedBasicBlock>(startNode, startBB);
			BasicBlockInContext<IExplodedBasicBlock> b =
					new BasicBlockInContext<IExplodedBasicBlock>(stopNode, stopBB);
			addEdge(p,b);
			sLifecycleEdge.add(p);
		}
	}


	public Map<String,CGNode> getCallbacks() {
		if (callbacks == null) {
			callbacks = new HashMap<String, CGNode>();
		}
		return callbacks;
	}

	/**
	 * Simple method that detects an exceptional edge
	 * Src and Dst must belong to the same method.
	 * Src node is the one throwing the exception, so 
	 * we can search in its instructions and find the 
	 * ones that throw a certain exception.
	 * TODO: Should the type of the exception matter? 
	 * @param src
	 * @param dest
	 * @return
	 */
	public boolean isExceptionalEdge(
			BasicBlockInContext<IExplodedBasicBlock> src,
			BasicBlockInContext<IExplodedBasicBlock> dest) {

		//The edges we added for calling and returning from other contexts 
		//should not be treated as exceptional edges
		if (isCallToContextEdge(src, dest) || isReturnFromContextEdge(src, dest)) {
			return false;
		}

		//Edge from the exit of a method to the exit of an other one is
		//probably an exceptional one.
		if((!src.getMethod().equals(dest.getMethod())) && src.getDelegate().isExitBlock()
				&& dest.getDelegate().isExitBlock()) {
			Log.log(2, "EXCE: " + src.toShortString() + " -> " + dest.toShortString());
			return true;
		}
		//Exceptional successors from method's CFG
		try {
			Collection<IExplodedBasicBlock> exceptionalSuccessors = 
					getCFG(src).getExceptionalSuccessors(src.getDelegate());
			if (exceptionalSuccessors.contains(dest.getDelegate())) {
				Log.log(2, "EXCE1: " + src.toShortString() + " -> " + dest.toShortString());
				return true;
			}
		}
		//Ugly: needed to suppress some exceptions
		catch(IllegalArgumentException np) {
			if (!src.getMethod().equals(dest.getMethod()) || (src.getNumber() != 0) || (dest.getNumber() != 1)) {
				throw np;
			}
		}
	
		if (!getCGNode(src).equals(getCGNode(dest))) {
			//If the edge spans between two methods, make sure that:
			//the starting vertex is the exit of a method, or 
			//a caller method
			if((!src.getDelegate().equals(this.getCFG(src).exit())) && 		//callee: any point - not the exit
			(!(src.getDelegate().getInstruction() instanceof SSAAbstractInvokeInstruction))) {
				return true;
			}				
		}
		
		return false;
	} 	

	public BasicBlockInContext<IExplodedBasicBlock> getExplodedBasicBlock(CGNode n, ProgramCounter pc) {
		ExplodedControlFlowGraph cfg = (ExplodedControlFlowGraph) this.getCFG(n);
		BasicBlockInContext<IExplodedBasicBlock> explodedBasicBlock = cfg.getExplodedBasicBlock(n, pc);
		return explodedBasicBlock;
	}

	public AbstractContext getComponent() {
		return absCtx;
	}


	public Set<Context> getContainingContext(BasicBlockInContext<IExplodedBasicBlock> bb) {
		CGNode cgNode = getCGNode(bb);
		return absCtx.getContainingContexts(cgNode);
	}
	
	

	//Super CFG will have to override these
	
	public abstract boolean isReturnFromContextEdge(BasicBlockInContext<IExplodedBasicBlock> bb1,
			BasicBlockInContext<IExplodedBasicBlock> bb2);

	public abstract boolean isCallToContextEdge(BasicBlockInContext<IExplodedBasicBlock> src,
			BasicBlockInContext<IExplodedBasicBlock> dest);

	public abstract boolean isCallToContext(BasicBlockInContext<IExplodedBasicBlock> src);
	
	abstract public Context returnFromContext(BasicBlockInContext<IExplodedBasicBlock> src);
	
	abstract public Context getCalleeContext(BasicBlockInContext<IExplodedBasicBlock> bb);
	
	abstract public Set<BasicBlockInContext<IExplodedBasicBlock>> getContextExit(Context c);
	
	
}

