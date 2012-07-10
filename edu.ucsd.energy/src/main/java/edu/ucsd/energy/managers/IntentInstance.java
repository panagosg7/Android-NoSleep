package edu.ucsd.energy.managers;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;

/**
 * _Context-insensitive_ notion of an intent
 * @author pvekris
 *
 */
public class IntentInstance extends AbstractRunnableInstance {
	
	private String actionString;

	public IntentInstance(CreationPoint pp) {
		super(pp);
	}

	public IntentInstance(FieldReference field) {
		super(field);
	}
	
	public IntentInstance(IMethod m, int v) {
		super(m,v);
	}

	public IntentInstance(TypeReference reference) {
		super(reference);
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

	public void setActionString(String constantValue) {
		this.actionString = constantValue;
	} 
		
	public String toString() {
		return (super.toString() +  
				(((actionString!=null)?("ActionString: " + actionString):""))); 
	}
	
}
