package edu.ucsd.energy.interproc;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.core.runtime.content.IContentTypeManager.ISelectionPolicy;

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
import edu.ucsd.energy.component.AbstractComponent;
import edu.ucsd.energy.interproc.LockingTabulationSolver.LockingResult;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.managers.WakeLockManager;
import edu.ucsd.energy.util.E;

public class CtxSensLocking {

	private static final int DEBUG = 2;
	
	/**
	 * The underlying Inter-procedural Control Flow Graph
	 */
	private AbstractComponent component;
	
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
	private final TabDomain domain = new TabDomain();

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

	public CtxSensLocking(AbstractComponent comp) {
		/*
		 * We already have created our SensibleExplodedInterproceduralCFG which
		 * contains extra edges compared to the exploded i-cfg WALA usually creates
		 */
		this.component = comp;
		this.icfg = component.getICFG();
		this.supergraph = component.getSupergraph(); 
		///////////////
		/*Iterator<BasicBlockInContext<IExplodedBasicBlock>> iterator = supergraph.iterator();
		while(iterator.hasNext()) {
			BasicBlockInContext<IExplodedBasicBlock> next = iterator.next();
			E.log(1, "Visiting: " + next.toShortString());
			Iterator<BasicBlockInContext<IExplodedBasicBlock>> succNodes = supergraph.getSuccNodes(next);
			while(succNodes.hasNext()) {
				BasicBlockInContext<IExplodedBasicBlock> next2 = succNodes.next();
				try {
					E.log(1, "\t" + next2.toShortString() + 
							(icfg.isExceptionalEdge(next2, next)?" (EXC)":""));
				}
				catch (Exception e) {
					E.log(1, "\t" + next2.toShortString() + " (THREW EXC)" );
					e.printStackTrace();
				}
			}
		}
		*/
		///////////////////
		this.wakeLockManager = component.getGlobalManager().getWakeLockManager();
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
				E.log(PRINT_EXCEPTIONAL, "KILL EXC: " + src.toShortString() + " -> " + dest.toShortString());				
				return KillEverything.singleton(); 
			}

			E.log(PRINT_EXCEPTIONAL, "NORMAL: " + src.toShortString() + " -> " + dest.toShortString());				

			
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
			E.log(PRINT_EXCEPTIONAL, "CALL FLOW: " + src.toShortString() + "->" + dest.toShortString());
			return IdentityFlowFunction.identity();
		}


		/**
		 * Flow function from call node to return node at a call site when
		 * callees exist.
		 */
		public IUnaryFlowFunction getCallToReturnFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			
			E.log(PRINT_EXCEPTIONAL, "KILLING(call to return): " + src.toShortString() + "->" + dest.toShortString());
			
			if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && icfg.isExceptionalEdge(src, dest)) {			
				E.log(PRINT_EXCEPTIONAL, "EXCEPTIONAL Killing [" + src.toShortString() + " -> " + dest.toShortString() + "]");
				return KillEverything.singleton();				
			}

			//XXX: This info should probably not be passed, bacause we'll miss any info of timing!
			//Two merged states will have lost any info regarding their sequencing...
			//We have marked cases where a different context is called (e.g. startActivity)
			//We need to propagate the state and not treat this as a common method call!
			//if (icfg.propagateState(src, dest)) {
			//	E.log(1, "PROPAGATING: " + src.toString() + " -> " + dest.toString());
			//	return IdentityFlowFunction.identity();
			//}
			
			//Kill the info for all the rest functions - info will 
			return KillEverything.singleton();
		}
	


		/**
		 * Call to wakelock operation methods are handled as None-to-return 
		 * As we removed these target nodes from the callgraph.
		 */
		public IUnaryFlowFunction getCallNoneToReturnFlowFunction (
				final BasicBlockInContext<IExplodedBasicBlock> src,
				final BasicBlockInContext<IExplodedBasicBlock> dest) {
			
			if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && icfg.isExceptionalEdge(src, dest)) {
				E.log(PRINT_EXCEPTIONAL, "KILL EXC: " + src.toShortString() + " -> " + dest.toShortString());
				return KillEverything.singleton();
			}

			final WakeLockInstance releasedWL = release(src);
			if (releasedWL != null)  {
				return new IUnaryFlowFunction() {
					public IntSet getTargets(int d1) {
						Pair<WakeLockInstance, SingleLockState> mappedObject = domain.getMappedObject(d1);
						MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
						if (releasedWL.equals(mappedObject.fst)) {
							SingleLockState oldSt = mappedObject.snd;
							SingleLockState newSt = new SingleLockState(false, false, oldSt.async());
							result.add(domain.getMappedIndex(Pair.make(releasedWL, newSt)));
						} 
						else {
							result.add(d1);
						}
						return result;
					}
				};
			}
			
			return IdentityFlowFunction.identity();
		}

		public IFlowFunction getUnbalancedReturnFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {

			E.log(PRINT_EXCEPTIONAL, "unbal: " + src.toShortString() + "->" + dest.toShortString());
			
			//Returning from a context ASYNCHRONOUSLY
			if (icfg.isReturnFromContext(src, dest)) {
				E.log(1, "CTX UNBAL RETURN: " + src.toShortString() + " -> " + dest.toShortString());	
				return new IUnaryFlowFunction() {
					public IntSet getTargets(int d1) {
						Pair<WakeLockInstance, SingleLockState> mappedObject = domain.getMappedObject(d1);
						MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
						SingleLockState oldSt = mappedObject.snd;
						SingleLockState newSt = new SingleLockState(oldSt.acquired(), oldSt.timed(), true);
						//This is probably unknown to the domain so far, so we have to add it
						//getMappedIndex returns -1 if the value is not in the domain 
						result.add(domain.add(Pair.make(mappedObject.fst, newSt)));
						return result;
					}
				};
			}			
			
			if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && icfg.isExceptionalEdge(src, dest)) {			
				E.log(PRINT_EXCEPTIONAL,  "KILL EXC: " + src.toShortString() + " -> " + dest.toShortString() +"]");
				return KillEverything.singleton();				
			}
			return IdentityFlowFunction.identity();
		}

		
		public IFlowFunction getReturnFlowFunction(
				BasicBlockInContext<IExplodedBasicBlock> call,
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
		
			E.log(PRINT_EXCEPTIONAL, "return: " + src.toShortString() + "->" + dest.toShortString());
			
			/**
			 * Exceptional edges in cases where no acquire/release is involved. 
			 * BE CAREFUL : exceptional edges that span across multiple procedures 
			 * are not determined.
			 */
			if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && icfg.isExceptionalEdge(call, dest)) {
				E.log(PRINT_EXCEPTIONAL, "KILL EXC: " + src.toShortString() + " -> " + dest.toShortString());
				return KillEverything.singleton();							
			}
			
			if (icfg.isReturnFromContext(src, dest)) {
				E.log(1, "CTX RETURN: " + src.toShortString() + " -> " + dest.toShortString());	
				return new IUnaryFlowFunction() {
					public IntSet getTargets(int d1) {
						Pair<WakeLockInstance, SingleLockState> mappedObject = domain.getMappedObject(d1);
						MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
						SingleLockState oldSt = mappedObject.snd;
						SingleLockState newSt = new SingleLockState(oldSt.acquired(), oldSt.timed(), true);
						result.add(domain.getMappedIndex(Pair.make(mappedObject.fst, newSt)));							
						return result;
					}
				};
			}			
			return IdentityFlowFunction.identity();			
		}

	}
	//End of Locking Flow Functions
	//******************************

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
		 */
		private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> collectInitialSeeds() {
			Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> result =	HashSetFactory.make();
			for (BasicBlockInContext<IExplodedBasicBlock> bb : supergraph) {
				WakeLockInstance timedAcquiredWL = timedAcquire(bb);
				if (timedAcquiredWL != null) {
					E.log(DEBUG, "Adding timed acquire seed: " + bb.toShortString());
					SingleLockState sls = new SingleLockState(true, true, false);
					Pair<WakeLockInstance, SingleLockState> fact = Pair.make(timedAcquiredWL, sls);					
					int factNum = domain.add(fact);
					BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(bb.getNode());
					result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb, factNum));
				}

				WakeLockInstance acquiredWL = acquire(bb);
				if (acquiredWL != null) {
					E.log(DEBUG, "Adding acquire seed: " + bb.toShortString());
					SingleLockState sls = new SingleLockState(true, false, false);
					Pair<WakeLockInstance, SingleLockState> fact = Pair.make(acquiredWL, sls);					
					int factNum = domain.add(fact);
					BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(bb.getNode());
					result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb, factNum));
				}

				WakeLockInstance releasedWL = release(bb);
				if (releasedWL != null)  {					
					E.log(DEBUG, "Adding release seed: " + bb.toShortString());
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
