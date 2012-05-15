package edu.ucsd.energy.results;

import java.util.HashMap;
import java.util.HashSet;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import edu.ucsd.energy.analysis.WakeLockManager;
import edu.ucsd.energy.components.Component;
import edu.ucsd.energy.results.ProcessResults.ComponentPolicyCheck;

public class ApkBugReport implements IReport {
	
	private HashMap<Component,ComponentPolicyCheck> map; 
	
	private HashSet<BugResult> set;

	private WakeLockManager manager;
	
	public ApkBugReport() {
		map = new HashMap<Component, ProcessResults.ComponentPolicyCheck>();
		set = new HashSet<BugResult>();		
	}
	
	public void insertFact(BugResult r) {
		set.add(r);
	}
	
	public void insertFact(Component c, ComponentPolicyCheck policy) {
		map.put(c,policy);
	}
	
	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		//Get individual reports
		JSONArray jsonArray = new JSONArray();
		for (BugResult r : set) {
			jsonArray.add(r.toString());
		}
		obj.put("facts", jsonArray);
		//TODO: maybe arrange by type of bug ?
		//Get component reports
		jsonArray = new JSONArray();
		for (ComponentPolicyCheck e : map.values()) {
			JSONArray json = e.toJSON();
			for(int i = 0; i < json.size(); i++) {
				jsonArray.add(json.get(i));
			}
		}
		obj.put("components", jsonArray);
		if (manager != null) {
			obj.put("wakelocks", manager.getJSON());
		}
		return obj;
	}
	
	public String toString() {
		try {
			return toJSON().toString(2);
		} catch (JSONException e) {
			return e.toString();
		}
	}

	public void setWakeLockManager(WakeLockManager man) {
		manager = man;
	}

}
