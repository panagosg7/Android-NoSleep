package edu.ucsd.energy.interproc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.HashSetMultiMap;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Acyclic;
import com.ibm.wala.util.graph.Path;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;

import edu.ucsd.energy.component.AbstractComponent;
import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.managers.GlobalManager;

public class SuperContextCFG extends AbstractComponentCFG {

	private static final int DEBUG = 0;

	//Keeps all the edges that connect different contexts
	//Helps distinguish from function calls
	protected HashSetMultiMap<BasicBlockInContext<IExplodedBasicBlock>, BasicBlockInContext<IExplodedBasicBlock>> mContextCall = 
			new HashSetMultiMap<BasicBlockInContext<IExplodedBasicBlock>,BasicBlockInContext<IExplodedBasicBlock>>();

	protected HashSetMultiMap<BasicBlockInContext<IExplodedBasicBlock>, BasicBlockInContext<IExplodedBasicBlock>> mContextReturnEdge = 
			new HashSetMultiMap<BasicBlockInContext<IExplodedBasicBlock>, BasicBlockInContext<IExplodedBasicBlock>>();

	private Map<BasicBlockInContext<IExplodedBasicBlock>, Component> mCallerToContext = 
			new HashMap<BasicBlockInContext<IExplodedBasicBlock>, Component>();

	private Map<BasicBlockInContext<IExplodedBasicBlock>, Component> mContextReturn = 
			new HashMap<BasicBlockInContext<IExplodedBasicBlock>, Component>();

	private HashSetMultiMap<Component, BasicBlockInContext<IExplodedBasicBlock>> mContextToExit = 
			new HashSetMultiMap<Component, BasicBlockInContext<IExplodedBasicBlock>>();

	public boolean isCallToComponentEdge(BasicBlockInContext<IExplodedBasicBlock> a, BasicBlockInContext<IExplodedBasicBlock> b) {
		Set<BasicBlockInContext<IExplodedBasicBlock>> b1 = mContextCall.get(a);
		return ((b1!=null)?b1.contains(b):false);
	}

	public boolean isCallToComponent(BasicBlockInContext<IExplodedBasicBlock> a) {
		return mContextCall.containsKey(a);
	}

	public boolean isReturnFromComponentEdge(BasicBlockInContext<IExplodedBasicBlock> a, BasicBlockInContext<IExplodedBasicBlock> b) {
		Set<BasicBlockInContext<IExplodedBasicBlock>> b1 = mContextReturnEdge.get(a);
		return ((b1!=null)?b1.contains(b):false);
	}

	public Component returnFromComponent(BasicBlockInContext<IExplodedBasicBlock> a) {
		return mContextReturn.get(a);
	}

	public Component getCalleeComponent(BasicBlockInContext<IExplodedBasicBlock> bb) {
		return mCallerToContext.get(bb);
	}
	
	
	public Set<BasicBlockInContext<IExplodedBasicBlock>> getContextExit(Component c) {
		return mContextToExit.get(c);
	}

	public SuperContextCFG(
			//the abstract component
			AbstractComponent component,
			//Pairs of edges within the same context
			Set<Pair<CGNode, CGNode>> packedEdges,
			//Edges between different contexts
			Map<SSAInstruction, Component> seeds) {

		super(component.getContextCallGraph());
		this.absCtx = component;
		this.callgraph = component.getContextCallGraph();
		/* Will only work like this - loses laziness. */
		constructFullGraph();

		//For the implicit edges within components
		cacheCallbacks(packedEdges);
		addReturnToEntryEdge(packedEdges);
		//Add edges from Intent calls etc
		//Using as packed edges the total of the edges for every component
		addCallToEntryAndReturnEdges(seeds);

	}

	
	/**
	 * This method adds edges that refer to Intent, Thread and Handler posts etc.
	 * @param map
	 */
	private void addCallToEntryAndReturnEdges(Map<SSAInstruction, Component> map) {
		Iterator<BasicBlockInContext<IExplodedBasicBlock>> iterator = iterator();
		while(iterator.hasNext()) {
			BasicBlockInContext<IExplodedBasicBlock> caller = iterator.next();
			SSAInstruction instr = caller.getDelegate().getInstruction();
			Component targetComp = map.get(instr);
			if (targetComp != null) {
				if (DEBUG > 0) {
					System.out.println();
					System.out.println("Adding ASYNC: " + caller.toString() + " to " + 
							targetComp.toString());	
				}

				if (!(instr instanceof SSAInvokeInstruction)) continue;
				SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;					
				Selector callSelector = inv.getDeclaredTarget().getSelector();

				//Adding a sanity check: if we enter a component there must at least 
				//one edge exiting it.
				boolean enter = false;
				boolean exit  = false;

				//Exit points might depend on the call method to the specific component
				//Return edges must be added first to avoid having the entry method as
				//a successor of the invoke instruction
				Set<Selector> exitPoints = targetComp.getExitPoints(callSelector);
				for (Selector exitSel : exitPoints) {
					Set<CallBack> callBacks = targetComp.getNextCallBack(exitSel, false); 
					//					CallBack callBack = targetComp.getCallBack(exitSel);	//this missed some cases...
					//Continue only if this callback is indeed overridden
					for (CallBack callBack : callBacks) {
						if (callBack != null) {
							CGNode node = callBack.getNode();
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
									exit = true;
									mContextReturnEdge.put(src, returnBB);
									mContextToExit.put(targetComp, src);
									mContextReturn.put(src, targetComp);
									if (DEBUG > 1) {
										System.err.println("Adding to: " + targetComp.toString());
										System.out.println("Adding ASYNC CTX RETURN: " + src.toShortString() +
												" -> " + returnBB.toShortString());
									}
								}
							}
						}
					}
				}
				Set<Selector> entryPoints = targetComp.getEntryPoints(callSelector);
				for (Selector sel : entryPoints) {
					Set<CallBack> callBacks = targetComp.getNextCallBack(sel, false);
					//					CallBack callBack = targetComp.getCallBack(sel);	//this missed some cases...
					for (CallBack callBack : callBacks) {
						if (callBack != null) {
							CGNode node = callBack.getNode();
							ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> destCFG = getCFG(node);				     
							IExplodedBasicBlock destIEBB = destCFG.entry();				     
							BasicBlockInContext<IExplodedBasicBlock> dest =
									new BasicBlockInContext<IExplodedBasicBlock>(node, destIEBB);
							addEdge(caller,dest);
							enter = true;
							if (DEBUG > 1) {
								System.out.println("Adding ASYNC CTX CALL: " + caller.toShortString() + 
										" -> " + dest.toShortString());
							}
							mContextCall.put(caller,dest);
							mCallerToContext .put(caller,targetComp);
						}
					}
				}

				//Sanity check: enter and exit should take the same values
				if (enter ^ exit) {
					Assertions.productionAssertion(enter, caller.toString() + " calls " + 
							targetComp.toString() +	" but does not enter");
					Assertions.productionAssertion(exit, caller.toString() + " calls " + 
							targetComp.toString() +	" but does not return from it");
				}
			}
		}
	}


	@Override
	public boolean isContextExit(BasicBlockInContext<IExplodedBasicBlock> a) {
		return mContextReturn.containsKey(a);
	}

}

