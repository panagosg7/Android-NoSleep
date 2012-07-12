package edu.ucsd.energy.contexts;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.Violation;
import edu.ucsd.energy.results.ProcessResults.ResultType;

/**
 * Life-cycle info from:
 * http://developer.android.com/reference/android/os/AsyncTask.html 
 * @author pvekris
 *
 */
public class AsyncTask extends Component {

  public AsyncTask(IClass c) {
	    super(c);
  }

	@Override
	protected Set<Violation> gatherViolations(ContextSummary summary) {
		Set<Violation> violations = new HashSet<Violation>();
		violations.addAll(super.gatherViolations(summary, Interesting.AsyncTaskOnPostExecute, ResultType.ASYNC_TASK_ONPOSTEXECUTE));
		return violations;
	}

	@Override
	public Set<Selector> getEntryPoints(Selector callSelector) {
		return Interesting.asyncTaskEntryMethods;
	}

	@Override
	public Set<Selector> getExitPoints(Selector callSelector) {
		return Interesting.asyncTaskExitMethods;
	}
}
