package edu.ucsd.energy.results;


public class GeneralViolation implements IViolationKey {

	private static ThreadLocal<Integer> staticId = new ThreadLocal<Integer>();
	
	private int id;
	
	private String description;
	
	public GeneralViolation(String str) {
		id = freshId();
		description = str;
	}
	
	private static int freshId() {
		Integer i = staticId.get();
		staticId.set(new Integer(i+1));
		return i;
	}
	
	public boolean equals(Object o) {
		if (o instanceof GeneralViolation) {
			GeneralViolation gv = (GeneralViolation) o;
			return (gv.hashCode() == hashCode());
		}
		return false;
	}
	
	public int hashCode() {
		return id;
	}
	
	public String toString() {
		return description;
	}

}
