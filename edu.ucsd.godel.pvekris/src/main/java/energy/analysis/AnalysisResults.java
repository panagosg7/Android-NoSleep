package energy.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

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
	
	int threadCount = 0; 
	
	int lockThreads = 0 ;
	
	int unlockThreads = 0;
	
	int locknlockThreds = 0;
	
	
	void registerThread() {
		
	}


	public void registerExitLockState(Component component,
			Map<String, LockState> exitLockStates) {
		
		resultStuff.add(Pair.make(component, exitLockStates));
		
	}


	public void processResults() {

		for (Pair<Component, Map<String, LockState>> pair : resultStuff) {
			
			Component component = pair.fst;
			
			//Map<String, LockState> lockStateMap = pair.snd;
			
			if (component.isThread()) {
				
				
			}
			
			if (component.isActivity()) {
				
				
			}
			
			
		}
		
	} 
	
	
	
}
