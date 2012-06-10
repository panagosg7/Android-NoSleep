package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.policy.ActivityPolicy;

public class Activity extends Component {

	static Selector elements[] = {
		Interesting.ActivityOnCreate, 
		Interesting.ActivityOnDestroy,
		Interesting.ActivityOnPause,
		Interesting.ActivityOnResume,
		Interesting.ActivityOnStart,
		Interesting.ActivityOnStop,
		Interesting.ActivityOnRestart
	};

	public Set<Selector> getEntryPoints() {
		return Interesting.activityEntryMethods;
	}
	
	public Set<Selector> getExitPoints() {
		return Interesting.activityExitMethods;
	}

	public Activity(GlobalManager global, CGNode root) {
		super(global, root);
		sTypicalCallback.addAll(Arrays.asList(elements));
		callbackEdges.add(Pair.make(Interesting.ActivityOnCreate, Interesting.ActivityOnStart));
		callbackEdges.add(Pair.make(Interesting.ActivityOnStart, Interesting.ActivityOnResume));
		callbackEdges.add(Pair.make(Interesting.ActivityOnResume, Interesting.ActivityOnPause));
		callbackEdges.add(Pair.make(Interesting.ActivityOnPause, Interesting.ActivityOnStop));
		callbackEdges.add(Pair.make(Interesting.ActivityOnStop, Interesting.ActivityOnDestroy));
	}

	public ActivityPolicy makePolicy() {
		return new ActivityPolicy(this);
	}


}
