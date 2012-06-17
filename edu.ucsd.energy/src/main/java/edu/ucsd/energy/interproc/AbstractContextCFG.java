package edu.ucsd.energy.interproc;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.component.AbstractComponent;
import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.util.E;

/**
 * This is an exploded inter-procedural CFG with extra edges connecting 
 * methods specified at the creation. 
 */
abstract public class AbstractContextCFG extends ExplodedInterproceduralCFG {

	private static final int DEBUG = 2;

	protected AbstractComponent component;

	protected CallGraph callgraph;

	protected Map<String,CGNode> callbacks;

	//Keeps all the edges that connect different contexts
	//Helps distinguish from function calls
	protected Set<Pair<BasicBlockInContext<IExplodedBasicBlock>,BasicBlockInContext<IExplodedBasicBlock>>> sPropagateState = 
			new HashSet<Pair<BasicBlockInContext<IExplodedBasicBlock>,BasicBlockInContext<IExplodedBasicBlock>>>();

	protected Set<Pair<BasicBlockInContext<IExplodedBasicBlock>,BasicBlockInContext<IExplodedBasicBlock>>> sContextReturn = 
			new HashSet<Pair<BasicBlockInContext<IExplodedBasicBlock>,BasicBlockInContext<IExplodedBasicBlock>>>();

	//We need to keep these nodes so that the tabulation solver know that he has to 
	//propagate the state (these are going to be unbalanced seeds)
	protected Set<BasicBlockInContext<IExplodedBasicBlock>> sLifecycleEdge = 
			new HashSet<BasicBlockInContext<IExplodedBasicBlock>>();


	public AbstractContextCFG(CallGraph cg) {
		super(cg);
	}

	public boolean isCallToContext(BasicBlockInContext<IExplodedBasicBlock> a, BasicBlockInContext<IExplodedBasicBlock> b) {
		return sPropagateState.contains(Pair.make(a, b));
	}

	public boolean isReturnFromContext(BasicBlockInContext<IExplodedBasicBlock> a, BasicBlockInContext<IExplodedBasicBlock> b) {
		return sContextReturn.contains(Pair.make(a, b));
	}
	
	/**
	 * It suffices to account just for the exit point of a node that is important for the 
	 * lifecycle of the context 
	 */
	public boolean isLifecycleExit(BasicBlockInContext<IExplodedBasicBlock> a) {
		return sLifecycleEdge.contains(a);
	}

	/**
	 * This method adds edges that refer to Intent, Thread and Handler posts etc.
	 * @param map
	 */
	protected void addCallToEntryAndReturnEdges(Map<SSAInstruction, Context> map) {
		Iterator<BasicBlockInContext<IExplodedBasicBlock>> iterator = iterator();
		while(iterator.hasNext()) {
			BasicBlockInContext<IExplodedBasicBlock> caller = iterator.next();
			SSAInstruction instr = caller.getDelegate().getInstruction();
			Context targetComp = map.get(instr);
			if (targetComp != null) {

				//For the moment, we propagate the information from all exit points
				//Return edges must be added first to avoid having the entry method as
				//a successor of the invoke instruction

				Set<Selector> exitPoints = targetComp.getExitPoints();
				E.log(1, "Adding ASYNC CALL return edges : " + caller.toShortString());
				//E.log(1, "To: " + targetComp.toString());
				for (Selector sel : exitPoints) {
					CallBack callBack = targetComp.getCallBack(sel);
					//Continue only if this callback is indeed overridden
					if (callBack != null) {
						//E.log(1, "\tTO: " + callBack.toString());
						CGNode node = callBack.getNode();
						//addCalleeEdgesForCall(node, caller);
						
						ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> srcCFG = getCFG(node);				     
						IExplodedBasicBlock srcIEBB = srcCFG.exit();
						BasicBlockInContext<IExplodedBasicBlock> src =
								new BasicBlockInContext<IExplodedBasicBlock>(node, srcIEBB);
						for (Iterator<BasicBlockInContext<IExplodedBasicBlock>> succIter = 
								getSuccNodes(caller); succIter.hasNext();) {
							BasicBlockInContext<IExplodedBasicBlock> returnBB = succIter.next();
							//Eliminate edges from called context to the exception catch node 
							//of the calling context
							if (!isExceptionalEdge(caller, returnBB)) {
								addEdge(src, returnBB);
								sContextReturn.add(Pair.make(src, returnBB));
								E.log(1, "ADDING ASYNC CTX RETURN: " + src.toShortString() +
										" -> " + returnBB.toShortString());
							}
						}
					}
				}
				Set<Selector> entryPoints = targetComp.getEntryPoints();
				
				for (Selector sel : entryPoints) {
					CallBack callBack = targetComp.getCallBack(sel);
					//Continue only if this callback is indeed overridden
					if (callBack != null) {
						CGNode node = callBack.getNode();
						ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> destCFG = getCFG(node);				     
						IExplodedBasicBlock destIEBB = destCFG.entry();				     
						BasicBlockInContext<IExplodedBasicBlock> dest =
								new BasicBlockInContext<IExplodedBasicBlock>(node, destIEBB);
						addEdge(caller,dest);
						E.log(DEBUG, "Adding ASYNC CTX CALL: " + caller.toShortString() + 
								" -> " + dest.toShortString());
						sPropagateState.add(Pair.make(caller,dest));
					}
				}
			}
		}
	}


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
		if (isCallToContext(src, dest) || isReturnFromContext(src, dest)) {
			return false;
		}

		//Edge from the exit of a method to the exit of an other one is
		//probably an exceptional one.
		if((!src.getMethod().equals(dest.getMethod())) && src.getDelegate().isExitBlock()
				&& dest.getDelegate().isExitBlock()) {
			E.log(2, "EXCE: " + src.toShortString() + " -> " + dest.toShortString());
			return true;
		}
		//Exceptional successors from method's CFG
		try {
			Collection<IExplodedBasicBlock> exceptionalSuccessors = 
					getCFG(src).getExceptionalSuccessors(src.getDelegate());
			if (exceptionalSuccessors.contains(dest.getDelegate())) {
				E.log(2, "EXCE1: " + src.toShortString() + " -> " + dest.toShortString());
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

	public AbstractComponent getComponent() {
		return component;
	}
}

