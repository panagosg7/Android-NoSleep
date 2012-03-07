package energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;


/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author progsys
 *
 */

public class LocationListener extends Component{

  public LocationListener(IClass declaringClass, CGNode root) {
    super(declaringClass, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("LocationListener: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
