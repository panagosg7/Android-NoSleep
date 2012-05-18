package edu.ucsd.energy.analysis;

import net.sf.json.JSONObject;

import com.ibm.wala.types.FieldReference;

import edu.ucsd.energy.util.SSAProgramPoint;

public abstract class ObjectInstance {
	
	//The program point where an object of this instance was created
	protected SSAProgramPoint pp;
		
	protected FieldReference field;

	ObjectInstance(SSAProgramPoint pp) {
		this.pp = pp;
	}

	ObjectInstance(FieldReference field) {
		this.field = field;
	}

	public int hashCode() {
		return pp.hashCode();
	} 
	
	public boolean equals(Object o) {
		if (o instanceof ObjectInstance){
			ObjectInstance wli = (ObjectInstance) o;
			return pp.equals(wli.getPP());
		}
		return false;				
	}
	
	public SSAProgramPoint getPP() {
		return pp;
	}

	public FieldReference getField() {
		return field;
	}
	
	public void setField(FieldReference fr) {
		field = fr;			
	}	

	abstract public String toString();
	
	abstract public JSONObject toJSON();

}
