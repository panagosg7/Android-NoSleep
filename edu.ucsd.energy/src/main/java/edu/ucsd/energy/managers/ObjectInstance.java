package edu.ucsd.energy.managers;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.FieldReference;

public abstract class ObjectInstance {
	
	//The program point where an object of this instance was created
	protected CreationPoint creationPP;
		
	protected FieldReference field;

	protected IMethod method;
	protected int param;
	
	ObjectInstance(CreationPoint pp) {
		this.creationPP = pp;
	}

	ObjectInstance(FieldReference field) {
		this.field = field;
	}

	ObjectInstance(IMethod m, int v) {
		this.method = m;
		this.param = v;
	}

	//Using this really just to support the "this" case
	public ObjectInstance() {

	}

	public int hashCode() {
		return creationPP.hashCode();
	} 
	
	public boolean equals(Object o) {
		if (o instanceof ObjectInstance){
			ObjectInstance wli = (ObjectInstance) o;
			return creationPP.equals(wli.getPP());
		}
		return false;				
	}
	
	public CreationPoint getPP() {
		return creationPP;
	}
	
	public void setPP(CreationPoint pp) {
		this.creationPP = pp;
	}

	public FieldReference getField() {
		return field;
	}
	
	public void setField(FieldReference fr) {
		field = fr;
	}	

	public IMethod getMethod() {
		return method;
	}
	
	public int getParam() {
		return param;
	}
	
	abstract public String toString();
	
}
