package energy.interproc;

import com.ibm.wala.util.collections.Quartet;
/**
 * State of a program point - very simple at the moment
 * @author pvekris
 *
 */
public class SingleLockState  {
	
	/** state */
	private String color;	
    private boolean maybeAcquired ;
    private boolean mustbeAcquired;
    private boolean maybeReleased ;
    private boolean mustbeReleased ;

	private LockStateColor lockStateColor;
	
	
	public enum LockStateColor {
		MUSTBERELEASED("green"),
		MAYBERELEASED("lightgreen"),
		MUSTBEACQUIRED("red"),
		MAYBEACQUIRED("lightpink"),
		NOLOCKS("lightgrey");		
		
		public String color;
		
		private LockStateColor(String c) { color = c; }
	}
	
	public SingleLockState(boolean a, boolean b, boolean c, boolean d) {		
	    maybeAcquired = a;
	    mustbeAcquired = b;
	    maybeReleased = c;
	    mustbeReleased = d;
	    if (mustbeReleased) {
	      color = "green";
	      lockStateColor = LockStateColor.MUSTBERELEASED;
	    } else if (maybeReleased) {
	      color = "lightgreen";
	      lockStateColor = LockStateColor.MAYBERELEASED;
	    } else if (mustbeAcquired) {
	      color = "red";
	      lockStateColor = LockStateColor.MUSTBEACQUIRED;
	    } else if (maybeAcquired) {
	      color = "lightpink";
	      lockStateColor = LockStateColor.MAYBEACQUIRED;
	    } else {
	      color = "lightgrey";
	      lockStateColor = LockStateColor.NOLOCKS;
	    }	    
	}
		
	public SingleLockState(Quartet<Boolean, Boolean, Boolean, Boolean> q) {
		this(q.fst.booleanValue(),q.snd.booleanValue(),
				q.thr.booleanValue(),q.frt.booleanValue());
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	@Override
	public int hashCode() {
	  return 1 * (maybeAcquired?1:0) +  2 * (mustbeAcquired?1:0) + 
	      4 * (maybeReleased?1:0) + 8 * (mustbeReleased?1:0);
	}

	
	@Override
	public boolean equals(Object o) {
		if (o instanceof SingleLockState) {
			SingleLockState l = (SingleLockState) o;			
			return (
				(maybeAcquired == l.isMaybeAcquired()) &&
				(mustbeAcquired == l.isMustbeAcquired()) &&
				(maybeReleased == l.isMaybeReleased()) &&
				(mustbeReleased == l.isMustbeReleased())
				);			
		}
		return false;		
	}
	
	public String toString () {
		if (isReached()) {
			StringBuffer sb = new StringBuffer();
			sb.append("WA:"); sb.append(maybeAcquired?"T":"F"); 
			sb.append(" SA:"); sb.append(mustbeAcquired?"T":"F");
			sb.append(" WR:"); sb.append(maybeReleased?"T":"F"); 
			sb.append(" SR:"); sb.append(mustbeReleased?"T":"F");
			return sb.toString();
		}
		else {
			return "empty";
		}
	}

	
	public boolean isReached () {
		return (maybeAcquired || maybeReleased);
	}
	
	
	
	/*XXX: should I return a new LockState every time?? */
	public SingleLockState merge(SingleLockState l) {		
	  return new SingleLockState(	maybeAcquired || l.isMaybeAcquired(), //may
			  		mustbeAcquired	&& l.isMustbeAcquired(), // must	  
	  				maybeReleased 	|| l.isMaybeReleased(), // may
	  				mustbeReleased	&& l.isMustbeReleased()); // must	  
	}


	public boolean isMaybeAcquired() {
		return maybeAcquired;
	}

	public boolean isMustbeAcquired() {
		return mustbeAcquired;
	}

	public boolean isMaybeReleased() {
		return maybeReleased;
	}

	public boolean isMustbeReleased() {
		return mustbeReleased;
	}

	public LockStateColor getLockStateColor() {
		return lockStateColor;
	}

	
}

	
