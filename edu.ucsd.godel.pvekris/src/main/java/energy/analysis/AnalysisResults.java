package energy.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.util.collections.Pair;

import energy.analysis.WakeLockManager.WakeLockInstance;
import energy.components.Component;
import energy.components.Component.CallBack;
import energy.interproc.CompoundLockState;
import energy.interproc.SingleLockState;

public class AnalysisResults {
	
	/**
	 * Main structures that hold the analysis results for every component
	 */
	private HashSet<Pair<Component, ComponentSummary>> allStates = null;
	
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
		
	public AnalysisResults() {
		allStates = new HashSet<Pair<Component,ComponentSummary>>();
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
		BROADCAST_RECEIVER_ONRECEIVE, 
		LOCKING_ON_START,
		LOCKING_ON_RESTART, 
		STRONG_PAUSE_RESUME, 
		WEAK_PAUSE_RESUME, 
		WEAK_SERVICE_DESTROY, 
		STRONG_SERVICE_DESTROY, DID_NOT_PROCESS;
	}
	
	public class Result { 
		
		private ResultType resultType;
		private String message; 
		
		public ResultType getResultType() {
			return resultType;
		}
		
		public String getMessage() {
			return message;
		}
		
		public Result(ResultType rt, String msg) {
			message = msg;
			resultType = rt;
		}
		
		public String toString() {
			return (resultType.name() + " " + message);
		}
	}
		
	

	/**
	 * Invoke this after the component has been analyzed
	 * @param component
	 */
	public void createComponentSummary(Component component) {
		ComponentSummary componentSummary = new ComponentSummary(component);
		for(Iterator<CGNode> it = component.getCallgraph().iterator(); it.hasNext(); ) {
			CGNode next = it.next();
			Map<WakeLockInstance, Set<SingleLockState>> exitState = component.getExitState(next);
			if (exitState != null) {
				CompoundLockState compoundLockState = new CompoundLockState(exitState);
				componentSummary.registerNodeState(next, compoundLockState);
			}			
		}
		allStates.add(Pair.make(component,componentSummary));
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
		
	
	public ArrayList<Result> processResults() {
		ArrayList<Result> result = new ArrayList<Result>();
		System.out.println("\n==========================================");
		HashMap<LockUsage, Set<Pair<Component, CGNode>>> usageMap = 
				new HashMap<LockUsage, Set<Pair<Component, CGNode>>>();
		HashMap<Component, Logger> componentMap = new HashMap<Component, Logger>();
		for (Pair<Component, ComponentSummary> pair : allStates) {
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
					SingleLockState sl = SingleLockState.mergeSingleLockStates(sls);
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
							tempState.add(SingleLockState.mergeSingleLockStates(value));
							sb.append("\t" + key.toString() + "\n\t" + value.toString() + "\n");
						}
						
						SingleLockState mergedLS = SingleLockState.mergeSingleLockStates(tempState);
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
				System.out.println(component.toString() + "\n" + sb.toString());
				policy.solveFacts();
				componentMap.put(component, policy.getLogger());
			}
		}	//endfor Component

		System.out.println("==========================================\n");
		for(LockUsage e : usageMap.keySet()) {
			System.out.println(e.toString());
			for (Pair<Component, CGNode> s : usageMap.get(e)) {
				System.out.println("   " + s.toString());
			}
		}
		System.out.println("==========================================\n");

		for(Component e : componentMap.keySet()) {
			Logger logger = componentMap.get(e);
			if (!logger.isEmpty()) {
				System.out.println(e.toString());
				System.out.println(logger.toString());
				System.out.println("\n");
			}
			result = logger.getStringList();
		}
		return result;
	}
	
	public class Logger extends ArrayList<Result> {
		private static final long serialVersionUID = 4402714524487791090L;
		public void output() {
			for(Result r : this) {
				System.out.println("    " + r.getResultType().toString());
			}
		}
		
		public ArrayList<Result> getStringList() {
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
				
				if (!unlocking(onPauseState)) {
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
				if (weakUnlocking(onDestroyState)) {
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
					logNote(new Result(ResultType.BROADCAST_RECEIVER_ONRECEIVE, component.toString()));					
				}				
			}

		}
		
	} 

}

	