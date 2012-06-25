package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;

public class WebViewClient extends Context{

  public WebViewClient(GlobalManager gm, IClass c) {
	    super(gm, c);
  }

}
