package edu.ucsd.energy.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import net.sf.json.JSONObject;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.contexts.Component;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.managers.ComponentManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.policy.IPolicy;
import edu.ucsd.energy.util.E;

public class ProcessResults {

	public static class LockUsage extends HashMap<WakeLockInstance, SingleLockState> {

		private static final long serialVersionUID = -6164699487290522725L;

		public LockUsage() {
			super();
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
		
	}
	
	public static class ComponentState extends HashMap<CallBack, LockUsage> {
		private static final long serialVersionUID = -3391906027956899103L;
	
		public JSONObject toJSON() {
			JSONObject obj = new JSONObject();
			for (Entry<CallBack, LockUsage> e : entrySet()) {
				obj.put(e.getKey().toString(), e.getValue().toJSON());
			}
			return obj;
		}
		
		public String toString() {
			StringBuffer sb = new StringBuffer();
			for (Entry<CallBack, LockUsage> e : entrySet()) {
				sb.append(e.getKey().getName() + " :: " + e.getValue().toString() + "\n");
			}	
			return sb.toString();
		}
	}
	
	
	public enum SingleLockUsage {
		ACQUIRED,
		RELEASED,
		TIMED_ACQUIRED,
		ASYNC_ACQUIRED,
		ASYNC_RELEASED,
		EMPTY,	//No lock operation was performed
		UNKNOWN; //Error state
	}

	public enum ResultType {
		UNRESOLVED_INTERESTING_CALLBACKS,
		//Activity
		LOCKING_ON_CREATE,
		LOCKING_ON_START,
		LOCKING_ON_RESTART,
		START_DESTROY,
		THREAD_RUN,
		HANDLE_MESSAGE,
		BROADCAST_RECEIVER_ON_RECEIVE, 
		 
		PAUSE_RESUME, 
		SERVICE_START,
		SERVICE_DESTROY, 
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
	
	
	public ProcessResults(ComponentManager componentManager) {
		this.componentManager = componentManager;
	}


	public IReport[] processExitStates() {
		System.out.println();
		PolicyReport policyRep = new PolicyReport();
		LockUsageReport usageRep = new LockUsageReport();
		//Account for unresolved Callbacks
		if (componentManager.getNUnresInterestingCBs() > 0) {
			policyRep.insertFact(new BugResult(ResultType.UNRESOLVED_INTERESTING_CALLBACKS, ""));
		}
		for (Context context : componentManager.getComponents()) {
			//Focus just on activities, services, BcastRcv...
			//if (!(context instanceof Component)) continue;
			E.log(2, "Gathering results for: " + context.toString());
			ContextSummary cSummary = summarize(context);
			if (cSummary.isEmpty()) continue;
			
			//Avoid empty components
			if (context instanceof Component) {
				Component c = (Component) context;
				E.log(2, cSummary.toString());
				IPolicy policy = c.makePolicy();
	
				for(Entry<CallBack, LockUsage> e : cSummary.getCallBackUsage().entrySet()) {
					CGNode node = e.getKey().getNode();
					LockUsage lu = e.getValue();
					
					//TODO: fix this !!!					
					//policy.addFact(node, lu);
				}
				policy.solveFacts();		//Do this here!
				policyRep.insertPolicy(context, policy);
			}
			usageRep.insert(cSummary);
		}
		
		//Return them all
		IReport[] rep = new IReport[2];
		rep[0] = policyRep;
		rep[1] = usageRep;
		
		//Test Usage report
		System.out.println(usageRep.toShortDescription());
		
		return rep;
	}

	private ContextSummary summarize(Context component) {
		ContextSummary ctxSum = new ContextSummary(component);
		for(Iterator<CGNode> it = component.getCallGraph().iterator(); it.hasNext(); ) {
			CGNode node = it.next();
			if (node.getMethod().isNative()) continue;
			CompoundLockState exitState = component.getReturnState(node);
			//E.log(1, "\t" + node.getMethod().getReference().toString());
			if (exitState != null) {
				//E.log(1, "\tNONEMPTY");
				ctxSum.registerNodeState(node, exitState);
			}
			//collectCallsWhileAcquired(node);
		}
		return ctxSum;
	}


	/*
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
	 */
}

