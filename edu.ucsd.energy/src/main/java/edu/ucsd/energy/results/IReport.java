package edu.ucsd.energy.results;

import net.sf.json.JSONObject;

public interface IReport {

	public String getTag();
	
	public JSONObject toJSON();
	
}
