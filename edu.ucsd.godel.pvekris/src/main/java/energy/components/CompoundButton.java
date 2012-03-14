package energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

import energy.analysis.ApplicationCallGraph;

public class CompoundButton extends Component{

  public CompoundButton(ApplicationCallGraph originalCG, IClass declaringClass, CGNode root) {
    super(originalCG, declaringClass, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("CompoundButton: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
