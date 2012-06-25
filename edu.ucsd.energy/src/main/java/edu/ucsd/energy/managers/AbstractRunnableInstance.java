package edu.ucsd.energy.managers;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeName;

import edu.ucsd.energy.contexts.Context;

public abstract class AbstractRunnableInstance extends ObjectInstance {

	protected SSANewInstruction newInstr;	

	public AbstractRunnableInstance(CreationPoint pp) {
		super(pp);
	}

	public AbstractRunnableInstance(FieldReference field) {
		super(field);
	}

	public AbstractRunnableInstance(IMethod m, int v) {
		super(m,v);
	}

	public SSANewInstruction getCreationInstruction() {
		return newInstr;
	}

	private TypeName callee;

	//TODO: is this needed
	private Context calledComponent;
	
	public String toString() {
		return (((field!=null)?("Field: " + field.toString()):"") 
				+ ((creationPP!=null)?("\nCreated: " + creationPP.toString()):"")
				+ ((callee!=null)?("\nCalleeType: " + callee.toString()):"")
				+ ((method!=null)?("\nTypParam: " + method.getSelector().toString() + " - " + param):""));
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
