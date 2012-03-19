package energy.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.ibm.wala.demandpa.flowgraph.ReturnBarLabel;
import com.ibm.wala.util.collections.Pair;

import energy.components.Component;
import energy.interproc.LockState;

public class AnalysisResults {
	
	/* Ugly structure to keep interesting stuff */
	private HashSet<Pair<Component, Map<String, LockState>>> resultStuff = null; 
	
	
	public class ComponentResult {
		HashMap<String,LockState> callBackExitStates;
		
		public ComponentResult() {
			callBackExitStates = new HashMap<String,LockState>();
		}	
		
	}
		
	
	AnalysisResults() {
		
	 resultStuff = new HashSet<Pair<Component,Map<String,LockState>>>();
		
	}
	
	int threadCount 	= 0; 
	int activityCount 	= 0;
			
	int lockThreads 	= 0;
	int nolockThreads 	= 0;
	int unlockThreads 	= 0;
	
	int lockunlockThreads = 0;
	
	public enum LockUsage {
		LOCKING, UNLOCKING,	NOLOCKING, LOCKUNLOCK, EMPTY, UNKNOWN_STATE;		
	}
	
	
	void registerThread() {
		
	}


	public void registerExitLockState(Component component,
			Map<String, LockState> exitLockStates) {
		
		resultStuff.add(Pair.make(component, exitLockStates));
		
	}
	
	
	private LockUsage getLockUsage(LockState runState) {
						
		if (runState != null) {
			
			if(runState.isMaybeAcquired() && (!runState.isMaybeReleased())) {				
				return LockUsage.EMPTY;
			}
			
			else if(!runState.isMaybeReleased() && (!runState.isMaybeAcquired())) {
				return LockUsage.EMPTY;
			}
			
			else if(runState.isMaybeReleased() && (!runState.isMaybeAcquired())) {				
				return LockUsage.EMPTY;
			}
			
			else if(runState.isMaybeReleased()) {				
				return LockUsage.EMPTY;
			}
			else {
				return LockUsage.UNKNOWN_STATE;		
			}
		}
		else {
			return LockUsage.EMPTY;
		}
		
		
	}
	


	public void processResults() {

		for (Pair<Component, Map<String, LockState>> pair : resultStuff) {
			
			Component component = pair.fst;
			
			Map<String, LockState> callBackMap = pair.snd;
			
			if (component.isThread()) {
				threadCount++;				
				LockUsage lockUsage = getLockUsage(callBackMap.get("run"));
				
				
			}
			
			if (component.isActivity()) {
				activityCount++;
				
				LockState onPauseState = callBackMap.get("onPause");
				
				if (onPauseState != null) {
					
					
					
					
					
					
				}
				
				
			}
			
			
		}
		
	} 
	
	
	
}
