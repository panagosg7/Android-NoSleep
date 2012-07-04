package edu.ucsd.energy.results;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.sf.json.JSONObject;

import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.HashSetMultiMap;

import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.component.SuperComponent;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.contexts.GeneralContext;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.managers.ComponentManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.util.E;

public class ProcessResults {

	private static final int DEBUG = 1;

	
	public static class LockUsage extends HashMap<WakeLockInstance, SingleLockState> {

		private static final long serialVersionUID = -6164699487290522725L;

		public LockUsage() {
			super();
		}
		
		Set<Context> relevant;
		
		public LockUsage(CompoundLockState cls) {
			super(cls.getLockStateMap());
			relevant = new HashSet<Context>();			
			for (SingleLockState ls : cls.getLockStateMap().values()) {
				relevant.addAll(ls.involvedContexts());				
			}
		}
		
		public boolean relevant(Context c) {
			return relevant.contains(c);
		}

		public Set<Context> getRelevantCtxs() {
			return relevant;
		}

		
		public String toString() {
			StringBuffer sb = new StringBuffer();
			for(java.util.Map.Entry<WakeLockInstance, SingleLockState> e : entrySet()) {
				sb.append(e.getKey().toShortString() + " - " +
						e.getValue().toString() + "\n");
			}
			return sb.toString();
		}

		public JSONObject toJSON() {
			JSONObject obj = new JSONObject();
			for(java.util.Map.Entry<WakeLockInstance, SingleLockState> e : entrySet()) {
				obj.put(e.getKey().toShortString(), e.getValue().toString());
			}
			return null;
		}

		private boolean syncLocking(WakeLockInstance wli) {
			SingleLockState singleLockState = get(wli);
			if (singleLockState != null) {
				return (singleLockState.acquired() && (!singleLockState.async()));
			}
			return false;
		}

		private boolean asyncLocking(WakeLockInstance wli) {
			SingleLockState singleLockState = get(wli);
			if (singleLockState != null) {
				return (singleLockState.acquired() && singleLockState.async());
			}
			return false;
		}
		
		public boolean locking(WakeLockInstance wli) {
			return (syncLocking(wli) || asyncLocking(wli));
		}

	}


	public enum SingleLockUsage {
		ACQUIRED,
		RELEASED,
		TIMED_ACQUIRED,
		ASYNC_ACQUIRED,
		ASYNC_RELEASED,
		EMPTY,	//No lock operation was performed
		UNKNOWN; //Error state
	}

	public enum ResultType {
		
		UNRESOLVED_INTERESTING_CALLBACKS(1),
		
		UNRESOLVED_ASYNC_CALLS(2),
		
		//Activity
		ACTIVITY_ONPAUSE(2),
		ACTIVITY_ONSTOP(3),
		//Service
		SERVICE_ONSTART(2),
		SERVICE_ONDESTORY(3),
		SERVICE_ONUNBIND(2),
		INTENTSERVICE_ONHANDLEINTENT(2),
		//Runnable
		RUNNABLE_RUN(2),
		//BoradcaseReceiver
		BROADCAST_RECEIVER_ONRECEIVE(2),		
		//Application
		APPLICATION_TERMINATE(2),
		
		//Analysis results
		OPTIMIZATION_FAILURE(2),
		ANALYSIS_FAILURE(2),
		UNIMPLEMENTED_FAILURE(2), 
		 
		IOEXCEPTION_FAILURE(2);
		
		int level;		//the level of seriousness of the condition
		
		private ResultType(int a) {
			level = a;
		}

		public int getLevel() {
			return level;			
		}
	}





	/**
	 * Main structures that hold the analysis results for every component
	 */
	private ComponentManager componentManager;


	public ProcessResults(ComponentManager componentManager) {
		this.componentManager = componentManager;
	}


	public ViolationReport processExitStates() {
		System.out.println();
		//LockUsageReport usageReport = new LockUsageReport();
		ViolationReport report = new ViolationReport();
		
		//Check that there are no unresolved Intent calls 
		//performed at high energy state
		
		HashSetMultiMap<MethodReference, SSAInstruction> criticalUnresolvedAsyncCalls = 
				componentManager.getCriticalUnresolvedAsyncCalls();
		
		if (criticalUnresolvedAsyncCalls.size() > 0) {
			report.insertViolation(GeneralContext.singleton(componentManager.getGlobalManager()),
					new Violation(ResultType.UNRESOLVED_ASYNC_CALLS));
		}
		
		for (MethodReference mr : criticalUnresolvedAsyncCalls.keySet()) {
			Set<SSAInstruction> is = criticalUnresolvedAsyncCalls.get(mr);
			E.red();
			System.out.println("Unresolved Intent/Runnable(s) called at high energy state: "); 
			System.out.println("  by method: " + mr.getSignature());
			System.out.println("  through instruction(s): ");
			for (SSAInstruction i : is) {
				System.out.println("  " + i.toString());
			}
			System.out.println();
			E.resetColor();
		}
		
		for (SuperComponent superComponent : componentManager.getSuperComponents()) {
			
			if (!superComponent.callsInteresting()) {
				E.grey();
				System.out.println("Skipping uninteresting: "+ superComponent.toString());
				E.resetColor();
				continue;
			}
			
			System.out.println("Checking: "+ superComponent.toString());
			
			//Each context should belong to exactly one SuperComponent
			for (Context context : superComponent.getContexts()) {
				
				//Focus just on Components (Activities, Services, BcastRcv...)
				if (!(context instanceof Component)) continue;
				Component component = (Component) context;
				
				
				if (DEBUG > 0) {
					System.out.println(" - Checking violatios for: " + component.toString());
				}
				
				//Do not analyze abstract classes (they will have to be 
				//extended in order to be used)
				if (component.isAbstract()) {
					if (DEBUG > 0) {
						E.grey();
						System.out.println(" - Skipping abstract: " + component.toString() );
						E.resetColor();
					}
					continue;
				}
				Set<Violation> assembleReport = component.assembleReport();
				report.insertViolations(component, assembleReport);
				//Do this if you want to get color on the violating methods 
				if (DEBUG > 2) {
					if (assembleReport.size() > 0) {
						E.yellow();
					}
					System.out.println(" - Checking violatios for: " + component.toString());
					E.resetColor();
				}
			}
		}
    report.dump();
		return report;
	}


}

