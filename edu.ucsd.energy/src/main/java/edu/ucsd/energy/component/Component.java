package edu.ucsd.energy.component;

import java.util.Iterator;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.ViolationReport;

abstract public class Component extends Context {

	protected Component(GlobalManager gm, IClass c) {
		super(gm, c);
	}
	
	public ViolationReport assembleReport() {
		ContextSummary ctxSum = new ContextSummary(this);
		for(Iterator<CallBack> it = getCallbacks().iterator() ; it.hasNext(); ) {
			CallBack cb = it.next();
			CompoundLockState exitState = getReturnState(cb.getNode());
			//if (exitState != null) {
				ctxSum.registerState(cb, exitState);
			//}
		}
		return gatherViolations(ctxSum);
	}
	
	abstract protected ViolationReport gatherViolations(ContextSummary ctx);
	
}
