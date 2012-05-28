package edu.ucsd.energy.results;


import net.sf.json.JSONObject;
import edu.ucsd.energy.results.ProcessResults.ResultType;

public class FailReport implements IReport {

	ResultType message;
	
	public FailReport(ResultType didNotProcess) {
		message = didNotProcess;
	}
	
	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		obj.put("status", message.toString());
		return obj;
	}

	public String getTag() {
		return "Fail";
	}

}
