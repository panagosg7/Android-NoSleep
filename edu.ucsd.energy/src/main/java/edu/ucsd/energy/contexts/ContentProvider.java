package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;

public class ContentProvider extends Context{

  public ContentProvider(GlobalManager gm, IClass c) {
	    super(gm, c);
	}

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("Content Provider: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
