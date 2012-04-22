package energy.analysis;

import java.util.EnumMap;
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
	
	int threadCount 	= 0; 
	int activityCount 	= 0;
			
	int lockThreads 	= 0;
	int nolockThreads 	= 0;
	int unlockThreads 	= 0;
	
	int lockunlockThreads = 0;
	
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
		for (Pair<Component, ComponentSummary> pair : allStates) {
			Component component = pair.fst;
			ComponentSummary cSummary = pair.snd;
			
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
						sb.append(node.getMethod().getSignature().toString() + "\n");
						for ( Entry<WakeLockInstance, Set<SingleLockState>>  fs : compLS.getAllLockStates().entrySet()) {
							sb.append("\t" + fs.getKey().toString() + "\n\t" + fs.getValue().toString() + "\n");
						}
						
						/*
						Predicate<SingleLockState> p = new Predicate<SingleLockState>() {		
							@Override
							public boolean test(SingleLockState c) {
								
							}
						};
						*/						
					}
				}
			}
			if (printComponent) {
				E.log(1, component.toString() + "\n" + sb.toString());
			}
			
		}	
	}


	private void updateEnumMap(EnumMap<LockUsage, Integer> enumMap, LockUsage lockUsage) {
		if(enumMap == null) {
			enumMap = new EnumMap<LockUsage, Integer>(LockUsage.class);			
		}
		Integer count = enumMap.get(lockUsage);
		if (count == null) {
			count = new Integer(1);
			enumMap.put(lockUsage, count);
		} 
		else {
			enumMap.put(lockUsage, count + 1);
		}
	}

	/*
	public void outputFinalResults() {
		for (Entry<Pair<String, String>, EnumMap<LockUsage, Integer>> e : callBackResultMap.entrySet()) {
			Pair<String, String> key = e.getKey();
			E.log(1, key.toString());
			EnumMap<LockUsage, Integer> usages = e.getValue();
			for (Entry<LockUsage, Integer> u : usages.entrySet()) {
				LockUsage usage = u.getKey();
				Integer count = u.getValue();
				E.log(1, "--> " + usage.toString() + " :: " + count.toString());
			}
		}
	}
	*/
}
