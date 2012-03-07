/**
 * 
 */
package edu.ucsd.salud.mcmutton.smali;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LameMethod implements Iterable<LameInstruction> {
	public String mSignature;
	public List<LameInstruction> mInstructions;
	public int mStartingLineNumber;
	public int mEndingLineNumber;
	
	public LameMethod(String sig, int startingLineNumber) {
		mSignature = sig;
		mInstructions = new ArrayList<LameInstruction>();
		mStartingLineNumber = startingLineNumber;
		mEndingLineNumber = -1;
	}
	
	public void addInstruction(LameInstruction instruction) {
		mInstructions.add(instruction);
	}
	
	public void setEndingLineNumber(int l) { mEndingLineNumber = l; }

	public Iterator<LameInstruction> iterator() {
		return mInstructions.iterator();
	}
	
	public final String getSignature() { return mSignature; }
}
