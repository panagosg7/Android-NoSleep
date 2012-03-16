package energy.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.util.debug.Assertions;

import energy.components.Component;
import energy.components.ComponentManager;
import energy.components.RunnableThread;
import energy.util.E;
import energy.util.SSAProgramPoint;

public class ThreadCreation {
	
	private ApplicationCallGraph cg;
	private ComponentManager cm; 
	
	private boolean computedThreadInvocations = false;
	
	public ThreadCreation(ComponentManager componentManager) {
		this.cg = componentManager.getCG();
		this.cm = componentManager;
	}
	
	HashSet<SSANewInstruction> iSet = new HashSet<SSANewInstruction>();
	
	
	/**
	 * This will map the point where a thread is spawned to the threads class reference 
	 */
	private HashMap<SSAProgramPoint,Component> siteToClass = new HashMap<SSAProgramPoint, Component>();
	
	/**
	 * Invoke this to fill up the ProgPoint to thread mapping
	 */
	private void gatherThreadInvocations() {
		computedThreadInvocations = true;		
		
		for (CGNode n : cg) {
			iSet.clear();
			for (Iterator<SSAInstruction> it = n.getIR().iterateAllInstructions(); it.hasNext();) {
				SSAInstruction instr = it.next();
				
				if (instr instanceof SSANewInstruction) {
					SSANewInstruction newi = (SSANewInstruction) instr;
					if (newi.toString().contains("Ljava/lang/Thread")) {						
					/* This is a thread creation - keep this for later */
						iSet.add(newi);						
					}
				}
			}
			/* After the search is done, do the defuse only if there are interesting results */
			if (iSet.size() > 0) {
				IR ir = n.getIR();
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
								//E.log(1, "Initializer: " + n.getMethod().getSignature().toString() + " - " + inv.toString() );
								//XXX: No harder tests necessary... (?)							
							
								int use = inv.getUse(1);
								SSAInstruction def = du.getDef(use);
								
								if (def instanceof SSANewInstruction) {
									SSANewInstruction inv1 = (SSANewInstruction) def;
									Assertions.productionAssertion(targetComponent == null);	//this should be done only once																										
									targetComponent = cm.getComponent(inv1.getConcreteType());
									Assertions.productionAssertion(targetComponent instanceof RunnableThread);
									//E.log(1, " >>> param: " + dclass.toString());									
								
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
						E.log(1, "Adding: " + pp + " --> " + targetComponent);
						siteToClass.put(pp, targetComponent);
					}
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
		if (!computedThreadInvocations) {
			gatherThreadInvocations();
		}
		
		return siteToClass;
	}
	
	
	
}
