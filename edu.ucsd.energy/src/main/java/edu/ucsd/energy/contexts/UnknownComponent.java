package edu.ucsd.energy.contexts;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.managers.GlobalManager;

public class UnknownComponent extends Context{

  public UnknownComponent(GlobalManager gm, CGNode root) {
	    super(gm, root);

  }
  
}
