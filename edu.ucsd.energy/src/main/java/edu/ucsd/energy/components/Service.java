package edu.ucsd.energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.analysis.AppCallGraph;

public class Service extends Component {

  public Service(AppCallGraph originalCG, IClass declaringClass, CGNode root) {
    super(originalCG, declaringClass, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("Service: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }

  
  
}
