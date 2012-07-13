package edu.ucsd.energy.contexts;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ComponentSummary;
import edu.ucsd.energy.results.Violation;
import edu.ucsd.energy.results.Violation.ViolationType;

public class BroadcastReceiver extends Component {

	public Set<Selector> getEntryPoints() {
		return Interesting.broadcastReceiverEntryMethods;
	}

	public BroadcastReceiver(IClass c) {
		super(c);
		sTypicalCallback.addAll(Interesting.broadcastReceiverCallbackMethods);
	}

	
	public Set<Violation> gatherViolations(ComponentSummary summary) {
		Set<Violation> violations = new HashSet<Violation>();
		violations.addAll(super.gatherViolations(summary, Interesting.BroadcastReceiverOnReceive, ViolationType.BROADCAST_RECEIVER_ONRECEIVE));
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

	public boolean extendsAndroid() {
		return true;
	}
	
}
