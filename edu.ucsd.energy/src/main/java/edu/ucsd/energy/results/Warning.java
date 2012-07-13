package edu.ucsd.energy.results;

import net.sf.json.JSONObject;

public class Warning extends Message {

	public enum WarningType implements IType {
		
		NO_WAKELOCK_CALLS(1),
		
		UNRESOLVED_ASYNC_CALLS(2),
		
		UNRESOLVED_WAKELOCK_CALLS(2),
		
		//Analysis results
		OPTIMIZATION_FAILURE(2),
		ANALYSIS_FAILURE(2),
		UNIMPLEMENTED_FAILURE(2), 
		 
		IOEXCEPTION_FAILURE(2);
		
		int level;		//the level of seriousness of the condition
		
		private WarningType(int a) {
			level = a;
		}
	
		public int getLevel() {
			return level;
		}
	}
	
	WarningType warning;
	
	public Warning(WarningType w) {
		warning = w;
	}

	public String toString() {
		return warning.toString();
	}
	

	public void logColor() {
		//Just use default color - do nothing
	}
	
	public JSONObject toJSON() {
		return JSONObject.fromObject(warning);
	}

}
