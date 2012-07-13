package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ComponentSummary;
import edu.ucsd.energy.results.Violation;
import edu.ucsd.energy.results.Violation.ViolationType;

public class Service extends Component {

	
	static Selector elements[] = {
		Interesting.ServiceOnCreate, 
		Interesting.ServiceOnStart,
		Interesting.ServiceOnStartCommand,
		Interesting.ServiceOnBind,
		Interesting.ServiceOnRebind,
		Interesting.ServiceOnUnbind,
		Interesting.ServiceOnDestroy
	};

	
	public Service(IClass c) {
		super(c);
		sTypicalCallback.addAll(Arrays.asList(elements));
		callbackEdges.add(Pair.make(Interesting.ServiceOnCreate, Interesting.ServiceOnStart));
		//XXX: the service can't be implementing both, right...?
		//The one that is not implemented will simply be removed
		//from the lifecycle graph, and only the other one will be taken into account.
		//So to get the state at the end of onStart(Command) just as for the state at 
		//the end of either.
		//Started service
		callbackEdges.add(Pair.make(Interesting.ServiceOnCreate, Interesting.ServiceOnStart));
		callbackEdges.add(Pair.make(Interesting.ServiceOnStart, Interesting.ServiceOnStartCommand));
		callbackEdges.add(Pair.make(Interesting.ServiceOnStartCommand, Interesting.ServiceOnDestroy));
		//callbackEdges.add(Pair.make(Interesting.ServiceOnStartCommand, Interesting.ServiceOnDestroy));
		
		//Bound service
		callbackEdges.add(Pair.make(Interesting.ServiceOnCreate, Interesting.ServiceOnBind));
		callbackEdges.add(Pair.make(Interesting.ServiceOnBind, Interesting.ServiceOnUnbind));
		callbackEdges.add(Pair.make(Interesting.ServiceOnUnbind, Interesting.ServiceOnRebind));
		callbackEdges.add(Pair.make(Interesting.ServiceOnRebind, Interesting.ServiceOnUnbind));
		callbackEdges.add(Pair.make(Interesting.ServiceOnUnbind, Interesting.ServiceOnDestroy));
	}

	public Set<Selector> getEntryPoints(Selector callSelector) {
		if (callSelector.equals(Interesting.StartService)) {
			return Interesting.startedServiceEntryMethods;
		}
		else if (callSelector.equals(Interesting.BindService)) {
			return Interesting.boundServiceEntryMethods; 
		}
		return null;
	}
	

	public Set<Selector> getExitPoints(Selector callSelector) {
		if (callSelector.equals(Interesting.StartService)) {
			return Interesting.startedServiceExitMethods;
		}
		else if (callSelector.equals(Interesting.BindService)) {
			return Interesting.boundServiceExitMethods;
		}
		return null;
	}
	
	
	public String toString() {
		StringBuffer b = new StringBuffer();
		b.append("Service: ");
		b.append(getKlass().getName().toString());
		return b.toString();
	}
	
	
	public Set<Violation> gatherViolations(ComponentSummary summary) {
		Set<Violation> violations = new HashSet<Violation>();
		
		//XXX: This is not completely sound...
		//What should be done is check all the possible call-sites of this service and 
		//check if it is started or bound. But this is definitely a good indicator.
		if (isCallBack(Interesting.ServiceOnStartCommand) || isCallBack(Interesting.ServiceOnStartCommand)) {
			violations.addAll(super.gatherViolations(summary, Interesting.ServiceOnStartCommand, ViolationType.SERVICE_ONSTART));	
		}
		if (isCallBack(Interesting.ServiceOnBind)) {
			violations.addAll(super.gatherViolations(summary, Interesting.ServiceOnUnbind, ViolationType.SERVICE_ONUNBIND));
		}
		return violations;
	}

	public boolean extendsAndroid() {
		return true;
	}
}

