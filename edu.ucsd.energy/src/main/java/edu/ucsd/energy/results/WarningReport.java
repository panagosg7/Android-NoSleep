package edu.ucsd.energy.results;

import java.util.HashSet;
import java.util.Set;

import edu.ucsd.energy.util.Log;

import net.sf.json.JSONArray;

public class WarningReport implements IReport {

	Set<Warning> sWarning;
	
	WarningReport() {
		sWarning = new HashSet<Warning>();
	}
	
	public String getTag() {
		return "Warning Report";
	}

	public Object toJSON() {
		JSONArray arr = new JSONArray();
		for (Warning w : sWarning) {
			arr.add(w.toString());
		}
		return arr;
	}

	
	public String toShortDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	public void dump() {
		System.out.println();
		if (hasMessages()) {
			Log.boldYellow();
			System.out.println("WARNINGS");
			Log.resetColor();
		}
		else {
			Log.boldGreen();
			System.out.println("NO WARNINGS");
			Log.resetColor();
		}
		for (Warning w : sWarning) {
			System.out.println(w.toString());
		}
	}

	private boolean hasMessages() {
		return !sWarning.isEmpty();
	}

	public void insertElement(Warning warning) {
		sWarning.add(warning);
	}

}
