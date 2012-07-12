package edu.ucsd.energy.results;


/**
 * equals() and hashCode() can be inherited by Object. 
 * @author pvekris
 *
 */
public class GeneralViolation implements IViolationKey {

	private String description;
	
	public GeneralViolation(String str) {
		description = str;
	}
	
	public String toString() {
		return description;
	}

}
