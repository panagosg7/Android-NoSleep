package energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

import energy.analysis.ApplicationCallGraph;


/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author progsys
 *
 */

public class AdapterViewOnItemClickListener extends Component{

  public AdapterViewOnItemClickListener(ApplicationCallGraph originalCG, IClass declaringClass, CGNode root) {
    super(originalCG, declaringClass, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("AdapterView$OnItemClickListener: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
