package energy.interproc;

import java.util.Collection;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IMergeFunction;
import com.ibm.wala.dataflow.IFDS.IPartiallyBalancedFlowFunctions;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.dataflow.IFDS.KillEverything;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationProblem;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationSolver;
import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableMapping;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import energy.components.RunnableThread;
import energy.util.E;

public class ContextSensitiveLocking {

	/**
	 * the supergraph over which tabulation is performed
	 */
	private final ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph;

	/**
	 * the tabulation domain
	 */
	private final TabDomain domain = new TabDomain();

	/**
	 * The underlaying Inter-procedural Control Flow Graph
	 */
	private SensibleExplodedInterproceduralCFG icfg;

	/**
	 * We are going to extend the ICFGSupergraph that WALA had for reaching
	 * definitions to that we are able to define our own sensible supergraph
	 * (containing extra edges).
	 * 
	 * @author pvekris
	 * 
	 */
	public class SensibleICFGSupergraph extends ICFGSupergraph {
		protected SensibleICFGSupergraph(ExplodedInterproceduralCFG icfg,
				AnalysisCache cache) {
			super(icfg, cache);

		}
	}

	public ContextSensitiveLocking(SensibleExplodedInterproceduralCFG icfg) {
		/*
		 * We already have created our SensibleExplodedInterproceduralCFG which
		 * contains extra nodes compared to the exploded i-cfg WALA usually
		 * creates
		 */
		AnalysisCache cache = new AnalysisCache();
		this.icfg = icfg;
		this.supergraph = new SensibleICFGSupergraph(icfg, cache);
	}

	/**
	 * Useful functions
	 */

	private boolean isWLReleaseCall(BasicBlockInContext<IExplodedBasicBlock> bb) {
		final IExplodedBasicBlock ebb = bb.getDelegate();
		SSAInstruction instruction = ebb.getInstruction();
		if (instruction instanceof SSAInvokeInstruction) {
			final SSAInvokeInstruction invInstr = (SSAInvokeInstruction) instruction;
			String methSig = invInstr.getDeclaredTarget().getSignature()
					.toString();
			if (methSig.equals("android.os.PowerManager$WakeLock.release()V")) {
				return true;
			}
		}
		return false;
	}

	private boolean isWLAcquireCall(BasicBlockInContext<IExplodedBasicBlock> bb) {
		final IExplodedBasicBlock ebb = bb.getDelegate();
		SSAInstruction instruction = ebb.getInstruction();
		if (instruction instanceof SSAInvokeInstruction) {
			final SSAInvokeInstruction invInstr = (SSAInvokeInstruction) instruction;
			String methSig = invInstr.getDeclaredTarget().getSignature()
					.toString();
			if (methSig.equals("android.os.PowerManager$WakeLock.acquire()V") ||
				methSig.equals("android.os.PowerManager$WakeLock.acquire(J)V")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Get the method reference called by a bb (null if not a call site)
	 * 
	 * @param bb
	 * @return
	 */
	private MethodReference getCalledMethodReference(BasicBlockInContext<IExplodedBasicBlock> bb) {
		final IExplodedBasicBlock ebb = bb.getDelegate();
		SSAInstruction instruction = ebb.getInstruction();
		if (instruction instanceof SSAInvokeInstruction) {
			final SSAInvokeInstruction invInstr = (SSAInvokeInstruction) instruction;
			return invInstr.getDeclaredTarget();
		}
		return null;
	}
	
	/**
	 * Check if we have an entry for this in the bb -> thread map
	 * @param bb
	 * @return
	 */
	private LockState getCalledRunnable(BasicBlockInContext<IExplodedBasicBlock> bb) {
		
		RunnableThread calleeThread = icfg.getThreadInvocations(bb);
		
		if (calleeThread != null) {
			Assertions.productionAssertion(calleeThread.isSolved);
			LockState threadExitState = calleeThread.getThreadExitState();
			
			E.log(2, calleeThread.toString() + " :: " + threadExitState.toString() );
			
			return threadExitState;	
		}

		return null;
		
		 
	}		
	

	/**
	 * Domain is the answer to the questions: (maybe acquired, must be acquired,
	 * maybe released, must be released)
	 * 
	 * @author pvekris
	 */
	private class TabDomain extends MutableMapping<LockState>
			implements
			TabulationDomain<LockState, BasicBlockInContext<IExplodedBasicBlock>> {

		public boolean hasPriorityOver(
				PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p1,
				PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p2) {
			// don't worry about worklist priorities
			return false;
		}
	}

	private class LockingFunctions
			implements
			IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> {

		private static final int PRINT_EXCEPTIONAL = 2;
		private final TabDomain domain;

		protected LockingFunctions(TabDomain domain) {
			this.domain = domain;
		}

		@Override
		public IUnaryFlowFunction getNormalFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			/**
			 * Exceptional edges should be treated as normal in cases where no
			 * acquire/release is involved. BE CAREFUL : exceptional edges that
			 * span across multiple procedures (if possible) cannot be
			 * determined.
			 */
			/*
			 * if (isExceptionalEdge(src, dest)) { E.log(2, "Killing [" +
			 * src.toString() + " -> " + dest.toString() +"]"); return
			 * KillEverything.singleton(); }
			 */
			return IdentityFlowFunction.identity();
		}

		@Override
		public IUnaryFlowFunction getCallFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest,
				BasicBlockInContext<IExplodedBasicBlock> ret) {

			return IdentityFlowFunction.identity();
		}

		@Override
		public IFlowFunction getReturnFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> call,
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			/**
			 * Exceptional edges should be treated as normal in cases where no
			 * acquire/release is involved. BE CAREFUL : exceptional edges that
			 * span across multiple procedures are not determined.
			 */
			if (isExceptionalEdge(call, dest) && isWLAcquireCall(call)) {
				E.log(PRINT_EXCEPTIONAL, "Killing [" + src.toString() + " -> "
						+ dest.toString() + "]");
				return KillEverything.singleton();
			}
			if (isWLAcquireCall(call) || isWLReleaseCall(call)) {
				return KillEverything.singleton();
			}
			return IdentityFlowFunction.identity();
		}

		/**
		 * Flow function from call node to return node at a call site when
		 * callees exist.
		 */
		@Override
		public IUnaryFlowFunction getCallToReturnFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			E.log(2, "[" + src.toString() + " -> " + dest.toString() + "]");
			/**
			 * Exceptional edges should be treated as normal in cases where no
			 * acquire/release is involved. BE CAREFUL : exceptional edges that
			 * span across multiple procedures are not determined.
			 */
			if (isExceptionalEdge(src, dest) && isWLAcquireCall(src)) {
				E.log(PRINT_EXCEPTIONAL, "Killing [" + src.toString() + " -> "
						+ dest.toString() + "]");
				return KillEverything.singleton();
			}

			if (isWLAcquireCall(src)) {
				return new IUnaryFlowFunction() {
					@Override
					public IntSet getTargets(int d1) {
						LockState fact = new LockState(true, true, false, false);
						int factNum = domain.getMappedIndex(fact);
						MutableSparseIntSet result = MutableSparseIntSet
								.makeEmpty();
						result.add(factNum);
						return result;
					}
				};
			}

			if (isWLReleaseCall(src)) {
				E.log(2, dest.toString() + " Propagating Released");
				return new IUnaryFlowFunction() {
					@Override
					public IntSet getTargets(int d1) {
						LockState fact = new LockState(false, false, true, true);
						// int factNum = domain.add(fact);
						int factNum = domain.getMappedIndex(fact);
						MutableSparseIntSet result = MutableSparseIntSet
								.makeEmpty();
						result.add(factNum);
						E.log(2, "Propagating Released. fact: "
								+ fact.toString() + " result: "
								+ result);
						return result;
					}
				};
			}
			return KillEverything.singleton();
		}

		@Override
		public IUnaryFlowFunction getCallNoneToReturnFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			/**
			 * if we're missing callees, just keep what information we have.
			 * These are cases where we don't have the code for the callee, e.g.
			 * android, java, library code. Acquires and releases are not
			 * handled here.
			 * 
			 * Also take thread info into account. 
			 */
			
			final LockState threadExitState = getCalledRunnable(src);
			

			if (threadExitState != null) {

				E.log(2, "Call to: " + threadExitState.toString());			
					
				return new IUnaryFlowFunction() {

					@Override
					public IntSet getTargets(int d1) {
						MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
						result.add(d1);
						
						int threadIndex = domain.add(threadExitState);
						int mergedState = mergeStates(result, threadIndex);
						result.clear();
						result.add(mergedState);
						return result;
												
					}
				};
			}
									
			return IdentityFlowFunction.identity();
		}


		private boolean isExceptionalEdge(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			/* Must belong to the same method */
			if (icfg.getCGNode(src).equals(icfg.getCGNode(dest))) {
				ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg = icfg
						.getCFG(src);
				IExplodedBasicBlock d = src.getDelegate();
				Collection<IExplodedBasicBlock> normalSuccessors = cfg
						.getNormalSuccessors(d);
				int nsn = normalSuccessors.size();
				int snc = cfg.getSuccNodeCount(d);
				if (nsn != snc) {
					IExplodedBasicBlock dst = dest.getDelegate();
					return (!normalSuccessors.contains(dst));
				}
			}
			return false;
		}

		@Override
		public IFlowFunction getUnbalancedReturnFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			return IdentityFlowFunction.identity();
		}

	}
	
	protected int mergeStates(IntSet x, int j) {
		IntIterator it = x.intIterator();
		LockState n = domain.getMappedObject(j);
		StringBuffer sb = new StringBuffer();
		sb.append("Merging: " + n.toString());
		while (it.hasNext()) {
			int i = it.next();
			LockState q = domain.getMappedObject(i);
			n = n.merge(q);
			sb.append(" + " + q.toString());
		}
		sb.append(" -> " + n.toString());
		E.log(2, sb.toString());
		return domain.add(n);
		
	}

	

	private class LockingProblem
			implements
			PartiallyBalancedTabulationProblem<BasicBlockInContext<IExplodedBasicBlock>, CGNode, LockState> {
		private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> initialSeeds = collectInitialSeeds();
		private IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> flowFunctions = new LockingFunctions(
				domain);

		@Override
		public ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> getSupergraph() {
			return supergraph;
		}

		/**
		 * Define the set of path edges to start propagation with.
		 * 
		 * @return
		 */
		private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> collectInitialSeeds() {
			Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> result = HashSetFactory
					.make();

			for (BasicBlockInContext<IExplodedBasicBlock> bb : supergraph) {
				if (isWLAcquireCall(bb)) {
					LockState fact = new LockState(true, true, false, false);
					int factNum = domain.add(fact);
					final CGNode cgNode = bb.getNode();
					BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(cgNode);
					// note that the fact number used for the source of this
					// path edge
					// doesn't really matter
					result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb,
							factNum));
				}
				if (isWLReleaseCall(bb)) {
					E.log(2, bb.toString() + " Adding release fact");
					LockState fact = new LockState(false, false, true, true);
					int factNum = domain.add(fact);
					final CGNode cgNode = bb.getNode();
					BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(cgNode);
					// note that the fact number used for the source of this
					// path edge doesn't really matter
					result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb,
							factNum));
				}
				if ((supergraph.getPredNodeCount(bb) == 0)) {
					E.log(2, bb.toString() + " Adding entry point");
					LockState fact = new LockState(false, false, false, false);
					int factNum = domain.add(fact);
					final CGNode cgNode = bb.getNode();
					BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(cgNode);
					// note that the fact number used for the source of this
					// path edge
					// doesn't really matter
					result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb,
							factNum));
				}
			}
			E.log(2, "Collected: " + result.size());
			return result;
		}

		@Override
		public TabulationDomain<LockState, BasicBlockInContext<IExplodedBasicBlock>> getDomain() {
			return domain;
		}

		@Override
		public Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> initialSeeds() {
			return initialSeeds;
		}

		@Override
		public IMergeFunction getMergeFunction() {
			return new IMergeFunction() {

				@Override
				public int merge(IntSet x, int j) {					
					return mergeStates(x, j);
				}
			};
		}

		@Override
		public IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> getFunctionMap() {
			return flowFunctions;
		}

		@Override
		public BasicBlockInContext<IExplodedBasicBlock> getFakeEntry(
				BasicBlockInContext<IExplodedBasicBlock> n) {
			final CGNode cgNode = n.getNode();
			return getFakeEntry(cgNode);

		}

		private BasicBlockInContext<IExplodedBasicBlock> getFakeEntry(
				CGNode cgNode) {
			BasicBlockInContext<IExplodedBasicBlock>[] entriesForProcedure = supergraph
					.getEntriesForProcedure(cgNode);
			assert entriesForProcedure.length == 1;
			return entriesForProcedure[0];
		}

	}

	/**
	 * perform the tabulation analysis and return the {@link TabulationResult}
	 */
	public TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, LockState> analyze() {

		PartiallyBalancedTabulationSolver<BasicBlockInContext<IExplodedBasicBlock>, CGNode, LockState> solver = PartiallyBalancedTabulationSolver
				.createPartiallyBalancedTabulationSolver(new LockingProblem(),
						null);

		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, LockState> result = null;

		try {
			result = solver.solve();
		} catch (CancelException e) {
			// this shouldn't happen
			assert false;
		}
		return result;

	}

	public ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> getSupergraph() {
		return supergraph;
	}

	public TabulationDomain<LockState, BasicBlockInContext<IExplodedBasicBlock>> getDomain() {
		return domain;
	}

}
