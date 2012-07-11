package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.ProcessResults.ResultType;
import edu.ucsd.energy.results.Violation;

/**
 * This is not really a component, but i decided to treat it as one because 
 * a Callable behaves like a called component (e.g. like an Service), 
 * and we need it to be this way for the super-graph construction 
 */
public class Callable extends Component {

	static Selector elements[] = { Interesting.ThreadCall };

	public Callable(IClass c) {
		super(c);

		sTypicalCallback.addAll(Arrays.asList(elements));          
	}

	@Override
	protected Set<Violation> gatherViolations(ContextSummary summary) {
		Set<Violation> violations = new HashSet<Violation>();
		violations.addAll(super.gatherViolations(summary, Interesting.ThreadCall, ResultType.CALLABLE_CALL));
		return violations;
	}

	@Override
	public Set<Selector> getEntryPoints(Selector callSelector) {
		return Interesting.callableCallbackMethods;
	}

	@Override
	public Set<Selector> getExitPoints(Selector callSelector) {
		return Interesting.callableCallbackMethods;
	}    

}
