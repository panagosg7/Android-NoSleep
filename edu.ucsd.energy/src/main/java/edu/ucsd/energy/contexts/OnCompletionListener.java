package edu.ucsd.energy.contexts;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.managers.GlobalManager;

public class OnCompletionListener extends Context{

  public OnCompletionListener(GlobalManager gm, CGNode root) {
	    super(gm, root);

  }
}
