package edu.ucsd.energy.contexts;

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


/**
 * From android developers:
 * public void onCreate ()
 * Since: API Level 1
 * Called when the application is starting, before any other application objects have been created. 
 * Implementations should be as quick as possible (for example using lazy initialization of state) 
 * since the time spent in this function directly impacts the performance of starting the first activity, 
 * service, or receiver in a process. If you override this method, be sure to call super.onCreate().
 * 
 * onCreate and onTerminate can be used as Entry and Exit methods.
 * 
 * @author pvekris
 */
public class Application extends Component {

	public Application(IClass c) {
	    super(c);
	    sTypicalCallback.add(Interesting.ApplicationOnCreate);
	    sTypicalCallback.add(Interesting.ApplicationOnTerminate);
	    callbackEdges.add(Pair.make(Interesting.ApplicationOnCreate, Interesting.ApplicationOnTerminate));
	}
	
	  public String toString() {	    
		    StringBuffer b = new StringBuffer();
		    b.append("Application: ");
		    b.append(getKlass().getName().toString());
		    return b.toString();
		  }

		@Override
		protected Set<Violation> gatherViolations(ComponentSummary summary) {
			Set<Violation> violations = new HashSet<Violation>();
			violations.addAll(super.gatherViolations(summary, Interesting.ApplicationOnTerminate, ViolationType.APPLICATION_TERMINATE));
			return violations;
		}

		@Override
		public Set<Selector> getEntryPoints(Selector callSelector) {
			return Interesting.applicationEntryMethods;
		}

		@Override
		public Set<Selector> getExitPoints(Selector callSelector) {
			return Interesting.applicationExitMethods;
		}

		public boolean extendsAndroid() {
			return true;
		}
}
