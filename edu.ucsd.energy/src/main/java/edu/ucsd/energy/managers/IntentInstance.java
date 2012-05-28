package edu.ucsd.energy.managers;

import java.util.ArrayList;

import com.ibm.wala.types.FieldReference;

import edu.ucsd.energy.util.SSAProgramPoint;

/**
 * _Context-insensitive_ notion of an intent
 * @author pvekris
 *
 */
public class IntentInstance extends AbstractRunnableInstance {
	
	public IntentInstance(SSAProgramPoint pp) {
		super(pp);
		new ArrayList<String>();
	}

	public IntentInstance(FieldReference field) {
		super(field);
		new ArrayList<String>();
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
		
}
