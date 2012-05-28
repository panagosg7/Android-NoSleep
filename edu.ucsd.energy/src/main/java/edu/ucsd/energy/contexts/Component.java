package edu.ucsd.energy.contexts;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.policy.IPolicy;

abstract public class Component extends Context {

	protected Component(GlobalManager gm, CGNode root) {
		super(gm, root);
	}

	abstract public IPolicy makePolicy();
	
}
