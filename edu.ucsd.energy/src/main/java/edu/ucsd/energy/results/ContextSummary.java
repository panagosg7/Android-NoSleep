package edu.ucsd.energy.results;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.util.strings.Atom;

import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.interproc.SingleLockState.LockStateDescription;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.policy.Policy;
import edu.ucsd.energy.results.ProcessResults.ComponentState;
import edu.ucsd.energy.results.ProcessResults.LockUsage;
import edu.ucsd.energy.results.ProcessResults.SingleLockUsage;

public class ContextSummary  {

	private Context context;

	private ComponentState callBackExitStates;

	private HashMap<CGNode, CompoundLockState> allExitStates;

	public ContextSummary(Context c) {
		this.setContext(c);
		allExitStates = new HashMap<CGNode, CompoundLockState>();
		callBackExitStates = new ComponentState();			
	}	

	public HashMap<CGNode, CompoundLockState> getAllExitStates() {
		return allExitStates;
	}
	
	public ComponentState getCallBackUsage() {
		return callBackExitStates;
	}

	public void registerNodeState(CGNode n, CompoundLockState st) {
		allExitStates.put(n,st);
		if (getContext().isCallBack(n)){
			registerCallBackState(CallBack.findOrCreateCallBack(n), st);
		}
	}

	private void registerCallBackState(CallBack cb, CompoundLockState st) {
		LockUsage map = new LockUsage();
		for(Entry<WakeLockInstance, SingleLockState> e : st.getLockStateMap().entrySet()) {
			WakeLockInstance wli = e.getKey();
			SingleLockState sls = e.getValue();
			map.put(wli, sls);
		}
		//Putting only non-empty exit states
		if (!map.isEmpty()) {
			callBackExitStates.put(cb,map);
		}
	}

	public CompoundLockState getState(CGNode node) {
		return allExitStates.get(node);
	}

	//TODO: may have to fix this
	public boolean isEmpty() {
		for (LockUsage cbState : callBackExitStates.values()) {				
			if ((cbState != null) && (!cbState.isEmpty())) {
				return false;
			}
		}
		return true;
	}
	
	
	public String toString () {
		StringBuffer sb = new StringBuffer();
		/*sb.append("Methods:\n");
		for (Entry<CGNode, CompoundLockState> e : allExitStates.entrySet()) {
			CompoundLockState stateForMethod = e.getValue();
			if ((stateForMethod != null) && (!stateForMethod.isEmpty())) {
				sb.append(e.getKey().getMethod().getSelector().toString()
						+ ":\n" + stateForMethod.toShortString());
			}
		}
		*/
		sb.append("Callbacks:\n");
		for (Entry<CallBack, LockUsage> cb : callBackExitStates.entrySet()) {				
			sb.append("   " + cb.getKey().getName() + "\n"); 
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
			for (Entry<CGNode, CompoundLockState> e : allExitStates.entrySet()) {
				IMethod method = e.getKey().getMethod();
				Atom name = method.getName();
				JSONObject methContent = new JSONObject();
				methContent.put("signature", method.getSignature());
				CompoundLockState state = e.getValue();
				methContent.put("end_state", state.toJSON());
				methObj.accumulate(name.toString(), methObj);
			}
			JSONObject compObj = new JSONObject();
			compObj.put("method_states", methObj);
			return compObj;
		} catch(JSONException e) {
			e.printStackTrace();
		}
		return null;			
	}

	public Context getContext() {
		return context;
	}

	public void setContext(Context context) {
		this.context = context;
	} 
}