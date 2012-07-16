package edu.ucsd.energy.results;

import net.sf.json.JSONObject;


public interface IReport {

	public String getTag();
	
	public Object toJSON();
	
	//Append the results in this report to a given JSONObject
	public void appendTo(JSONObject o);

	public String toShortDescription();
	
	public void dump();
	
}
