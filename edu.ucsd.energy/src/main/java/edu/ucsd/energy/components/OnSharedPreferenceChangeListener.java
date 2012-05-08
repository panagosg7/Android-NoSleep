package edu.ucsd.energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.analysis.AppCallGraph;


/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author progsys
 *
 */

public class OnSharedPreferenceChangeListener extends Component{

  public OnSharedPreferenceChangeListener(AppCallGraph originalCG, IClass declaringClass, CGNode root) {
    super(originalCG, declaringClass, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("OnSharedPreferenceChangeListener: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
