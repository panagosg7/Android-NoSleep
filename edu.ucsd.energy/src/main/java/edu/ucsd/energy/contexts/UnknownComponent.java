package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;

public class UnknownComponent extends Context{

  public UnknownComponent(GlobalManager gm, IClass c) {
	    super(gm, c);

  }
  
}
