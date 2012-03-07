package energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

public class Service extends Component {

  public Service(IClass declaringClass, CGNode root) {
    super(declaringClass, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("Service: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }

  
  
}
