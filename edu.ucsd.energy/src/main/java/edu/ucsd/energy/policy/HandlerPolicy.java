package edu.ucsd.energy.policy;

import java.util.Map;

import edu.ucsd.energy.contexts.Handler;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.SingleLockUsage;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class HandlerPolicy extends Policy<Handler>{

	public HandlerPolicy(Handler component) {
		super(component);
	}

	public void solveFacts() {
		Map<WakeLockInstance, SingleLockUsage> handleMessageState = map.get("handleMessage");
		for(WakeLockInstance wli : instances) {
			if (locking(handleMessageState, wli)) {
				trackResult(new BugResult(ResultType.BROADCAST_RECEIVER_ON_RECEIVE, component.toString()));					
			}				
		}
	}
	
	
}
