package edu.ucsd.energy.policy;

import java.util.Map;

import edu.ucsd.energy.contexts.Activity;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.SingleLockUsage;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class ActivityPolicy extends Policy<Activity> {

	public ActivityPolicy(Activity activity) {
		super(activity);
	}

	public void solveFacts() {
		//Apply the policies for every lock separately 
		for(WakeLockInstance wli : instances) {
		
			Map<WakeLockInstance, SingleLockUsage> onCreateState = map.get("onCreate");
			Map<WakeLockInstance, SingleLockUsage> onStartState = map.get("onStart");
			Map<WakeLockInstance, SingleLockUsage> onRestartState = map.get("onRestart");
			Map<WakeLockInstance, SingleLockUsage> onPauseState = map.get("onPause");
			
			if (locking(onCreateState, wli)) {
				trackResult(new BugResult(ResultType.LOCKING_ON_CREATE, component.toString()));
			}
			
			if (locking(onStartState, wli)) {
				trackResult(new BugResult(ResultType.LOCKING_ON_START, component.toString()));
			}
			
			if (locking(onRestartState, wli)) {
				trackResult(new BugResult(ResultType.LOCKING_ON_RESTART, component.toString()));
			}
			//TODO : these need more work
			if ((!locking(onCreateState, wli)) && (!locking(onStartState, wli)) 
					&& locking(onRestartState, wli) && (!unlocking(onPauseState, wli))) {
				trackResult(new BugResult(ResultType.PAUSE_RESUME, component.toString()));
			}
			
			if (unlocking(onPauseState, wli)) {
				trackResult(new BugResult(ResultType.PAUSE_RESUME, component.toString()));
			}
		}
	}
	
	

}
