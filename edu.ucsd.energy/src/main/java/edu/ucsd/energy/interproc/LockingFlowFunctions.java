package edu.ucsd.energy.interproc;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.dataflow.IFDS.KillEverything;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import edu.ucsd.energy.analysis.Options;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.conditions.SpecialConditions.IsHeldCondition;
import edu.ucsd.energy.conditions.SpecialConditions.NullCondition;
import edu.ucsd.energy.conditions.SpecialConditions.SpecialCondition;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.util.Log;

class LockingFlowFunctions implements ILockingFlowFunctionMap<BasicBlockInContext<IExplodedBasicBlock>> {

	private final CtxSensLocking ctxSensLocking;

	/**
	 * @param ctxSensLocking
	 */
	LockingFlowFunctions(CtxSensLocking ctxSensLocking) {
		this.ctxSensLocking = ctxSensLocking;
	}

	private static final int PRINT_EXCEPTIONAL = 2;
	private static final int DEBUG = 0;

	public IUnaryFlowFunction getNormalFlowFunction(
			final BasicBlockInContext<IExplodedBasicBlock> src,
			final BasicBlockInContext<IExplodedBasicBlock> dest) {
		/**
		 * Exceptional edges should be treated as normal in cases where no
		 * acquire/release is involved. BE CAREFUL : exceptional edges that
		 * span across multiple procedures (if possible) cannot be determined.
		 */
		if (Options.DATAFLOW_IGNORE_EXCEPTIONAL && this.ctxSensLocking.getICFG().isExceptionalEdge(src, dest)) {
			Log.log(PRINT_EXCEPTIONAL, "KILL EXC: " + src.toShortString() + " -> " + dest.toShortString());				
			return KillEverything.singleton(); 
		}

		if(DEBUG > 1) {
			System.out.println("NORMAL: " + src.toShortString() + " -> " + dest.toShortString());				
		}
		
		if(Options.ENFORCE_SPECIAL_CONDITIONS) {
			//Check for special conditions
			final SpecialCondition specialCondition = this.ctxSensLocking.getSpecialCondition(src);
			if (specialCondition != null) {
				if (specialCondition instanceof NullCondition) {
					NullCondition nc = (NullCondition) specialCondition;
					Boolean checkDestination = this.ctxSensLocking.checkDestination(nc, dest);
					if (checkDestination!= null) {
						if (checkDestination.booleanValue()) {
							if(DEBUG > 1) {
								System.out.println("Killing NULL PATH: " + src.toShortString() + " -> " + dest.toShortString());
							}
							return KillEverything.singleton();	//This only kills the wl instance we want
						}
						else {
							if(DEBUG > 1) {
								System.out.println("NOT NULL PATH: " + src.toShortString() + " -> " + dest.toShortString());
							}
						}
					}
				}
				//Look for isHeld() condition
				else if (specialCondition instanceof IsHeldCondition) {
					IsHeldCondition isc = (IsHeldCondition) specialCondition;
					Boolean checkDestination = this.ctxSensLocking.checkDestination(isc, dest);						
					if (checkDestination!= null) {
						if (checkDestination.booleanValue()) {
							if(DEBUG > 1) {
								System.out.println("ISHELD TRUE PATH: " + src.toShortString() + " -> " + dest.toShortString());
							}
						}
						else {
							if(DEBUG > 1) {
								System.out.println("Killing ISHELD FALSE PATH: " + src.toShortString() + " -> " + dest.toShortString());
							}
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
		Log.log(PRINT_EXCEPTIONAL, "CALL FLOW: " + src.toShortString() + "->" + dest.toShortString());
		return IdentityFlowFunction.identity();
	}


	/**
	 * Flow function from call node to return node at a call site when
	 * callees exist.
	 */
	public IUnaryFlowFunction getCallToReturnFlowFunction(
			BasicBlockInContext<IExplodedBasicBlock> src,
			BasicBlockInContext<IExplodedBasicBlock> dest) {
		
		if (Options.DATAFLOW_IGNORE_EXCEPTIONAL && this.ctxSensLocking.getICFG().isExceptionalEdge(src, dest)) {			
			Log.log(PRINT_EXCEPTIONAL, "KILL(call-to-return):" + src.toShortString() + " -> " + dest.toShortString());
			return KillEverything.singleton();				
		}

		//XXX: This info should probably not be passed, because we'll miss any info of timing!
		//Two merged states will have lost any info regarding their sequencing...
		//We have marked cases where a different context is called (e.g. startActivity)
		//We need to propagate the state and not treat this as a common method call!
		if (this.ctxSensLocking.getICFG().isCallToContextEdge(src, dest)) {
			return IdentityFlowFunction.identity();
		}
		
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
		
		if (Options.DATAFLOW_IGNORE_EXCEPTIONAL && this.ctxSensLocking.getICFG().isExceptionalEdge(src, dest)) {
			Log.log(PRINT_EXCEPTIONAL, "KILL(call-none-to-return): " +
					src.toShortString() + " -> " + dest.toShortString());
			return KillEverything.singleton();
		}

		final WakeLockInstance releasedWL = this.ctxSensLocking.release(src);
		if (releasedWL != null)  {
			return new IUnaryFlowFunction() {
				public IntSet getTargets(int d1) {
					Pair<WakeLockInstance, SingleLockState> mappedObject = ctxSensLocking.getDomain().getMappedObject(d1);
					MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
					//If the incoming state operates on the same lock... 
					if (releasedWL.equals(mappedObject.fst)) {
						SingleLockState oldSt = mappedObject.snd;
						Set<Component> releaseCtxs = ctxSensLocking.getICFG().getContainingContext(src);
						Set<Component> oldCtxs = oldSt.involvedContexts();
						if (oldSt.async()) {
							//If the incoming state is an asynchronous acquire then
							//we cannot guarantee that this release is going to take 
							//care of it, so we _ignore_ the release operation, and 
							//just add the original factoid
							//We will add the context to the involved contexts.
							//It is ok to mutate the original state, since we are being 
							//conservative by adding an "involved" context 
							oldSt.addContexts(releaseCtxs);
							boolean acquired = oldSt.acquired();
							boolean timed = oldSt.timed();
							boolean async = true;
							//Do not create a new state if the state is already there
							if (oldCtxs.containsAll(releaseCtxs)) {
								result.add(d1);
							}
							else {
								Set<Component> newCtxs = new HashSet<Component>(oldCtxs);
								newCtxs.addAll(releaseCtxs);
								SingleLockState newSt = new SingleLockState(acquired, timed, async, newCtxs);
								result.add(ctxSensLocking.getDomain().add(Pair.make(releasedWL, newSt)));
							}
							
						}
						else {
							//If it is not an asynchronous operation then release is used normally.
							
							Set<Component> acquireCtxs = oldCtxs;
							
							//It suffices to have the release in a superset of the contexts that the 
							//acquire can belong to. In the case the release is in the same context
							//as the acquire, then it shouldn't propagate the "asynchronous" state, but
							//rather the same state as before. Otherwise, it should propagate "asynchronous".
							boolean async = (releaseCtxs.containsAll(acquireCtxs))?oldSt.async():true;
 
							SingleLockState newSt = new SingleLockState(false, false, async, oldCtxs);
							result.add(ctxSensLocking.getDomain().add(Pair.make(releasedWL, newSt)));
						}
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
		//System.out.println("Check unbal: " + src.toShortString() + " -> " + dest.toShortString());
		
		AbstractContextCFG icfg = this.ctxSensLocking.getICFG();
		if((DEBUG > 0) && (icfg.isLifecycleExit(src))) {
			System.out.println("LIFECYCLE HOP: " + src.toString() + " -> " + dest.toString());
		}
		
		//Returning from a context ASYNCHRONOUSLY
		if (icfg.isReturnFromContextEdge(src, dest)) {
			if(DEBUG > 0) {
				System.out.println("UNBALANCED RETURN FROM CTX: " + src.toString() + " -> " + dest.toString());
			}
			return new IUnaryFlowFunction() {
				public IntSet getTargets(int d1) {
					Pair<WakeLockInstance, SingleLockState> mappedObject = ctxSensLocking.getDomain().getMappedObject(d1);
					MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
					
					//icfg.getContainingContext(bb);
					
					SingleLockState oldSt = mappedObject.snd;
					//XXX: we are keeping the same async state cause we might be returning back
					//to the same context after visiring a context that did not change the state
					//at all
					SingleLockState newSt = new SingleLockState(oldSt.acquired(), oldSt.timed(), oldSt.async(), oldSt.involvedContexts());
					//This is probably unknown to the domain so far, so we have to add it
					//getMappedIndex returns -1 if the value is not in the domain
					result.add(ctxSensLocking.getDomain().add(Pair.make(mappedObject.fst, newSt)));
					return result;
				}
			};
		}
		
		if (Options.DATAFLOW_IGNORE_EXCEPTIONAL && icfg.isExceptionalEdge(src, dest)) {
			Log.log(PRINT_EXCEPTIONAL,  "KILL(unbal): " + src.toShortString() + " -> " + dest.toShortString());
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
		if (Options.DATAFLOW_IGNORE_EXCEPTIONAL && this.ctxSensLocking.getICFG().isExceptionalEdge(call, dest)) {
			Log.log(PRINT_EXCEPTIONAL, "KILL(return): " + src.toShortString() + " -> " + dest.toShortString());
			return KillEverything.singleton();							
		}
		
		if (ctxSensLocking.getICFG().isReturnFromContextEdge(src, dest)) {
			
		if(DEBUG > 0) {
			System.out.println("RETURN FROM CTX: " + src.toString() + " -> " + dest.toString());
		}
			
			return new IUnaryFlowFunction() {
				public IntSet getTargets(int d1) {
					Pair<WakeLockInstance, SingleLockState> mappedObject = ctxSensLocking.getDomain().getMappedObject(d1);
					MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
					SingleLockState oldSt = mappedObject.snd;
					//XXX: we are keeping the same async state cause we might be returning back
					//to the same context after visiring a context that did not change the state
					//at all
					SingleLockState newSt = new SingleLockState(oldSt.acquired(), oldSt.timed(), oldSt.async(), oldSt.involvedContexts());
					result.add(ctxSensLocking.getDomain().add(Pair.make(mappedObject.fst, newSt)));							
					return result;
				}
			};
		}			
		return IdentityFlowFunction.identity();			
	}

	
	/**
	 * Flow function for calls to other contexts - we treat these as regular calls.
	 */
	public IUnaryFlowFunction getAsyncCallFlowFunction(
			BasicBlockInContext<IExplodedBasicBlock> src,
			BasicBlockInContext<IExplodedBasicBlock> dest,
			BasicBlockInContext<IExplodedBasicBlock> ret) {
		return IdentityFlowFunction.identity();
	}

	public IFlowFunction getAsyncReturnFlowFunction(
			BasicBlockInContext<IExplodedBasicBlock> target,
			BasicBlockInContext<IExplodedBasicBlock> exit,
			BasicBlockInContext<IExplodedBasicBlock> returnSite) {
		// TODO Auto-generated method stub
		return null;
	}

}
//End of Locking Flow Functions
//******************************