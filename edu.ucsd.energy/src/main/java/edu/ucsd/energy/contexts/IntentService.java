package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
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

public class IntentService extends Component {
	
	private static final int DEBUG = 0;
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

	public IntentService(GlobalManager gm, IClass c) {
		super(gm, c);
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

	
	public ViolationReport gatherViolations(ContextSummary summary) {
		Set<LockUsage> onHandleIntentStates = summary.getCallBackState(Interesting.ServiceOnHandleIntent);
		
		ViolationReport report = new ViolationReport();

		for (WakeLockInstance wli : summary.lockInstances()) {
			if (DEBUG > 0) {
				System.out.println("Checking policies for lock: " + wli.toShortString());
				System.out.println("onDestroyStates: " + onHandleIntentStates.size());
			}
			for (LockUsage st : onHandleIntentStates) {
				if (DEBUG > 0) {
					System.out.println("Examining: " + st.toString());
				}
				if (relevant(st) && st.locking(wli)) {
					report.insertViolation(this, new Violation(ResultType.INTENTSERVICE_ONHANDLEINTENT));
				}	
			}		}
		
		return report;
	}

	
	
}
