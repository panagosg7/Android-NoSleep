package edu.ucsd.energy.interproc;

import java.util.HashSet;
import java.util.Set;

import edu.ucsd.energy.contexts.Context;
/**
 * State of a program point - very simple at the moment
 * @author pvekris
 *
 */
public class SingleLockState {

	SingleLockState UNDEFINED;

	/** state */	
	private boolean acquired;
	private boolean timed;
	private boolean async;
	
	//All the possible contexts that this state can originate from
	//or contexts that operate on this state
	private Set<Context> involvedContexts;

	private LockStateDescription lockStateColor;


	public enum LockStateDescription {
		ACQUIRED("red"),
		TIMED_ACQUIRED("yellow"),
		ASYNC_TIMED_ACQUIRED("khaki1"),
		ASYNC_ACQUIRED("pink1"),
		
		RELEASED("green"),
		//(ASYNC_)TIMED_RELEASED does not make sense
		ASYNC_RELEASED("greenyellow"),
		
//		NO_LOCKS("grey"),
		UNDEFINED("black");		

		public String color;

		public String toString () {
			return color;
		}

		private LockStateDescription(String c) { color = c; }
	}

	public SingleLockState(boolean a, boolean t, boolean as, Set<Context> sc) {
		acquired = a;
		timed = t;
		async = as;   
		involvedContexts = sc;
	}

	@Override
	public int hashCode() {
		return 1 * (timed?1:0) +  2 * (acquired?1:0) + 4 * (async?1:0) + 
				involvedContexts.size();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof SingleLockState) {
			SingleLockState l = (SingleLockState) o;			
			return ((acquired() == l.acquired()) &&
					(timed() == l.timed()) &&
					(async() == l.async()) &&
					involvedContexts.containsAll(l.involvedContexts()) &&
					l.involvedContexts.containsAll(involvedContexts));
		}
		return false;
	}

	public String toString () {
		StringBuffer sb = new StringBuffer();
		sb.append(async?"ASYNC_":""); 
		sb.append(timed?"TIMED_":"");
		sb.append(acquired?"ACQUIRED":"RELEASED");
		return sb.toString();
	}

	
	public SingleLockState merge(SingleLockState l) {
		if (l == null) {
			return this;
		}		
		//Obviously we need a may analysis here
		HashSet<Context> sOrig = new HashSet<Context>();
		sOrig.addAll(l.involvedContexts());
		sOrig.addAll(involvedContexts());
		
		return new SingleLockState(	
			acquired || l.acquired(), 
			timed && l.timed(), 
			async || l.async(),
			sOrig);
		
	}


	public Set<Context> involvedContexts() {
		return involvedContexts;
	}
	
	public void addContexts(Set<Context> ctxs) {
		involvedContexts.addAll(ctxs);		
	}

	public boolean acquired() {
		return acquired;
	}

	public boolean timed() {
		return timed;
	}

	public boolean async() {
		return async;
	}


	public LockStateDescription getLockStateDescription() {
		if (async && timed && acquired) {
			return LockStateDescription.ASYNC_TIMED_ACQUIRED;
		}
		if (async && (!timed) && (!acquired)) {
			return LockStateDescription.ASYNC_RELEASED;
		}
		if ((!async) && timed && acquired) {
			return LockStateDescription.TIMED_ACQUIRED;
		}
		if ((!async) && (!timed) && acquired) {
			return LockStateDescription.ACQUIRED;
		}
		if ((!async) && (!timed) && (!acquired)) {
			return LockStateDescription.RELEASED;
		}
		if (async && (!timed) && (!acquired)) {
			return LockStateDescription.ASYNC_RELEASED;
		}
		if (async && (!timed) && acquired) {
			return LockStateDescription.ASYNC_ACQUIRED;
		}
		return LockStateDescription.UNDEFINED;	//the rest of the cases are bogus
	}

	public String getColor() {
		return lockStateColor.toString();
	}

	public static SingleLockState merge(Set<SingleLockState> set) {
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
	
	public static SingleLockState merge(SingleLockState a, SingleLockState b) {
		if (a == null) return b;
		return a.merge(b);
	}

	public void setAcquired(boolean b) {
		acquired = b;		
	}

	


}


