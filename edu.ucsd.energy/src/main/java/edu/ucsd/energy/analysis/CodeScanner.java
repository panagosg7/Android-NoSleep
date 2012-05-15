package edu.ucsd.energy.analysis;

import java.util.Iterator;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;

import edu.ucsd.energy.util.E;

public abstract class CodeScanner {


	protected static final int DEBUG = 2;
	protected AppCallGraph cg;
	protected ComponentManager cm;
	private DefUse du;
	private IR ir; 
	
	public CodeScanner(ComponentManager componentManager) {
		this.cg = componentManager.getCG();
		this.cm = componentManager;
	}
	
	protected abstract void init();
	
	protected abstract void doSSAInstr(SSAInstruction instr);
	
	protected abstract void doCGNode();
	
	public void prepare() {
		
		for (CGNode n : cg) {
			du = null;
			ir = n.getIR();
			/* Null for JNI methods */
			E.log(DEBUG, "Analyzing: " + n.getMethod().getSignature());
			if (ir == null) {
				continue;				
			}	
			SSACFG cfg = ir.getControlFlowGraph();
			//doCGNode();
			
			for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext(); ) {
				SSAInstruction instr = it.next();
				doSSAInstr(instr);	
			}
		}
	}
	
}
