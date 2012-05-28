package edu.ucsd.energy.policy;

import edu.ucsd.energy.contexts.Handler;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class HandlerPolicy extends Policy<Handler>{

	public HandlerPolicy(Handler component) {
		super(component);
	}

	public void solveFacts() {
		SingleLockState handleState = map.get("handleMessage");
		if (unlocking(handleState) && (!strongUnlocking(handleState))) {
			trackResult(new BugResult(ResultType.WEAK_SERVICE_DESTROY, component.toString()));
		}
		
	}

}
