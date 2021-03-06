package edu.ucsd.energy.util;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.intset.IntSet;

public class SSAProgramPoint {

	private CGNode node;
	private IMethod method;
	private ISSABasicBlock bb;
	private IntSet indices;	//index within the basic block - at the moment only for "new" and "invoke" instructions
	private SSAInstruction instruction;
	private int bytecodeIndex = -1;
	
	
	public SSAProgramPoint(CGNode n, SSAInstruction instr) {
		this.node = n;
		this.method = n.getMethod();
		IR ir = n.getIR();
		this.bb = ir.getBasicBlockForInstruction(instr);
		
		if (instr instanceof SSAInvokeInstruction) {
			SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
			CallSiteReference site = inv.getCallSite();
			this.indices = ir.getCallInstructionIndices(site);
			this.bytecodeIndex = site.getProgramCounter();
		}
		this.instruction = instr;				
	}

	public boolean equals(Object o) {
		if (o instanceof SSAProgramPoint) {
			SSAProgramPoint pp = (SSAProgramPoint) o;
			return (
				this.method.equals(pp.getMethod()) 
				&& this.bb.equals(pp.getBasicBlock()) 
				//&& this.indices.equals(pp.getIndices())
				);
		}
		return false;
	}
	
	public int hashCode() {
		return (node.hashCode() * 7329) 
				+ (instruction.hashCode() * 9223);		
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("[");
		sb.append(method.getDeclaringClass().getName().toString());
		sb.append(".");
		sb.append(method.getName().toString());
		//sb.append(method.getSignature());
		sb.append(", ");
		sb.append("(bb) " + bb.getNumber()+"]");
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


	public CGNode getCGNode() {
		return node;
	}
	
	
	
	public int getCreationByteCodeIndex() {
		return bytecodeIndex;
	}

}
