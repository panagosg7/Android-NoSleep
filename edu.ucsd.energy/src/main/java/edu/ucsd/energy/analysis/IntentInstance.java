package edu.ucsd.energy.analysis;

import java.util.ArrayList;

import net.sf.json.JSONObject;

import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;

import edu.ucsd.energy.util.SSAProgramPoint;

/**
 * _Context-insensitive_ notion of an intent
 * @author pvekris
 *
 */
public class IntentInstance extends ObjectInstance {
	
	private SSANewInstruction newInstr;
	
	public IntentInstance(SSAProgramPoint pp) {
		super(pp);
		uses = new ArrayList<String>();
	}

	public IntentInstance(FieldReference field) {
		super(field);
		uses = new ArrayList<String>();
	}

	private ArrayList<String> uses;

	//TODO: this needs to be translated to Component
	private TypeReference callee;
	
	public String toString() {
		return ("CREATED: " + ((pp!=null)?pp.toString():"null") 
				//+ "\n" + "FIELD: " + ((field!=null)?field.toString():"null") 
				//+ "\n" + "CALLEE: " + ((callee!=null)?callee.toString():"null")
				);
	}
	
	public int hashCode() {
		return newInstr.hashCode();
	}
	
	public boolean equals(Object o) {
		if (o instanceof IntentInstance) {
			IntentInstance i = (IntentInstance) o;
			return newInstr.equals(i.getCreationInstruction());
		}
		return false;
	} 
	
	public SSANewInstruction getCreationInstruction() {
		return newInstr;
	}
	
	public int getSSAVar() {
		return newInstr.getDef(0);
	}

	@Override
	public JSONObject toJSON() {
		// TODO Auto-generated method stub
		return null;
	}

	public void setCalledType(TypeReference calledType) {
		this.callee = calledType;
	}
	
	public TypeReference getCalledType() {
		return callee;
	}
	
}
