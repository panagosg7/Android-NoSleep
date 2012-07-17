package edu.ucsd.energy.results;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.sf.json.JSONObject;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.managers.WakeLockInstance;

public class LockUsage extends HashMap<WakeLockInstance, SingleLockState> {

	private static final long serialVersionUID = 5180520420981751118L;

	public LockUsage() {
		super();
	}
	
	Set<Component> relevant;
	
	public LockUsage(CompoundLockState cls) {
		super(cls.getLockStateMap());
		relevant = new HashSet<Component>();			
		for (SingleLockState ls : cls.getLockStateMap().values()) {
			relevant.addAll(ls.involvedContexts());				
		}
	}
	
	public boolean relevant(Component c) {
		return relevant.contains(c);
	}

	public Set<Component> getRelevantCtxs() {
		return relevant;
	}

	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for(java.util.Map.Entry<WakeLockInstance, SingleLockState> e : entrySet()) {
			sb.append(e.getKey().toShortString() + " - " +
					e.getValue().toString() + "\n");
		}
		return sb.toString();
	}

	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		for(java.util.Map.Entry<WakeLockInstance, SingleLockState> e : entrySet()) {
			obj.put(e.getKey().toShortString(), e.getValue().toString());
		}
		return null;
	}

	private boolean syncLocking(WakeLockInstance wli) {
		SingleLockState singleLockState = get(wli);
		if (singleLockState != null) {
			return (singleLockState.acquired() && (!singleLockState.async()));
		}
		return false;
	}

	private boolean asyncLocking(WakeLockInstance wli) {
		SingleLockState singleLockState = get(wli);
		if (singleLockState != null) {
			return (singleLockState.acquired() && singleLockState.async());
		}
		return false;
	}
	
	public boolean locking(WakeLockInstance wli) {
		return (syncLocking(wli) || asyncLocking(wli));
	}

}