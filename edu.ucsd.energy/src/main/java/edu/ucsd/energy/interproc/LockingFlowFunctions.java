package edu.ucsd.energy.interproc;

import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.dataflow.IFDS.KillEverything;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.conditions.SpecialConditions.IsHeldCondition;
import edu.ucsd.energy.conditions.SpecialConditions.NullCondition;
import edu.ucsd.energy.conditions.SpecialConditions.SpecialCondition;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.util.E;

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
		if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && this.ctxSensLocking.getICFG().isExceptionalEdge(src, dest)) {
			E.log(PRINT_EXCEPTIONAL, "KILL EXC: " + src.toShortString() + " -> " + dest.toShortString());				
			return KillEverything.singleton(); 
		}

		if(DEBUG > 1) {
			System.out.println("NORMAL: " + src.toShortString() + " -> " + dest.toShortString());				
		}
		
		if(Opts.ENFORCE_SPECIAL_CONDITIONS) {
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
		
		if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && this.ctxSensLocking.getICFG().isExceptionalEdge(src, dest)) {			
			E.log(PRINT_EXCEPTIONAL, "KILL(call-to-return):" + src.toShortString() + " -> " + dest.toShortString());
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
		
		if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && this.ctxSensLocking.getICFG().isExceptionalEdge(src, dest)) {
			E.log(PRINT_EXCEPTIONAL, "KILL(call-none-to-return): " +
					src.toShortString() + " -> " + dest.toShortString());
			return KillEverything.singleton();
		}

		final WakeLockInstance releasedWL = this.ctxSensLocking.release(src);
		if (releasedWL != null)  {
			return new IUnaryFlowFunction() {
				public IntSet getTargets(int d1) {
					Pair<WakeLockInstance, SingleLockState> mappedObject = ctxSensLocking.getDomain().getMappedObject(d1);
					MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
					if (releasedWL.equals(mappedObject.fst)) {
						SingleLockState oldSt = mappedObject.snd;
						


						if (oldSt.async()) {
							//If the incoming state is an asynchronous acquire then
							//we cannot guarantee that this release is going to take 
							//care of it, so we _ignore_ the relase operation.
							result.add(d1);
						}
						else {
							//If it is not an asynchronous operation then release is 
							//used normally. 
						
							SingleLockState newSt = new SingleLockState(false, false, oldSt.async());
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
		
		if((DEBUG > 0) && (this.ctxSensLocking.getICFG().isLifecycleExit(src))) {
			System.out.println("LIFECYCLE HOP: " + src.toString() + " -> " + dest.toString());
		}
		
		//Returning from a context ASYNCHRONOUSLY
		if (this.ctxSensLocking.getICFG().isReturnFromContextEdge(src, dest)) {
			if(DEBUG > 0) {
				System.out.println("RETURN FROM CTX: " + src.toString() + " -> " + dest.toString());
			}
			return new IUnaryFlowFunction() {
				public IntSet getTargets(int d1) {
					Pair<WakeLockInstance, SingleLockState> mappedObject = ctxSensLocking.getDomain().getMappedObject(d1);
					MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
					SingleLockState oldSt = mappedObject.snd;
					SingleLockState newSt = new SingleLockState(oldSt.acquired(), oldSt.timed(), true);
					//This is probably unknown to the domain so far, so we have to add it
					//getMappedIndex returns -1 if the value is not in the domain
					result.add(ctxSensLocking.getDomain().add(Pair.make(mappedObject.fst, newSt)));
					return result;
				}
			};
		}
		
		if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && this.ctxSensLocking.getICFG().isExceptionalEdge(src, dest)) {
			E.log(PRINT_EXCEPTIONAL,  "KILL(unbal): " + src.toShortString() + " -> " + dest.toShortString());
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
		if (Opts.DATAFLOW_IGNORE_EXCEPTIONAL && this.ctxSensLocking.getICFG().isExceptionalEdge(call, dest)) {
			E.log(PRINT_EXCEPTIONAL, "KILL(return): " + src.toShortString() + " -> " + dest.toShortString());
			return KillEverything.singleton();							
		}
		
		if (ctxSensLocking.getICFG().isReturnFromContextEdge(src, dest)) {
			return new IUnaryFlowFunction() {
				public IntSet getTargets(int d1) {
					Pair<WakeLockInstance, SingleLockState> mappedObject = ctxSensLocking.getDomain().getMappedObject(d1);
					MutableSparseIntSet result = MutableSparseIntSet.makeEmpty();
					SingleLockState oldSt = mappedObject.snd;
					SingleLockState newSt = new SingleLockState(oldSt.acquired(), oldSt.timed(), true);
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