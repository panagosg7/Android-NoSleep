package energy.util;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.intset.IntSet;

import energy.components.Component;

public class SSAProgramPoint {

	
	private IMethod method;
	private ISSABasicBlock bb;
	private IntSet indices;	//index within the basic block - at the moment only for "new" and "invoke" instructions
	private SSAInstruction instruction;
	private Component component;
	
	public SSAProgramPoint(IMethod m, ISSABasicBlock bb, IntSet ii) {
		this.method = m;
		this.bb = bb;
		this.indices = ii;
	}
	
	public SSAProgramPoint(Component c, IR ir, SSAInvokeInstruction inv) {
		this.component = c;
		CallSiteReference site = inv.getCallSite();		
		this.method = ir.getMethod();
		this.bb = ir.getBasicBlockForInstruction(inv);		
		this.indices = ir.getCallInstructionIndices(site);
		this.instruction = inv;		
		
	}

	public boolean equals(Object o) {
		if (o instanceof SSAProgramPoint) {
			SSAProgramPoint pp = (SSAProgramPoint) o;
			return (
				this.method.equals(pp.getMethod()) &&
				this.bb.equals(pp.getBasicBlock()) &&
				this.indices.equals(pp.getIndices()));
		}
		return false;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(method.getDeclaringClass().getName().toString());
		sb.append(" || ");
		sb.append(method.getName().toString());
		sb.append(" || ");
		sb.append(instruction.toString());
		return sb.toString();
	}
	

	public IMethod getMethod() {
		return method;
	}

	public ISSABasicBlock getBasicBlock() {
		return bb;
	}

	public IntSet getIndices() {
		return indices;
	}

	public SSAInstruction getInstruction() {
		return instruction;
	}

	public Component getComponent() {
		return component;
	}
	
}
