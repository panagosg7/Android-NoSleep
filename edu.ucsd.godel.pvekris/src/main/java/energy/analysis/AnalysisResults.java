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
import energy.util.E;

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
	

	/**
	 * Invoke this after the component has been analyzed
	 * @param component
	 */
	public void createComponentSummary(Component component) {
		ComponentSummary componentSummary = new ComponentSummary(component);
		for(Iterator<CGNode> it = component.getCallgraph().iterator(); it.hasNext(); ) {
			CGNode next = it.next();
			Map<WakeLockInstance, Set<SingleLockState>> exitState = component.getExitState(next);
			CompoundLockState compoundLockState = new CompoundLockState(exitState);
			componentSummary.registerNodeState(next, compoundLockState);
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
		
	
	public void processResults() {
		System.out.println("\n");
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
						sb.append(node.getMethod().getSignature().toString() + "\n");
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
				E.log(1, component.toString() + "\n" + sb.toString());
				policy.solveFacts();
				componentMap.put(component, policy.getLogger());
			}
		}	//endfor Component

		System.out.println("==========================================");
		for(LockUsage e : usageMap.keySet()) {
			System.out.println(e.toString());
			for (Pair<Component, CGNode> s : usageMap.get(e)) {
				System.out.println("   " + s.toString());
			}
		}
		System.out.println("==========================================");

		for(Component e : componentMap.keySet()) {
			Logger logger = componentMap.get(e);
			if (!logger.isEmpty()) {
				System.out.println(e.toString());
				logger.output();
				System.out.println("\n");
				
			}
		}
	}
	
	
	public class Logger extends ArrayList<String> {
		private static final long serialVersionUID = 4402714524487791090L;
		public void output() {
			for(String s : this) {
				System.out.println("   " + s);
			}
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
			if (state != null) {
				LockUsage lu = getLockUsage(state);
				return (lu.equals(LockUsage.UNLOCKING) || lu.equals(LockUsage.FULL_UNLOCKING));
			} 
			return false;		
		} 
		
		private boolean locking(SingleLockState onCreateState) {
			if (onCreateState != null) {
				return (getLockUsage(onCreateState).equals(LockUsage.LOCKING));
			} 
			return false;
		} 
		
		private Logger logger;
		
		private void logNote(String s) {
			logger.add(s);
		}
		
		public Logger getLogger() {
			return logger;
		}
		
		
		public void solveFacts() {
			if (component.getComponentName().equals("Activity")) {
				
				SingleLockState onCreateState = map.get("onCreate");
				if (locking(onCreateState)) {
						logNote("POSSIBLE BUG: Locking @ onCreate");
					}
				
				SingleLockState onStartState = map.get("onStart");
				if (locking(onStartState)) {
					logNote("POSSIBLE BUG: Locking @ onStart");
				}
				
				SingleLockState onPauseState = map.get("onPause");
				SingleLockState onResumeState = map.get("onResume");
				
				if (locking(onResumeState) && (! unlocking(onPauseState))) {
					logNote("POSSIBLE BUG(onPause - onResume)");
				}
			}
		}
	} 

}
