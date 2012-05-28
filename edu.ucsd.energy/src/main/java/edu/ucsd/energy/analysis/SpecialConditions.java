package edu.ucsd.energy.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.collections.Iterator2List;

import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.managers.WakeLockManager;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SSAProgramPoint;

/**
 * Need to run WakeLockManager before invoking this
 * @author pvekris
 *
 */
public class SpecialConditions {
	
	private final int DEBUG = 2;

	private GlobalManager global;
	
	public SpecialConditions(GlobalManager gm) {
		this.global = gm;
	}

	//private HashMap<SSAProgramPoint,SpecialCondition> mSpecCond = null;
	private HashMap<SSAInstruction,SpecialCondition> mSpecCond = null;
	
	private CGNode node;
	private DefUse du;
	private IR ir;
	
	public class SpecialCondition {
		WakeLockInstance field = null;
		protected ISSABasicBlock instrBlock;
		protected ISSABasicBlock trueSucc;
		protected ISSABasicBlock falseSucc;
		private int trueInstrInd;
		private int falseInstrInd;

		public ISSABasicBlock getTrueSucc() {
			return trueSucc;
		}
		
		public ISSABasicBlock getFalseSucc() {
			return falseSucc;
		}

		public WakeLockInstance getField() {
			return field;
		}
		
		SpecialCondition (ISSABasicBlock bb, WakeLockInstance f, ISSABasicBlock trueSucc, ISSABasicBlock falseSucc) {
			this.instrBlock = bb;
			this.field = f;		
			this.trueSucc = trueSucc;
			if (trueSucc != null) {
				this.trueInstrInd = trueSucc.getFirstInstructionIndex() + 1;		//this is very arbitrary 
			}
			else {
				this.trueInstrInd = -1;
			}
			this.falseSucc = falseSucc;
			if (falseSucc != null) {
				this.falseInstrInd = falseSucc.getFirstInstructionIndex() + 1;
			}
			else {
				this.falseInstrInd = -1;
			}
			
		}
		
		public String toString() {
			return ("[(" + instrBlock.getMethod().getName() + ", " + instrBlock.getNumber() + ")"  
			+ " true: (" + trueSucc.getMethod().getName().toString()  + ", " + trueSucc.getNumber() + ", " + trueInstrInd + ")" 
			+ " false: (" + falseSucc.getMethod().getName().toString() + ", " + falseSucc.getNumber() + ", " + falseInstrInd + ")]");
		}

		public int getTrueInstrInd() {
			return trueInstrInd;
		}

		public int getFalseInstrInd() {
			return falseInstrInd;
		}
	}
	
	public class NullCondition extends SpecialCondition {		

		NullCondition(ISSABasicBlock bb, WakeLockInstance f, ISSABasicBlock trueSucc, ISSABasicBlock falseSucc) {
			super(bb, f, trueSucc, falseSucc);
			
		}		
		
		public String toString() {
			return ("NC : " + super.toString());
		}
		
	}
	
	public class IsHeldCondition extends SpecialCondition {	

		IsHeldCondition(ISSABasicBlock bb, WakeLockInstance f, ISSABasicBlock trueSucc, ISSABasicBlock falseSucc) {			
			super(bb, f, trueSucc, falseSucc);					
		}
		
		public String toString() {
			return ("IH : " + super.toString());
		}
	}
	
	public void prepare() {
		
		mSpecCond = new HashMap<SSAInstruction, SpecialCondition>();
		for (CGNode n : global.getAppCallGraph()) {
			//WARNING: this needs to be done here!!!
			node = n;
			du = null;
			ir = n.getIR();
			/* Null for JNI methods */
			E.log(DEBUG + 1, "Analyzing: " + n.getMethod().getSignature());
			if (ir == null) {
				E.log(DEBUG, "Skipping: " + n.getMethod().getSignature());
				continue;				
			}	
			SSACFG cfg = ir.getControlFlowGraph();
			
			for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext(); ) {
				SSAInstruction instr = it.next();
				if (instr instanceof SSAConditionalBranchInstruction) {
					SSAConditionalBranchInstruction cinstr = (SSAConditionalBranchInstruction) instr;
					ISSABasicBlock bb = ir.getBasicBlockForInstruction(instr);
					Iterator<ISSABasicBlock> succNodesItr = cfg.getSuccNodes(bb);
					Iterator2List<ISSABasicBlock> succNodesArray = 
							new Iterator2List<ISSABasicBlock>(succNodesItr, new ArrayList<ISSABasicBlock>(2));
					switch (succNodesArray.size()) {
					/* We assume for the moment that the first successor is the 
					 * true branch and the second the false */
					case 2: {
						if (du == null) {
							du = new DefUse(ir);
						}
						int use1 = cinstr.getUse(0);
						int use2 = cinstr.getUse(1);	
						
						//Try first for Null Check
						WakeLockInstance ins1 = findInterestingInstance(use1);
						WakeLockInstance ins2 = findInterestingInstance(use2);
						
						if (((ins1 != null) && ir.getSymbolTable().isNullConstant(use2)) ||
							((ins2 != null) && ir.getSymbolTable().isNullConstant(use1))) {
							WakeLockInstance ins = (ins1!=null)?ins1:ins2;
							E.log(DEBUG, ins.toString());
							E.log(DEBUG, instr.toString());
							SSAProgramPoint pp = new SSAProgramPoint(n,cinstr);
							ISSABasicBlock trueSucc = succNodesArray.get(0);
							ISSABasicBlock falseSucc = succNodesArray.get(1);									
							NullCondition c = new NullCondition(pp.getBasicBlock(), ins, trueSucc, falseSucc);
							E.log(DEBUG, c.toString());										
							mSpecCond.put(instr, c);
							continue;
						}
						
						//Try for isHeld()
						//XXX: the test must be exactly isHeld() (and not !isHeld()), or else it will mess up...
						ins1 = getFieldForIsHeld(use1);
						ins2 = getFieldForIsHeld(use2);
						if (((ins1 != null) && ir.getSymbolTable().isZero(use2)) ||
							((ins2 != null) && ir.getSymbolTable().isZero(use1))) {
							WakeLockInstance field = (ins1 != null)?ins1:ins2;
							E.log(DEBUG, field.toString());
							E.log(DEBUG, instr.toString());
							SSAProgramPoint pp = new SSAProgramPoint(n,cinstr);
							ISSABasicBlock trueSucc = succNodesArray.get(0);
							ISSABasicBlock falseSucc = succNodesArray.get(1);
							IsHeldCondition c = new IsHeldCondition(pp.getBasicBlock(), field, falseSucc, trueSucc);
							mSpecCond.put(instr,c);
							E.log(DEBUG, c.toString());
							continue;
						}
					}
			        default: break;
					}
				} 
			}
		}
	}

	/**
	 * Get the field associated with a value in the IR
	 * @param val
	 * @return null if the value is not associated with a wakelock
	 */
	private WakeLockInstance findInterestingInstance(int val) {
		WakeLockManager wakeLockManager = global.getWakeLockManager();
		return wakeLockManager.traceInstance(node, val);
	}
		
	/**
	 * Get the field associated with an isHeld() call
	 * @param val
	 * @return null if the value is not associated with a wakelock
	 */
	private WakeLockInstance getFieldForIsHeld (int val) {
		SSAInstruction def = du.getDef(val);
		if (def instanceof SSAInvokeInstruction) {			
			SSAInvokeInstruction inv = (SSAInvokeInstruction) def;			
			if (inv.getDeclaredTarget().getSignature().equals("android.os.PowerManager$WakeLock.isHeld()Z")) {
				int wlNum = inv.getUse(0);			
				return findInterestingInstance(wlNum);
			}
		};
		return null;
	}

	public SpecialCondition get(SSAInstruction ssaInstruction) {
		return mSpecCond.get(ssaInstruction);
	}
}