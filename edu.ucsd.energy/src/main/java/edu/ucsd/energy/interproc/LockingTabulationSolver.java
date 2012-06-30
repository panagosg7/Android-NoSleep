package edu.ucsd.energy.interproc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.dataflow.IFDS.CallFlowEdges;
import com.ibm.wala.dataflow.IFDS.IBinaryReturnFlowFunction;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationSolver;
import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.IFDS.TabulationProblem;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Collection;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetAction;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.CtxSensLocking.LockingProblem;
import edu.ucsd.energy.managers.WakeLockInstance;


public class LockingTabulationSolver  extends PartiallyBalancedTabulationSolver<
BasicBlockInContext<IExplodedBasicBlock>, 
CGNode, Pair<WakeLockInstance, SingleLockState>> {

	//Toggle this to enable context sensitive asynchronous calls to contexts
	private static final boolean ENABLE_CTX_SENS = false;
	
	private static final int DEBUG = 1;

	private AbstractContextCFG icfg;

	protected final LockingFlowFunctions flowFunctionMap;
	
	final protected Map<Context, ContextSummaryEdges> ctxSummaryEdges = HashMapFactory.make();

	protected LockingTabulationSolver(LockingProblem problem, IProgressMonitor monitor, AbstractContextCFG icfg) {
		super(problem, monitor);
		this.icfg = icfg;
		this.flowFunctionMap = problem.getFunctionMap();
	} 


	public LockingResult solve() throws CancelException {
		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, 
		CGNode, Pair<WakeLockInstance, SingleLockState>> result = super.solve();		
		return new LockingResult(result);
	}

	public class LockingResult implements TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, 
	CGNode, Pair<WakeLockInstance, SingleLockState>> {
		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<WakeLockInstance, SingleLockState>> result;

		LockingResult(TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<WakeLockInstance, SingleLockState>> r) {
			this.result = r;
		} 

		/**
		 * Get the a mapping with all the states with a single state for every field 
		 * @param n
		 * @return
		 */
		public HashMap<WakeLockInstance, SingleLockState> getMergedState(BasicBlockInContext<IExplodedBasicBlock> n) {
			HashMap<WakeLockInstance, SingleLockState> result = new  HashMap<WakeLockInstance, SingleLockState>();						
			IntSet orig = getResult(n);			
			TabulationDomain<Pair<WakeLockInstance, SingleLockState>, BasicBlockInContext<IExplodedBasicBlock>> dom =
					this.getProblem().getDomain();	
			for (IntIterator it = orig.intIterator(); it.hasNext(); ) {
				int i = it.next();
				Pair<WakeLockInstance, SingleLockState> iObj = dom.getMappedObject(i);
				SingleLockState already = result.get(iObj.fst);
				if (already != null) {
					result.put(iObj.fst, already.merge(iObj.snd));
				}
				else{
					result.put(iObj.fst, iObj.snd);			
				}			
			}
			return result;			
		}

		public IntSet getResult(BasicBlockInContext<IExplodedBasicBlock> node) {			
			return result.getResult(node);
		}

		public TabulationProblem<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<WakeLockInstance, SingleLockState>> getProblem() {
			return result.getProblem();
		}

		public Collection<BasicBlockInContext<IExplodedBasicBlock>> getSupergraphNodesReached() {
			return result.getSupergraphNodesReached();
		}

		public IntSet getSummaryTargets(
				BasicBlockInContext<IExplodedBasicBlock> n1, int d1,
				BasicBlockInContext<IExplodedBasicBlock> n2) {			
			return result.getSummaryTargets(n1, d1, n2);
		}

		public Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> getSeeds() {
			return result.getSeeds();
		}

	}

	//We also classify cases of propagating state to the next method in the lifecycle as unbalanced seeds.
	protected boolean wasUsedAsUnbalancedSeed(BasicBlockInContext<IExplodedBasicBlock> s_p, int i,
			BasicBlockInContext<IExplodedBasicBlock> n, int j) {
		boolean b = super.wasUsedAsUnbalancedSeed(s_p,i, n, j) ||	(icfg.isLifecycleExit(n));
		return b;		
	}

	
	protected boolean propagate(BasicBlockInContext<IExplodedBasicBlock> s_p, int i, 
			BasicBlockInContext<IExplodedBasicBlock> n, int j) {
		return super.propagate(s_p, i, n, j);
	}
	
	
	
	/**
	 * Handle lines [14 - 19] of the algorithm, propagating information into and across a call site.
	 * We need to implement the part of the tabulation algorithm that deals with asynchronous calls 
	 * to other contexts in a context sensitive way.
	 */
	protected void processCall(final PathEdge<BasicBlockInContext<IExplodedBasicBlock>> edge) {
		// This is an asynchronous call 
		BasicBlockInContext<IExplodedBasicBlock> callSite = edge.getTarget();
		if (ENABLE_CTX_SENS && icfg.isCallToContext(callSite)) {

			// c:= number of the call node
			final int c = supergraph.getNumber(callSite);
			if (DEBUG > 0) {
				System.err.println("Async call site: " + callSite.toString() + " number: " + c);
			}

			Collection<BasicBlockInContext<IExplodedBasicBlock>> allReturnSites = HashSetFactory.make();
			// XXX: populate allReturnSites with return sites for missing calls - do we need this here?
			for (Iterator<? extends BasicBlockInContext<IExplodedBasicBlock>> it =
					supergraph.getReturnSites(callSite, null); it.hasNext();) {
				BasicBlockInContext<IExplodedBasicBlock> retSite = it.next();
				allReturnSites.add(retSite);
				if (DEBUG > 0) {
					System.err.println("Return site: " + retSite.toString());
				}	
			}

			for (Iterator<? extends BasicBlockInContext<IExplodedBasicBlock>> it = supergraph.getCalledNodes(callSite); it.hasNext();) {
				final BasicBlockInContext<IExplodedBasicBlock> callee = it.next();
				if (DEBUG > 0) {
					System.err.println("callee: " + callee.toString());
				}	
				processParticularContextCallee(edge, c, allReturnSites, callee);
			}
			
			// special logic: in backwards problems, a "call" node can have
			// "normal" successors as well. deal with these.
			for (Iterator<? extends BasicBlockInContext<IExplodedBasicBlock>> it = 
					supergraph.getNormalSuccessors(callSite); it.hasNext();) {
				final BasicBlockInContext<IExplodedBasicBlock> m = it.next();
				if (DEBUG_LEVEL > 0) {
					System.err.println("normal successor: " + m);
				}
				IUnaryFlowFunction f = flowFunctionMap.getNormalFlowFunction(callSite, m);
				IntSet D3 = computeFlow(edge.getD2(), f);
				if (DEBUG_LEVEL > 0) {
					System.err.println("normal successor reached: " + D3);
				}
				if (D3 != null) {
					D3.foreach(new IntSetAction() {
						public void act(int d3) {
							propagate(edge.getEntry(), edge.getD1(), m, d3);
						}
					});
				}
			}

			// [17 - 19]
			// we modify this to handle each return site individually
			for (final BasicBlockInContext<IExplodedBasicBlock> returnSite : allReturnSites) {
				if (DEBUG_LEVEL > 0) {
					System.err.println(" my process return site: " + returnSite);
				}
				IUnaryFlowFunction f = flowFunctionMap.getCallToReturnFlowFunction(callSite, returnSite);
				IntSet reached = computeFlow(edge.getD2(), f);
				if (DEBUG_LEVEL > 0) {
					System.err.println("reached: " + reached);
				}
				if (reached != null) {
					reached.foreach(new IntSetAction() {
						public void act(int x) {
							assert x >= 0;
							assert edge.getD1() >= 0;
							propagate(edge.getEntry(), edge.getD1(), returnSite, x);
						}
					});
				}
			}
		}
		else {
			// Normal call
			super.processCall(edge);
		}
	}


	/**
	 * handle a particular called context for some call node.
	 * 
	 * @param edge the path edge being processed
	 * @param callNodeNum the number of the call node in the supergraph
	 * @param allReturnSites a set collecting return sites for the call. This set is mutated with the
	 * 				return sites for this callee.
	 * @param calleeEntry the entry node of the called context in question (i.e. the first block in
	 * 				an entry method of the called context)
	 */
	protected void processParticularContextCallee(
			final PathEdge<BasicBlockInContext<IExplodedBasicBlock>> edge, 
			final int callNodeNum, 
			Collection<BasicBlockInContext<IExplodedBasicBlock>> allReturnSites, 
			final BasicBlockInContext<IExplodedBasicBlock> calleeEntry) {

		if (DEBUG > 0) {
			System.err.println(" process called context: " + calleeEntry);
		}
		// reached := {d1} that reach the callee
		MutableSparseIntSet reached = MutableSparseIntSet.makeEmpty();

		final BasicBlockInContext<IExplodedBasicBlock> caller = edge.getTarget();
		
		//This should be ok: the return sites are the same regardless of whether 
		//if the call is asynchronous or not
		final Collection<BasicBlockInContext<IExplodedBasicBlock>> returnSitesForCallee = 
				Iterator2Collection.toSet(supergraph.getReturnSites(caller, 
						supergraph.getProcOf(calleeEntry)));

		allReturnSites.addAll(returnSitesForCallee);

		// we modify this to handle each return site individually. Some types of problems
		// compute different flow functions for each return site.
		for (final BasicBlockInContext<IExplodedBasicBlock> returnSite : returnSitesForCallee) {
			IUnaryFlowFunction f = flowFunctionMap.getAsyncCallFlowFunction(caller, calleeEntry, returnSite);
			IntSet r = computeFlow(edge.getD2(), f);
			if (r != null) {
				reached.addAll(r);
			}
		}
		// in some problems, we also want to consider flow into a callee that can never flow out
		// via a return. in this case, the return site is null.
		//XXX: not sure if this is necessary for the contexts
		IUnaryFlowFunction f = flowFunctionMap.getAsyncCallFlowFunction(caller, calleeEntry, null);
		IntSet r = computeFlow(edge.getD2(), f);
		if (r != null) {
			reached.addAll(r);
		}
		if (DEBUG > 0) {
			System.err.println(" reached: " + reached);
		}
		//If there is _some_ state to propagate
		if (reached != null) {

			final Context calledContext = icfg.getCalleeContext(caller);
			if(calledContext == null) {
				Assertions.UNREACHABLE();
				return;		//This might suffice
			}
			
			// Retrieve the summaries of a context
			final ContextSummaryEdges summaries = ctxSummaryEdges.get(calledContext);

			final CallFlowEdges callFlow = findOrCreateCallFlowEdges(calleeEntry);

			//we also need an identifier for the entry node
			final int s_p_num = supergraph.getNumber(calleeEntry);

			reached.foreach(new IntSetAction() {
				public void act(final int d1) {
					propagate(calleeEntry, d1, calleeEntry, d1);
					// cache the fact that we've flowed <c, d2> -> <callee, d1> by a call flow
					callFlow.addCallEdge(callNodeNum, edge.getD2(), d1);
					// handle summary edges now as well. this is different from the PoPL
					// 95 paper.
					if (summaries != null) {

						// for each exit from the called context
						Set<BasicBlockInContext<IExplodedBasicBlock>> exits = icfg.getContextExit(calledContext);

						for(final BasicBlockInContext<IExplodedBasicBlock> exit : exits) {
							
							if (DEBUG > 0) {
								assert supergraph.containsNode(exit);
							}

							if (DEBUG > 0) {
								System.err.println("Exit: " + exit.toString());
							}
							
							//Get global exit node too
							int x_num = supergraph.getNumber(exit);

							// reachedBySummary := {d2} s.t. <callee,d1> -> <exit,d2>
							// was recorded as a summary edge

							IntSet reachedBySummary = summaries.getSummaryEdges(s_p_num, x_num, d1);
							if (reachedBySummary != null) {
								for (final BasicBlockInContext<IExplodedBasicBlock> returnSite : returnSitesForCallee) {
									// if "exit" is a valid exit from the callee to the return
									// site being processed
									if (supergraph.hasEdge(exit, returnSite)) {
										final IFlowFunction retf = flowFunctionMap.getAsyncReturnFlowFunction(caller, exit, returnSite);
										reachedBySummary.foreach(new IntSetAction() {
											public void act(int d2) {
												if (retf instanceof IBinaryReturnFlowFunction) {
													final IntSet D5 = computeBinaryFlow(edge.getD2(), d2, (IBinaryReturnFlowFunction) retf);
													if (D5 != null) {
														D5.foreach(new IntSetAction() {
															public void act(int d5) {
																propagate(edge.getEntry(), edge.getD1(), returnSite, d5);
															}
														});
													}
												} else {
													final IntSet D5 = computeFlow(d2, (IUnaryFlowFunction) retf);
													if (D5 != null) {
														D5.foreach(new IntSetAction() {
															public void act(int d5) {
																propagate(edge.getEntry(), edge.getD1(), returnSite, d5);
															}
														});
													}
												}
											}
										});
									}
								}
							}
						}
					}
				}

			});
		}
	}
	
	
	protected void processExit(final PathEdge<BasicBlockInContext<IExplodedBasicBlock>> edge) {
		BasicBlockInContext<IExplodedBasicBlock> target = edge.getTarget();
		Context ctx = icfg.returnFromContext(target);
		//Check if this is a context return block
		if (ENABLE_CTX_SENS && (ctx != null)) {
			if (DEBUG > 0) {
	      System.err.println("process context exit: " + edge.getTarget());
	    }
	    
			final ContextSummaryEdges summaries = findOrCreateContextSummaryEdges(ctx);
	    int s_p_n = supergraph.getNumber(edge.getEntry());
	    int x 		= supergraph.getNumber(edge.getTarget());
	    if (DEBUG > 0) {
	    	System.err.println("entry: " + edge.getEntry().toString() + " target: " + edge.getTarget().toString());
	      System.err.println("s_p_n: " + s_p_n + " x: " + x );
	    }
	    
	    if (!summaries.contains(s_p_n, x, edge.getD1(), edge.getD2())) {
	      summaries.insertSummaryEdge(s_p_n, x, edge.getD1(), edge.getD2());
	    }

	    final CallFlowEdges callFlow = findOrCreateCallFlowEdges(edge.getEntry());
	    // [22] for each c /in callers(p)
	    IntSet callFlowSourceNodes = callFlow.getCallFlowSourceNodes(edge.getD1());
	    if (callFlowSourceNodes != null) {
	    	if (DEBUG > 0) {
          System.err.println("callFlowSources not null" );
        }
	      for (IntIterator it = callFlowSourceNodes.intIterator(); it.hasNext();) {
	        // [23] for each d4 s.t. <c,d4> -> <s_p,d1> occurred earlier
	        int globalC = it.next();
	        final IntSet D4 = callFlow.getCallFlowSources(globalC, edge.getD1());
	        if (DEBUG > 0) {
	          System.err.println("MISSING: Propagating to return" );
	        }
	        // [23] for each d5 s.t. <e_p,d2> -> <returnSite(c),d5> ...	        
	        //propagateToContextReturnSites(edge, supergraph.getNode(globalC), D4);
	      }
	    }
		}
		else {
			super.processExit(edge);
		}
	}

	
	
  /**
   * Propagate information for an "exit" edge to the appropriate return sites
   * 
   * [23] for each d5 s.t. <s_p,d2> -> <returnSite(c),d5> ..
   * 
   * @param edge the edge being processed
   * @param succ numbers of the nodes that are successors of edge.n (the return block in the callee) in the call graph.
   * @param c a call site of edge.s_p
   * @param D4 set of d1 s.t. <c, d1> -> <edge.s_p, edge.d2> was recorded as call flow
   */
	/*
  private void propagateToContextReturnSites(
  		final PathEdge<BasicBlockInContext<IExplodedBasicBlock>> edge, 
  		final BasicBlockInContext<IExplodedBasicBlock> c, final IntSet D4) {
    
  	P proc = supergraph.getProcOf(c);
    final T[] entries = supergraph.getEntriesForProcedure(proc);

    // we iterate over each potential return site;
    // we might have multiple return sites due to exceptions
    // note that we might have different summary edges for each
    // potential return site, and different flow functions from this
    // exit block to each return site.
    for (Iterator<? extends T> retSites = supergraph.getReturnSites(c, supergraph.getProcOf(edge.target)); retSites.hasNext();) {
      final T retSite = retSites.next();
      if (DEBUG_LEVEL > 0) {
        System.err.println("candidate return site: " + retSite + " " + supergraph.getNumber(retSite));
      }
      // note: since we might have multiple exit nodes for the callee, (to handle exceptional returns)
      // not every return site might be valid for this exit node (edge.n).
      // so, we'll filter the logic by checking that we only process reachable return sites.
      // the supergraph carries the information regarding the legal successors
      // of the exit node
      if (!supergraph.hasEdge(edge.target, retSite)) {
        continue;
      }
      if (DEBUG_LEVEL > 0) {
        System.err.println("feasible return site: " + retSite);
      }
      final IFlowFunction retf = flowFunctionMap.getReturnFlowFunction(c, edge.target, retSite);
      if (retf instanceof IBinaryReturnFlowFunction) {
        propagateToReturnSiteWithBinaryFlowFunction(edge, c, D4, entries, retSite, retf);
      } else {
        final IntSet D5 = computeFlow(edge.d2, (IUnaryFlowFunction) retf);
        if (DEBUG_LEVEL > 1) {
          System.err.println("D4" + D4);
          System.err.println("D5 " + D5);
        }
        IntSetAction action = new IntSetAction() {
          public void act(final int d4) {
            propToReturnSite(c, entries, retSite, d4, D5);
          }
        };
        D4.foreach(action);
      }
    }
  }
	 */
	
	protected ContextSummaryEdges findOrCreateContextSummaryEdges(Context proc) {
    ContextSummaryEdges result = ctxSummaryEdges.get(proc);
    if (result == null) {
      result = new ContextSummaryEdges();
      ctxSummaryEdges.put(proc, result);
    }
    return result;
  }
	

	/**
	 * These should be the return sites of the context call - successors of the new context caller
	 * @param target
	 * @return
	 */
	private Collection<BasicBlockInContext<IExplodedBasicBlock>> 
		getContextReturnSites(BasicBlockInContext<IExplodedBasicBlock> target) {
		return null;
	}

}
