package edu.ucsd.energy.results;

import java.util.HashMap;
import java.util.Map.Entry;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.util.strings.Atom;

import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.CompoundLockState;

public class ContextSummary  {

	private Context context;

	private HashMap<CallBack, CompoundLockState> callBackExitStates;

	private HashMap<CGNode, CompoundLockState> allExitStates;

	public ContextSummary(Context c) {
		this.context = c;
		allExitStates = new HashMap<CGNode, CompoundLockState>();
		callBackExitStates = new HashMap<CallBack, CompoundLockState>();			
	}	

	public HashMap<CGNode, CompoundLockState> getAllExitStates() {
		return allExitStates;
	}

	public void registerNodeState(CGNode n, CompoundLockState st) {
		allExitStates.put(n,st);
		if (context.isCallBack(n)){
			registerCallBackState(CallBack.findOrCreateCallBack(n), st);
		}
	}

	private void registerCallBackState(CallBack cb, CompoundLockState st) {
		callBackExitStates.put(cb,st);
	}

	public CompoundLockState getState(CGNode node) {
		return allExitStates.get(node);
	}

	public String toString () {
		StringBuffer sb = new StringBuffer();
		sb.append("Methods:\n");
		for (Entry<CGNode, CompoundLockState> e : allExitStates.entrySet()) {
			CompoundLockState stateForMethod = e.getValue();
			if ((stateForMethod != null) && (!stateForMethod.isEmpty())) {
				sb.append(e.getKey().getMethod().getSelector().toString()
						+ ":\n" + stateForMethod.toShortString());
			}
		}
		sb.append("Callbacks:\n");
		for (Entry<CallBack, CompoundLockState> cb : callBackExitStates.entrySet()) {				
			String name = cb.getKey().getName();
			CompoundLockState stateForMethod = cb.getValue();
			if ((stateForMethod != null) && (!stateForMethod.isEmpty())) {
				sb.append("   " + name + ":\n" + stateForMethod.toShortString());
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
}