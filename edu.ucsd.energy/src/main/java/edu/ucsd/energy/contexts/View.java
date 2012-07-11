package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author pvekris
 *
 */

public class View extends Context{

  public View(IClass c) {
	    super(c);
  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("View: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
