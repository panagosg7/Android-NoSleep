package edu.ucsd.energy.results;

import java.util.Map.Entry;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.managers.AbstractDUManager;
import edu.ucsd.energy.results.ComponentSummary.ContextState;

public class ManagerReport<V extends AbstractDUManager<?>> implements IReport {
	
	private V manager;
	
	public ManagerReport(V man) {
		this.manager = man;		
	}

	public String getTag() {
		return manager.getTag();
	};
	
	public JSONObject toJSON() throws JSONException {
		return manager.toJSON();
	}

	public String toShortDescription() {
		return "";
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
