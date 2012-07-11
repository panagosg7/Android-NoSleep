package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

public class ContentProvider extends Context{

  public ContentProvider(IClass c) {
	    super(c);
	}

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("Content Provider: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
