package edu.ucsd.energy.results;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import com.ibm.wala.types.Selector;

import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.managers.WakeLockInstance;

public class ComponentSummary {
	
	private static final int DEBUG = 0;
	
	
	public class ContextState extends HashMap<Selector, LockUsage> {

		private static final long serialVersionUID = 1L;

		public JSONObject toJSON() {
			JSONObject obj = new JSONObject();
			for (Entry<Selector, LockUsage> e : entrySet()) {
				obj.put(e.getKey().toString(), e.getValue().toJSON());
			}
			return obj;
		}

		public String toString() {
			StringBuffer sb = new StringBuffer();
			for (Entry<Selector, LockUsage> e : entrySet()) {
				sb.append(e.getKey().getName() + " :: " + e.getValue().toString() + "\n");
			}	
			return sb.toString();
		}
	}

	

	//A mapping with the state at the exit of every callback 
	private ContextState callBackExitStates;
	
	//A set with all the wakelock instances used
	private Set<WakeLockInstance> instances;

	//The context which this class summarizes
	private Component delegatingContext;
	

	public ComponentSummary(Component component) {
		callBackExitStates = new ContextState();
		instances = new HashSet<WakeLockInstance>();
		delegatingContext = component;
	}	

	
	public void registerState(CallBack cb, CompoundLockState st) {
		LockUsage map = new LockUsage(st);
		instances.addAll(st.getWakeLocks());
		Selector methSel = cb.getNode().getMethod().getSelector();
		if (DEBUG > 0) {
			System.out.println("RegisteringState: " + methSel);
			System.out.println(map.toString());
			System.out.println(instances);
		}
		callBackExitStates.put(methSel, map);
		
	}
	
	
	public String toString () {
		StringBuffer sb = new StringBuffer();
		sb.append("Callbacks:\n");
		for (Entry<Selector, LockUsage> cb : callBackExitStates.entrySet()) {				
			sb.append("   " + cb.getKey().toString() + "\n"); 
			LockUsage stateForMethod = cb.getValue();
			if ((stateForMethod != null) && (!stateForMethod.isEmpty())) {
				for(Entry<WakeLockInstance, SingleLockState> e : stateForMethod.entrySet()) {
					sb.append("\t" + e.getKey().toShortString() + " : " + e.getValue().toString() + "\n");
				}
			}				
		}
		return sb.toString();
	}

	public JSONObject toJSON() {
		try {
			JSONObject methObj = new JSONObject();
			for (Entry<Selector, LockUsage> e : callBackExitStates.entrySet()) {
				JSONObject methContent = new JSONObject();
				methContent.put("selector", e.getKey());
				LockUsage lu = e.getValue();
				methContent.put("end_state", lu.toJSON());
				methObj.accumulate(e.getKey().toString(), methObj);
			}
			JSONObject compObj = new JSONObject();
			compObj.put("method_states", methObj);
			return compObj;
		} catch(JSONException e) {
			e.printStackTrace();
		}
		return null;			
	}

	
	public Set<WakeLockInstance> lockInstances() {
		return instances;
	}

	public Set<LockUsage> getCallBackState(Selector selector) {
		Set<CallBack> cbs = delegatingContext.getNextCallBack(selector, false);
		Set<LockUsage> result = new HashSet<LockUsage>();
		for (CallBack cb : cbs) {
			//new LockUsage(delegatingContext.getReturnState(cb.getNode()));
			Selector s = cb.getNode().getMethod().getSelector();
			LockUsage lockUsage = callBackExitStates.get(s);
			if (lockUsage != null) {
				result.add(lockUsage);
			}
		}
		return result;
	}


}