package edu.ucsd.energy.policy;

import edu.ucsd.energy.contexts.Activity;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class ActivityPolicy extends Policy<Activity> {

	public ActivityPolicy(Activity activity) {
		super(activity);
	}

	public void solveFacts() {
		SingleLockState onCreateState = map.get("onCreate");
		if (locking(onCreateState)) {
			trackResult(new BugResult(ResultType.LOCKING_ON_CREATE, component.toString()));
		}
		SingleLockState onStartState = map.get("onStart");
		if (locking(onStartState)) {
			trackResult(new BugResult(ResultType.LOCKING_ON_START, component.toString()));
		}
		
		SingleLockState onRestartState = map.get("onRestart");
		if (locking(onRestartState)) {
			trackResult(new BugResult(ResultType.LOCKING_ON_RESTART, component.toString()));
		}
		
		SingleLockState onPauseState = map.get("onPause");
		
		if ((!locking(onCreateState)) && (!locking(onStartState)) 
				&& locking(onRestartState) && (!unlocking(onPauseState))) {
			trackResult(new BugResult(ResultType.STRONG_PAUSE_RESUME, component.toString()));
		}
		
		if (weakUnlocking(onPauseState) && (!strongUnlocking(onPauseState))) {
			trackResult(new BugResult(ResultType.WEAK_PAUSE_RESUME, component.toString()));
		}
		
	}
	
	

}
