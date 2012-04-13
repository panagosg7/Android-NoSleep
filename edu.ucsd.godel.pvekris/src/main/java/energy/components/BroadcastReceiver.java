package energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

import energy.analysis.AppCallGraph;

public class BroadcastReceiver extends Component{

  public BroadcastReceiver(AppCallGraph originalCG, IClass declaringClass, CGNode root) {
    super(originalCG, declaringClass, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("Broadcast Receiver: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
