package edu.ucsd.energy.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import net.sf.json.JSONArray;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.results.BugResult;
import edu.ucsd.energy.results.ProcessResults.LockUsage;

abstract public class Policy<T extends Context> implements IPolicy {

	protected Context component;
	
	protected HashMap<String,SingleLockState> map = new  HashMap<String, SingleLockState>();
	
	private ArrayList<BugResult> bugArray = new ArrayList<BugResult>();

	public Policy(T c) {
		component = c;
	}
	
	public void addFact(CGNode n, SingleLockState st) {
		map.put(n.getMethod().getName().toString(), st);
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

	public static LockUsage getLockUsage(SingleLockState runState) {								
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

	protected boolean locking(SingleLockState onCreateState) {
		if (onCreateState != null) {
			return (getLockUsage(onCreateState).equals(LockUsage.LOCKING));
		} 
		return false;
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

	protected boolean strongUnlocking(SingleLockState state) {
		if (state != null) {
			return (getLockUsage(state).equals(LockUsage.FULL_UNLOCKING));
		} 
		return true;		//reverse logic
	}

	protected boolean unlocking(SingleLockState state) {
		return (strongUnlocking(state) || weakUnlocking(state));		
	}

	protected boolean weakUnlocking(SingleLockState state) {
		if (state != null) {
			return (getLockUsage(state).equals(LockUsage.UNLOCKING));
		} 
		return true;		//reverse logic
	}

}