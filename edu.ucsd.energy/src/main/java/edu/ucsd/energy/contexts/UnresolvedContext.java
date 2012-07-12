package edu.ucsd.energy.contexts;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.ProcessResults.ResultType;
import edu.ucsd.energy.results.Violation;

/**
 * This is a generic component class used whenever a component/context 
 * (not sure atm if we should be using both) cannot be resolved due to 
 * insufficient input data. 
 * We do not need to fill up sTypicalCallback and callbackEdges, since we
 * cannot know this kind of information beforehand
 * 
 * @author pvekris
 *
 */
public class UnresolvedContext extends Component {

	public UnresolvedContext(IClass c) {
		super(c);
	}

	/**
	 * Gather violations for all possible callbacks in unresolved components
	 */
	@Override
	protected Set<Violation> gatherViolations(ContextSummary summary) {
		Set<Violation> violations = new HashSet<Violation>();
		for(CallBack cb : getRoots()) {
			violations.addAll(super.gatherViolations(summary, 
					cb.getSelector(), ResultType.UNRESOLVED_CALLBACK));
		}
		return violations;
	}

	/**
	 * An unresolved context cannot be called from another context so 
	 * it shouldn't have any entry or exit points
	 */
	@Override
	public Set<Selector> getEntryPoints(Selector callSelector) {
		return new HashSet<Selector>();
	}

	@Override
	public Set<Selector> getExitPoints(Selector callSelector) {
		return new HashSet<Selector>();
	}

}
