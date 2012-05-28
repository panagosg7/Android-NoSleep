package edu.ucsd.energy.managers;

import java.util.Collection;
import java.util.HashSet;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import com.ibm.wala.types.FieldReference;

import edu.ucsd.energy.managers.WakeLockManager.RefCount;
import edu.ucsd.energy.util.SSAProgramPoint;

public class WakeLockInstance extends ObjectInstance {

	public enum LockType {
		// Values taken from here:
		// http://developer.android.com/reference/android/os/PowerManager.html
		ACQUIRE_CAUSES_WAKEUP	(0x10000000),
		FULL_WAKE_LOCK 			(0x0000001a),
		ON_AFTER_RELEASE 		(0x20000000),
		PARTIAL_WAKE_LOCK 		(0x00000001),
		SCREEN_BRIGHT_WAKE_LOCK (0x0000000a),
		SCREEN_DIM_WAKE_LOCK 	(0x00000006),
		UNKNOWN					(0xFFFFFFFF);
				
		private int code;
	    
		LockType(int value) {
	        this.code = value;
	    }
		
	    public int getCode() {
	        return code;
	    }
	}
	
	public class WakeLockInfo {
		
		private Collection<LockType> types;
		private RefCount referenceCounted;		
				
		WakeLockInfo() {
			this.types = new HashSet<LockType>();
			this.referenceCounted = RefCount.UNSET;
		}

		public Collection<LockType> getLockType() {
			return types;
		}

		public void setLockType(Collection<LockType> types) {
			this.types = types;
		}

		public RefCount isReferenceCounted() {
			return referenceCounted;
		}

		public void setReferenceCounted(RefCount referenceCounted) {
			this.referenceCounted = referenceCounted;
		}
		
		public String toString() {
			return ("Type:" + ((types==null)?"NULL":types.toString()) + " RefCounted:" + referenceCounted);
		}
		
	}
	
	private WakeLockInfo info;
	
	public WakeLockInstance(SSAProgramPoint pp) {
		super(pp);
		this.info = new WakeLockInfo();
	}

	public WakeLockInstance(FieldReference field) {
		super(field);
		this.info = new WakeLockInfo();
	}


	public int hashCode() {
		return creationPP.hashCode();
	} 
	
	public boolean equals(Object o) {
		if (o instanceof WakeLockInstance){
			WakeLockInstance wli = (WakeLockInstance) o;
			return creationPP.equals(wli.getPP());
		}
		return false;				
	}
	
	public SSAProgramPoint getPP() {
		return creationPP;
	}

	public FieldReference getField() {
		return field;
	}
	
	public String toString() {
		return (((field!=null)?field.toString():"NO_FIELD") + 
				" Created: " + ((creationPP!=null)?creationPP.toString():"null")); 
	}
	
	public String toShortString() {
		if (field != null) {
			return field.getName().toString();
		}
		else if(creationPP != null) {
			return creationPP.toString();
		}
		else {
			return "NULL";
		}
	}

	public WakeLockInfo getInfo() {
		return info;
	}

	public void setLockType(Collection<LockType> lockType) {
		this.info.setLockType(lockType);			
	}
	
	public JSONObject toJSON() {
		JSONObject o = new JSONObject();
		WakeLockInfo info = getInfo();				
		if (creationPP!=null) {
			String methSig = creationPP.getMethod().getSignature();
			creationPP.getInstruction();				
			o.put("creating_method", methSig);
		}
		else {
			o.put("creating_method", "could not resolve this");
		}
		//o.put("method_offset", 0);
		o.put("reference_counted", info.isReferenceCounted().toString());
		JSONArray ta = new JSONArray();
		ta.addAll(info.getLockType());
		o.put("lock_type",info.getLockType());
		return o;
	}

	public void setField(FieldReference fr) {
		field = fr;			
	}
	
}
