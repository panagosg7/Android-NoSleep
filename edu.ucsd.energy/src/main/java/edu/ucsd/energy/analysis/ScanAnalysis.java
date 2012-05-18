package edu.ucsd.energy.analysis;

import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;

import edu.ucsd.energy.util.SSAProgramPoint;

public interface ScanAnalysis<T> {
	
	/**
	 * Need to provide a creator for the instances we want
	 * @return
	 */
	public T newInstance(SSAProgramPoint pp);

	public T newInstance(FieldReference fr);
	
	public boolean isInterestingType(TypeReference typeReference);
	
}

