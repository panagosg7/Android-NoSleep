package edu.ucsd.energy.component;

import java.util.HashMap;
import java.util.Map;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.Selector;

/**
 * CallBack representation 
 */

public class CallBack {

	private static Map<CGNode, CallBack> callbacks = new HashMap<CGNode, CallBack>();

	private CGNode node;

	public String getSignature() {
		return node.getMethod().getSignature();
	}

	public CGNode getNode () {
		return node;
	}	  

	public static CallBack findOrCreateCallBack(CGNode n) {
		CallBack callBack = callbacks.get(n);
		if (callBack != null) {
			return callBack;
		}
		else {
			callBack = new CallBack(n);
			callbacks.put(n, callBack);
			return callBack;
		}
	}

	public CallBack(CGNode node) {
		this.node = node;
	}

	public String getName() {
		return node.getMethod().getName().toString();
	}

	public Selector getSelector() {
		return node.getMethod().getSelector();
	}
	
	public boolean equals(Object o) {
		if (o instanceof CallBack) {
			return this.node.equals(((CallBack) o).getNode());
		}
		return false;
	}

	public int hashCode() {
		return node.hashCode();
	}

	public String toString() {
		return ("CallBack: " + node.getMethod().getSignature().toString());
	}
}