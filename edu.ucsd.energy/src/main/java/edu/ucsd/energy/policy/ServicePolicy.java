package edu.ucsd.energy.policy;

import java.util.Map;

import edu.ucsd.energy.contexts.Service;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.ResultType;
import edu.ucsd.energy.results.ProcessResults.SingleLockUsage;

public class ServicePolicy extends Policy<Service> {

	public ServicePolicy(Service service) {
		super(service);
	}
	
	private Map<WakeLockInstance, SingleLockUsage> getServiceOnStart() {
		Map<WakeLockInstance, SingleLockUsage> onStart = map.get("onStart");			//deprecated version
		Map<WakeLockInstance, SingleLockUsage> onStartCommand = map.get("onStartCommand");	
		if (onStartCommand == null) {
			return onStart;				
		}
		else {
			return onStartCommand;
		}
	}
	
	public void solveFacts() {
		Map<WakeLockInstance, SingleLockUsage> onStartState = getServiceOnStart();
		Map<WakeLockInstance, SingleLockUsage> onDestroyState = map.get("onDestroy");
		for(WakeLockInstance wli : instances) {
			if (locking(onStartState, wli)) {
				trackResult(new BugResult(ResultType.SERVICE_START, component.toString()));
			}
			if (locking(onDestroyState, wli)) {
				trackResult(new BugResult(ResultType.SERVICE_DESTROY, component.toString()));
			}
		}
	}

	


}
