package edu.ucsd.energy.results;

import java.util.HashMap;
import java.util.HashSet;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.policy.IPolicy;

public class ApkBugReport implements IReport {
	
	private HashMap<Context, IPolicy> map; 
	
	private HashSet<BugResult> set;

	public ApkBugReport() {
		map = new HashMap<Context, IPolicy>();
		set = new HashSet<BugResult>();		
	}
	
	public void insertFact(BugResult r) {
		set.add(r);
	}
	
	public void insertFact(Context c, IPolicy policy) {
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
		for (IPolicy e : map.values()) {
			JSONArray json = e.toJSON();
			for(int i = 0; i < json.size(); i++) {
				jsonArray.add(json.get(i));
			}
		}
		obj.put("components", jsonArray);
		return obj;
	}
	
	public String toString() {
		try {
			return toJSON().toString(2);
		} catch (JSONException e) {
			return e.toString();
		}
	}

	public String getTag() {
		return "BugReport";
	}

}
