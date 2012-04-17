package energy.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.FieldReference;

import energy.util.E;
import energy.util.SSAProgramPoint;
import energy.util.Util;

/**
 * Need to run LockInvestigation before invoking this
 * @author pvekris
 *
 */
public class SpecialConditions {
	
	private AppCallGraph cg;
//	private ComponentManager cm;
//	private ClassHierarchy ch; 
	

	public SpecialConditions(ComponentManager componentManager) {
		this.cg = componentManager.getCG();
//		this.cm = componentManager;
	}

	private HashMap<SSAProgramPoint,SpecialCondition> ppToSpecCondition = null;
	
	
	public class SpecialCondition {
		FieldReference field = null;
		protected ISSABasicBlock instrBlock;
		protected ISSABasicBlock trueSucc;
		protected ISSABasicBlock falseSucc;

		public ISSABasicBlock getTrueSucc() {
			return trueSucc;
		}
		
		public ISSABasicBlock getFalseSucc() {
			return falseSucc;
		}
		public FieldReference getField() {
			return field;
		}
		
		SpecialCondition (ISSABasicBlock bb, FieldReference f, ISSABasicBlock trueSucc, ISSABasicBlock falseSucc) {
			this.instrBlock = bb;
			this.field = f;		
			this.trueSucc = trueSucc;
			this.falseSucc = falseSucc;	
		}
		
		public String toString() {
			return ("[(" + instrBlock.getMethod().getName() + ", " + instrBlock.getNumber() + ")"  
			+ " true: (" + trueSucc.getMethod().getName().toString()  + ", " + trueSucc.getLastInstructionIndex() + ")" 
			+ " false: (" + falseSucc.getMethod().getName().toString() + ", " + falseSucc.getLastInstructionIndex() + ")]");
		}
	}
	
	public class NullCondition extends SpecialCondition {		

		NullCondition(ISSABasicBlock bb, FieldReference f, ISSABasicBlock trueSucc, ISSABasicBlock falseSucc) {
			super(bb, f, trueSucc, falseSucc);
			
		}		
		
		public String toString() {
			return ("NC : " + super.toString());
		}
		
	}
	
	public class IsHeldCondition extends SpecialCondition {	

		IsHeldCondition(ISSABasicBlock bb, FieldReference f, ISSABasicBlock trueSucc, ISSABasicBlock falseSucc) {			
			super(bb, f, trueSucc, falseSucc);					
		}
		
		public String toString() {
			return ("IH : " + super.toString());
		}
	}
	
	
		

	public void prepare() {
		
		ppToSpecCondition = new HashMap<SSAProgramPoint, SpecialCondition>();
		for (CGNode n : cg) {
			//WARNING: this needs to be done here!!!
			DefUse du = null;			//TODO: cache this nicer
			SSACFG cfg = n.getIR().getControlFlowGraph();
			IR ir = n.getIR();			
			
			// Clear the map from previous bindings
			//edgeToBranchInfo = new HashMap<ConditionEdge, GeneralCondition>();			
			
			for (Iterator<ISSABasicBlock> it = cfg.iterator(); it.hasNext(); ) {
			ISSABasicBlock bb = it.next();
				for (Iterator<SSAInstruction> iIter = bb.iterator(); iIter.hasNext(); ) {
				SSAInstruction instr = iIter.next();
				if (instr != null) {
					try {	//TODO: Fix array out of bound
						//TODO: refine check: check operator ...
						if (instr instanceof SSAConditionalBranchInstruction) {
							SSAConditionalBranchInstruction cinstr = (SSAConditionalBranchInstruction) instr;							
							Iterator<ISSABasicBlock> succNodesItr = cfg.getSuccNodes(bb);
							ArrayList<ISSABasicBlock> succNodesArray = Util.iteratorToArrayList(succNodesItr);
							switch (succNodesArray.size()) {
							/* XXX: assume for the moment that the first successor is the 
							 * true branch and the second the false 
							 */
							case 2: {
								
								if (du == null) {
									du = new DefUse(ir);
								}
								int use1 = cinstr.getUse(0);
								int use2 = cinstr.getUse(1);	
								
								//Try first for Null Check
								FieldReference field1 = getFieldForNullCheck(ir, du, use1);
								FieldReference field2 = getFieldForNullCheck(ir, du, use2);
								
								/*
								SSAInstruction def = du.getDef(use1);
								if (def instanceof SSAGetInstruction) {
									SSAGetInstruction get = (SSAGetInstruction) def;			
									FieldReference field = get.getDeclaredField();
									
									E.log(1, "f1: " + ((field1==null)?"null":field1.toString()));
									E.log(1, "f2: " + ((field2==null)?"null":field2.toString()));
										
																			
									//E.log(1, "Looking for: " + field.toString());
									if (cg.getLockFieldInfo().isWakeLock(field)) {
										E.log(1, "Found");
									};			
								}
								*/
								
								
								if ((field1 != null && ir.getSymbolTable().isNullConstant(use2)) ||
									(field2 != null && ir.getSymbolTable().isNullConstant(use1))) {
									
									//E.log(1, instr.toString());
									SSAProgramPoint pp = new SSAProgramPoint(n,cinstr);
									ISSABasicBlock trueSucc = succNodesArray.get(0);
									ISSABasicBlock falseSucc = succNodesArray.get(1);									
									if (field1 != null){
										NullCondition c = new NullCondition(pp.getBasicBlock(), field1, trueSucc, falseSucc);
										//E.log(1, c.toString());										
										ppToSpecCondition.put(pp, c);
									}
									else {
										NullCondition c = new NullCondition(pp.getBasicBlock(), field2, trueSucc, falseSucc);
										//E.log(1, c.toString());
										ppToSpecCondition.put(pp, c);
									}
								}		
								
								//Try for isHeld()
								//XXX: the test must be exactly isHeld() (and not !isHeld()), or else it will mess up...
								field1 = getFieldForIsHeld(ir, du, use1);
								field2 = getFieldForIsHeld(ir, du, use2);
								if (((field1 != null) && ir.getSymbolTable().isZero(use2)) ||
									((field2 != null) && ir.getSymbolTable().isZero(use1))) {
									SSAProgramPoint pp = new SSAProgramPoint(n,cinstr);
									ISSABasicBlock trueSucc = succNodesArray.get(0);
									ISSABasicBlock falseSucc = succNodesArray.get(1);
									//E.log(1,instr.toString());
									if (field1 != null){
										IsHeldCondition c = new IsHeldCondition(pp.getBasicBlock(), field1, falseSucc, trueSucc);
										ppToSpecCondition.put(pp,c);
										E.log(1, c.toString());
									}
									else {
										IsHeldCondition c = new IsHeldCondition(pp.getBasicBlock(), field2, falseSucc, trueSucc);
										ppToSpecCondition.put(pp,c);										
										E.log(1, c.toString());
									}
								}
							}
					        default: break;
							}
						} 
						/* Switch, exception variables not investigated */	
					} catch (ArrayIndexOutOfBoundsException e) {
						// Just ignore the out of bound for now
					}
				}
				}
			}
		}
	}

	
	/**
	 * Get the field associated with a value in the IR
	 * @param ir
	 * @param du
	 * @param val
	 * @return null if the value is not associated with a wakelock
	 */
	private FieldReference getFieldForNullCheck(IR ir, DefUse du, int val) {
		SSAInstruction def = du.getDef(val);
		if (def instanceof SSAGetInstruction) {
			SSAGetInstruction get = (SSAGetInstruction) def;			
			FieldReference field = get.getDeclaredField();
			//if (cg.getLockFieldInfo().isWakeLock(field)) {
				return field;				
			//};			
		}
		return null;
	}
		
	/**
	 * Get the field associated with an isHeld() call
	 * @param ir
	 * @param du
	 * @param val
	 * @return null if the value is not associated with a wakelock
	 */
	private FieldReference getFieldForIsHeld (IR ir, DefUse du, int val) {
		SSAInstruction def = du.getDef(val);
		if (def instanceof SSAInvokeInstruction) {			
			SSAInvokeInstruction inv = (SSAInvokeInstruction) def;			
			if (inv.getDeclaredTarget().getSignature().equals("android.os.PowerManager$WakeLock.isHeld()Z")) {
				int wlNum = inv.getUse(0);			
				return getFieldForNullCheck(ir, du, wlNum);	
			}
		};
		return null;
	}
	
	public void setAppCallGraph(AppCallGraph cg){
		this.cg = cg;
	} 	
	
	public HashMap<SSAProgramPoint, SpecialCondition> getSpecialConditions() {
		if (ppToSpecCondition == null) {
			prepare();			
		}		
		return ppToSpecCondition;
	}
	
	
}
