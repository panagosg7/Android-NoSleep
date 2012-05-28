package edu.ucsd.energy.policy;

import edu.ucsd.energy.contexts.RunnableThread;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class RunnablePolicy extends Policy<RunnableThread> {

	public RunnablePolicy(RunnableThread component) {
		super(component);
	}

	public void solveFacts() {
		SingleLockState runState = map.get("run");
		if (locking(runState)) {
			trackResult(new BugResult(ResultType.THREAD_RUN, component.toString()));
		}				

	}

}
