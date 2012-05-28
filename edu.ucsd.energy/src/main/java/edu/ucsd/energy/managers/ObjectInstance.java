package edu.ucsd.energy.managers;

import java.util.HashMap;

import com.ibm.wala.types.FieldReference;

import edu.ucsd.energy.util.SSAProgramPoint;

public abstract class ObjectInstance {
	
	//The program point where an object of this instance was created
	protected SSAProgramPoint creationPP;
		
	protected FieldReference field;

	ObjectInstance(SSAProgramPoint pp) {
		this.creationPP = pp;
	}

	ObjectInstance(FieldReference field) {
		this.field = field;
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
	
	public SSAProgramPoint getPP() {
		return creationPP;
	}
	
	public void setPP(SSAProgramPoint pp) {
		this.creationPP = pp;
	}

	public FieldReference getField() {
		return field;
	}
	
	public void setField(FieldReference fr) {
		field = fr;
	}	

	abstract public String toString();
	
}
