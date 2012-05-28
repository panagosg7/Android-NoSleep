package edu.ucsd.energy.interproc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONObject;

import edu.ucsd.energy.managers.WakeLockInstance;


public class CompoundLockState {
	
	
	/** This is the main content of the compound lock state */
	private Map<WakeLockInstance, Set<SingleLockState>> map = null;	
	
	/**
	 * Constructors
	 */
	public CompoundLockState(Map<WakeLockInstance, Set<SingleLockState>> m) {	
		this.map = m;
	}
	
	public CompoundLockState() {
		this.map = new HashMap<WakeLockInstance, Set<SingleLockState>>();
	}
	
	public void register(WakeLockInstance wli, SingleLockState ls) {
		Set<SingleLockState> set = map.get(wli);
		if (set == null) {
			set = new HashSet<SingleLockState>();
		}
		set.add(ls);
		map.put(wli,set);
	}
	
	public static CompoundLockState merge(Set<CompoundLockState> set) {
		CompoundLockState result = new CompoundLockState();
		for (CompoundLockState cls : set) {
			for(Entry<WakeLockInstance, Set<SingleLockState>> ls : cls.getLockStateMap().entrySet()) {
				for(SingleLockState sls : ls.getValue()) {
					result.register(ls.getKey(), sls);	
				}
			}
		}
		return result;
	}
	
	
	/**
	 * Add a state for the first time
	 * @param field
	 * @param lockState
	 */
	public void addLockState(WakeLockInstance wli, Set<SingleLockState> lockState) {
		map.put(wli, lockState);
	}
	
	public SingleLockState simplify() {
		SingleLockState result = null;
		for (Set<SingleLockState> e : map.values()) {
			for(SingleLockState s : e)  {
				if (result != null) {
					result = result.merge(s);
				}
				else {
					result = s;
				}
			}
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
	
	public Set<SingleLockState> getLockState(WakeLockInstance f) {		
		Set<SingleLockState> pair = map.get(f);
		return pair;		
	}
	
	public Map<WakeLockInstance, Set<SingleLockState>> getLockStateMap() {			
		return map;
	}
	
	public String toString(){
		StringBuffer sb = new StringBuffer();
		for (Entry<WakeLockInstance, Set<SingleLockState>> e : map.entrySet()) {
			sb.append(e.getKey().toString() + " :: " + e.getValue().toString() + "\n");			
		}
		return sb.toString();
	}
	
	public String toShortString(){
		StringBuffer sb = new StringBuffer();
		for (Entry<WakeLockInstance, Set<SingleLockState>> e : map.entrySet()) {
			sb.append(e.getKey().toShortString() + " :: " + e.getValue().toString() + "\n");			
		}
		return sb.toString();
	}

	public JSONObject toJSON() {
		JSONObject compObj = new JSONObject(map);
		
		/*
		for(Entry<WakeLockInstance, Set<SingleLockState>> e : map.entrySet()) {
			JSONObject key = e.getKey().toJSON();
		}
		*/
		return compObj;
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}
	
}
