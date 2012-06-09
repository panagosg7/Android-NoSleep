package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.policy.IPolicy;
import edu.ucsd.energy.policy.ServicePolicy;

public class Service extends Component {

	static Selector elements[] = {
		Interesting.ServiceOnCreate, 
		Interesting.ServiceOnStart,
		Interesting.ServiceOnDestroy
	};

	
	public Service(GlobalManager gm, CGNode root) {
		super(gm, root);
		sTypicalCallback.addAll(Arrays.asList(elements));
		callbackEdges.add(Pair.make(Interesting.ServiceOnCreate, Interesting.ServiceOnStart));
		callbackEdges.add(Pair.make(Interesting.ServiceOnStart, Interesting.ServiceOnDestroy));
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
