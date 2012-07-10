package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.ProcessResults.ResultType;
import edu.ucsd.energy.results.Violation;

public class Activity extends Component {
	
	static Selector elements[] = {
		Interesting.ActivityConstructor,
		Interesting.ActivityOnCreate, 
		Interesting.ActivityOnDestroy,
		Interesting.ActivityOnPause,
		Interesting.ActivityOnResume,
		Interesting.ActivityOnStart,
		Interesting.ActivityOnStop,
		Interesting.ActivityOnRestart
	};

	
	/**
	 * Activities can only be called with a single method - startActivity
	 * So the result is independent of the call method selector
	 */
	public Set<Selector> getEntryPoints(Selector callSelector) {
		return Interesting.activityEntryMethods;
	}

	public Set<Selector> getExitPoints(Selector callSelector) {
		return Interesting.activityExitMethods;
	}

	public Activity(GlobalManager global, IClass c) {
		super(global, c);
		sTypicalCallback.addAll(Arrays.asList(elements));
		
		//hard-coding the activity life-cycle edges as found here: 
		//http://developer.android.com/reference/android/app/Activity.html
		callbackEdges.add(Pair.make(Interesting.ActivityConstructor, Interesting.ActivityOnCreate));
		callbackEdges.add(Pair.make(Interesting.ActivityOnCreate, Interesting.ActivityOnStart));
		callbackEdges.add(Pair.make(Interesting.ActivityOnStart, Interesting.ActivityOnResume));
		callbackEdges.add(Pair.make(Interesting.ActivityOnResume, Interesting.ActivityOnPause));
		callbackEdges.add(Pair.make(Interesting.ActivityOnPause, Interesting.ActivityOnStop));
		callbackEdges.add(Pair.make(Interesting.ActivityOnStop, Interesting.ActivityOnDestroy));
		//back-edges
		callbackEdges.add(Pair.make(Interesting.ActivityOnPause, Interesting.ActivityOnResume));
		callbackEdges.add(Pair.make(Interesting.ActivityOnStop, Interesting.ActivityOnCreate));
		callbackEdges.add(Pair.make(Interesting.ActivityOnStop, Interesting.ActivityOnRestart));
		callbackEdges.add(Pair.make(Interesting.ActivityOnRestart, Interesting.ActivityOnStart));
	}

	
	/**
	 * Determine the policies for Activities:
	 */
	protected Set<Violation> gatherViolations(ContextSummary summary) {
		Set<Violation> violations = new HashSet<Violation>();
		violations.addAll(super.gatherviolations(summary, Interesting.ActivityOnPause, ResultType.ACTIVITY_ONPAUSE));
		violations.addAll(super.gatherviolations(summary, Interesting.ActivityOnStop, ResultType.ACTIVITY_ONSTOP));
		return violations;
	}

}
