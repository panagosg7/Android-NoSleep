package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;


/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author progsys
 *
 */

public class Widget extends Context{

  public Widget(GlobalManager gm, IClass c) {
	    super(gm, c);

  }

  
}
