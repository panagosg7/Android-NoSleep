package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;


/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author progsys
 *
 */

public class OnSharedPreferenceChangeListener extends Context{

  public OnSharedPreferenceChangeListener(GlobalManager gm, IClass c) {
	    super(gm, c);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("OnSharedPreferenceChangeListener: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
