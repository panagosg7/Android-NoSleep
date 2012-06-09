package edu.ucsd.energy.policy;

import java.util.Map;

import edu.ucsd.energy.contexts.BroadcastReceiver;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.SingleLockUsage;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class BroadcastReceiverPolicy extends Policy<BroadcastReceiver> {

	public BroadcastReceiverPolicy(BroadcastReceiver component) {
		super(component);
	}

	public void solveFacts() {
		Map<WakeLockInstance, SingleLockUsage> onReceiveState = map.get("onReceive");
		for(WakeLockInstance wli : instances) {
			if (locking(onReceiveState, wli)) {
				trackResult(new BugResult(ResultType.BROADCAST_RECEIVER_ON_RECEIVE, component.toString()));					
			}				
		}
	}

}
