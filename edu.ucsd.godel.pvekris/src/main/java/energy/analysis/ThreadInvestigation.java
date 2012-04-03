package energy.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.util.debug.Assertions;

import energy.components.Component;
import energy.components.RunnableThread;
import energy.util.E;
import energy.util.SSAProgramPoint;

public class ThreadInvestigation {
	
	private ApplicationCallGraph cg;
	private ComponentManager cm; 
	
	public ThreadInvestigation(ComponentManager componentManager) {
		this.cg = componentManager.getCG();
		this.cm = componentManager;
	}
	
	HashSet<SSANewInstruction> iSet = new HashSet<SSANewInstruction>();
	
	
	/**
	 * This will map the point where a thread is spawned to the threads class reference 
	 */
	private HashMap<SSAProgramPoint,Component> siteToClass = null;
	
	/**
	 * Invoke this to fill up the ProgPoint to thread mapping
	 */
	private HashMap<SSAProgramPoint, Component> gatherThreadInvocations() {
		
		HashMap<SSAProgramPoint,Component> result = new HashMap<SSAProgramPoint, Component>();		
		
		for (CGNode n : cg) {
			iSet.clear();
			
			IR ir = n.getIR();
			
			/* Null for JNI methods */
			if (ir == null) {
				E.log(2, "Skipping: " + n.getMethod().toString());
				continue;				
			}
			
			for (Iterator<NewSiteReference> it = ir.iterateNewSites(); //ir.iterateAllInstructions(); 
					it.hasNext();) {
				
				SSANewInstruction newi = ir.getNew(it.next());			
								
				if (newi.getConcreteType().toString().equals("<Application,Ljava/lang/Thread>")) {					
					iSet.add(newi);						
				}
				
			}
			/* After the search is done, do the defuse only if there are interesting results */
			if (iSet.size() > 0) {
				
				//System.out.println(ir);
				DefUse du = new DefUse(ir);
				for (SSANewInstruction i : iSet) {
					//E.log(1, n.getMethod().getSignature().toString() + " :: " + i.toString());					
					SSAProgramPoint pp = null;
					Component targetComponent = null;					
					for(Iterator<SSAInstruction> uses = du.getUses(i.getDef()); uses.hasNext(); ) {
						SSAInstruction user = uses.next();
						if (user instanceof SSAInvokeInstruction) {
							SSAInvokeInstruction inv = (SSAInvokeInstruction) user;							
							//Get the thread <init>
							if (inv.getDeclaredTarget().getName().toString().equals("<init>")) {
								E.log(2, "Initializer: " + n.getMethod().getSignature().toString() + " - " + inv.toString() );
								//XXX: No harder tests necessary... (?)							
								try {
									int use = inv.getUse(1);
									SSAInstruction def = du.getDef(use);
									
									if (def instanceof SSANewInstruction) {
										SSANewInstruction inv1 = (SSANewInstruction) def;
										Assertions.productionAssertion(targetComponent == null);	//this should be done only once																										
										targetComponent = cm.getComponent(inv1.getConcreteType());
										if (targetComponent == null) {
											Assertions.productionAssertion(targetComponent instanceof RunnableThread, 
													"Cannot handle circular dependencies in thread calls.");
										}

									}
								} catch (Exception e) {
									E.log(1, ir.toString());
									e.printStackTrace();
									
								}
							}
							//Get the thread start()
							if (inv.getDeclaredTarget().getName().toString().equals("start")) {
								assert inv.getNumberOfParameters() == 0;
								assert pp == null;	//this should be done only once
								pp = new SSAProgramPoint(n,inv);
							}
						}
					} //for uses
					if ((pp!=null) && (targetComponent!= null)) {
						E.log(2, "Adding: " + pp + " --> " + targetComponent);
						result.put(pp, targetComponent);
					}
				}
			}
		}
		return result;
	}
	
	public void printThreadPairs() {
		for (Entry<SSAProgramPoint, Component> e : siteToClass.entrySet()) {
			E.log(1, e.getKey().toString() + " -- > " + e.getValue().toString());			
		}		
	}	
	

	public HashMap<SSAProgramPoint,Component> getThreadInvocations() {
				
		if (siteToClass == null) {
			siteToClass = gatherThreadInvocations();
		}
		
		return siteToClass;
	}
	
	
	
}
