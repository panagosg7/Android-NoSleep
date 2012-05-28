package edu.ucsd.energy.contexts;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.managers.GlobalManager;

public class Binder extends Context{

  public Binder(GlobalManager gm, CGNode root) {
	    super(gm, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("Binder: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
