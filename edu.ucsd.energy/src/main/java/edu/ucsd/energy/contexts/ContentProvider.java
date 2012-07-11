package edu.ucsd.energy.contexts;

import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ContextSummary;
import edu.ucsd.energy.results.Violation;


/**
 * Not used at the moment
 * @author pvekris
 *
 */
public class ContentProvider extends Component {

  public ContentProvider(IClass c) {
	    super(c);
	}

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("Content Provider: ");
    b.append(getKlass().getName().toString());

    return b.toString();
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
