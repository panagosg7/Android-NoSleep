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
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.managers.ComponentManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.util.Log;

public class ProcessResults {

	private static final int DEBUG = 2;

	
	public static class LockUsage extends HashMap<WakeLockInstance, SingleLockState> {

		private static final long serialVersionUID = -6164699487290522725L;

		public LockUsage() {
			super();
		}
		
		Set<Component> relevant;
		
		public LockUsage(CompoundLockState cls) {
			super(cls.getLockStateMap());
			relevant = new HashSet<Component>();			
			for (SingleLockState ls : cls.getLockStateMap().values()) {
				relevant.addAll(ls.involvedContexts());				
			}
		}
		
		public boolean relevant(Component c) {
			return relevant.contains(c);
		}

		public Set<Component> getRelevantCtxs() {
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
		//Callable
		CALLABLE_CALL(2),
		//BoradcaseReceiver
		BROADCAST_RECEIVER_ONRECEIVE(2),		
		//Application
		APPLICATION_TERMINATE(2),
		//Unresolved component
		UNRESOLVED_CALLBACK(2),
		
		
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
		if(DEBUG > 0) {
			Log.println();	
		}
		
		//LockUsageReport usageReport = new LockUsageReport();
		ViolationReport report = new ViolationReport();
		
		//Check that there are no unresolved Intent calls 
		//performed at high energy state
		
		HashSetMultiMap<MethodReference, SSAInstruction> criticalUnresolvedAsyncCalls = 
				componentManager.getCriticalUnresolvedAsyncCalls();
		
		if (criticalUnresolvedAsyncCalls.size() > 0) {
			GeneralViolation generalViolation = new GeneralViolation("General");
			report.insertViolation(generalViolation, new Violation(ResultType.UNRESOLVED_ASYNC_CALLS));
		}
		
		for (MethodReference mr : criticalUnresolvedAsyncCalls.keySet()) {
			Set<SSAInstruction> is = criticalUnresolvedAsyncCalls.get(mr);
			Log.red();
			Log.println("Unresolved Intent/Runnable(s) called at high energy state: "); 
			Log.println("  by method: " + mr.getSignature());
			Log.println("  through instruction(s): ");
			for (SSAInstruction i : is) {
				Log.println("  " + i.toString());
			}
			Log.println();
			Log.resetColor();
		}
		
		for (SuperComponent superComponent : componentManager.getSuperComponents()) {
			
			if (!superComponent.callsInteresting()) {
				Log.grey();
				Log.println("Skipping uninteresting: "+ superComponent.toString());
				Log.resetColor();
				continue;
			}
			
			Log.println("Checking: "+ superComponent.toString());
			
			//Each context should belong to exactly one SuperComponent
			for (Component component : superComponent.getContexts()) {
				//Do not analyze abstract classes (they will have to be 
				//extended in order to be used)
				if (component.isAbstract()) {
					if (DEBUG > 0) {
						Log.grey("Skipping abstract: " + component.toString() );
					}
					continue;
				}
				
				Set<Violation> assembleReport = component.assembleReport();
				report.insertViolations(component, assembleReport);
				if (DEBUG > 0) {
					if (assembleReport.size() > 0) {
						Log.yellow();
					}
					Log.println(" - Checking violatios for: " + component.toString());
					Log.resetColor();
				}
			}
		}
    report.dump();
		return report;
	}


}

