package edu.ucsd.energy.results;

import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

public class CompoundReport implements IReport {

	private List<IReport> list;

	public CompoundReport() {
		list = new ArrayList<IReport>();
	}
	
	public void register(IReport r) {
		list.add(r);
	}
	
	public JSONObject toJSON() throws JSONException {
		JSONObject result = new JSONObject();
		for (IReport r : list) {
			result.put(
					r.getTag(), 
					r.toJSON());	
		}
		return result;
	}

	public String getTag() {
		return "Compound";
	}

}
