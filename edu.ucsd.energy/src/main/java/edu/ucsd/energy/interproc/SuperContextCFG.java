package edu.ucsd.energy.interproc;

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

import edu.ucsd.energy.component.AbstractContext;
import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.contexts.Context;

public class SuperContextCFG extends AbstractContextCFG {

	private static final int DEBUG = 2;

	//Keeps all the edges that connect different contexts
	//Helps distinguish from function calls
	protected HashSetMultiMap<BasicBlockInContext<IExplodedBasicBlock>, BasicBlockInContext<IExplodedBasicBlock>> mContextCall = 
			new HashSetMultiMap<BasicBlockInContext<IExplodedBasicBlock>,BasicBlockInContext<IExplodedBasicBlock>>();
	
	protected HashSetMultiMap<BasicBlockInContext<IExplodedBasicBlock>, BasicBlockInContext<IExplodedBasicBlock>> mContextReturnEdge = 
			new HashSetMultiMap<BasicBlockInContext<IExplodedBasicBlock>, BasicBlockInContext<IExplodedBasicBlock>>();

	private Map<BasicBlockInContext<IExplodedBasicBlock>, Context> mCallerToContext = 
			new HashMap<BasicBlockInContext<IExplodedBasicBlock>, Context>();
	
	private Map<BasicBlockInContext<IExplodedBasicBlock>, Context> mContextReturn = 
			new HashMap<BasicBlockInContext<IExplodedBasicBlock>, Context>();
	
	private HashSetMultiMap<Context, BasicBlockInContext<IExplodedBasicBlock>> mContextToExit = 
			new HashSetMultiMap<Context, BasicBlockInContext<IExplodedBasicBlock>>();
	
	public boolean isCallToContextEdge(BasicBlockInContext<IExplodedBasicBlock> a, BasicBlockInContext<IExplodedBasicBlock> b) {
		Set<BasicBlockInContext<IExplodedBasicBlock>> b1 = mContextCall.get(a);
		return ((b1!=null)?b1.contains(b):false);
	}
	
	public boolean isCallToContext(BasicBlockInContext<IExplodedBasicBlock> a) {
		return mContextCall.containsKey(a);
	}
	
	public boolean isReturnFromContextEdge(BasicBlockInContext<IExplodedBasicBlock> a, BasicBlockInContext<IExplodedBasicBlock> b) {
		Set<BasicBlockInContext<IExplodedBasicBlock>> b1 = mContextReturnEdge.get(a);
		return ((b1!=null)?b1.contains(b):false);
	}
	
	public Context returnFromContext(BasicBlockInContext<IExplodedBasicBlock> a) {
		return mContextReturn.get(a);
	}
	
	public Context getCalleeContext(BasicBlockInContext<IExplodedBasicBlock> bb) {
		return mCallerToContext.get(bb);
	}
	
	public Set<BasicBlockInContext<IExplodedBasicBlock>> getContextExit(Context c) {
		return mContextToExit.get(c);
	}
	
	public SuperContextCFG(
		//the abstract component
		AbstractContext component,
		//Pairs of edges within the same context
		Set<Pair<CGNode, CGNode>> packedEdges,
		//Edges between different contexts
		Map<SSAInstruction, Context> seeds) {
		
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
	private void addCallToEntryAndReturnEdges(Map<SSAInstruction, Context> map) {
		Iterator<BasicBlockInContext<IExplodedBasicBlock>> iterator = iterator();
		while(iterator.hasNext()) {
			BasicBlockInContext<IExplodedBasicBlock> caller = iterator.next();
			SSAInstruction instr = caller.getDelegate().getInstruction();
			Context targetComp = map.get(instr);
			if (targetComp != null) {
				if (DEBUG > 0) {
					System.out.println("Adding ASYNC: " + caller.toString() + " to " + 
							targetComp.toString());	
				}
				
				if (!(instr instanceof SSAInvokeInstruction)) continue;
				SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;					
				Selector callSelector = inv.getDeclaredTarget().getSelector();
				
				//Exit points might depend on the call method to the specific component
				//Return edges must be added first to avoid having the entry method as
				//a successor of the invoke instruction
				Set<Selector> exitPoints = targetComp.getExitPoints(callSelector);
				//E.log(1, "To: " + targetComp.toString());
				for (Selector exitSel : exitPoints) {
					CallBack callBack = targetComp.getCallBack(exitSel);
					//Continue only if this callback is indeed overridden
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
				Set<Selector> entryPoints = targetComp.getEntryPoints(callSelector);
				
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
						if (DEBUG > 1) {
							System.out.println("Adding ASYNC CTX CALL: " + caller.toShortString() + 
								" -> " + dest.toShortString());
						}
						mContextCall.put(caller,dest);
						mCallerToContext .put(caller,targetComp);
					}
				}
			}
		}
	}

	@Override
	public boolean isContextExit(BasicBlockInContext<IExplodedBasicBlock> a) {
		return mContextReturn.containsKey(a);
	}


	
}

