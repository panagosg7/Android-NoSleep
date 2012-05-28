package edu.ucsd.energy.policy;

import net.sf.json.JSONArray;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.interproc.SingleLockState;


public interface IPolicy {

	public void solveFacts();

	public void addFact(CGNode node, SingleLockState mergedLS);

	public JSONArray toJSON();	
	
}
