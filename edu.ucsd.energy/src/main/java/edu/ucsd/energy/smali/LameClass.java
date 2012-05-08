package edu.ucsd.energy.smali;
//Author: John C. McCullough
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LameClass implements Iterable<LameMethod> {
	public String mSignature;
	public List<LameMethod> mMethods;
	
	public LameClass(String sig) {
		mSignature = sig;
		mMethods = new ArrayList<LameMethod>();
	}
	
	public void addMethod(LameMethod method) {
		mMethods.add(method);
	}

	public Iterator<LameMethod> iterator() {
		return mMethods.iterator();
	}
	
	public final String getSignature() { return mSignature; }
	
	public LameMethod getMethod(String signature) {
		for (LameMethod meth: mMethods) {
			if (meth.getSignature().equals(signature)) return meth;
		}
		for (LameMethod meth: mMethods) {
			System.out.println(meth.getSignature());
		}
		return null;
	}
}
