package edu.ucsd.energy.results;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.ucsd.energy.util.Log;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

public class CompoundReport implements IReport {

	private List<IReport> list;

	public CompoundReport() {
		list = new ArrayList<IReport>();
	}
	
	public Iterator<IReport> iterate() {
		return list.iterator();
	}
	
	public void register(IReport r) {
		if (r instanceof CompoundReport) {
			CompoundReport cr = (CompoundReport) r;
			for (Iterator<IReport> it = cr.iterate(); it.hasNext(); )
				list.add(it.next());	
		}
		else 
			list.add(r);
	}
	
	
	public void appendTo(JSONObject o) {
		if (o == null) 
			o = new JSONObject();
		for (IReport r : list) {
			o.put(r.getTag(),	r.toJSON());
		}
	}

	public JSONObject toJSON() throws JSONException {
		JSONObject result = new JSONObject();
		for (IReport r : list) {
			result.put(r.getTag(), r.toJSON());
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
