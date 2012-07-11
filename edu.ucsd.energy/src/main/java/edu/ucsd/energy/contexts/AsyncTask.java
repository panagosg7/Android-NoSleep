package edu.ucsd.energy.contexts;

import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.Violation;

/**
 * TODO: Fill up life-cycle info from:
 * http://developer.android.com/reference/android/os/AsyncTask.html 
 * @author pvekris
 *
 */
public class AsyncTask extends Component {

  public AsyncTask(IClass c) {
	    super(c);

  }

	@Override
	protected Set<Violation> gatherViolations(ContextSummary ctx) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<Selector> getEntryPoints(Selector callSelector) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<Selector> getExitPoints(Selector callSelector) {
		// TODO Auto-generated method stub
		return null;
	}
}
