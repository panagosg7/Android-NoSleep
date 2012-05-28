package edu.ucsd.energy.contexts;

import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.policy.BroadcastReceiverPolicy;
import edu.ucsd.energy.policy.IPolicy;

public class BroadcastReceiver extends Component {

	public Set<Selector> getEntryPoints() {
		return Interesting.broadcastReceiverEntryMethods;
	}

	public BroadcastReceiver(GlobalManager gm, CGNode root) {
		super(gm, root);
	}

	@Override
	public IPolicy makePolicy() {
		return new BroadcastReceiverPolicy(this);
	}
}
