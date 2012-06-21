package edu.ucsd.energy.contexts;

import java.util.Arrays;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.Selector;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.managers.GlobalManager;

/**
 * TODO: This is not really a component
 * Using it just for now  
 */
public class RunnableThread extends Context {

	static Selector elements[] = { Interesting.ThreadRun };

	public RunnableThread(GlobalManager gm, CGNode root) {
		super(gm, root);
		sTypicalCallback.addAll(Arrays.asList(elements));
	}

	public String toString() {
		StringBuffer b = new StringBuffer();
		b.append("Runnable thread: ");
		b.append(getKlass().getName().toString());
		return b.toString();
	}  


	public Set<Selector> getEntryPoints() {
		return Interesting.runnableEntryMethods;
	}

	public Set<Selector> getExitPoints() {
		return Interesting.runnableExitMethods;
	}

}
