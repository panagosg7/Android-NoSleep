package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

public class PhoneStateListener extends Context{

  public PhoneStateListener(IClass c) {
	    super(c);
  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("PhoneStateListener: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
