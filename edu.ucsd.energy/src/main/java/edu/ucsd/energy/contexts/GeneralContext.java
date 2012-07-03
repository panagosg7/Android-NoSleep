package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

import edu.ucsd.energy.managers.GlobalManager;

public class GeneralContext extends Context {

	private static Context singleton;
	
	public static Context singleton(GlobalManager gm) {
		if (singleton == null) {
			singleton = new GeneralContext(gm, gm.getClassHierarchy().getRootClass()); 
		}
		return singleton;
	}
	
	protected GeneralContext(GlobalManager gm, IClass c) {
		super(gm, c);
	}
	
	public String toString () {
		return "General Context";
	}

}
