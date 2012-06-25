package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;

public class SensorEventListener extends Context{

  public SensorEventListener(GlobalManager gm, IClass c) {
	    super(gm, c);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("SensorEventListener: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
