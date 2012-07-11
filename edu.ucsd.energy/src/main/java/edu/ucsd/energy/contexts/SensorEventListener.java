package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

public class SensorEventListener extends Context{

  public SensorEventListener(IClass c) {
	    super(c);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("SensorEventListener: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
