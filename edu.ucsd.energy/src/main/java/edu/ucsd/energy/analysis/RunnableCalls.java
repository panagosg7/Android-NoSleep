package edu.ucsd.energy.analysis;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.components.Component;
import edu.ucsd.energy.components.RunnableThread;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SSAProgramPoint;

public class RunnableCalls {
	
	private AppCallGraph cg;
	private ComponentManager cm; 
	
	public RunnableCalls(ComponentManager componentManager) {
		this.cg = componentManager.getCG();
		this.cm = componentManager;
	}
	
	/**
	 * These are the possible names of methods that post to handler queues
	 * TODO: put the whole signature
	 */
	private static final Set<String> addToMessageQueue;
    static {
		addToMessageQueue = new HashSet<String>();
    	addToMessageQueue.add("post");
    	addToMessageQueue.add("postAtFrontOfQueue"); 
    	addToMessageQueue.add("postAtTime");
    	addToMessageQueue.add("postDelayed");
    }
	
	/**
	 * This will map the point where a thread is spawned to the threads class reference 
	 */
	private HashMap<SSAProgramPoint,Component> siteToClass = null;
	private Collection<Pair<SSANewInstruction, Component>> iSet ;
	
	/**
	 * Invoke this to fill up the ProgPoint to runnable mapping
	 */
	private void prepare() {
		siteToClass = new HashMap<SSAProgramPoint, Component>();
		iSet = new HashSet<Pair<SSANewInstruction, Component>>();
		for (CGNode n : cg) {
			iSet.clear();
			IR ir = n.getIR();
			/* Null for JNI methods */
			if (ir == null) {
				continue;				
			}			
			for (Iterator<NewSiteReference> it = ir.iterateNewSites(); it.hasNext(); ) {
				SSANewInstruction newi = ir.getNew(it.next());
				TypeReference concreteType = newi.getConcreteType();
				Component targetComponent = cm.getComponent(concreteType);
				if (targetComponent instanceof RunnableThread) {
					String file = cm.getCG().getAppClassHierarchy().getAppJar();
					//System.out.println(n.getMethod().getSignature().toString());
					//System.out.println(targetComponent.toString());
					//Assertions.productionAssertion(targetComponent instanceof RunnableThread, 
					//		file + " : A Runnable should be called here.");
					Pair<SSANewInstruction, Component> p = Pair.make(newi, targetComponent);
					iSet.add(p);
				}
			}
			/* After the search is done, do the defuse only if there are interesting results */
			if (iSet.size() > 0) {				
				DefUse du = new DefUse(ir);
				for (Pair<SSANewInstruction, Component> i : iSet) {
					//E.log(1, n.getMethod().getSignature().toString() + " :: " + i.toString());					
					SSAProgramPoint pp = null;
					SSANewInstruction newi = i.fst;
					Component targetComponent = i.snd;
					for(Iterator<SSAInstruction> uses = du.getUses(newi.getDef()); uses.hasNext(); ) {
						SSAInstruction user = uses.next();
						if (user instanceof SSAInvokeInstruction) {
							SSAInvokeInstruction inv = (SSAInvokeInstruction) user;
							//Get the thread start()
							String startName = inv.getDeclaredTarget().getName().toString();
							if (startName.equals("start")) {
								pp = new SSAProgramPoint(n,inv);
								E.log(2, "Adding: " + pp + " --> " + targetComponent);
								siteToClass.put(pp, targetComponent);
							}
							//Check for messages being posted to handlers
							if (addToMessageQueue.contains(startName)) {
								pp = new SSAProgramPoint(n,inv);
								siteToClass.put(pp, targetComponent);
							}
						}
					} //for uses
				}
			}
		}		
	}

	public HashMap<SSAProgramPoint,Component> getThreadInvocations() {				
		if (siteToClass == null) {
			prepare();
		}		
		return siteToClass;
	}
			
}
