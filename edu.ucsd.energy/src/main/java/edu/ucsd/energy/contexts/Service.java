package edu.ucsd.energy.contexts;

import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.policy.IPolicy;
import edu.ucsd.energy.policy.ServicePolicy;

public class Service extends Component {

	public Service(GlobalManager gm, CGNode root) {
		super(gm, root);
	}

	public Set<Selector> getEntryPoints() {
		return Interesting.serviceEntryMethods;
	}

	public String toString() {

		StringBuffer b = new StringBuffer();
		b.append("Service: ");
		b.append(getKlass().getName().toString());

		return b.toString();
	}

	@Override
	public IPolicy makePolicy() {
		return new ServicePolicy(this);

	}



}
