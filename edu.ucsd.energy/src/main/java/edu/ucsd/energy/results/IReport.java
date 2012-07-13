package edu.ucsd.energy.results;


public interface IReport {

	public String getTag();
	
	public Object toJSON();

	public String toShortDescription();
	
	public void dump();
	
}
