package edu.ucsd.energy.results;

import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import com.ibm.wala.util.collections.HashSetMultiMap;

import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.util.Log;

public class ViolationReport implements IReport {

	private HashSetMultiMap<IViolationKey, Violation> violations;

	private boolean hasViolations = false; 

	public ViolationReport() {
		violations = new HashSetMultiMap<IViolationKey, Violation>();
	}

	public boolean hasViolations() {
		return hasViolations;
	}

	public HashSetMultiMap<IViolationKey, Violation> getViolations() {
		return violations;
	}


	public void mergeReport(ViolationReport violation) {
		if (violation == null) return;
		for (IViolationKey c : violation.getViolations().keySet()) {
			Set<Violation> vs = violation.getViolations().get(c);
			insertViolations(c, vs);
		}
	}

	public void insertViolations(IViolationKey c, Set<Violation> vs) {
		violations.putAll(c, vs);
		hasViolations = (vs.isEmpty())?hasViolations:true;
	}

	public void insertViolation(IViolationKey c, Violation v) {
		violations.put(c, v);
		hasViolations = true;		//we definitely have a violation here
	}


	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		for (IViolationKey ctx : violations.keySet()) {
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

	public void dump() {
		System.out.println();
		if (!hasViolations()) {
			Log.boldGreen();
			System.out.println("NO VIOLATIONS");
			Log.resetColor();
			return;
		}
		else {
			Log.boldRed();
			System.out.println("POLICY VIOLATIONS");
			Log.resetColor();
		}
		for (IViolationKey r : violations.keySet()) {
			Set<Violation> set = violations.get(r);
			for (Violation v : set) {
				int level = v.getResultType().getLevel();
				switch (level) {
					case 0: break;
					case 1: Log.green();break;
					case 2: Log.yellow();break;
					default: Log.red();
				}
				Log.println(r.toString() + " :: " + v.toString());
				Log.resetColor();
			}
		}
	}

	public String getTag() {
		return "Violation ";
	}

	public String toShortDescription() {
		// TODO Auto-generated method stub
		return null;
	}

}
