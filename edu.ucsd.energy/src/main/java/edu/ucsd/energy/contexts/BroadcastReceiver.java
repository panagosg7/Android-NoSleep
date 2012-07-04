package edu.ucsd.energy.contexts;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.ProcessResults.ResultType;
import edu.ucsd.energy.results.Violation;

public class BroadcastReceiver extends Component {

	public Set<Selector> getEntryPoints() {
		return Interesting.broadcastReceiverEntryMethods;
	}

	public BroadcastReceiver(GlobalManager gm, IClass c) {
		super(gm, c);
		sTypicalCallback.addAll(Interesting.broadcastReceiverCallbackMethods);
	}

	
	public Set<Violation> gatherViolations(ContextSummary summary) {
		Set<Violation> violations = new HashSet<Violation>();
		violations.addAll(super.gatherviolations(summary, Interesting.BroadcastReceiverOnReceive, ResultType.BROADCAST_RECEIVER_ONRECEIVE));
		return violations;

	}

	@Override
	public Set<Selector> getEntryPoints(Selector callSelector) {
		return Interesting.broadcastReceiverEntryMethods;
	}

	@Override
	public Set<Selector> getExitPoints(Selector callSelector) {
		return Interesting.broadcastReceiverExitMethods;
	}

	
}
