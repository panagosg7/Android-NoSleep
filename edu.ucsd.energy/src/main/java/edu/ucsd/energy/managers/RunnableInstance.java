package edu.ucsd.energy.managers;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;

/**
 * _Context-insensitive_ notion of an Runnable
 */
public class RunnableInstance extends AbstractRunnableInstance {
	
	public RunnableInstance(CreationPoint pp) {
		super(pp);
	}
	
	public RunnableInstance(FieldReference fr) {
		super(fr);
	}
	
	public RunnableInstance(IMethod m, int v) {
		super(m,v);
	}

	public RunnableInstance(TypeReference reference) {
		super(reference);
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
