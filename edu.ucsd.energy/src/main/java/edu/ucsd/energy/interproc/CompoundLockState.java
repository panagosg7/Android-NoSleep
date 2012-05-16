package edu.ucsd.energy.interproc;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONObject;

import edu.ucsd.energy.analysis.WakeLockInstance;


public class CompoundLockState {
	
	
	/** This is the main content of the compound lock state */
	private Map<WakeLockInstance, Set<SingleLockState>> map = null;	
	
	/**
	 * Constructors
	 */
	public CompoundLockState(Map<WakeLockInstance, Set<SingleLockState>> m) {	
		this.map = m;
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
			sb.append(e.getKey().toString() + " :: " + e.getValue().toString());			
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
	
}
