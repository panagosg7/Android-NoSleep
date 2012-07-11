package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.ProcessResults.ResultType;
import edu.ucsd.energy.results.Violation;

public class IntentService extends Component {
	
	static Selector elements[] = {
		Interesting.ServiceOnCreate, 
		Interesting.ServiceOnHandleIntent,
		Interesting.ServiceOnBind,
		Interesting.ServiceOnRebind,
		Interesting.ServiceOnUnbind,
		Interesting.ServiceOnDestroy
	};


	public Set<Selector> getEntryPoints() {
		return Interesting.intentServiceEntryMethods;
	}

	public IntentService(IClass c) {
		super(c);
		sTypicalCallback.addAll(Arrays.asList(elements));
		
		//Handle Intent stuff
		callbackEdges.add(Pair.make(Interesting.ServiceOnCreate, Interesting.ServiceOnHandleIntent));
		callbackEdges.add(Pair.make(Interesting.ServiceOnHandleIntent, Interesting.ServiceOnDestroy));
		
		//Bound service
		callbackEdges.add(Pair.make(Interesting.ServiceOnCreate, Interesting.ServiceOnBind));
		callbackEdges.add(Pair.make(Interesting.ServiceOnBind, Interesting.ServiceOnUnbind));
		callbackEdges.add(Pair.make(Interesting.ServiceOnUnbind, Interesting.ServiceOnRebind));
		callbackEdges.add(Pair.make(Interesting.ServiceOnRebind, Interesting.ServiceOnUnbind));
		callbackEdges.add(Pair.make(Interesting.ServiceOnUnbind, Interesting.ServiceOnDestroy));

	}


	public Set<Selector> getEntryPoints(Selector callSelector) {
		return Interesting.intentServiceEntryMethods;
	}
	

	public Set<Selector> getExitPoints(Selector callSelector) {
		return Interesting.intentServiceExitMethods;
	}

	
	public Set<Violation> gatherViolations(ContextSummary summary) {
		Set<Violation> violations = new HashSet<Violation>();
		violations.addAll(super.gatherViolations(summary, Interesting.ServiceOnHandleIntent, ResultType.INTENTSERVICE_ONHANDLEINTENT));
		return violations;
	}

	
	
}
