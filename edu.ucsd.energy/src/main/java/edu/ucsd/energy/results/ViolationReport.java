package edu.ucsd.energy.results;

import java.util.Set;

import com.ibm.wala.util.collections.HashSetMultiMap;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.util.Log;

public class ViolationReport implements IReport {

	private HashSetMultiMap<Component, Violation> violations;

	public String getTag() {
		return "Violation Report";
	}

	public ViolationReport() {
		violations = new HashSetMultiMap<Component, Violation>();
	}


	public String toShortDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	public void dump() {
		System.out.println();
		if (!hasMessages()) {
			Log.boldGreen();
			System.out.println("NO VIOLATIONS");
			Log.resetColor();
			return;
		}
		else {
			Log.boldRed();
			System.out.println("POLICY VIOLATIONS");
			Log.resetColor();
			for (Component r : violations.keySet()) {
				Set<Violation> set = violations.get(r);
				for (Violation v : set) {

					v.logColor();
					Log.println(r.toString() + " :: " + v.toString());
					Log.resetColor();
				}
			}
		}
	}

	private boolean hasMessages() {
		return (!violations.isEmpty());
	}

	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		for (Component ctx : violations.keySet()) {
			Set<Violation> set = violations.get(ctx);
			JSONArray arr = new JSONArray();
			for (Violation v : set) {
				arr.add(v.toJSON());
			}
			if (!arr.isEmpty()) {
				obj.put(ctx.toString(), arr);
			}
		}
		return obj;
	}

	public void insertViolations(Component component, Set<Violation> sViolations) {
		violations.putAll(component, sViolations);	
	}




}
