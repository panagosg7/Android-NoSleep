package edu.ucsd.energy.contexts;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.policy.IPolicy;


/**
 * From android developers:
 * public void onCreate ()
 * Since: API Level 1
 * Called when the application is starting, before any other application objects have been created. 
 * Implementations should be as quick as possible (for example using lazy initialization of state) 
 * since the time spent in this function directly impacts the performance of starting the first activity, 
 * service, or receiver in a process. If you override this method, be sure to call super.onCreate().
 * @author pvekris
 *
 */
public class Application extends Component {

	public Application(GlobalManager gm, CGNode root) {
	    super(gm, root);
	}
	
	  public String toString() {	    
		    StringBuffer b = new StringBuffer();
		    b.append("Application: ");
		    b.append(getKlass().getName().toString());
		    return b.toString();
		  }

	@Override
	public IPolicy makePolicy() {
		// TODO Auto-generated method stub
		return null;
	}

}
