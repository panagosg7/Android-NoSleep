package edu.ucsd.energy.results;

import net.sf.json.JSONObject;
import edu.ucsd.energy.util.Log;

public class Violation extends Message {
	
	public enum ViolationType implements IType {
		//Activity
		ACTIVITY_ONPAUSE(2),
		ACTIVITY_ONSTOP(3),
		//Service
		SERVICE_ONSTART(2),
		SERVICE_ONDESTORY(3),
		SERVICE_ONUNBIND(2),
		INTENTSERVICE_ONHANDLEINTENT(2),
		//Runnable
		RUNNABLE_RUN(2),
		//Callable
		CALLABLE_CALL(2),
		//BoradcaseReceiver
		BROADCAST_RECEIVER_ONRECEIVE(2),		
		//Application
		APPLICATION_TERMINATE(2),
		//AsyncTask
		ASYNC_TASK_ONPOSTEXECUTE(2),
		//Unresolved component
		UNRESOLVED_CALLBACK_LOCKED(2);
		
		int level;		//the level of seriousness of the condition
		
		private ViolationType(int a) {
			level = a;
		}
	
		public int getLevel() {
			return level;			
		}
	}

	private ViolationType resultType;
	
	private String message = ""; 
	
	public ViolationType getResultType() {
		return resultType;
	}
	
	public String getMessage() {
		return message;
	}
	
	public Violation(ViolationType rt, String msg) {
		message = msg;
		resultType = rt;
	}
	
	public Violation(ViolationType rt) {
		resultType = rt;
	}

	public String toString() {
		return (resultType.name() + " " + message);
	}

	public JSONObject toJSON() {
		JSONObject obj = new JSONObject();
		obj.put(resultType.toString(), message);
		return obj;
	}

	public void logColor() {
		int level = getResultType().getLevel();
		switch (level) {
			case 0: break;
			case 1: Log.green();break;
			case 2: Log.yellow();break;
			default: Log.red();
		}
	}
	
}
