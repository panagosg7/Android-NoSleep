package edu.ucsd.energy.component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.debug.Assertions;

import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.ProcessResults.LockUsage;
import edu.ucsd.energy.results.ProcessResults.ResultType;
import edu.ucsd.energy.results.Violation;

abstract public class Component extends Context {

	private static final int DEBUG = 0;

	protected Component(GlobalManager gm, IClass c) {
		super(gm, c);
	}

	public Set<Violation> assembleReport() {
		ContextSummary ctxSum = new ContextSummary(this);
		for(Iterator<CallBack> it = getCallbacks().iterator() ; it.hasNext(); ) {
			CallBack cb = it.next();
			CompoundLockState exitState = getReturnState(cb.getNode());
			if (exitState != null) {	//for the case of native methods 
				ctxSum.registerState(cb, exitState);
			}
			else {
				IMethod method = cb.getNode().getMethod();
				Assertions.productionAssertion(method.isNative(), 
						"Empty exit state from non-native method: " + method.toString());
			}
			
		}
		return gatherViolations(ctxSum);
	}

	abstract protected Set<Violation> gatherViolations(ContextSummary ctx);


	protected boolean relevant(LockUsage st) {
		return st.relevant(this);
	}


	/**
	 * The set of entry and exit points might depend on the call method used 
	 * for this component call
	 */
	abstract public Set<Selector> getEntryPoints(Selector callSelector);
	abstract public Set<Selector> getExitPoints(Selector callSelector);

	
	
	public Set<Violation> gatherviolations(ContextSummary summary, Selector sel, ResultType res) {
		
		Set<LockUsage> stateForSelector = summary.getCallBackState(sel);

		Set<Violation> violations = new HashSet<Violation>();

		for (WakeLockInstance wli : summary.lockInstances()) {
			if (DEBUG > 0) {
				System.out.println("Checking policies for lock: " + wli.toShortString());
			}

			for (LockUsage st : stateForSelector) {
				if (DEBUG > 0) {
					System.out.println("Examining: " + st.toString());
					System.out.println("Relevant ctxs: " + st.getRelevantCtxs());
				}
				if (relevant(st) && st.locking(wli)) {
					if (DEBUG > 0) {
						System.out.println("Adding violation: " + wli.toShortString());
						System.out.println();
					}
					violations.add(new Violation(res));
				}	
			}

		}

		return violations;		
	}

}
