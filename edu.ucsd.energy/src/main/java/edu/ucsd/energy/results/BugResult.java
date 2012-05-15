package edu.ucsd.energy.results;

import net.sf.json.JSONObject;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class BugResult {
	
	private ResultType resultType;
	private String message; 
	
	public ResultType getResultType() {
		return resultType;
	}
	
	public String getMessage() {
		return message;
	}
	
	public BugResult(ResultType rt, String msg) {
		message = msg;
		resultType = rt;
	}
	
	public String toString() {
		return (resultType.name() + " " + message);
	}

	public Object toJSON() {
		JSONObject obj = new JSONObject();
		obj.put(resultType.toString(), message);
		return obj;
	}
}
