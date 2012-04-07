package energy.interproc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.collections.Util;

public class CompoundLockState {
	
	
	/** This is the main content of the compound lock state */
	private HashMap<FieldReference, SingleLockState> map = null;	
	
	/**
	 * Constructors
	 */
	public CompoundLockState() {	
		map = new HashMap<FieldReference, SingleLockState>();		
	}			
	
	
	public CompoundLockState(HashMap<FieldReference, SingleLockState> newMap) {		
		map = newMap;		
	}
	
	public CompoundLockState(FieldReference f, SingleLockState s) {		
		if (map == null) {
			map = new HashMap<FieldReference, SingleLockState>();		
		}		
		map.put(f, s);		
	}
	
	
	/**
	 * Add a state for the first time
	 * @param field
	 * @param lockState
	 */
	public void addLockState(FieldReference field, SingleLockState lockState) {
		/* XXX: originally had the type of the wakelock and did some checks too */ 
		map.put(field, lockState);
	}
	
	
	/**
	 * Try to merge a state in an *existing* one. Will add new lock if necessary.
	 * @param field 
	 * @param lockState
	 * @return 
	 */
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
		
		for (Entry<FieldReference, SingleLockState> e : c.getFullLockState().entrySet()) {
			
			FieldReference field	= e.getKey();			
			SingleLockState state 	= e.getValue();
			
			result = result.mergeLockState(field, state);
			
		}
		
		return result;

	} 	
	
	public SingleLockState getLockState(FieldReference f) {		
		SingleLockState pair = map.get(f);
		return pair;
		
	}
	
	public Collection<SingleLockState> getLockStates() {		
		return map.values();		
	}
	
	
	public HashMap<FieldReference, SingleLockState> getFullLockState() {			
		return map;
	}
	
	/**
	 * TODO: fix this
	 * @return
	 */
	public String getColor() {
		String result = "lightgrey";		
		
		if (map.values().iterator().hasNext()) {
			result = map.values().iterator().next().getColor();
		}
		
		return result;
			
	}
	
	
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
	
	
	@Override
	public int hashCode() {
		int hash = 1;
		for (Entry <FieldReference, SingleLockState> e : map.entrySet()) {
			hash += 71123 * e.getKey().hashCode() + 1937 * e.getValue().hashCode();
		}
		return hash;		
	}
	
	@Override
	public boolean equals(Object o) {		
		
		if (o instanceof CompoundLockState) {
			
			CompoundLockState cls = (CompoundLockState) o;
			
			if(cls.getFullLockState().size() == this.getFullLockState().size()) {
				
				for (Entry<FieldReference, SingleLockState> e : cls.getFullLockState().entrySet()) {
					SingleLockState sls = getFullLockState().get(e.getKey());
					if (sls == null) {
						return false;				
					}
					else {
						if (!sls.equals(e.getValue())) {
							return false;	
						}
					}
				}				
				return true;
			}			
		}
		return false;		
	}
	
	
	public String toString(){
		StringBuffer sb = new StringBuffer();
		for ( Entry<FieldReference, SingleLockState> e : map.entrySet()) {
			sb.append(e.getKey().toString() + " -> " + e.getValue().toString());			
		}
		return sb.toString();
		
	}
	
}
