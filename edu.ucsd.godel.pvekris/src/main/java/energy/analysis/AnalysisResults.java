package energy.analysis;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.collections.Util;

import energy.components.Component;
import energy.interproc.LockState;
import energy.util.E;

public class AnalysisResults {
	
	/* Ugly structure to keep interesting stuff */	
	private HashSet<Pair<Component, Map<String, LockState>>> resultStuff = null;
	//The string is the component callback 
	
	
	public class ComponentResult {
		HashMap<String,LockState> callBackExitStates;
		
		public ComponentResult() {
			callBackExitStates = new HashMap<String,LockState>();
		}	
		
	}
		
	/**
	 * The constructor
	 */
	AnalysisResults() {
				
		resultStuff = new HashSet<Pair<Component,Map<String,LockState>>>();
		callBackResultMap = new HashMap<Pair<String,String>, EnumMap<LockUsage,Integer>>();
		
	}
	
	int threadCount 	= 0; 
	int activityCount 	= 0;
			
	int lockThreads 	= 0;
	int nolockThreads 	= 0;
	int unlockThreads 	= 0;
	
	int lockunlockThreads = 0;
	
	public enum LockUsage {
		LOCKING,
		UNLOCKING,	
		NOLOCKING,
		LOCKUNLOCK, 
		EMPTY,
		UNKNOWN_STATE, 
		FULL_UNLOCKING;
	}
	
	private Map<Pair<String, String>, EnumMap<LockUsage, Integer>> callBackResultMap;
	

	public void registerExitLockState(Component component, Map<String, LockState> exitLockStates) {
		
		resultStuff.add(Pair.make(component, exitLockStates));
		
		/* Testing */    	
    	
    	Predicate<LockState> p =
    	  new Predicate<LockState>() {    		
			@Override
			public boolean test(LockState s) {				
				return s.isReached();
			}
		  };
		  		  
		  if (Util.forSome(exitLockStates.values(), p)) {
			
			E.log(1, component.toString() + " |-> ");
			
			for( Entry<String, LockState> e : exitLockStates.entrySet()) {
				if(e.getValue().isReached()) {
					E.log(1, "\t" + e.getKey() + " -> " + e.getValue()); 
				}
			}	
			
		  };		
		
		
	}
	
	
	private LockUsage getLockUsage(LockState runState) {								
		
		if (runState != null) {
			
			if(runState.isMaybeAcquired() && (!runState.isMaybeReleased())) {				
				return LockUsage.LOCKING;
			}
			
			else if(!runState.isMaybeReleased() && (!runState.isMaybeAcquired())) {
				return LockUsage.EMPTY;
			}
			
			else if(runState.isMaybeReleased() && (!runState.isMaybeAcquired())) {				
				return LockUsage.FULL_UNLOCKING;
			}
			
			else if(runState.isMaybeReleased()) {				
				return LockUsage.UNLOCKING;
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
			String componentName = component.getComponentName();
			
			Map<String, LockState> callBackMap = pair.snd;
			
			for(Entry<String, LockState> e : callBackMap.entrySet()) {
				
				String callBackName = e.getKey();				
				LockState lockState = e.getValue();			
				
				Pair<String, String> compAndCB = Pair.make(component.toString(),callBackName);
				Pair<String, String> abstCompAndCB = Pair.make(componentName,callBackName);
				LockUsage lockUsage = getLockUsage(lockState);
				
				EnumMap<LockUsage, Integer> enumMap = callBackResultMap.get(abstCompAndCB);
				
				if(lockUsage != LockUsage.EMPTY) {
					E.log(1, compAndCB.toString() + " :: "  + lockUsage.toString());	
				}				
				
				updateEnumMap(enumMap, lockUsage);
			}		
		}	
	}


	private void updateEnumMap(EnumMap<LockUsage, Integer> enumMap, LockUsage lockUsage) {
		
		if(enumMap == null) {
			enumMap = new EnumMap<LockUsage, Integer>(LockUsage.class);			
		}
		
		Integer count = enumMap.get(lockUsage);
		if (count == null) {
			count = new Integer(1);
			enumMap.put(lockUsage, count);
		} 
		else {
			enumMap.put(lockUsage, count + 1);
		}
		
		
	}

	public void outputFinalResults() {
		
		for (Entry<Pair<String, String>, EnumMap<LockUsage, Integer>> e : callBackResultMap.entrySet()) {
			
			Pair<String, String> key = e.getKey();
			
			E.log(1, key.toString());

			EnumMap<LockUsage, Integer> usages = e.getValue();
			
			for (Entry<LockUsage, Integer> u : usages.entrySet()) {
				LockUsage usage = u.getKey();
				Integer count = u.getValue();
				E.log(1, "--> " + usage.toString() + " :: " + count.toString());
								
			}
			
		}
		
		
	}
	
	
}
