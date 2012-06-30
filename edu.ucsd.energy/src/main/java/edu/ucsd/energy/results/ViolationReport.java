package edu.ucsd.energy.results;

import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import com.ibm.wala.util.collections.HashSetMultiMap;

import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.util.E;

public class ViolationReport implements IReport {
	
	private HashSetMultiMap<Context, Violation> violations; 
	
	public ViolationReport() {
		violations = new HashSetMultiMap<Context, Violation>();
	}

	public boolean hasViolations() {
		return (!violations.isEmpty());
	}

	public HashSetMultiMap<Context, Violation> getViolations() {
		return violations;
	}

	
	public void mergeReport(ViolationReport violation) {
		if (violation == null) return;
		for (Context c : violation.getViolations().keySet()) {
			Set<Violation> vs = violation.getViolations().get(c);
			insertViolations(c, vs);
		}
	}
	
	public void insertViolations(Context c, Set<Violation> violation) {
		violations.putAll(c, violation);
	}
	
	public void insertViolation(Context c, Violation violation) {
		violations.put(c, violation);
	}
	
	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		for (Context r : violations.keySet()) {
			Set<Violation> set = violations.get(r);
			JSONArray arr = new JSONArray();
			for (Violation v : set) {
				arr.add(v.toJSON());
			}
			obj.put(r.toString(), arr);
		}
		return obj;
	}
	
	public void dump() {
		if (!hasViolations()) {
			E.boldGreen();
			System.out.println("NO VIOLATIONS");
      E.resetColor();
      return;
		}
		else {
			E.boldRed();
			System.out.println("VIOLATES POLICIES:");
      E.resetColor();
		}
		for (Context r : violations.keySet()) {
			Set<Violation> set = violations.get(r);
			for (Violation v : set) {
				int level = v.getResultType().getLevel();
				switch (level) {
				case 0: break;
				case 1: System.out.print("\033[32m");break;
				case 2: System.out.print("\033[33m");break;
				case 3: System.out.print("\033[31m");break;
				default: System.out.print("\033[31m");
				}
				System.out.println(r.toString() + " :: " + v.toString());
	      System.out.print("\033[0m");
			}
		}
	}

	public String getTag() {
		return "BugReport";
	}

	public String toShortDescription() {
		// TODO Auto-generated method stub
		return null;
	}

}
