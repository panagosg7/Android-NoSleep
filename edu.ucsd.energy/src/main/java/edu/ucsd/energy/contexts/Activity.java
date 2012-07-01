package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.ProcessResults.LockUsage;
import edu.ucsd.energy.results.ProcessResults.ResultType;
import edu.ucsd.energy.results.Violation;
import edu.ucsd.energy.results.ViolationReport;

public class Activity extends Component {

	private static final int DEBUG = 0;
	
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
		callbackEdges.add(Pair.make(Interesting.ActivityOnStop, Interesting.ActivityOnResume));
		callbackEdges.add(Pair.make(Interesting.ActivityOnStop, Interesting.ActivityOnCreate));
		callbackEdges.add(Pair.make(Interesting.ActivityOnStop, Interesting.ActivityOnRestart));
		callbackEdges.add(Pair.make(Interesting.ActivityOnRestart, Interesting.ActivityOnStart));
	}

	
	/**
	 * Determine the policies for Activities:
	 */
	protected ViolationReport gatherViolations(ContextSummary summary) {

		ViolationReport report = new ViolationReport();

		//We are going to get multiple LockUsages for the various possible
		//methods that can be overridden - we have to account for each one 
		//separately.
		Set<LockUsage> onStartStates = summary.getCallBackState(Interesting.ActivityOnStart);
		Set<LockUsage> onPauseStates = summary.getCallBackState(Interesting.ActivityOnPause);
		Set<LockUsage> onStopStates = summary.getCallBackState(Interesting.ActivityOnStop);

		for (WakeLockInstance wli : summary.lockInstances()) {
			if (DEBUG > 0) {
				System.out.println("Checking policies for lock: " + wli.toShortString());
				System.out.println("onPauseStates: " + onPauseStates.size());
				System.out.println("onStopStates: " + onStopStates.size());
				System.out.println("onStartStates: " + onStartStates.size());
			}

			//Usually this is the case when the interraction between app and the user
			//stops. So any cleanup regarding locks should be done here.
			for (LockUsage st : onPauseStates) {
				if (DEBUG > 0) {
					System.out.println("Examining: " + st.toString());
				}
				if (st.locking(wli)) {
					report.insertViolation(this, new Violation(ResultType.ACTIVITY_ONPAUSE));
				}	
			}
			
			for (LockUsage st : onStopStates) {
				if (DEBUG > 0) {
					System.out.println("Examining: " + st.toString());
				}
				if (st.locking(wli)) {
					report.insertViolation(this, new Violation(ResultType.ACTIVITY_ONSTOP));
				}	
			}
		}
		return report;
	}
	

}
