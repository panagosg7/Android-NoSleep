package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;

public class PhoneStateListener extends Context{

  public PhoneStateListener(GlobalManager gm, IClass c) {
	    super(gm, c);
  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("PhoneStateListener: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
