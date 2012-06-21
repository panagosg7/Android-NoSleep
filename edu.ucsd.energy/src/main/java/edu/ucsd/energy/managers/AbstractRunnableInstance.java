package edu.ucsd.energy.managers;

import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeName;

import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.util.SSAProgramPoint;

public abstract class AbstractRunnableInstance extends ObjectInstance {

	protected SSANewInstruction newInstr;	

	public AbstractRunnableInstance(SSAProgramPoint pp) {
		super(pp);
	}

	public AbstractRunnableInstance(FieldReference field) {
		super(field);
	}

	public SSANewInstruction getCreationInstruction() {
		return newInstr;
	}

	private TypeName callee;

	//TODO: is this needed
	private Context calledComponent;
	
	public String toString() {
		return (" - CREATE: " + ((creationPP!=null)?creationPP.toString():"null") 
				+ "\n" + "   CALLEE: " + ((callee!=null)?callee.toString():"null")
				+ "\n" + "   FIELD : " + ((field!=null)?field.toString():"null")
			);
	}

	public void setCalledType(TypeName calledType) {
		this.callee = calledType;
	}
	
	public void setCalledComponent(Context comp) {
		this.calledComponent = comp;
	}
	
	public boolean isResolved() {
		return (callee != null);
	}
	
	
	public TypeName getCalledType() {
		return callee;
	}

	public Object toJSON() {
		// TODO Auto-generated method stub
		return null;
	}


}
