package energy.interproc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.collections.Util;

import energy.analysis.WakeLockManager.WakeLockInfo;
import energy.analysis.WakeLockManager.WakeLockInstance;

public class CompoundLockState {
	
	
	/** This is the main content of the compound lock state */
	private Map<WakeLockInstance, Set<SingleLockState>> map = null;	
	
	/**
	 * Constructors
	 */
	public CompoundLockState() {	
		map = new HashMap<WakeLockInstance, Set<SingleLockState>>();		
	}			
	
	public CompoundLockState(Map<WakeLockInstance, Set<SingleLockState>> m) {	
		this.map = m;
	}
	
	
	/**
	 * Add a state for the first time
	 * @param field
	 * @param lockState
	 */
	public void addLockState(WakeLockInstance wli, Set<SingleLockState> lockState) {
		/* XXX: originally had the type of the wakelock and did some checks too */ 
		map.put(wli, lockState);
	}
	
	/*
	public CompoundLockState mergeLockState(FieldReference field, SingleLockState lockState) {
		SingleLockState p = map.get(field);
		if (p != null) {
			SingleLockState result = p.merge(lockState);				
			//overwrite old state
			map.put(field, result);			
		}
		else {		
			map.put(field, lockState);
		}
		return new CompoundLockState(map);
	}
	
	
	public CompoundLockState merge(CompoundLockState c) {
		CompoundLockState result = new CompoundLockState();
		for (Entry<FieldReference, SingleLockState> e : c.getAllLockStates().entrySet()) {
			FieldReference field	= e.getKey();			
			SingleLockState state 	= e.getValue();
			result = result.mergeLockState(field, state);
		}
		return result;
	} 	
	*/
	
	public Set<SingleLockState> getLockState(WakeLockInstance f) {		
		Set<SingleLockState> pair = map.get(f);
		return pair;		
	}
	
	public Map<WakeLockInstance, Set<SingleLockState>> getAllLockStates() {			
		return map;
	}
	
	/*
	public boolean isReached() {
		Predicate<SingleLockState> p =
			new Predicate<SingleLockState>() {
				@Override
				public boolean test(SingleLockState p) {				
					return p.isReached();
				}
		  	};		  	
		return Util.forSome(getLockStates(), p);
	}
	*/
	
	/*
	@Override
	public int hashCode() {
		int hash = 1;
		for (Entry<WakeLockInstance, Set<SingleLockState>> e : map.entrySet()) {
			hash += 71123 * e.getKey().hashCode() + 1937 * e.getValue().hashCode();
		}
		return hash;		
	}
	
	@Override
	public boolean equals(Object o) {		
		if (o instanceof CompoundLockState) {
			CompoundLockState cls = (CompoundLockState) o;
			if(cls.getAllLockStates().size() == this.getAllLockStates().size()) {
				for (Entry<WakeLockInstance, Set<SingleLockState>> e : cls.getAllLockStates().entrySet()) {
					Set<SingleLockState> sls = getAllLockStates().get(e.getKey());
					
				}				
				return true;
			}			
		}
		return false;		
	}
	*/
	
	public String toString(){
		StringBuffer sb = new StringBuffer();
		for (Entry<WakeLockInstance, Set<SingleLockState>> e : map.entrySet()) {
			sb.append(e.getKey().toString() + " :: " + e.getValue().toString());			
		}
		return sb.toString();
	}
	
}
