package energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

import energy.analysis.ApplicationCallGraph;

/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author pvekris
 *
 */

public class View extends Component{

  public View(ApplicationCallGraph originalCG, IClass declaringClass, CGNode root) {
    super(originalCG, declaringClass, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("View: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
