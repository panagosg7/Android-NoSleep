package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;

public class OnCompletionListener extends Context{

  public OnCompletionListener(GlobalManager gm, IClass c) {
	    super(gm, c);

  }
}
