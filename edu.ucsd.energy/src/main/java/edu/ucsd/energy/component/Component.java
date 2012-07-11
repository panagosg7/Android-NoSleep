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
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.ProcessResults.LockUsage;
import edu.ucsd.energy.results.ProcessResults.ResultType;
import edu.ucsd.energy.results.Violation;

abstract public class Component extends Context {

	private static final int DEBUG = 0;

	protected Component(IClass c) {
		super(c);
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

	
	
	public Set<Violation> gatherViolations(ContextSummary summary, Selector sel, ResultType res) {
		Set<LockUsage> stateForSelector = summary.getCallBackState(sel);
		if (DEBUG > 0) {
			System.out.println("States for :" + sel.toString());
			System.out.println("  " + stateForSelector);
		}
		Set<Violation> violations = new HashSet<Violation>();
		for (WakeLockInstance wli : summary.lockInstances()) {
			if (DEBUG > 0) {
				System.out.println("Checking policies for lock: " + wli.toShortString());
			}

			if (stateForSelector != null) {
				for (LockUsage st : stateForSelector) {
					
					boolean relevant = relevant(st);
					
					if (DEBUG > 0) {
						if (!relevant) {
							System.out.println("IRRELEVANT Examining: " + st.toString());
							System.out.println("Relevant ctxs: " + st.getRelevantCtxs());
						}
					}
					
					if (relevant && st.locking(wli)) {
						if (DEBUG > 0) {
							System.out.println("Adding violation: " + wli.toShortString());
							System.out.println();
						}
						violations.add(new Violation(res));
					}	
				}
			}
			else {
				Assertions.UNREACHABLE("Cannot ask for a callback state and " +
						"not be able to find it in our results.");
			}
		}
		return violations;		
	}

}

 