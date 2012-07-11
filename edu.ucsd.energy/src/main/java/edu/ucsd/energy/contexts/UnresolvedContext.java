package edu.ucsd.energy.contexts;

import com.ibm.wala.classLoader.IClass;

public class UnresolvedContext extends Context {

	protected UnresolvedContext(IClass c) {
		super(c);
		
	}

}
