package edu.ucsd.energy.results;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;



public interface IReport {
	
	public JSONObject toJSON() throws JSONException;
	
	public String toString();
	
}
