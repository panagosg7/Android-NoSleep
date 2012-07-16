package edu.ucsd.energy.results;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.json.JSONObject;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.ComponentSummary.ContextState;

public class LockUsageReport implements IReport {

	public LockUsageReport() {
		mComponent = new HashMap<Component, ContextState>();
	}
	
	private Map<Component, ContextState> mComponent;
	
	public String getTag() {
		return "Lock Usage Report";
	}

	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		if (mComponent != null) { 
			for (Entry<Component, ContextState> e : mComponent.entrySet()) {
				obj.put(e.getKey().toString(), e.getValue().toJSON());
			}
		}
		return obj;		
	}
	
	public String toShortDescription() {
		StringBuffer sb = new StringBuffer();
		for (Entry<Component, ContextState> e : mComponent.entrySet()) {
			sb.append(e.getKey().toString() + " :: " + e.getValue().toString() + "\n");
		}
		return sb.toString().replaceAll("\n{2,}", "\n");		//ugly fix...
	}

	//TODO: fix this
	public void insert(Component component, ComponentSummary cSummary) {
		//ContextState callBackUsage = cSummary.getCallBackStates();
		//mComponent.put(component, callBackUsage);
	}

	public void dump() {
		// TODO Auto-generated method stub
		
	}

	public void appendTo(JSONObject o) {
		if (o == null) 
			o = new JSONObject();
		o.put(getTag(), toJSON());
	}
	
	
}
