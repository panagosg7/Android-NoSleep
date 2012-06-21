package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.Violation;
import edu.ucsd.energy.results.ViolationReport;
import edu.ucsd.energy.results.ProcessResults.LockUsage;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class Service extends Component {

	private static final int DEBUG = 0;
	static Selector elements[] = {
		Interesting.ServiceOnCreate, 
		Interesting.ServiceOnStart,
		Interesting.ServiceOnDestroy
	};

	
	public Service(GlobalManager gm, CGNode root) {
		super(gm, root);
		sTypicalCallback.addAll(Arrays.asList(elements));
		callbackEdges.add(Pair.make(Interesting.ServiceOnCreate, Interesting.ServiceOnStart));
		//XXX: the service can't be implementing both, right...?
		//The one that is not implemented will simply be removed
		//from the lifecycle graph, and only the other one will be taken into account.
		//So to get the state at the end of onStart(Command) just as for the state at 
		//the end of either.
		callbackEdges.add(Pair.make(Interesting.ServiceOnStart, Interesting.ServiceOnStartCommand));
		callbackEdges.add(Pair.make(Interesting.ServiceOnStartCommand, Interesting.ServiceOnStart));
		callbackEdges.add(Pair.make(Interesting.ServiceOnStart, Interesting.ServiceOnDestroy));
		//callbackEdges.add(Pair.make(Interesting.ServiceOnStartCommand, Interesting.ServiceOnDestroy));
	}

	public Set<Selector> getEntryPoints() {
		return Interesting.serviceEntryMethods;
	}
	
	public Set<Selector> getExitPoints() {
		return Interesting.serviceExitMethods;
	}
	
	public String toString() {

		StringBuffer b = new StringBuffer();
		b.append("Service: ");
		b.append(getKlass().getName().toString());

		return b.toString();
	}
	
	public ViolationReport gatherViolations(ContextSummary summary) {
		Set<LockUsage> onStartStates = summary.getCallBackState(Interesting.ServiceOnStart);
		Set<LockUsage> onDestroyStates = summary.getCallBackState(Interesting.ServiceOnDestroy);
		
		ViolationReport report = new ViolationReport();
		
		//TODO: refine policies for services 

		for (WakeLockInstance wli : summary.lockInstances()) {
			if (DEBUG > 0) {
				System.out.println("Checking policies for lock: " + wli.toShortString());
				System.out.println("onStartStates: " + onStartStates.size());
				System.out.println("onDestroyStates: " + onDestroyStates.size());
			}

			for (LockUsage st : onStartStates) {
				if (DEBUG > 0) {
					System.out.println("Examining: " + st.toString());
				}
				if (st.locking(wli)) {
					report.insertViolation(this, new Violation(ResultType.SERVICE_ONSTART));
				}	
			}
			for (LockUsage st : onDestroyStates) {
				if (DEBUG > 0) {
					System.out.println("Examining: " + st.toString());
				}
				if (st.locking(wli)) {
					report.insertViolation(this, new Violation(ResultType.SERVICE_ONDESTORY));
				}	
			}
		}
		
		return report;
	}


}
