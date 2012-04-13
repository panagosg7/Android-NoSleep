package energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

import energy.analysis.AppCallGraph;


/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author progsys
 *
 */

public class LocationListener extends Component{

  public LocationListener(AppCallGraph originalCG, IClass declaringClass, CGNode root) {
    super(originalCG, declaringClass, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("LocationListener: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
