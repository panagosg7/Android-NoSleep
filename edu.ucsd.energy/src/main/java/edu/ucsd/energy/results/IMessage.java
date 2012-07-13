package edu.ucsd.energy.results;

import net.sf.json.JSONObject;

public interface IMessage {

	public JSONObject toJSON();
	
	public void logColor();
	
}
