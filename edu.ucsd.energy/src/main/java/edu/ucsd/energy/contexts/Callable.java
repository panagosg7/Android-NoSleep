package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ComponentSummary;
import edu.ucsd.energy.results.Violation;
import edu.ucsd.energy.results.Violation.ViolationType;

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
	protected Set<Violation> gatherViolations(ComponentSummary summary) {
		Set<Violation> violations = new HashSet<Violation>();
		violations.addAll(super.gatherViolations(summary, Interesting.ThreadCall, ViolationType.CALLABLE_CALL));
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


	public boolean extendsAndroid() {
		return true;
	}
}
