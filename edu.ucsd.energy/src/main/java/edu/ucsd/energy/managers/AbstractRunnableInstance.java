package edu.ucsd.energy.managers;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

public abstract class AbstractRunnableInstance extends ObjectInstance {

	protected SSANewInstruction newInstr;
	
	/**
	 * The case of self call must be treated separately, because 
	 * creating the CFG in the normal way will cause a gap in the 
	 * graph, so in this case we need to short-circuit the context call
	 */
	private boolean selfCall;	

	public AbstractRunnableInstance(CreationPoint pp) {
		super(pp);
	}

	public AbstractRunnableInstance(FieldReference field) {
		super(field);
	}

	public AbstractRunnableInstance(IMethod m, int v) {
		super(m,v);
		//If v is "this" then set the type as the type of method m
		if ((!m.isStatic()) && (v == 1)) {
			setCalledType(m.getDeclaringClass().getName());
		}
		setSelfCall(true);
	}
	

	public AbstractRunnableInstance(TypeReference reference) {
		super();
		callee = reference.getName();
	}

	public SSANewInstruction getCreationInstruction() {
		return newInstr;
	}

	protected TypeName callee;

	public String toString() {
		return (((field!=null)?("Field: " + field.toString()):"") 
				+ ((creationPP!=null)?("\nCreated: " + creationPP.toString()):"")
				+ ((callee!=null)?("\nCalleeType: " + callee.toString()):"")
				+ ((method!=null)?("\nTypParam: " + method.getSelector().toString() + " - " + param):""));
	}

	public void setCalledType(TypeName calledType) {
		this.callee = calledType;
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

	public boolean isSelfCall() {
		return selfCall;
	}

	public void setSelfCall(boolean selfCall) {
		this.selfCall = selfCall;
	}

}
