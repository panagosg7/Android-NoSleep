package edu.ucsd.energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.analysis.AppCallGraph;

/**
 * TODO: This is definitely not a component
 * Using it just for now  
 * @author pvekris
 *
 */

public class Initializer extends Component{

  
//  static String elements[] = { "<init>", "<clinit>"};
  
  public Initializer(AppCallGraph originalCG, IClass declaringClass, CGNode root) {
    super(originalCG, declaringClass, root);
//    callbackNames.addAll(Arrays.asList(elements));          
//    callbackExpectedState = new HashSet<Pair<String,List<String>>>();
//    callbackExpectedState.add(Pair.make(
//        "run", 
//        Arrays.asList("lightgreen", "green","lightgrey")));
  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("Initializer: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
