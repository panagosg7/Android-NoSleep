package edu.ucsd.energy.contexts;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.managers.GlobalManager;

public class ContentProvider extends Context{

  public ContentProvider(GlobalManager gm, CGNode root) {
	    super(gm, root);
	}

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("Content Provider: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
