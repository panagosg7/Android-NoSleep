package edu.ucsd.energy.interproc;

import java.util.Collection;
import java.util.Set;

import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.IMergeFunction;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
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

import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.AbstractContext;
import edu.ucsd.energy.conditions.SpecialConditions;
import edu.ucsd.energy.conditions.SpecialConditions.SpecialCondition;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.LockingTabulationSolver.LockingResult;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.managers.WakeLockManager;
import edu.ucsd.energy.util.Log;

public class CtxSensLocking {

	static final int DEBUG = 0;
	
	/**
	 * The underlying Inter-procedural Control Flow Graph
	 */
	private AbstractContext component;
	
	private AbstractContextCFG icfg;	
	
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
	final TabDomain domain = new TabDomain();
	

	/**
	 * We are going to extend the ICFGSupergraph that WALA had for reaching
	 * definitions to that we are able to define our own sensible supergraph
	 * (containing extra edges).
	 */
	public static class SensibleICFGSupergraph extends ICFGSupergraph {
		public SensibleICFGSupergraph(ExplodedInterproceduralCFG icfg, AnalysisCache cache) {
			super(icfg, cache);
		}
	}

	public CtxSensLocking(AbstractContext comp) {
		 //We already have created our SensibleExplodedInterproceduralCFG which
		 //contains extra edges compared to the exploded i-cfg WALA usually creates
		this.component = comp;
		this.setICFG(component.getICFG());
		this.supergraph = component.getSupergraph(); 
		this.wakeLockManager = GlobalManager.get().getWakeLockManager();
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

	WakeLockInstance release(BasicBlockInContext<IExplodedBasicBlock> bb) {
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
				Log.log(1, signature);
				return true;
			}
		}
		return false;
	}


	private SpecialConditions specialConditions;

	/**
	 * Check if we have an entry for this in the bb -> special_conditions map
	 * @param bb
	 * @return null if this is not a thread call site
	 */
	SpecialCondition getSpecialCondition(BasicBlockInContext<IExplodedBasicBlock> bb) {
		if (specialConditions == null) {
			specialConditions = GlobalManager.get().getSpecialConditions();
		}
		return specialConditions.get(bb.getDelegate().getInstruction());
	}		

	Boolean checkDestination(
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


	class LockingProblem implements	PartiallyBalancedTabulationProblem<
		BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<WakeLockInstance, SingleLockState>> {

		private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> initialSeeds ;
		
		private LockingFlowFunctions flowFunctions;

		public ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> getSupergraph() {
			return supergraph;
		}
		
		public LockingProblem() {
			flowFunctions = new LockingFlowFunctions(CtxSensLocking.this);
			initialSeeds = collectInitialSeeds();
		}
		
		public boolean noSeeds() {
			return initialSeeds.isEmpty();
		}
		
		/**
		 * Define the set of path edges to start propagation with.
		 */
		private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> collectInitialSeeds() {
			Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> result =	HashSetFactory.make();
			if (DEBUG > 0) {
				System.out.println("Computing seeds");
			}
			for (BasicBlockInContext<IExplodedBasicBlock> bb : supergraph) {
				
				
				if (Opts.USE_TIMED_ACQUIRE_AS_SEED) {
					
					WakeLockInstance timedAcquiredWL = timedAcquire(bb);
					//Note: We do not add release as a seed. Release will kill the relevant acquire 
					//
					if (timedAcquiredWL != null) {
						if (DEBUG > 0) {
							System.out.println("Adding timed acquire seed: " + bb.toString());
						}
						//Get all the possible contexts that this bb might belong to 
						CGNode node = supergraph.getProcOf(bb);
						Set<Context> contCtxs = component.getContainingContexts(node);
						SingleLockState sls = new SingleLockState(true, true, false, contCtxs);
						Pair<WakeLockInstance, SingleLockState> fact = Pair.make(timedAcquiredWL, sls);					
						int factNum = domain.add(fact);
						BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(bb.getNode());
						result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb, factNum));
					}
				}

				WakeLockInstance acquiredWL = acquire(bb);
				if (acquiredWL != null) {
					if (DEBUG > 0) {
						System.out.println("Adding acquire seed: " + bb.toString());
					}
					CGNode node = supergraph.getProcOf(bb);
					Set<Context> contCtxs = component.getContainingContexts(node);
					SingleLockState sls = new SingleLockState(true, false, false, contCtxs);
					Pair<WakeLockInstance, SingleLockState> fact = Pair.make(acquiredWL, sls);					
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
							Log.log(2, "Merge yields: " + ind + " :: " + resState.toString());
							Assertions.productionAssertion(ind>=0, pair.toString());
							return ind;
						}
					}
					return j;
				}
			};
		}

		public LockingFlowFunctions getFunctionMap() {
			return flowFunctions;
		}

		public BasicBlockInContext<IExplodedBasicBlock> getFakeEntry(BasicBlockInContext<IExplodedBasicBlock> n) {
			final CGNode cgNode = n.getNode();
			return getFakeEntry(cgNode);
		}

		private BasicBlockInContext<IExplodedBasicBlock> getFakeEntry(CGNode cgNode) {
			BasicBlockInContext<IExplodedBasicBlock>[] entriesForProcedure = supergraph.getEntriesForProcedure(cgNode);
			assert entriesForProcedure.length == 1;
			return entriesForProcedure[0];
		}

	}

	/**
	 * perform the tabulation analysis and return the {@link TabulationResult}
	 */
	public LockingResult analyze() {
		LockingProblem problem = new LockingProblem();
		LockingTabulationSolver solver = new LockingTabulationSolver(problem, null, getICFG());
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

	public AbstractContextCFG getICFG() {
		return icfg;
	}

	public void setICFG(AbstractContextCFG icfg) {
		this.icfg = icfg;
	}

}
