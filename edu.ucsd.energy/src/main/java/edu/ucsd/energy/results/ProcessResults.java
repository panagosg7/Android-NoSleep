package edu.ucsd.energy.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.strings.Atom;

import edu.ucsd.energy.analysis.ComponentManager;
import edu.ucsd.energy.analysis.WakeLockInstance;
import edu.ucsd.energy.components.Component;
import edu.ucsd.energy.components.Component.CallBack;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.util.E;

public class ProcessResults {

	public enum LockUsage {
		LOCKING,
		UNLOCKING,	
		NOLOCKING,
		LOCKUNLOCK, 
		EMPTY,
		UNKNOWN_STATE, 
		FULL_UNLOCKING;
	}
	
	public enum ResultType {
		UNRESOLVED_INTERESTING_CALLBACKS,		
		LOCKING_ON_CREATE,
		START_DESTROY,
		SERVICE_DESTROY,
		THREAD_RUN,
		HANDLE_MESSAGE,
		BROADCAST_RECEIVER_ON_RECEIVE, 
		LOCKING_ON_START,
		LOCKING_ON_RESTART, 
		STRONG_PAUSE_RESUME, 
		WEAK_PAUSE_RESUME, 
		WEAK_SERVICE_DESTROY, 
		STRONG_SERVICE_DESTROY, 
		DID_NOT_PROCESS,
		
		OPTIMIZATION_FAILURE,
		ANALYSIS_FAILURE,
		UNIMPLEMENTED_FAILURE;
	}
	
	public class Logger extends ArrayList<BugResult> {
		private static final long serialVersionUID = 4402714524487791090L;
		public void output() {
			for(BugResult r : this) {
				E.log(1, "    " + r.getResultType().toString());
			}
		}
		
		public ArrayList<BugResult> getResultList() {
			return this;
		}
		
		public String toString() {
			StringBuffer result = new StringBuffer();
			for(BugResult r : this) {
				result.append(r.toString() + "\n");
			}
			return result.toString();					
		}
	}


	public class ComponentPolicyCheck {

		private Component component; 
		
		private HashMap<String,SingleLockState> map = new  HashMap<String, SingleLockState>();
		
		private ArrayList<BugResult> bugArray = new ArrayList<BugResult>();
		
		public ComponentPolicyCheck(Component component) {
			this.component = component;
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
		
		public void solveFacts() {
			/*
			 * Policies for Activity
			 */
			if (component.getComponentType().equals("Activity")) {
				SingleLockState onCreateState = map.get("onCreate");
				if (locking(onCreateState)) {
					trackResult(new BugResult(ResultType.LOCKING_ON_CREATE, component.toString()));
				}
				SingleLockState onStartState = map.get("onStart");
				if (locking(onStartState)) {
					trackResult(new BugResult(ResultType.LOCKING_ON_START, component.toString()));
				}
				
				SingleLockState onRestartState = map.get("onRestart");
				if (locking(onRestartState)) {
					trackResult(new BugResult(ResultType.LOCKING_ON_RESTART, component.toString()));
				}
				
				SingleLockState onPauseState = map.get("onPause");
				
				if ((!locking(onCreateState)) && (!locking(onStartState)) 
						&& locking(onRestartState) && (!unlocking(onPauseState))) {
					trackResult(new BugResult(ResultType.STRONG_PAUSE_RESUME, component.toString()));
				}
				
				if (weakUnlocking(onPauseState) && (!strongUnlocking(onPauseState))) {
					trackResult(new BugResult(ResultType.WEAK_PAUSE_RESUME, component.toString()));
				}
			}
			/*
			 * Policies for Service
			 */
			if (component.getComponentType().equals("Service")) {
				SingleLockState onStartState = getServiceOnStart();
				SingleLockState onDestroyState = map.get("onDestroy");		//FIX THIS
				if (locking(onStartState) && (!unlocking(onDestroyState))) {
					trackResult(new BugResult(ResultType.STRONG_SERVICE_DESTROY, component.toString()));
				}
				if (weakUnlocking(onDestroyState) && (!strongUnlocking(onDestroyState))) {
					trackResult(new BugResult(ResultType.WEAK_SERVICE_DESTROY, component.toString()));
				}
			}
			
			/*
			 * Policies for Thread
			 */
			if (component.getComponentType().equals("RunnableThread")) {
				SingleLockState runState = map.get("run");
				if (locking(runState)) {
					trackResult(new BugResult(ResultType.THREAD_RUN, component.toString()));
				}				
			}
	
			/*
			 * Policies for Handler
			 */
			if (component.getComponentType().equals("Handler")) {
				SingleLockState handleState = map.get("handleMessage");
				if (unlocking(handleState) && (!strongUnlocking(handleState))) {
					trackResult(new BugResult(ResultType.WEAK_SERVICE_DESTROY, component.toString()));
				}
			}
		
			/*
			 * Policies for BroadcastReceivers
			 */
			if (component.getComponentType().equals("BroadcastReceiver")) {
				SingleLockState onReceiveState = map.get("onReceive");
				if (locking(onReceiveState)) {
					trackResult(new BugResult(ResultType.BROADCAST_RECEIVER_ON_RECEIVE, component.toString()));					
				}				
			}
		}
		
		private void trackResult(BugResult result) {
			this.bugArray.add(result);
		}

		private SingleLockState getServiceOnStart() {
			SingleLockState onStart = map.get("onStart");			//deprecated version
			SingleLockState onStartCommand = map.get("onStartCommand");	
			if (onStartCommand == null) {
				return onStart;				
			}
			else {
				return onStartCommand;
			}
		}
		
	}

	
	public class ComponentSummary  {
		
		private Component component;
		
		private HashMap<CallBack, CompoundLockState> callBackExitStates;
	
		private HashMap<CGNode, CompoundLockState> allExitStates;
		
		public ComponentSummary(Component c) {
			this.component = c;
			allExitStates = new HashMap<CGNode, CompoundLockState>();
			callBackExitStates = new HashMap<Component.CallBack, CompoundLockState>();			
		}	
		
		public HashMap<CGNode, CompoundLockState> getAllExitStates() {
			return allExitStates;
		}
		
		public void registerNodeState(CGNode n, CompoundLockState st) {
			allExitStates.put(n,st);
		}
		
		public void registerCallBackState(CallBack cb, CompoundLockState st) {
			callBackExitStates.put(cb,st);
		}
		
		public CompoundLockState getStateForMethod(String method) {
			return allExitStates.get(method);
		}
		
		public String toString () {
			StringBuffer sb = new StringBuffer();
			for (Iterator<CGNode> it = component.getCallgraph().iterator(); it.hasNext(); ) {
				CGNode next = it.next();
				String name = next.getMethod().getName().toString();
				CompoundLockState stateForMethod = getStateForMethod(name);
				if (stateForMethod != null) {
					sb.append(name + ":\n" + stateForMethod.toString());
				}				
			}
			HashSet<CallBack> callbacks = component.getCallbacks();
			if (callbacks != null) {
				if (callbacks.size() > 0) {
					sb.append("Callbacks:\n");
					for (CallBack cb : callbacks) {				
						String name = cb.getName();;
						CompoundLockState stateForMethod = getStateForMethod(name);
						if (stateForMethod != null) {
							sb.append("   " + name + ":\n" + stateForMethod.toString());
						}				
					}
				}
			}
			return null;
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

	/**
	 * Main structures that hold the analysis results for every component
	 */
	private ComponentManager componentManager;
	private ApkBugReport result;
	
	public ProcessResults(ComponentManager componentManager) {
		this.componentManager = componentManager;
	}
	
		
	public ApkBugReport processExitStates() {
		ApkBugReport report = new ApkBugReport();
		if (componentManager.getNUnresInterestingCBs() > 0) {
			report.insertFact(new BugResult(ResultType.UNRESOLVED_INTERESTING_CALLBACKS, ""));
		}
		HashMap<LockUsage, Set<Pair<Component, CGNode>>> usageMap = 
				new HashMap<LockUsage, Set<Pair<Component, CGNode>>>();
		for (Component component : componentManager.getComponents().values()) {
			ComponentSummary cSummary = summarize(component);
			ComponentPolicyCheck policy = new ComponentPolicyCheck(component);
			
			for(Entry<CGNode, CompoundLockState> e : cSummary.getAllExitStates().entrySet()) {
				CGNode node = e.getKey();
				HashMap<WakeLockInstance,LockUsage> lockUsages = new HashMap<WakeLockInstance, LockUsage>();
				CompoundLockState compLS = e.getValue();
				Map<WakeLockInstance, Set<SingleLockState>> allLockStates = compLS.getLockStateMap();
				for (Entry<WakeLockInstance, Set<SingleLockState>>  fs : allLockStates.entrySet()) {
					WakeLockInstance wli = fs.getKey();		//TODO: make this better
					LockUsage lockUsage = getLockUsage(mergeSingleLockStates(fs.getValue()));
					lockUsages.put(wli, lockUsage);
				}
				HashSet<SingleLockState> tempState = new HashSet<SingleLockState>();	
				for ( Entry<WakeLockInstance, Set<SingleLockState>>  fs : compLS.getLockStateMap().entrySet()) {
					tempState.add(mergeSingleLockStates(fs.getValue()));	//TODO: super ugly, fix
				}
				SingleLockState mergedLS = mergeSingleLockStates(tempState);
				//We have the aggregate lock usage for a single node
				LockUsage lu = getLockUsage(mergedLS);
				if (component.isCallBack(node)) {
					if (usageMap.containsKey(lu)) {
						usageMap.get(lu).add(Pair.make(component,node));
					} else {
						HashSet<Pair<Component, CGNode>> set = 
								new HashSet<Pair<Component,CGNode>>();
						set.add(Pair.make(component,node));
						usageMap.put(lu, set);
					}
					//Add the fact to the policy module
					policy.addFact(node, mergedLS);
				}
			}
			policy.solveFacts();
			report.insertFact(component, policy);
		}	//endfor Component

		//TODO: Add usage to apk report ??
		/*
		for(LockUsage e : usageMap.keySet()) {
			E.log(1, e.toString());
			for (Pair<Component, CGNode> s : usageMap.get(e)) {
				E.log(1, "   " + s.toString());
			}
		}
		*/
		return report;
	}
	
	
	
	public ApkBugReport getResult() {
		return result;
	}

	private boolean unlocking(SingleLockState state) {
		return (strongUnlocking(state) || weakUnlocking(state));		
	}


	private boolean strongUnlocking(SingleLockState state) {
		if (state != null) {
			return (getLockUsage(state).equals(LockUsage.FULL_UNLOCKING));
		} 
		return true;		//reverse logic
	}


	private boolean weakUnlocking(SingleLockState state) {
		if (state != null) {
			return (getLockUsage(state).equals(LockUsage.UNLOCKING));
		} 
		return true;		//reverse logic
	}


	private boolean locking(SingleLockState onCreateState) {
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
	private SingleLockState mergeSingleLockStates(Set<SingleLockState> set) {
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

	private LockUsage getLockUsage(SingleLockState runState) {								
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


	private ComponentSummary summarize(Component component) {
		StringBuffer compSB = new StringBuffer();
		ComponentSummary componentSummary = new ComponentSummary(component);
		for(Iterator<CGNode> it = component.getCallgraph().iterator(); it.hasNext(); ) {
			CGNode node = it.next();
			IR ir = node.getIR();
			if (ir == null) {
				continue;
			}
			//Get the exit state
			CompoundLockState exitState = component.getExitState(node);
			if (exitState != null) {
				componentSummary.registerNodeState(node, exitState);
			}			
			StringBuffer nodeSB = new StringBuffer();
			//Get the state at method calls
			
			SSACFG cfg = ir.getControlFlowGraph();
			for(Iterator<ISSABasicBlock> itcs = cfg.iterator(); itcs.hasNext(); ) {
				ISSABasicBlock ssabb = itcs.next();
				for(Iterator<SSAInstruction> ssait = ssabb.iterator(); ssait.hasNext() ; ) {
					SSAInstruction inst = ssait.next();
					if (inst instanceof SSAInvokeInstruction) {
						SSAInvokeInstruction inv = (SSAInvokeInstruction) inst;
						int num = cfg.getNumber(ssabb);
						CompoundLockState compState = component.getState(node, num);							
						SingleLockState state = compState.simplify();
						if (state != null && state.isMustbeAcquired()) {
							nodeSB.append("\t\t" + inv.getDeclaredTarget().getSignature() + "\n");
						}
					}
				}
			}
			if (nodeSB.length() > 0) {					
				compSB.append("\t" + node.getMethod().getSignature() + "\n");
				compSB.append(nodeSB.toString());
			}
		}
		if(compSB.length() > 0) {
			E.log(2, component.toString());
			E.log(2, compSB.toString());
		}
		return componentSummary;
	} 

}

	