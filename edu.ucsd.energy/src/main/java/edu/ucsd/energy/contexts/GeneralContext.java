package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

public class GeneralContext extends Context {

	private static Context singleton;
	
	public static Context singleton() {
		if (singleton == null) {
			singleton = new GeneralContext(global.getClassHierarchy().getRootClass()); 
		}
		return singleton;
	}
	
	protected GeneralContext(IClass c) {
		super(c);
	}
	
	public String toString () {
		return "General Context";
	}

}
