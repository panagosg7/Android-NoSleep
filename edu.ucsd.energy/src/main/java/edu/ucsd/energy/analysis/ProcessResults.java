package edu.ucsd.energy.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.analysis.WakeLockManager.WakeLockInstance;
import edu.ucsd.energy.components.Component;
import edu.ucsd.energy.components.Component.CallBack;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.util.E;

public class ProcessResults {
	
	/**
	 * Main structures that hold the analysis results for every component
	 */
	private HashSet<Pair<Component, ComponentSummary>> componentSummaries = null;
	private ComponentManager componentManager;
	private ArrayList<Result> result;
	
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
	}
		
	public ProcessResults(ComponentManager componentManager) {
		this.componentManager = componentManager;
	}
	
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
	
		
	

	/**
	 * Invoke this after the component has been analyzed
	 * @param component
	 */
	public void createComponentSummary() {
		componentSummaries = new HashSet<Pair<Component,ComponentSummary>>();
		for (Component component : componentManager.getComponents().values()) {
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
				E.log(1, component.toString());
				E.log(1, compSB.toString());
			}
			
			componentSummaries.add(Pair.make(component,componentSummary));
		}
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
		
	

	/**
	 * Merge a set of lock states. Should return UNDEFIINED if the set is empty
	 * @param set
	 * @return
	 */
	public SingleLockState mergeSingleLockStates(Set<SingleLockState> set) {
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
	
	
	public void processExitStates() {
		if (result == null) {
			result = new ArrayList<Result>();
		}
		
		if (componentManager.getNUnresInterestingCBs() > 0) {
			result.add(new Result(ResultType.UNRESOLVED_INTERESTING_CALLBACKS, ""));
		}
			
		if (componentSummaries == null) {
			createComponentSummary();
		}
		
		E.log(1, "\n==========================================");
		HashMap<LockUsage, Set<Pair<Component, CGNode>>> usageMap = 
				new HashMap<LockUsage, Set<Pair<Component, CGNode>>>();
		HashMap<Component, Logger> componentMap = new HashMap<Component, Logger>();
		
		for (Pair<Component, ComponentSummary> pair : componentSummaries) {
			Component component = pair.fst;
			ComponentSummary cSummary = pair.snd;
			ComponentPolicy policy = new ComponentPolicy(component);			
			
			StringBuffer sb = new StringBuffer();
			boolean printComponent = false;
			for(Entry<CGNode, CompoundLockState> e : cSummary.getAllExitStates().entrySet()) {
				CGNode node = e.getKey();
				//Pair<String, String> abstCompAndCB = Pair.make(componentName,callBackName);
				HashMap<WakeLockInstance,LockUsage> lockUsages = new HashMap<WakeLockInstance, LockUsage>();
				boolean printMethod = false;
				LockUsage lockUsage = LockUsage.EMPTY;
				CompoundLockState compLS = e.getValue();
				Map<WakeLockInstance, Set<SingleLockState>> allLockStates = compLS.getAllLockStates();
				for (Entry<WakeLockInstance, Set<SingleLockState>>  fs : allLockStates.entrySet()) {
					WakeLockInstance wli = fs.getKey();
					Set<SingleLockState> sls = fs.getValue();
					/* Use the merged state here.
					 * Ideally this shouldn't matter for callbacks, as there
					 * should only be one state 
					 */
					SingleLockState sl = mergeSingleLockStates(sls);
					lockUsage = getLockUsage(sl);
					lockUsages.put(wli, lockUsage);
					if(lockUsage != LockUsage.EMPTY) {
						printMethod = true;
					}
					
					//TODO: keep track of aggregate lock usage
					//updateEnumMap(enumMap, lockUsage);
				}
				if (printMethod) {
					if (Opts.OUTPUT_ALL_NODE_INFO || component.isCallBack(node)) {
						printComponent = true;
						
						HashSet<SingleLockState> tempState = new HashSet<SingleLockState>();	
						sb.append("    " + node.getMethod().getSignature().toString() + "\n");
						for ( Entry<WakeLockInstance, Set<SingleLockState>>  fs : compLS.getAllLockStates().entrySet()) {
							//Gather the info of every callback
							WakeLockInstance key = fs.getKey();
							Set<SingleLockState> value = fs.getValue();
							tempState.add(mergeSingleLockStates(value));
							sb.append("\t" + key.toString() + "\n\t" + value.toString() + "\n");
						}
						
						SingleLockState mergedLS = mergeSingleLockStates(tempState);
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
				}
			}
			if (printComponent) {
				E.log(1, component.toString() + "\n" + sb.toString());
				policy.solveFacts();
				componentMap.put(component, policy.getLogger());
			}
		}	//endfor Component

		E.log(1, "==========================================\n");
		for(LockUsage e : usageMap.keySet()) {
			E.log(1, e.toString());
			for (Pair<Component, CGNode> s : usageMap.get(e)) {
				E.log(1, "   " + s.toString());
			}
		}
		E.log(1, "==========================================\n");

		for(Component e : componentMap.keySet()) {
			Logger logger = componentMap.get(e);
			if (!logger.isEmpty()) {
				E.log(1, e.toString());
				E.log(1, logger.toString());
			}
		}
		
		result.addAll(collapseLogger(componentMap.values()));
	}
	
	public ArrayList<Result> collapseLogger(Collection<Logger> cl) {
		ArrayList<Result> result = new ArrayList<Result>();
		for (Logger l : cl) {
			result.addAll(l);
		}
		return result;
	}
	
	public ArrayList<Result> getResult() {
		if (result == null) {
			processExitStates();
		}
		return result;
	}


	public class Logger extends ArrayList<Result> {
		private static final long serialVersionUID = 4402714524487791090L;
		public void output() {
			for(Result r : this) {
				E.log(1, "    " + r.getResultType().toString());
			}
		}
		
		public ArrayList<Result> getResultList() {
			return this;
		}
		
		public String toString() {
			StringBuffer result = new StringBuffer();
			for(Result r : this) {
				result.append(r.toString() + "\n");
			}
			return result.toString();					
		}
	}
	
	public class ComponentPolicy {
		private Component component; 
		
		public ComponentPolicy(Component component) {
			this.component = component;
			map = new HashMap<String, SingleLockState>();
			logger = new Logger();
		}
		
		private HashMap<String,SingleLockState> map;
		
		public void addFact(CGNode n, SingleLockState st) {
			map.put(n.getMethod().getName().toString(), st);
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
		
		private Logger logger;
		
		
		private void logNote(Result result) {
			logger.add(result);
		}
		
		public Logger getLogger() {
			return logger;
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
		
		/**
		 * 
		 * TODO: make sure the same lock is locked and unlocked...
		 * 
		 */
		public void solveFacts() {
			/*
			 * Policies for Activity
			 */
			if (component.getComponentType().equals("Activity")) {
				SingleLockState onCreateState = map.get("onCreate");
				if (locking(onCreateState)) {
					logNote(new Result(ResultType.LOCKING_ON_CREATE, component.toString()));
				}
				SingleLockState onStartState = map.get("onStart");
				if (locking(onStartState)) {
					logNote(new Result(ResultType.LOCKING_ON_START, component.toString()));
				}
				
				SingleLockState onRestartState = map.get("onRestart");
				if (locking(onRestartState)) {
					logNote(new Result(ResultType.LOCKING_ON_RESTART, component.toString()));
				}
				
				SingleLockState onPauseState = map.get("onPause");
				
				if ((!locking(onCreateState)) && (!locking(onStartState)) 
						&& locking(onRestartState) && (!unlocking(onPauseState))) {
					logNote(new Result(ResultType.STRONG_PAUSE_RESUME, component.toString()));
				}
				
				if (weakUnlocking(onPauseState) && (!strongUnlocking(onPauseState))) {
					logNote(new Result(ResultType.WEAK_PAUSE_RESUME, component.toString()));
				}
			}
			/*
			 * Policies for Service
			 */
			if (component.getComponentType().equals("Service")) {
				SingleLockState onStartState = getServiceOnStart();
				SingleLockState onDestroyState = map.get("onDestroy");		//FIX THIS
				if (locking(onStartState) && (!unlocking(onDestroyState))) {
					logNote(new Result(ResultType.STRONG_SERVICE_DESTROY, component.toString()));
				}
				if (weakUnlocking(onDestroyState) && (!strongUnlocking(onDestroyState))) {
					logNote(new Result(ResultType.WEAK_SERVICE_DESTROY, component.toString()));
				}
			}
			
			/*
			 * Policies for Thread
			 */
			if (component.getComponentType().equals("RunnableThread")) {
				SingleLockState runState = map.get("run");
				if (locking(runState)) {
					logNote(new Result(ResultType.THREAD_RUN, component.toString()));
				}				
			}

			/*
			 * Policies for Handler
			 */
			if (component.getComponentType().equals("Handler")) {
				SingleLockState handleState = map.get("handleMessage");
				if (unlocking(handleState) && (!strongUnlocking(handleState))) {
					logNote(new Result(ResultType.WEAK_SERVICE_DESTROY, component.toString()));
				}
			}
		
		
			/*
			 * Policies for BroadcastReceivers
			 */
			if (component.getComponentType().equals("BroadcastReceiver")) {
				SingleLockState onReceiveState = map.get("onReceive");
				if (locking(onReceiveState)) {
					logNote(new Result(ResultType.BROADCAST_RECEIVER_ON_RECEIVE, component.toString()));					
				}				
			}

		}
		
	} 

}

	