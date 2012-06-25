package edu.ucsd.energy.contexts;

import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.ViolationReport;

public class BroadcastReceiver extends Component {

	public Set<Selector> getEntryPoints() {
		return Interesting.broadcastReceiverEntryMethods;
	}

	public BroadcastReceiver(GlobalManager gm, IClass c) {
		super(gm, c);
	}

	
	public ViolationReport gatherViolations(ContextSummary summary) {
		ViolationReport policyReport = new ViolationReport();
		//Not sure if there should be a policy here
		return policyReport;
	}

	
}
