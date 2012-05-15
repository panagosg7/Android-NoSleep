package edu.ucsd.energy.results;


import net.sf.json.JSONObject;
import edu.ucsd.energy.analysis.WakeLockManager;

public class WakeLockReport implements IReport {

	WakeLockManager manager;
	
	public WakeLockReport(WakeLockManager man) {
		manager = man;
	}
	
	public JSONObject toJSON() {
		return manager.getJSON();
	}

}
