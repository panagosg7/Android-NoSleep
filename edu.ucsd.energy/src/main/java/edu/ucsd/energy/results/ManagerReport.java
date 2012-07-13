package edu.ucsd.energy.results;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import edu.ucsd.energy.managers.AbstractDUManager;

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
		
	};
	
}
