package edu.ucsd.energy.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.internal.resources.AliasManager.AddToCollectionDoit;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

import edu.ucsd.energy.components.Component;
import edu.ucsd.energy.components.RunnableThread;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SSAProgramPoint;

public class RunnableInvestigation {
	
	private AppCallGraph cg;
	private ComponentManager cm; 
	
	public RunnableInvestigation(ComponentManager componentManager) {
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
	 * Invoke this to fill up the ProgPoint to thread mapping
	 * @param <U>
	 */
	private <U> void prepare() {
		siteToClass = new HashMap<SSAProgramPoint, Component>();
		iSet = new HashSet<Pair<SSANewInstruction, Component>>();
		for (CGNode n : cg) {
			iSet.clear();
			IR ir = n.getIR();
			/* Null for JNI methods */
			if (ir == null) {
				E.log(2, "Skipping: " + n.getMethod().toString());
				continue;				
			}			
			for (Iterator<NewSiteReference> it = ir.iterateNewSites();  
					it.hasNext();) {				
				SSANewInstruction newi = ir.getNew(it.next());
				TypeReference concreteType = newi.getConcreteType();
				IClassHierarchy ch = cg.getClassHierarchy();
				IClass thisClass = ch.lookupClass(concreteType);
				if (thisClass != null) {
					for (IClass iclass : thisClass.getAllImplementedInterfaces()) {
						if (iclass.toString().contains("Ljava/lang/Runnable>")) {
							//DEBUG
							if (concreteType.toString().contains("Lcom/kakao/talk/receiver/b")) {
								E.log(1, "Thread: " + iclass.toString());
							}							
							//DEBUG
							Component targetComponent = cm.getComponent(concreteType);
							if (targetComponent != null) {
								Assertions.productionAssertion(targetComponent instanceof RunnableThread, 
										"A Thread should be called here.");
								Pair<SSANewInstruction, Component> p = Pair.make(newi, targetComponent);
								iSet.add(p);
								break;
							}
						}
					}
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
	
	public void printThreadPairs() {
		for (Entry<SSAProgramPoint, Component> e : siteToClass.entrySet()) {
			E.log(1, e.getKey().toString() + " -- > " + e.getValue().toString());
		}
	}
	

	public HashMap<SSAProgramPoint,Component> getThreadInvocations() {				
		if (siteToClass == null) {
			prepare();
		}		
		return siteToClass;
	}
			
}
