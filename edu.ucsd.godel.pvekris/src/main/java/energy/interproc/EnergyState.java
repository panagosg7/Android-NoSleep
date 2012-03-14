package energy.interproc;

import com.ibm.wala.util.collections.Quartet;


/**
 * State of a program point - very simple at the moment
 * @author pvekris
 *
 */
public class EnergyState  {
	
	/** state */
	private String color;	
	private Quartet<Boolean, Boolean, Boolean, Boolean> quartet;
	private LockState lockState;
	
	
	public enum LockState {
		MUSTBERELEASED("green"),
		MAYBERELEASED("lightgreen"),
		MUSTBEACQUIRED("red"),
		MAYBEACQUIRED("lightpink"),
		NOLOCKS("lightgrey");		
		
		public String color;
		
		private LockState(String c) { color = c; }
	}
	
	public EnergyState(Quartet<Boolean, Boolean, Boolean, Boolean> q) {
		this.quartet = q;
		/* Single color here */
	    boolean maybeAcquired = q.fst;
	    boolean mustbeAcquired = q.snd;
	    boolean maybeReleased = q.thr;
	    boolean mustbeReleased = q.frt;
	    if (mustbeReleased) {
	      color = "green";
	      lockState = LockState.MUSTBERELEASED;
	    } else if (maybeReleased) {
	      color = "lightgreen";
	      lockState = LockState.MAYBERELEASED;
	    } else if (mustbeAcquired) {
	      color = "red";
	      lockState = LockState.MUSTBEACQUIRED;
	    } else if (maybeAcquired) {
	      color = "lightpink";
	      lockState = LockState.MAYBEACQUIRED;
	    } else {
	      color = "lightgrey";
	      lockState = LockState.NOLOCKS;
	    }	    
	}
	
	
	
	
	public EnergyState(String color) {
		this.color = color;
	}	
	
	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}
	

	public boolean equals(Object o) {
		if (o instanceof EnergyState) {
			EnergyState e  = (EnergyState) o;
			return (e.getColor().compareTo(this.color) == 0);
		}
		return false;		
	}
	
	public String toString ( ) {
		return this.color;
	}

}
