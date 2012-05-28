package edu.ucsd.energy.policy;

import edu.ucsd.energy.contexts.Service;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class ServicePolicy extends Policy<Service> {

	public ServicePolicy(Service service) {
		super(service);
	}
	
	private SingleLockState getServiceOnStart() {
		SingleLockState onStart = map.get("onStart");			//deprecated version
		SingleLockState onStartCommand = map.get("onStartCommand");	
		if (onStartCommand == null) {
			return onStart;				
		}
		else {
			return onStartCommand;
		}
	}
	
	public void solveFacts() {
		SingleLockState onStartState = getServiceOnStart();
		SingleLockState onDestroyState = map.get("onDestroy");		//FIX THIS
		if (locking(onStartState) && (!unlocking(onDestroyState))) {
			trackResult(new BugResult(ResultType.STRONG_SERVICE_DESTROY, component.toString()));
		}
		if (weakUnlocking(onDestroyState) && (!strongUnlocking(onDestroyState))) {
			trackResult(new BugResult(ResultType.WEAK_SERVICE_DESTROY, component.toString()));
		}
	}


}
