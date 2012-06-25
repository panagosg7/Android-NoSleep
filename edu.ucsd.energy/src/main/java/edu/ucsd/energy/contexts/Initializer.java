package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;

/**
 * TODO: This is definitely not a component
 * Using it just for now  
 * @author pvekris
 *
 */

public class Initializer extends Context{

  
//  static String elements[] = { "<init>", "<clinit>"};
  
  public Initializer(GlobalManager gm, IClass c) {
	    super(gm, c);
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
