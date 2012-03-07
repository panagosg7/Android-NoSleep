/**
 * 
 */
package edu.ucsd.salud.mcmutton.smali;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LameWorld {
	public List<LameClass> mClasses;
	
	List<String> mReverse = null;
	Map<String, Integer> mTargets = null;
	List<List<Integer>> mAdjacency = null;
	
	public LameWorld() {
		mClasses = new ArrayList<LameClass>();
	}
	
	public void addClass(LameClass cls) {
		mClasses.add(cls);
	}
	
	public List<String> getMethodList() {
		computeAdjacency();
		return mReverse;
	}
	
	public LameMethod findMethod(String methodName) {
		if (methodName == null) {
			System.err.println("null method name");
			return null;
		}
		int dotPos = methodName.indexOf('.');
		String className = methodName.substring(0, dotPos);
		String funcName = methodName.substring(dotPos + 1);
		
		LameClass cls = getClass(className);
		if (cls == null) {
			System.out.println("class lookup failed: " + className);
			return null;
		}
		
		LameMethod meth = cls.getMethod(funcName);
		
		return meth;
	}
	
	public LameClass getClass(String signature) {
		for (LameClass cls: mClasses) {
			if (cls.getSignature().equals(signature)) return cls;
		}
		return null;
	}
	
	public void computeAdjacency() {
		if (mReverse != null) return;
		
		mReverse = new ArrayList<String>();
		mTargets = new HashMap<String, Integer>();
		mAdjacency = new ArrayList<List<Integer>>();
		
		for (LameClass cls: mClasses) {
			String canonicalBase = "L" + cls.mSignature + ";->";
			for (LameMethod method: cls) {
				String canonicalName = canonicalBase + method.mSignature;
				
				if (!mTargets.containsKey(canonicalName)) {
					mReverse.add(canonicalName);
					mTargets.put(canonicalName, new Integer(mTargets.size()));
					mAdjacency.add(new ArrayList<Integer>());
				}
				
				Integer idx = mTargets.get(canonicalName);
				for (LameInstruction inst: method) {
					if (inst instanceof LameInvokeInstruction) {
						LameInvokeInstruction inv = (LameInvokeInstruction)inst;
						
						String target = inv.mTarget;
						
						if (!mTargets.containsKey(target)) {
							mReverse.add(target);
							mTargets.put(target, new Integer(mTargets.size()));
							mAdjacency.add(new ArrayList<Integer>());
						}
						
						Integer targetIdx = mTargets.get(target);
						
						if (!mAdjacency.get(idx).contains(targetIdx)) {
							mAdjacency.get(idx).add(targetIdx);
						}
					}
				}
			}
		}
	}
	
	public Map<String, Set<String>> invertAdjacency() {
		Map<String, Set<String>> results = new HashMap<String, Set<String>>();
		
		computeAdjacency();
		
		for (int i = 0; i < mAdjacency.size(); ++i) {
			String source = mReverse.get(i);
			
			for (Integer j: mAdjacency.get(i)) {
				String dest = mReverse.get(j);
				
				if (!results.containsKey(dest)) {
					results.put(dest, new HashSet<String>());
				}
				results.get(dest).add(source);
			}
		}
		
		return results;
	}
	
	public List<List<Integer>> computeReachability() {
		computeAdjacency();
		
		List<List<Integer>> results = new ArrayList<List<Integer>>();
		
		final int sz = mAdjacency.size();
		System.out.println("sz: " + sz);
		// Probably want a sparse matrix implementation...but oh well
		int [][] distanceMatrix = new int[sz][sz];
		
		for (int i = 0; i < sz; ++i) {
			for (int j = 0; j < sz; ++j) {
				distanceMatrix[i][j] = Integer.MAX_VALUE / 2; // Avoid overflow?
			}
		}
		
		for (int i = 0; i < sz; ++i) {
			for (Integer adj: mAdjacency.get(i)) {
				distanceMatrix[i][adj] = 1;
			}
		}
		
		for (int k = 0; k < sz; ++k) {
			for (int i = 0; i < sz; ++i) {
				for (int j = 0; j < sz; ++j) {
					final int kLen = distanceMatrix[i][k] + distanceMatrix[k][j];
					if (kLen < distanceMatrix[i][j]) {
						distanceMatrix[i][j] = kLen;
					}
				}
			}
			
			if (k % 100 == 0) {
				System.err.println("k=" + k);
			}
		}
		
		for (int i = 0; i < mReverse.size(); ++i) {
			List<Integer> adj = new ArrayList<Integer>();
			results.add(adj);
			
			for (int j = 0; j < sz; ++j) {
				if (distanceMatrix[i][j] < Integer.MAX_VALUE / 2) adj.add(j);
			}
		}
		
		return results;
	}
}
