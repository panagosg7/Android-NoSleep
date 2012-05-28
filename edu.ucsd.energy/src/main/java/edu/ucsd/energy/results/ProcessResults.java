package edu.ucsd.energy.results;

import java.util.ArrayList;
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
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.contexts.Component;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.managers.ComponentManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.policy.IPolicy;
import edu.ucsd.energy.policy.Policy;
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


	/**
	 * Main structures that hold the analysis results for every component
	 */
	private ComponentManager componentManager;
	private ApkBugReport result;

	public ProcessResults(ComponentManager componentManager) {
		this.componentManager = componentManager;
	}


	public ApkBugReport processExitStates() {

		System.out.println();

		ApkBugReport report = new ApkBugReport();
		//Account for unresolved Callbacks
		if (componentManager.getNUnresInterestingCBs() > 0) {
			report.insertFact(new BugResult(ResultType.UNRESOLVED_INTERESTING_CALLBACKS, ""));
		}

		HashMap<LockUsage, Set<Pair<Context, CGNode>>> usageMap = 
				new HashMap<LockUsage, Set<Pair<Context, CGNode>>>();

		for (Context context : componentManager.getComponents()) {
			//Focus just on activities, services, BcastRcv...
			if (context instanceof Component) {
				E.log(1, "Gathering results for: " + context.toString());
				ContextSummary cSummary = summarize(context);
				if (cSummary.isEmpty()) continue;
				
				Component c = (Component) context;
				
				E.log(1, cSummary.toString());
				IPolicy policy = c.makePolicy();

				for(Entry<CGNode, CompoundLockState> e : cSummary.getAllExitStates().entrySet()) {
					CGNode node = e.getKey();
					//E.log(1, "\tExit of: " + node.getMethod().getSelector().toString());
					HashMap<WakeLockInstance,LockUsage> lockUsages = new HashMap<WakeLockInstance, LockUsage>();
					CompoundLockState compLS = e.getValue();
					Map<WakeLockInstance, Set<SingleLockState>> allLockStates = compLS.getLockStateMap();
					for (Entry<WakeLockInstance, Set<SingleLockState>>  fs : allLockStates.entrySet()) {
						WakeLockInstance wli = fs.getKey();		//TODO: make this better
						SingleLockState sls = SingleLockState.mergeSingleLockStates(fs.getValue());
						LockUsage lockUsage = Policy.getLockUsage(sls);
						lockUsages.put(wli, lockUsage);
					}
					HashSet<SingleLockState> tempState = new HashSet<SingleLockState>();	
					for ( Entry<WakeLockInstance, Set<SingleLockState>>  fs : compLS.getLockStateMap().entrySet()) {
						tempState.add(SingleLockState.mergeSingleLockStates(fs.getValue()));	//TODO: super ugly, fix
					}
					SingleLockState mergedLS = SingleLockState.mergeSingleLockStates(tempState);
					//We have the aggregate lock usage for a single node
					LockUsage lu = Policy.getLockUsage(mergedLS);
					if (context.isCallBack(node)) {
						if (usageMap.containsKey(lu)) {
							usageMap.get(lu).add(Pair.make(context,node));
						} else {
							HashSet<Pair<Context, CGNode>> set = 
									new HashSet<Pair<Context,CGNode>>();
							set.add(Pair.make(context,node));
							usageMap.put(lu, set);
						}
						//Add the fact to the policy module
						policy.addFact(node, mergedLS);
					}
				}
				policy.solveFacts();
				report.insertFact(context, policy);
			}
		}	//endfor Component
		return report;
	}

	private ContextSummary summarize(Context component) {
		ContextSummary ctxSum = new ContextSummary(component);
		for(Iterator<CGNode> it = component.getCallGraph().iterator(); it.hasNext(); ) {
			CGNode node = it.next();
			if (node.getMethod().isNative()) continue;
			CompoundLockState exitState = component.getReturnState(node);
			E.log(1, node.getMethod().getReference().toString());
			if (exitState != null) {
				ctxSum.registerNodeState(node, exitState);
			}
			//collectCallsWhileAcquired(node);
		}
		return ctxSum;
	}


	private Set<MethodReference> collectCallsWhileAcquired(Context ctx, CGNode node) {
		IR ir = node.getIR();
		Set<MethodReference> result = new HashSet<MethodReference>();
		SSACFG cfg = ir.getControlFlowGraph();
		for(Iterator<ISSABasicBlock> itcs = cfg.iterator(); itcs.hasNext(); ) {
			ISSABasicBlock ssabb = itcs.next();
			for(Iterator<SSAInstruction> ssait = ssabb.iterator(); ssait.hasNext() ; ) {
				SSAInstruction inst = ssait.next();
				if (inst instanceof SSAInvokeInstruction) {
					SSAInvokeInstruction inv = (SSAInvokeInstruction) inst;
					CompoundLockState compState = ctx.getState(inst);
					SingleLockState state = compState.simplify();
					if (state != null && state.isMustbeAcquired()) {
						result.add(inv.getDeclaredTarget());
					}
				}
			}
		}
		return result;
	}

}

