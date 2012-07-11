package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;


/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author progsys
 *
 */

public class ClickListener extends Context{

  public ClickListener(IClass c) {
  	super(c);
  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("AdapterView$OnItemClickListener: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
