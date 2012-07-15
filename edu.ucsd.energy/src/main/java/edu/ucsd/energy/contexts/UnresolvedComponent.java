package edu.ucsd.energy.contexts;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ComponentSummary;
import edu.ucsd.energy.results.Violation;
import edu.ucsd.energy.results.Violation.ViolationType;
import edu.ucsd.energy.util.Log;

/**
 * This is a generic component class used whenever a component/context 
 * cannot be resolved due to insufficient input data. 
 * We do not need to fill up sTypicalCallback and callbackEdges, since we
 * cannot know this kind of information beforehand
 * 
 * @author pvekris
 *
 */
public class UnresolvedComponent extends Component {

	private static final int DEBUG = 1;

	public UnresolvedComponent(IClass c) {
		super(c);
	}

	/**
	 * Gather violations for all possible call-backs in unresolved components
	 * This includes just the call-backs that can be called by the Android OS,
	 * not arbitrary method calls.
	 */
	@Override
	protected Set<Violation> gatherViolations(ComponentSummary summary) {
		Set<Violation> violations = new HashSet<Violation>();
		for(CallBack cb : getRoots()) {
			if(isSystemCall(cb.getSelector())) {
				if (DEBUG > 0) {
					Log.println();
					Log.println("Examining callback: " + cb.toString());
				}
				violations.addAll(super.gatherViolations(summary,cb.getSelector(), 
						ViolationType.UNRESOLVED_CALLBACK_LOCKED));
			}
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



	public boolean isSystemCall(Selector sel) {
		if(extendsAndroid()) {
			HashSet<IClass> relevant = new HashSet<IClass>();
			relevant.addAll(getClassAncestors());
			relevant.addAll(getImplementedInterfaces());
			for (IClass r : relevant) {
				//System.out.println("Checking: " + r.toString());
				IMethod resolvedMethod = r.getMethod(sel);
				if (resolvedMethod != null) {
					//System.out.println("OVERRIDES: " + toString() + ", sel: " + sel);
					return true;					
				}
			}
		}
		return false;
	}


}
