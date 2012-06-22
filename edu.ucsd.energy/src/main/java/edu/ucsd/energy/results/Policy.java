package edu.ucsd.energy.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.json.JSONArray;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.results.ProcessResults.SingleLockUsage;

abstract public class Policy<T extends Component> {

	protected Component component;
	
	protected HashMap<Selector,Map<WakeLockInstance, SingleLockUsage>> map = 
			new  HashMap<Selector, Map<WakeLockInstance,SingleLockUsage>>();
	
	protected HashSet<WakeLockInstance> instances = new HashSet<WakeLockInstance>();
	
	private ArrayList<Violation> violations = new ArrayList<Violation>();

	public Policy(T c) {
		component = c;
	}
	
	public void addFact(CGNode n, Map<WakeLockInstance, SingleLockUsage> st) {
		map.put(n.getMethod().getSelector(), st);
		if (st != null) {
			for(Entry<WakeLockInstance, SingleLockUsage> e : st.entrySet()) {
				instances.add(e.getKey());			
			}
		}
	}
	
	public JSONArray toJSON() {
		JSONArray jsonArray = new JSONArray();
		for (Violation a : violations) {
			jsonArray.add(a.toString());
		}
		return jsonArray;
	}
		
	
	public boolean isEmpty() {
		return violations.isEmpty();
	}
	
	protected void trackResult(Violation result) {
		this.violations.add(result);
	}
	
}
