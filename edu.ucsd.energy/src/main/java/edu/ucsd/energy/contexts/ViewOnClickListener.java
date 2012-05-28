package edu.ucsd.energy.contexts;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.managers.GlobalManager;

/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author progsys
 *
 */

public class ViewOnClickListener extends Context{

  public ViewOnClickListener(GlobalManager gm, CGNode root) {
	    super(gm, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("View.OnClickListener: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
