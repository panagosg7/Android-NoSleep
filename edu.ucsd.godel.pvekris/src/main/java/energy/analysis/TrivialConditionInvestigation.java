package energy.analysis;

import java.util.Iterator;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.FieldReference;

import energy.util.E;



/**
 * Need to run LockInvestigation before invoking this
 * @author pvekris
 *
 */

public class TrivialConditionInvestigation {
	
	private ApplicationCallGraph cg;
	private ComponentManager cm;
	private ClassHierarchy ch; 
	
	
	public TrivialConditionInvestigation(ClassHierarchy classHierarchy) {
		this.ch = classHierarchy;
	}
	
		

	

	public void traceObviousConditions() {
		
		for (CGNode n : cg) {
			
			IR ir = n.getIR();
			DefUse du = null;
			
			/* Null for JNI methods */
			if (ir == null) {
				E.log(2, "Skipping: " + n.getMethod().toString());
				continue;				
			}
			
			for (SSAInstruction instr : ir.getInstructions()) {
								
				
				if (instr instanceof SSAPiInstruction) {
					
					SSAPiInstruction piInstr = (SSAPiInstruction) instr;							
					
					int c0 = piInstr.getUse(0);
					int c1 = piInstr.getUse(1);
					
					E.log(2, n.getMethod().getSignature().toString());
					
					E.log(2,piInstr.toString());
					  
					if (du == null) {
						du = new DefUse(ir);
					}
					
//					Collection<LockType> lockType = null;
																	
//					lockType = checkForLock(ir, du, condInstr.getUse(1));
											
					//The WakeLock is being assigned to the Def part of the instruction
					int lockNum = piInstr.getDef();
					
					//The use should be an instruction right next to the one creating the lock 
					Iterator<SSAInstruction> uses = du.getUses(lockNum);						
					
					SSAInstruction useInstr = uses.next();				
				
					if (useInstr instanceof SSAPutInstruction) {
					/*	This is the case that the lock is a field, so we expect a put 
						instruction right after the creation of the wakelock.	*/ 
						
						SSAPutInstruction put = (SSAPutInstruction) useInstr;						
						FieldReference field = put.getDeclaredField();
					}
					else {
						
						
					}
					
									
				}
			}			
		}
		
		
	}
	
	
	private boolean probeWakelock (IR ir, DefUse du, int val) {

		SSAInstruction def = du.getDef(val);
		
		if (def instanceof SSAPutInstruction) {
			SSAPutInstruction put = (SSAPutInstruction) def;
			
			FieldReference field = put.getDeclaredField();
			
			//Test to see if we have encountered this wakelock in the 
			//investigation phase
			if (cg.getLockFieldInfo().isWakeLock(field)) {
				
				return true;
				
			};
			
			
		}
		return false;
		
	}
	
	
	
	public void setAppCallGraph(ApplicationCallGraph cg){
		this.cg = cg;
	} 
	
	
}
