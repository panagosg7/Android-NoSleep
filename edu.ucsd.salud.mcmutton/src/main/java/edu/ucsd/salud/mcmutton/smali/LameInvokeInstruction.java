/**
 * 
 */
package edu.ucsd.salud.mcmutton.smali;

public class LameInvokeInstruction extends LameInstruction {
	public String mTarget;
	
	public LameInvokeInstruction(String target) {
		mTarget = target;
	}
}
