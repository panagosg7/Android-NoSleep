package edu.ucsd.energy.results;

import java.util.HashSet;
import java.util.Set;

import net.sf.json.JSONObject;

public class WarningReport implements IReport {

	Set<Warning> sWarning;
	
	WarningReport() {
		sWarning = new HashSet<Warning>();
	}
	
	public String getTag() {
		return "Warning Report";
	}

	public JSONObject toJSON() {
		return null;
	}

	
	public String toShortDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	public void dump() {
		// TODO Auto-generated method stub
		
	}
	
	

	public void insertElement(Warning warning) {
		// TODO Auto-generated method stub
		
	}

}
