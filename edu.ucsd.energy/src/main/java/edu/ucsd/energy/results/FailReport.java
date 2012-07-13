package edu.ucsd.energy.results;


import net.sf.json.JSONObject;

public class FailReport implements IReport {

	IType message;
	
	public FailReport(IType didNotProcess) {
		message = didNotProcess;
	}
	
	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		obj.put("status", message.toString());
		return obj;
	}

	public String getTag() {
		return "Fail Report";
	}

	public String toShortDescription() {
		return "";
	}

	public void dump() {
		// TODO Auto-generated method stub
		
	}

	
	
}
