package edu.ucsd.energy.policy;

import java.util.Map;

import edu.ucsd.energy.contexts.RunnableThread;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.SingleLockUsage;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class RunnablePolicy extends Policy<RunnableThread> {

	public RunnablePolicy(RunnableThread component) {
		super(component);
	}



	public void solveFacts() {
		Map<WakeLockInstance, SingleLockUsage> runState = map.get("run");
		for(WakeLockInstance wli : instances) {
			if (locking(runState, wli)) {
				trackResult(new BugResult(ResultType.BROADCAST_RECEIVER_ON_RECEIVE, component.toString()));					
			}				
		}
	}

	
}
