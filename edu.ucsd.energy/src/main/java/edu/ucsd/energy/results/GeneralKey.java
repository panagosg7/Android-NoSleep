package edu.ucsd.energy.results;


/**
 * equals() and hashCode() can be inherited by Object. 
 * @author pvekris
 *
 */
public class GeneralKey implements IReportKey {

	private String description;
	
	public GeneralKey(String str) {
		description = str;
	}
	
	public String toString() {
		return "-";
	}

}
