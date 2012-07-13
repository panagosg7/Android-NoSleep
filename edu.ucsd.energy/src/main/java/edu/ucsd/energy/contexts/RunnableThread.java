package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.Violation;
import edu.ucsd.energy.results.Violation.ViolationType;

/**
 * This is not really a component, but i decided to treat it as one because 
 * a Runnable behaves like a called component (e.g. like an Service), 
 * and we need it to be this way for the super-graph construction 
 */
public class RunnableThread extends Component {

	static Selector elements[] = { Interesting.ThreadRun };

	public RunnableThread(IClass c) {
		super(c);
		sTypicalCallback.addAll(Arrays.asList(elements));
	}

	public String toString() {
		StringBuffer b = new StringBuffer();
		b.append("Runnable thread: ");
		b.append(getKlass().getName().toString());
		return b.toString();
	}  


	public Set<Selector> getEntryPoints() {
		return Interesting.runnableEntryMethods;
	}

	public Set<Selector> getExitPoints(Selector sel) {
		return Interesting.runnableExitMethods;
	}

	@Override
	protected Set<Violation> gatherViolations(ContextSummary summary) {
		Set<Violation> violations = new HashSet<Violation>();
		violations.addAll(super.gatherViolations(summary, Interesting.ThreadRun, ViolationType.RUNNABLE_RUN));
		return violations;
	}

	@Override
	public Set<Selector> getEntryPoints(Selector callSelector) {
		return Interesting.runnableEntryMethods;
	}

	public boolean extendsAndroid() {
		return true;
	}
}
