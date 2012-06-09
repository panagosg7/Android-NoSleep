package edu.ucsd.energy.policy;

import java.util.Map;

import net.sf.json.JSONArray;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.ProcessResults.SingleLockUsage;


public interface IPolicy {

	public void solveFacts();

	public void addFact(CGNode node, Map<WakeLockInstance, SingleLockUsage> lu);

	public JSONArray toJSON();
	
}
