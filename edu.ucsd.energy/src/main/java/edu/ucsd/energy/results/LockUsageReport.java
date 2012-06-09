package edu.ucsd.energy.results;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.json.JSONObject;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.results.ProcessResults.ComponentState;

public class LockUsageReport implements IReport {

	public LockUsageReport() {
		mComponent = new HashMap<Context, ProcessResults.ComponentState>();
	}
	
	private Map<Context, ComponentState> mComponent;
	
	public String getTag() {
		return "Lock Usage";
	}

	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		if (mComponent != null) { 
			for (Entry<Context, ComponentState> e : mComponent.entrySet()) {
				obj.put(e.getKey().toString(), e.getValue().toJSON());
			}
		}
		return obj;		
	}
	
	public String toShortDescription() {
		StringBuffer sb = new StringBuffer();
		for (Entry<Context, ComponentState> e : mComponent.entrySet()) {
			sb.append(e.getKey().toString() + " :: " + e.getValue().toString() + "\n");
		}
		return sb.toString().replaceAll("\n{2,}", "\n");		//ugly fix...
	}

	public void insert(ContextSummary cSummary) {
		Context c = cSummary.getContext();
		ComponentState callBackUsage = cSummary.getCallBackUsage();
		mComponent.put(c, callBackUsage);
	}
	
	
}
