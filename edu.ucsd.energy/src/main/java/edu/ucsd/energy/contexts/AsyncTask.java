package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;

public class AsyncTask extends Context{

  public AsyncTask(GlobalManager gm, IClass c) {
	    super(gm, c);

  }
}
