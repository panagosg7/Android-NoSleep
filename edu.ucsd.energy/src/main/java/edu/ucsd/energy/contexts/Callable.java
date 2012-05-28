package edu.ucsd.energy.contexts;

import java.util.Arrays;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.managers.GlobalManager;

/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author progsys
 *
 */
public class Callable extends Context {

  static Selector elements[] = { Interesting.ThreadCall };
  
  public Callable(GlobalManager gm, CGNode root) {
	    super(gm, root);
    
    sTypicalCallback.addAll(Arrays.asList(elements));          
  }    
  
}
