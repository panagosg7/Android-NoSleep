package energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

public class BroadcastReceiver extends Component{

  public BroadcastReceiver(IClass declaringClass, CGNode root) {
    super(declaringClass, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("Broadcast Receiver: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
