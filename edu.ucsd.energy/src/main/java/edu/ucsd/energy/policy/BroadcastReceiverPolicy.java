package edu.ucsd.energy.policy;

import edu.ucsd.energy.contexts.BroadcastReceiver;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class BroadcastReceiverPolicy extends Policy<BroadcastReceiver> {

	public BroadcastReceiverPolicy(BroadcastReceiver component) {
		super(component);
	}

	public void solveFacts() {
		SingleLockState onReceiveState = map.get("onReceive");
		if (locking(onReceiveState)) {
			trackResult(new BugResult(ResultType.BROADCAST_RECEIVER_ON_RECEIVE, component.toString()));					
		}				

		
	}

}
