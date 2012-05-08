//Author: John C. McCullough
package edu.ucsd.energy.smali;

public class LameInvokeInstruction extends LameInstruction {
	public String mTarget;
	
	public LameInvokeInstruction(String target) {
		mTarget = target;
	}
}
