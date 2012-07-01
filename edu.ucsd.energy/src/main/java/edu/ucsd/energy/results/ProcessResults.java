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
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.managers.ComponentManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.util.E;

public class ProcessResults {

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
		
		//Activity
		ACTIVITY_ONPAUSE(2),
		ACTIVITY_ONSTOP(3),
		//Service
		SERVICE_ONSTART(2),
		SERVICE_ONDESTORY(3),

		//Analysis results
		OPTIMIZATION_FAILURE(2),
		ANALYSIS_FAILURE(2),
		UNIMPLEMENTED_FAILURE(2), 
		INTENTSERVICE_ONHANDLEINTENT(2);
		
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
		HashSetMultiMap<MethodReference, SSAInstruction> importantUnresolvedIntents =
				componentManager.getImportantUnresolvedIntents();
		for (MethodReference mr : importantUnresolvedIntents.keySet()) {
			Set<SSAInstruction> is = importantUnresolvedIntents.get(mr);
			E.red();
			System.out.println("Unresolved intent call at high energy state."); 
			System.out.println("Method: " + mr.getSignature());
			System.out.println(is);
			System.out.println();
			E.resetColor();
		}
		
		for (SuperComponent superComponent : componentManager.getSuperComponents()) {
			
			if (!superComponent.callsInteresting()) {
				System.out.println(superComponent.toString() + " is uninteresting - moving on...");
				continue;
			}
			
			//Each context should belong to exactly one SuperComponent
			for (Context context : superComponent.getContexts()) {
				
				//Focus just on Components (Activities, Services, BcastRcv...)
				if (!(context instanceof Component)) continue;
				//Do not analyze abstract classes (they will have to be 
				//extended in order to be used)
				if (context.isAbstract()) {
					E.grey();
					System.out.println(context.toString() + " is abstract - moving on...");
					E.resetColor();
					continue;
				}
				System.out.println(context.toString() + " - processing ...");
				Component component = (Component) context;
				report.mergeReport(component.assembleReport());		
			}
		}
		
    report.dump();  
		
		return report;
	}


}

