package edu.ucsd.energy.managers;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.TypeReference;

import edu.ucsd.energy.util.SSAProgramPoint;

/**
 * Extension of SSAProgramPoint where we try to find the type 
 * associated with the instance that is being created by a new 
 * instruction or an invoke instruction. 
 * WARNING: Assuming that SSANewInstruction
 * and SSAInvokeInstruction are the only types of instructions that 
 * can create instances of this class. Will have to extend if something 
 * more than this happens.    
 */
public class CreationPoint extends SSAProgramPoint {

	private TypeReference type;

	public CreationPoint(CGNode n, SSAInstruction instr) {
		super(n, instr);
		if (instr instanceof SSANewInstruction) {
			SSANewInstruction newi = (SSANewInstruction) instr;
			this.type = newi.getConcreteType();
		}
		if (instr instanceof SSAInvokeInstruction) {
			SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
			this.type = inv.getDeclaredTarget().getReturnType();
		}
	}

	public TypeReference getType() {
		return type;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(super.toString());
		sb.append(" " + type);
		return sb.toString();
	}
	
}
