package edu.ucsd.energy.managers;

import com.ibm.wala.types.FieldReference;

import edu.ucsd.energy.util.SSAProgramPoint;

/**
 * _Context-insensitive_ notion of an Runnable
 */
public class RunnableInstance extends AbstractRunnableInstance {
	
	public RunnableInstance(SSAProgramPoint pp) {
		super(pp);
	}
	
	public RunnableInstance(FieldReference fr) {
		super(fr);
	}
	
	public int hashCode() {
		return newInstr.hashCode();
	}
	
	public boolean equals(Object o) {
		if (o instanceof RunnableInstance) {
			RunnableInstance i = (RunnableInstance) o;
			return newInstr.equals(i.getCreationInstruction());
		}
		return false;
	} 
	
}
