package energy.analysis;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.collections.Pair;

import energy.components.Component;
import energy.interproc.CompoundLockState;
import energy.interproc.SingleLockState;
import energy.util.E;

public class AnalysisResults {
	
	/* Ugly structure to keep interesting stuff */	
	private HashSet<Pair<Component, Map<String, Map<FieldReference, Set<SingleLockState>>>>> resultStuff = null;
	//The string is the component callback 
	
	
	public class ComponentResult {
		HashMap<String,CompoundLockState> callBackExitStates;
		
		public ComponentResult() {
			callBackExitStates = new HashMap<String,CompoundLockState>();
		}	
		
	}
		
	/**
	 * The constructor
	 */
	AnalysisResults() {
				
		resultStuff = new HashSet<Pair<Component,Map<String, Map<FieldReference, Set<SingleLockState>>>>>();
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
	

	public void registerExitLockState(Component component, Map<String, Map<FieldReference, Set<SingleLockState>>> map) {		
		resultStuff.add(Pair.make(component, map));
		StringBuffer sb = new StringBuffer();
		for(Entry<String, Map<FieldReference, Set<SingleLockState>>> e : map.entrySet()) {
			//Check if this is an interesting callback 
			if (e.getValue().size() > 0) {
				sb.append(e.getKey() + "\n");
				for (Entry<FieldReference, Set<SingleLockState>> entry : e.getValue().entrySet()) {
					sb.append(entry.getKey() + " -> " + entry.getValue().toString());					
				}
			}
		}
		//Output only when there's something to output
		if (sb.length() > 0 ) {
			E.log(1, component.toString() + "\n" + sb.toString());				
		}				
	}
	
	
	
	private LockUsage getLockUsage(SingleLockState runState) {								
		
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

		for (Pair<Component, Map<String, Map<FieldReference, Set<SingleLockState>>>> pair : resultStuff) {
			
			Component component = pair.fst;
			String componentName = component.getComponentName();
			
			Map<String, Map<FieldReference, Set<SingleLockState>>> callBackMap = pair.snd;
			
			for(Entry<String, Map<FieldReference, Set<SingleLockState>>> e : callBackMap.entrySet()) {
				
				String callBackName = e.getKey();				
				Pair<String, String> compAndCB = Pair.make(component.toString(),callBackName);
				Pair<String, String> abstCompAndCB = Pair.make(componentName,callBackName);
				
				//TODO: add a more complete representation
				HashMap<FieldReference,LockUsage> lockUsages = new HashMap<FieldReference, LockUsage>();
				for ( Entry<FieldReference, Set<SingleLockState>>  fs : e.getValue().entrySet()) {
					FieldReference field = fs.getKey();
					Set<SingleLockState> sls = fs.getValue();
					/* Use the merged state here.
					 * Ideally this shouldn't matter for callbacks, as there
					 * should only be one state 
					 */					
					SingleLockState sl = SingleLockState.mergeSingleLockStates(sls);
					LockUsage lockUsage = getLockUsage(sl);
					lockUsages.put(field, lockUsage);					
					EnumMap<LockUsage, Integer> enumMap = callBackResultMap.get(abstCompAndCB);					
					if(lockUsage != LockUsage.EMPTY) {
						E.log(1, compAndCB.toString() + " :: "  + lockUsage.toString());	
					}				
					
					updateEnumMap(enumMap, lockUsage);
					
				}
				
								
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
