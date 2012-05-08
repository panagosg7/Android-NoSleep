package edu.ucsd.energy.analysis;

import edu.ucsd.energy.analysis.ProcessResults.ResultType;

public class Result {
	
	private ResultType resultType;
	private String message; 
	
	public ResultType getResultType() {
		return resultType;
	}
	
	public String getMessage() {
		return message;
	}
	
	public Result(ResultType rt, String msg) {
		message = msg;
		resultType = rt;
	}
	
	public String toString() {
		return (resultType.name() + " " + message);
	}
}
