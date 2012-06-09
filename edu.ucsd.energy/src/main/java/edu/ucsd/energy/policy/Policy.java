package edu.ucsd.energy.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.json.JSONArray;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.interproc.SingleLockState.LockStateDescription;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.SingleLockUsage;

abstract public class Policy<T extends Context> implements IPolicy {

	protected Context component;
	
	protected HashMap<String,Map<WakeLockInstance, SingleLockUsage>> map = 
			new  HashMap<String, Map<WakeLockInstance,SingleLockUsage>>();
	
	protected HashSet<WakeLockInstance> instances = new HashSet<WakeLockInstance>();
	
	private ArrayList<BugResult> bugArray = new ArrayList<BugResult>();

	public Policy(T c) {
		component = c;
	}
	
	public void addFact(CGNode n, Map<WakeLockInstance, SingleLockUsage> st) {
		map.put(n.getMethod().getName().toString(), st);
		if (st != null) {
			for(Entry<WakeLockInstance, SingleLockUsage> e : st.entrySet()) {
				instances.add(e.getKey());			
			}
		}
	}
	
	public JSONArray toJSON() {
		JSONArray jsonArray = new JSONArray();
		for (BugResult a : bugArray) {
			jsonArray.add(a.toString());
		}
		return jsonArray;
	}
		
	
	public boolean isEmpty() {
		return bugArray.isEmpty();
	}
	
	protected void trackResult(BugResult result) {
		this.bugArray.add(result);
	}

	public static LockStateDescription getLockUsage(SingleLockState runState) {								
		if (runState != null) {
			return runState.getLockStateDescription();
		}
		return null;		
	}

	/**
	 * Merge a set of lock states. Should return UNDEFIINED if the set is empty
	 * @param set
	 * @return
	 */
	protected SingleLockState mergeSingleLockStates(Set<SingleLockState> set) {
		SingleLockState result = null;
		for(SingleLockState s : set)  {
			if (result == null) {
				result = s;
			}
			else {
				result = result.merge(s);
			}
		}
		return result;		
	}

	protected boolean locking(Map<WakeLockInstance, SingleLockUsage> state, WakeLockInstance wli) {
		if (state != null) {
			SingleLockUsage lockUsage = state.get(wli);
			return (
				lockUsage.equals(SingleLockUsage.ACQUIRED) ||
				lockUsage.equals(SingleLockUsage.ASYNC_ACQUIRED)
			);
		} 
		return false;
	}


	protected boolean unlocking(Map<WakeLockInstance, SingleLockUsage> state, WakeLockInstance wli) {
		if (state != null) {
			SingleLockUsage lockUsage = state.get(wli);
			return (
				lockUsage.equals(SingleLockUsage.RELEASED) ||
				lockUsage.equals(SingleLockUsage.TIMED_ACQUIRED) ||
				lockUsage.equals(SingleLockUsage.EMPTY) ||
				lockUsage.equals(SingleLockUsage.ASYNC_RELEASED)	//Dunno about this
			);
		} 
		return true;
	}

}
