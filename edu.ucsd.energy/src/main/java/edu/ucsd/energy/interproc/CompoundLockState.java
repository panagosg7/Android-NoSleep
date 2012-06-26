package edu.ucsd.energy.interproc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.json.JSONObject;
import edu.ucsd.energy.managers.WakeLockInstance;

/**
 * Lockstate accounting for multiple locks 
 * The state associated with this is the result of merging all possible states
 * for each lock separately.
 */
public class CompoundLockState {
	
	/** This is the main content of the compound lock state */
	private Map<WakeLockInstance, SingleLockState> map = null;	
	
	/**
	 * Constructors
	 */
	public CompoundLockState(Map<WakeLockInstance, SingleLockState> m) {	
		this.map = m;
	}
	
	public CompoundLockState() {
		this.map = new HashMap<WakeLockInstance, SingleLockState>();
	}
	
	public void register(WakeLockInstance wli, SingleLockState ls) {
		SingleLockState oldSt = map.get(wli);
		if (oldSt == null) {
			map.put(wli,ls);
		}
		else {
			map.put(wli,SingleLockState.merge(oldSt, ls));
		}
	}
	
	public static CompoundLockState merge(Set<CompoundLockState> set) {
		CompoundLockState result = new CompoundLockState();
		for (CompoundLockState cls : set) {
			for(Entry<WakeLockInstance, SingleLockState> ls : cls.getLockStateMap().entrySet()) {
				result.register(ls.getKey(), ls.getValue());
			}
		}
		return result;
	}
	
	/**
	 * Simplified version of a CompoundLockState. 
	 * Handy for doing simple "is high energy state" checks.
	 * @return
	 */
	public SingleLockState simplify() {
		SingleLockState result = null;
		for (SingleLockState e : map.values()) {
			result = SingleLockState.merge(result, e);
		}
		return result;
	} 	
	
	public static SingleLockState simplify(Set<SingleLockState> set) {
		SingleLockState result = null;
		for(SingleLockState s : set)  {
			if (result != null) {
				result = result.merge(s);
			}
			else {
				result = s;				
			}
		}
		return result;
	} 	
	
	
	
	public SingleLockState getLockState(WakeLockInstance f) {		
		return map.get(f);
	}
	
	public Set<WakeLockInstance> getWakeLocks() {
		return map.keySet();
	}
	
	public Collection<SingleLockState> getStates() {
		return map.values();
	}
	
	public Map<WakeLockInstance, SingleLockState> getLockStateMap() {			
		return map;
	}
	
	public String toString(){
		StringBuffer sb = new StringBuffer();
		for (Entry<WakeLockInstance, SingleLockState> e : map.entrySet()) {
			sb.append(e.getKey().toString() + " :: " + e.getValue().toString() + "\n");			
		}
		return sb.toString();
	}
	
	public String toShortString(){
		StringBuffer sb = new StringBuffer();
		for (Entry<WakeLockInstance, SingleLockState> e : map.entrySet()) {
			sb.append(e.getKey().toShortString() + " :: " + e.getValue().toString() + "\n");			
		}
		return sb.toString();
	}

	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		for(Entry<WakeLockInstance, SingleLockState> e : map.entrySet()) {
			obj.put(e.getKey().toShortString(), e.getValue().toString());
		}
		return obj;
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}
	
}
