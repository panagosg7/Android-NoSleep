package energy.interproc;

import java.util.HashMap;

import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

import energy.analysis.LockInvestigation.LockType;

public class CompoundLockState {

	
	private HashMap<FieldReference, Pair<LockType,  SingleLockState>> map = null;
	
	/**
	 * Constructors
	 */
	public CompoundLockState() {	
		map = new HashMap<FieldReference, Pair<LockType,SingleLockState>>();		
	}			
	
	
	public CompoundLockState(HashMap<FieldReference, Pair<LockType, SingleLockState>> newMap) {		
		map = newMap;		
	}
	
	public CompoundLockState(FieldReference f, LockType t, SingleLockState s) {		
		if (map == null) {
			map = new HashMap<FieldReference, Pair<LockType,SingleLockState>>();		
		}		
		map.put(f, (Pair<LockType, SingleLockState>) Pair.make(t, s));		
	}
	
	
	/**
	 * Add a state for the first time
	 * @param field
	 * @param type
	 * @param lockState
	 */
	public void addLockState(FieldReference field, LockType type, SingleLockState lockState) {
		
		Pair<LockType, SingleLockState> p = map.get(field);
		
		if (p == null) {			
			map.put(field, Pair.make(type, lockState));				
		}
		else {
			Assertions.productionAssertion(p.fst.equals(type), 		
				"Trying to add the lock with difference types.");	
		}
		
	}
	
	
	/**
	 * Try to merge a state in an existing one. Will add new lock if necessary.
	 * @param field
	 * @param type
	 * @param lockState
	 */
	public CompoundLockState mergeLockState(FieldReference field, LockType type, SingleLockState lockState) {
		
		Pair<LockType, SingleLockState> p = map.get(field);
		
		if (p != null) {		
			SingleLockState result = p.snd.merge(lockState);			
			Pair<LockType, SingleLockState> newPair = Pair.make(type, result);							
			HashMap<FieldReference, Pair<LockType,  SingleLockState>> clone = 
					(HashMap<FieldReference, Pair<LockType, SingleLockState>>) map.clone();

			//overwrite old state
			clone.put(field, newPair);			
			return new CompoundLockState(clone);				
		}
		else {			
			return new CompoundLockState(field, type, lockState);			
		}		
	}
	
	
	
	
	public Pair<LockType, SingleLockState> getLockState(FieldReference f) {		
		Pair<LockType, SingleLockState> pair = map.get(f);
		return pair;
		
	}
	
	public HashMap<FieldReference, Pair<LockType, SingleLockState>> getFullLockState() {
			
		return map;
		
	}
	
	
	
	
}
