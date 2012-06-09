package edu.ucsd.energy.interproc;

import java.util.Collection;

import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IMergeFunction;
import com.ibm.wala.dataflow.IFDS.IPartiallyBalancedFlowFunctions;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.dataflow.IFDS.KillEverything;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationProblem;
import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.dataflow.IFDS.UnorderedDomain;
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
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.analysis.SpecialConditions;
import edu.ucsd.energy.analysis.SpecialConditions.IsHeldCondition;
import edu.ucsd.energy.analysis.SpecialConditions.NullCondition;
import edu.ucsd.energy.analysis.SpecialConditions.SpecialCondition;
import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.contexts.RunnableThread;
import edu.ucsd.energy.interproc.LockingTabulationSolver.LockingResult;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.managers.WakeLockManager;
import edu.ucsd.energy.util.E;

public class CtxSensLocking {

	private static final int DEBUG = 2;

	/**
	 * the supergraph over which tabulation is performed
	 */
	private final ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph;

	
	private class TabDomain extends 
		UnorderedDomain<Pair<WakeLockInstance, SingleLockState>, 
						BasicBlockInContext<IExplodedBasicBlock>> {} 
	
	/**
	 * the tabulation domain
	 */
	private final TabDomain domain = new TabDomain();

	/**
	 * The underlaying Inter-procedural Control Flow Graph
	 */
	private AbstractContextCFG icfg;

	/**
	 * We are going to extend the ICFGSupergraph that WALA had for reaching
	 * definitions to that we are able to define our own sensible supergraph
	 * (containing extra edges).
	 */
	public class SensibleICFGSupergraph extends ICFGSupergraph {
		protected SensibleICFGSupergraph(ExplodedInterproceduralCFG icfg,
				AnalysisCache cache) {
			super(icfg, cache);
		}
	}

	public CtxSensLocking(AbstractContextCFG icfg) {
		/*
		 * We already have created our SensibleExplodedInterproceduralCFG which
		 * contains extra nodes compared to the exploded i-cfg WALA usually
		 * creates
		 */
		AnalysisCache cache = new AnalysisCache();
		this.icfg = icfg;
		this.supergraph = new SensibleICFGSupergraph(icfg, cache);
		this.wakeLockManager = icfg.getComponent().getGlobalManager().getWakeLockManager();
	}

	private WakeLockManager wakeLockManager;


	/** 
	 * These functions will return a field reference or null if something went wrong
	 * @param bb
	 * @return null if this is not an acquire operation
	 */
	private WakeLockInstance acquire(BasicBlockInContext<IExplodedBasicBlock> bb) {
		return lockingCall(bb, Interesting.wakelockAcquire);
	}

	private WakeLockInstance timedAcquire(BasicBlockInContext<IExplodedBasicBlock> bb) {
		return lockingCall(bb, Interesting.wakelockTimedAcquire);
	}

	private WakeLockInstance release(BasicBlockInContext<IExplodedBasicBlock> bb) {
		return lockingCall(bb, Interesting.wakelockRelease);
	}

	private WakeLockInstance lockingCall(BasicBlockInContext<IExplodedBasicBlock> bb, MethodReference wakelockRelease) {		
		SSAInstruction instruction = bb.getDelegate().getInstruction();
		CGNode node = bb.getNode();
		WakeLockInstance wli = wakeLockManager.getInstance(instruction, node);
		if (wli != null) {
			if (instruction instanceof SSAInvokeInstruction) {
				SSAInvokeInstruction inv = (SSAInvokeInstruction) instruction;
				return inv.getDeclaredTarget().equals(wakelockRelease)?wli:null;
			}
		}
		return null;
	}	

	private boolean isFinishCall(BasicBlockInContext<IExplodedBasicBlock> bb) {
		IExplodedBasicBlock delegate = bb.getDelegate();
		SSAInstruction instruction = delegate.getInstruction();
		if (instruction instanceof SSAInvokeInstruction) {
			SSAInvokeInstruction inv = (SSAInvokeInstruction) instruction;
			String signature = inv.getDeclaredTarget().getSignature();
			if (signature.endsWith("finish()V")) {
				E.log(1, signature);
				return true;
			}
		}
		return false;
	}


	private SpecialConditions specialConditions;

	/**
	 * Check if we have an entry for this in the bb -> thread map
	 * @param bb
	 * @return null if this is not a thread call site
	 */
	private CompoundLockState getCalledRunnable(BasicBlockInContext<IExplodedBasicBlock> bb) {		
		RunnableThread calleeThread = icfg.getThreadInvocations(bb);		
		if (calleeThread != null) {
			Assertions.productionAssertion(calleeThread.isSolved);		//Make sure the constraint graph is working
			CompoundLockState threadExitState = calleeThread.getThreadExitState();
			E.log(2, calleeThread.toString() + " :: " + threadExitState.toString() );			
			return threadExitState;	
		}
		return null;
	}		

	/**
	 * Check if we have an entry for this in the bb -> special_conditions map
	 * @param bb
	 * @return null if this is not a thread call site
	 */
	private SpecialCondition getSpecialCondition(BasicBlockInContext<IExplodedBasicBlock> bb) {
		if (specialConditions == null) {
			specialConditions = icfg.getComponent().getGlobalManager().getSpecialConditions();
		}
		SpecialCondition scond = specialConditions.get(bb.getDelegate().getInstruction());

		//SpecialCondition sc = icfg.getSpecialConditions(bb);
		if (scond != null) {
			E.log(DEBUG, bb.toShortString() + " :: " + scond.toString() );
			return scond;
		}
		return null;
	}		

	private Boolean checkDestination(
			SpecialCondition specialCondition, 
			BasicBlockInContext<IExplodedBasicBlock> dest) {

		//E.log(1, "checking: " + specialCondition.toString() + " with " + dest.getDelegate().getLastInstructionIndex());
		if (dest.getDelegate().getNumber() == specialCondition.getTrueInstrInd()) {
			return Boolean.TRUE;			
		}
		if (dest.getDelegate().getNumber() == specialCondition.getFalseInstrInd()) {
			return Boolean.FALSE;
		}
		return null;
	}


	private IntSet getWakeLockTargets(int d1, WakeLockInstance field , SingleLockState st) {

		Pair<WakeLockInstance, SingleLockState> fact = Pair.make(field, st);
		int factNum = domain.getMappedIndex(fact);
		Assertions.productionAssertion(factNum>=0, fact.toString());
		MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();						

		if (d1 != factNum) {
			Pair<WakeLockInstance, SingleLockState> old = domain.getMappedObject(d1);							
			if (!(old.fst).equals(field)) {
				//This is a completely different field we're operating on
				//so put both states								
				result.add(d1);
				result.add(factNum);					
			}
			else {					
				result.add(factNum);								
			}							
		}
		else {										
			result.add(d1);							
		}						
		//else they are exactly the same so don't do anything
		return result;
	}


	private class LockingFunctions implements IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> {

		private static final int PRINT_EXCEPTIONAL = 2;

		public IUnaryFlowFunction getNormalFlowFunction(
				final BasicBlockInContext<IExplodedBasicBlock> src,
				final BasicBlockInContext<IExplodedBasicBlock> dest) {
			/**
			 * Exceptional edges should be treated as normal in cases where no
			 * acquire/release is involved. BE CAREFUL : exceptional edges that
			 * span across multiple procedures (if possible) cannot be
			 * determined.
			 */
			if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && icfg.isExceptionalEdge(src, dest)) {
				E.log(PRINT_EXCEPTIONAL, "KILL EXC: " + src.toString() + " -> " + dest.toString());				
				return KillEverything.singleton(); 
			}

			if(Opts.ENFORCE_SPECIAL_CONDITIONS) {
				//Check for special conditions
				final SpecialCondition specialCondition = getSpecialCondition(src);
				if (specialCondition != null) {
					if (specialCondition instanceof NullCondition) {
						NullCondition nc = (NullCondition) specialCondition;
						Boolean checkDestination = checkDestination(nc, dest);
						if (checkDestination!= null) {
							if (checkDestination.booleanValue()) {
								E.log(2, "Killing NULL PATH: " + src.toShortString() + " -> " + dest.toShortString());
								return KillEverything.singleton();	//This only kills the wl instance we want
							}
							else {
								E.log(2, "NOT NULL PATH: " + src.toShortString() + " -> " + dest.toShortString());
							}
						}
					}

					//Look for isHeld() condition
					else if (specialCondition instanceof IsHeldCondition) {
						E.log(2, src.toShortString() + " -> " + dest.toShortString() + " : " + specialCondition.toString());
						IsHeldCondition isc = (IsHeldCondition) specialCondition;
						Boolean checkDestination = checkDestination(isc, dest);						
						if (checkDestination!= null) {
							if (checkDestination.booleanValue()) {
								E.log(2, "ISHELD TRUE PATH: " + src.toShortString() + " -> " + dest.toShortString());
							}
							else {
								E.log(2, "Killing ISHELD FALSE PATH: " + src.toShortString() + " -> " + dest.toShortString());
								return KillEverything.singleton();
							}
						}
					}
				}
			}
			return IdentityFlowFunction.identity();	
		}

		public IUnaryFlowFunction getCallFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest,
				BasicBlockInContext<IExplodedBasicBlock> ret) {

			//exception("call", src, dest);			
			//We DO NOT want to check for exceptional edges here
			WakeLockInstance acquireField = acquire(src);				
			WakeLockInstance releaseField = release(src);			
			if ((acquireField != null) || (releaseField != null)) {				
				return KillEverything.singleton();				
			}

			return IdentityFlowFunction.identity();
		}

		public IFlowFunction getReturnFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> call,
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			/**
			 * Exceptional edges in cases where no acquire/release is involved. 
			 * BE CAREFUL : exceptional edges that span across multiple procedures 
			 * are not determined.
			 */
			if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && icfg.isExceptionalEdge(call, dest)) {
				E.log(PRINT_EXCEPTIONAL, "KILL EXC: " + src.toString() + " -> " + dest.toString());
				return KillEverything.singleton();							
			}

			WakeLockInstance acquireField = acquire(call);
			WakeLockInstance releaseField = release(call);			
			if ((acquireField != null) || (releaseField != null)) {				
				return KillEverything.singleton();				
			}			
			return IdentityFlowFunction.identity();			

		}

		/**
		 * Flow function from call node to return node at a call site when
		 * callees exist.
		 */
		public IUnaryFlowFunction getCallToReturnFlowFunction(
				final BasicBlockInContext<IExplodedBasicBlock> src,
				final BasicBlockInContext<IExplodedBasicBlock> dest) {					

			if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && icfg.isExceptionalEdge(src, dest)) {			
				E.log(2, "EXCEPTIONAL Killing [" + src.toString() + " -> " + dest.toString() + "]");
				return KillEverything.singleton();				
			}
			
			//Kill the info for all the rest functions - info will 
			return KillEverything.singleton();
		}
	


		/**
		 * Call to wakelock operation methods are handled as None-to-return 
		 * As we removed these target nodes from the callgraph (should have done
		 * that a long time ago...)
		 */
		public IUnaryFlowFunction getCallNoneToReturnFlowFunction (
				final BasicBlockInContext<IExplodedBasicBlock> src,
				final BasicBlockInContext<IExplodedBasicBlock> dest) {
			if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && icfg.isExceptionalEdge(src, dest)) {
				E.log(PRINT_EXCEPTIONAL, "KILL EXC: " + src.toString() + " -> " + dest.toString());
				return KillEverything.singleton();
			}

			/**
			 * if we're missing callees, just keep what information we have.
			 * These are cases where we don't have the code for the callee, e.g.
			 * android, java, library code. Acquires and releases are not
			 * handled here. 	
			 */			
			/*		//TODO : deprecate this
			final CompoundLockState runnableExitState = getCalledRunnable(src);			

			if (runnableExitState != null) {
				//This is a thread start point
				E.log(2, "Call to: " + runnableExitState.toString());
				return new IUnaryFlowFunction() {
					public IntSet getTargets(int d1) {
						MutableSparseIntSet threadSet = MutableSparseIntSet.makeEmpty();

						for(Entry<WakeLockInstance, Set<SingleLockState>> e : 
								runnableExitState.getLockStateMap().entrySet()) {
							//Be conservative and take the merge for state regarding a wakelock
							Pair<WakeLockInstance, Set<SingleLockState>> p = 
									Pair.make(e.getKey(), e.getValue());							
							//Will lose context sensitivity here, because we have to merge all 
							//the thread's states to a single lock state
							Pair<WakeLockInstance, SingleLockState> q = 
									Pair.make(p.fst, mergeSingleLockStates(p.snd));
							int ind = domain.add(q);
							threadSet.add(ind);
						}
						return threadSet;
			        }
				};
			}
			 */

			return IdentityFlowFunction.identity();
		}

		public IFlowFunction getUnbalancedReturnFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {

			if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && icfg.isExceptionalEdge(src, dest)) {			
				E.log(PRINT_EXCEPTIONAL,  "KILL EXC: " + src.toString() + " -> " + dest.toString() +"]");
				return KillEverything.singleton();				
			}
			return IdentityFlowFunction.identity();
		}

	}
	//End of Locking Flow Functions
	//******************************

	protected IntSet mergeStates(IntSet x, int j) {
		IntIterator it = x.intIterator();
		Pair<WakeLockInstance, SingleLockState> n = domain.getMappedObject(j);
		StringBuffer sb = new StringBuffer();
		sb.append("Merging: " + n.toString());
		MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
		boolean merged = false;
		while (it.hasNext()) {
			int i = it.next();
			Pair<WakeLockInstance, SingleLockState> q = domain.getMappedObject(i);
			if (q.fst.equals(n.fst)) {
				merged = true;
				SingleLockState mergedState = q.snd.merge(n.snd);
				Pair<WakeLockInstance, SingleLockState> newPair = Pair.make(q.fst, mergedState);
				int factNum = domain.getMappedIndex(newPair);
				Assertions.productionAssertion(factNum>=0, newPair.toString());
				result.add(factNum);				
			}
			else {
				result.add(i);
			}			
			sb.append(" + " + q.toString());
		}		
		if (!merged) {
			result.add(j);
		}		
		sb.append(" -> " + n.toString());		
		E.log(2, sb.toString());		
		return result;
	}



	private class LockingProblem implements
	PartiallyBalancedTabulationProblem<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<WakeLockInstance, SingleLockState>> {

		private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> initialSeeds = collectInitialSeeds();
		private IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> flowFunctions = 
				new LockingFunctions();

		public ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> getSupergraph() {
			return supergraph;
		}

		/**
		 * Define the set of path edges to start propagation with.
		 * 
		 * @return
		 */
		private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> collectInitialSeeds() {
			Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> result =	HashSetFactory.make();

			/*
			//This will be avoided
			Component component = icfg.getComponent();
			Map<Component, Set<SSAInstruction>> seeds = component.getSeeds();
			if (seeds!=null) {
				for(Entry<Component, Set<SSAInstruction>> seed :seeds.entrySet()) {
					if (seed.getValue().size() > 1) {
						E.log(DEBUG, "###MUTLIPLE SEEDS : " + component.toString() + "###");
					}
					for(SSAInstruction i : seed.getValue()) {
						Component seedComponent = seed.getKey();
						E.log(DEBUG, "  SEED FROM: " + seedComponent.toString() + " - " + 
								((SSAInvokeInstruction) i).getDeclaredTarget().getName().toString());
						CompoundLockState state = seedComponent.getState(i);
						E.log(DEBUG, "  State: " + state.toShortString());
						Set<Selector> entryPoints = component.getEntryPoints();
						if (entryPoints != null) {
							for (Selector entryPoint : entryPoints) {
								CallBack callback = component.getCallBack(entryPoint);
								if (callback != null) {
									E.log(DEBUG, "\tAdding seed to: " + entryPoint.toString());
									CGNode node = callback.getNode();
									BasicBlockInContext<IExplodedBasicBlock> bb = icfg.getEntry(node);
									BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(node);
									for(Entry<WakeLockInstance, Set<SingleLockState>> f : state.getLockStateMap().entrySet()) {
										WakeLockInstance wli = f.getKey();
										for(SingleLockState ls : f.getValue()) {
											Pair<WakeLockInstance, SingleLockState> fact = Pair.make(wli, ls);					
											int factNum = domain.add(fact);
											result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb, factNum));
										}
									}
								}
							}
						}
					}
				}
			}
			 */

			for (BasicBlockInContext<IExplodedBasicBlock> bb : supergraph) {
				WakeLockInstance timedAcquiredWL = timedAcquire(bb);
				if (timedAcquiredWL != null) {
					E.log(1, "Adding timed acquire seed: " + bb.toShortString());
					SingleLockState sls = new SingleLockState(true, true, false);
					Pair<WakeLockInstance, SingleLockState> fact = Pair.make(timedAcquiredWL, sls);					
					int factNum = domain.add(fact);
					BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(bb.getNode());
					result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb, factNum));
				}

				WakeLockInstance acquiredWL = acquire(bb);
				if (acquiredWL != null) {
					E.log(1, "Adding acquire seed: " + bb.toShortString());
					SingleLockState sls = new SingleLockState(true, false, false);
					Pair<WakeLockInstance, SingleLockState> fact = Pair.make(acquiredWL, sls);					
					int factNum = domain.add(fact);
					BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(bb.getNode());
					result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb, factNum));
				}

				WakeLockInstance releasedWL = release(bb);
				if (releasedWL != null)  {					
					E.log(1, "Adding release seed: " + bb.toShortString());
					SingleLockState sls = new SingleLockState(false, false, false);					
					Pair<WakeLockInstance, SingleLockState> fact = Pair.make(releasedWL, sls);
					int factNum = domain.add(fact);
					BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(bb.getNode());
					result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb, factNum));
				}
			}			
			return result;
		}

		public TabulationDomain<Pair<WakeLockInstance, SingleLockState>, BasicBlockInContext<IExplodedBasicBlock>> getDomain() {
			return domain;
		}

		public Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> initialSeeds() {
			return initialSeeds;
		}

		public IMergeFunction getMergeFunction() {
			return new IMergeFunction() {
				/**
				 * This method should return the factoid number z which should 
				 * actually be propagated, based on a merge of the new fact j into 
				 * the old state represented by x. return -1 if no fact should be 
				 * propagated.
				 */
				public int merge(IntSet x, int j) {				
					Pair<WakeLockInstance, SingleLockState> jObj = domain.getMappedObject(j);
					for (IntIterator it = x.intIterator(); it.hasNext(); ) {
						int i = it.next();
						Pair<WakeLockInstance, SingleLockState> iObj = domain.getMappedObject(i);
						WakeLockInstance iField = iObj.fst;
						//If the field is already in the mapping
						if (iField.equals(jObj.fst)) {							
							SingleLockState resState = jObj.snd.merge(iObj.snd);							
							Pair<WakeLockInstance, SingleLockState> pair = Pair.make(iField, resState);							
							int ind = domain.add(pair);
							//E.log(1, "Merge yields: " + ind + " :: " + resState.toString());
							Assertions.productionAssertion(ind>=0, pair.toString());
							return ind;
						}
					}
					return j;
				}
			};
		}

		public IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> getFunctionMap() {
			return flowFunctions;
		}

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
	public LockingResult analyze() {
		LockingTabulationSolver solver = new LockingTabulationSolver(new LockingProblem(), null);
		LockingResult result = null;
		try {
			result = solver.solve();

		} catch (CancelException e) {
			e.printStackTrace();
		}
		return result;

	}

	public ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> getSupergraph() {
		return supergraph;
	}

	public TabulationDomain<Pair<WakeLockInstance, SingleLockState>, BasicBlockInContext<IExplodedBasicBlock>> getDomain() {
		return domain;
	}

}
