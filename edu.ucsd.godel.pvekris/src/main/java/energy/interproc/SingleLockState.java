package energy.interproc;

import java.util.Set;

import com.ibm.wala.util.collections.Quartet;
/**
 * State of a program point - very simple at the moment
 * @author pvekris
 *
 */
public class SingleLockState  {
	
	static SingleLockState UNDEFINED;
	
	
	/** state */
    private boolean maybeAcquired ;
    private boolean mustbeAcquired;
    private boolean maybeReleased ;
    private boolean mustbeReleased ;

	private LockStateColor lockStateColor;
	
	
	public enum LockStateColor {
		MUSTBERELEASED("blue"),
		MAYBERELEASED("green"),
		MUSTBEACQUIRED("red"),
		MAYBEACQUIRED("yellow"),
		NOLOCKS("grey"),
		UNDEFINED("black") ;		
		
		public String color;
		
		public String toString () {
			return color;
		}
		
		private LockStateColor(String c) { color = c; }
	}
	
	public SingleLockState(boolean a, boolean b, boolean c, boolean d) {		
	    maybeAcquired = a;
	    mustbeAcquired = b;
	    maybeReleased = c;
	    mustbeReleased = d;
	    if (mustbeReleased) {	      
	      lockStateColor = LockStateColor.MUSTBERELEASED;
	    } else if (maybeReleased) {	      
	      lockStateColor = LockStateColor.MAYBERELEASED;
	    } else if (mustbeAcquired) {	      
	      lockStateColor = LockStateColor.MUSTBEACQUIRED;
	    } else if (maybeAcquired) {	      
	      lockStateColor = LockStateColor.MAYBEACQUIRED;
	    } else {	      
	      lockStateColor = LockStateColor.NOLOCKS;
	    }	    
	}
		
	public SingleLockState(Quartet<Boolean, Boolean, Boolean, Boolean> q) {
		this(q.fst.booleanValue(),q.snd.booleanValue(),
				q.thr.booleanValue(),q.frt.booleanValue());
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
		if (l == null) {
			return this;
		}		
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

	public String getColor() {
		return lockStateColor.toString();
	}
	
	
	/**
	 * Merge a set of lock states. Should return UNDEFIINED if the set is empty
	 * @param set
	 * @return
	 */
	public static SingleLockState mergeSingleLockStates(Set<SingleLockState> set) {
		SingleLockState result = null;
		for(SingleLockState s : set)  {
			if (result == null) {
				result = s;
			}
			else {
				result = result.merge(s);
			}
		}
		return result;		
	}
	
	
}

	
