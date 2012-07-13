package edu.ucsd.energy.results;

import java.util.ArrayList;
import java.util.List;

import edu.ucsd.energy.util.Log;

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
		return "Compound Report";
	}

	public String toShortDescription() {
		StringBuffer sb = new StringBuffer();
		for(IReport r : list) {
			sb.append(r.toShortDescription());
		}
		return sb.toString();
	}

	public void dump() {
		for (IReport i : list ) {
			Log.println("Dumping: " + i.getTag());
			i.dump();			
		}
	}

}
