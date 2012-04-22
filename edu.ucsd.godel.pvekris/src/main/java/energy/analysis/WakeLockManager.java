package energy.analysis;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.IOperator;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;

import energy.util.E;
import energy.util.SSAProgramPoint;

public class WakeLockManager {
	
	private AppCallGraph cg;
	private ComponentManager cm;
	
	public interface WakeLockInstance {}
	
	public class FieldWakeLock implements WakeLockInstance {
		private FieldReference field;
		
		public FieldReference getField() {
			return field;
		}
		
		public FieldWakeLock(FieldReference f) {
			this.field = f;
		}		
		
		@Override
		public int hashCode() {
			return (-1) * field.hashCode();		//keep them separate from locals
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof FieldWakeLock) {
				FieldWakeLock lwl = (FieldWakeLock) obj;
				return field.equals(lwl.getField());
			}
			return false;
		}		
		
		@Override
		public String toString() {
			return ("F:" + field.toString()); 
		}
	}
	
	public class LocalWakeLock implements WakeLockInstance { 
		//The program point where newWakeLock was called
		private SSAProgramPoint pp;
		
		public LocalWakeLock(SSAProgramPoint pp) {
			this.pp = pp;
		}

		public SSAProgramPoint getCreatioinPP() {
			return pp;
		}		
		
		@Override
		public int hashCode() {
			return pp.hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof LocalWakeLock) {
				LocalWakeLock lwl = (LocalWakeLock) obj;
				return pp.equals(lwl.getCreatioinPP());
			}
			return false;
		}
		
		@Override
		public String toString() {
			return ("LOCAL: " + pp.toString()); 
		}
		
	}
	
	
	public class WakeLockInfo {
		
		private Collection<LockType> types;
		private boolean referenceCounted;		
				
		WakeLockInfo() {}
			
		WakeLockInfo(Collection<LockType> t , boolean r) {			
			this.types = t;
			this.referenceCounted = r;
		}
		
		public Collection<LockType> getLockType() {
			return types;
		}

		public void setLockType(Collection<LockType> types) {
			this.types = types;
		}

		public Boolean isReferenceCounted() {
			return referenceCounted;
		}

		public void setReferenceCounted(Boolean referenceCounted) {
			this.referenceCounted = referenceCounted;
		}
		
		public String toString() {
			return ("T:" + ((types==null)?"NULL":types.toString()) + " RC:" + referenceCounted);
		}		
	}
	
	
	public WakeLockManager(AppCallGraph appCallGraph) {
		this.cg = appCallGraph;
	}
	
	HashSet<FieldReference> powerManagers = null;
	HashMap<WakeLockInstance, WakeLockInfo> variousWakeLocks = null;
	private IR ir;
	private DefUse du;
	

	public void scanDefinitions() {
		for(IClass c : cg.getClassHierarchy()) {
			for (IField f : c.getAllFields()) {
				FieldReference reference = f.getReference();
				TypeReference fieldType = reference.getFieldType();
				if (fieldType.equals(TypeReference.ApplicationPowerManager)) {
					E.log(2, "PowerManager: " + reference.toString());
					addPowerManager(reference);
				}
				else if (fieldType.equals(TypeReference.PrimordialPowerManager)) {
					E.log(2, "PowerManager: " + reference.toString());
					addPowerManager(reference);
				}
				else if (fieldType.equals(TypeReference.ApplicationWakeLock)) {
					E.log(1, reference.toString());
					addWakeLockField(reference);
				}
				else if (fieldType.equals(TypeReference.PrimordialWakeLock)) {
					E.log(1, reference.toString());
					addWakeLockField(reference);
				}				
			};
		}
	}
	

	private void addWakeLockField(FieldReference reference) {
		if (variousWakeLocks == null) {
			variousWakeLocks = new HashMap<WakeLockInstance, WakeLockInfo>();			
		}
		//Wake locks are reference counted by default.
		variousWakeLocks.put(new FieldWakeLock(reference), new WakeLockInfo(null, true));		
	}

	private void addPowerManager(FieldReference reference) {
		if (powerManagers== null) {
			powerManagers =new HashSet<FieldReference>(); 
		}
		powerManagers.add(reference);	
	}
	
	private HashSet<WakeLockInfo> getAllUnresolvedWakeLocks() {
		if (variousWakeLocks == null) {
			variousWakeLocks = new HashMap<WakeLockInstance, WakeLockInfo>();
		}
		HashSet<WakeLockInfo> result = new HashSet<WakeLockManager.WakeLockInfo>();
		for (WakeLockInfo wl : variousWakeLocks.values()) {
			if (wl.getLockType() == null) {
				result.add(wl);
			}			
		}
		return result;
	}
	
	
	public  boolean isWakeLock(FieldReference fr) {		
		if (variousWakeLocks == null) {
			scanCreation();
		}		
		FieldWakeLock fieldWakeLock = new FieldWakeLock(fr);
		return variousWakeLocks.containsKey(fieldWakeLock);		
	}
 
	
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
	
	
	private Collection<LockType> getLockType(int i) {		
		HashSet<LockType> result = new HashSet<LockType>();
		for (LockType lt : LockType.values()) {
			
			if ((i & lt.getCode()) == lt.getCode() ) {
				result.add(lt);
			}
		}
		return result;		
	}
	

	public void scanCreation() {		
		for (CGNode n : cg) {
			
			//Need to update these here
			ir = n.getIR();
			du = null;			
			/* Null for JNI methods */
			if (ir == null) {
				E.log(2, "Skipping: " + n.getMethod().toString());
				continue;				
			}			
			for (SSAInstruction instr : ir.getInstructions()) {							
				/* PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
				 * 
				 * PowerManager.WakeLock wl = pm.newWakeLock(
				 * 		  PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, 
				 * 		  TAG);
				 */
				if (instr instanceof SSAInvokeInstruction) {
					SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
					if (inv.toString().contains("newWakeLock")) {

						E.log(2, n.getMethod().getSignature().toString());						
						E.log(2,inv.toString());						  
						if (du == null) {
							du = new DefUse(ir);
						}						
						Collection<LockType> lockType = null;																		
						lockType = resolveLockTypeFromVar(inv.getUse(1));
						//The WakeLock is being assigned to the Def part of the instruction
						int lockNum = inv.getDef();
						//The use should be an instruction right next to the one creating the lock 
						Iterator<SSAInstruction> uses = du.getUses(lockNum);						
						//TODO: it does not have to be necessarily the next one ...
						SSAInstruction useInstr = uses.next();
						if (useInstr instanceof SSAPutInstruction) {
						/*	This is the case that the lock is a field, so we expect a put 
							instruction right after the creation of the wakelock.	*/ 							
							SSAPutInstruction put = (SSAPutInstruction) useInstr;						
							FieldReference field = put.getDeclaredField();							
							//Assertions.productionAssertion(variousWakeLocks.containsKey(field));
							WakeLockInfo wli = variousWakeLocks.get(new FieldWakeLock(field));
							if(wli != null) {
								wli.setLockType(lockType);
							}
							else {
								wli = new WakeLockInfo(lockType, true);
								//Wake locks are reference counted by default. 
								variousWakeLocks.put(new FieldWakeLock(field),wli);
							}
							E.log(1, "Field: " + field + "\nINFO= " + wli.toString());
						}
						else {
						/**
						 * This is the case of LOCAL VARIABLES used as WakeLocks, eg:
						 * try {
          					...
          					} catch (FileNotFoundException localObject1)
          					{
          						...
					          localObject1 = ((PowerManager)paramActivity.getSystemService("power")).newWakeLock(536870913, str2);
					          ((PowerManager.WakeLock)localObject1).setReferenceCounted(false);
					          ((PowerManager.WakeLock)localObject1).acquire(15000L);
					          Intent localIntent = new android/content/Intent;
					          localIntent.<init>("android.intent.action.VIEW");
					          ...
					          SystemClock.sleep(5000L);
					          ((PowerManager.WakeLock)localObject1).release();
					        } 
						 */
							SSAInstruction def = du.getDef(lockNum);
							E.log(1, "Local WakeLock Variable: " + def.toString());
							//Use the program point of the newWakeLock() to specify this
							
							SSAProgramPoint pp = new SSAProgramPoint(n, instr);
							WakeLockInfo wli = variousWakeLocks.get(new LocalWakeLock(pp));
							if(wli != null) {
								E.log(1, "LockType: " + lockType.toString());
								wli.setLockType(lockType);
							}
							else {
								//Wake locks are reference counted by default.
								wli = new WakeLockInfo(lockType, true);
								variousWakeLocks.put(new LocalWakeLock(pp),	wli);
							}							
						}						
					}	
					//Check setReferenceCounted
					else if (inv.toString().contains("setReferenceCounted")) {
						FieldReference field = getFieldFromVar(ir,du,inv.getUse(0));
						int bit = inv.getUse(1);
						boolean refCounted = ir.getSymbolTable().isTrue(bit);
						//Is it a field?
						if (field != null) {														
							WakeLockInfo wli = variousWakeLocks.get(new FieldWakeLock(field));							
							if(wli != null) {
								wli.setReferenceCounted(refCounted);
							}
							else {
								variousWakeLocks.put(new FieldWakeLock(field),
									new WakeLockInfo(null, refCounted));
							}
						}
						//Is is local?
						else {
							SSAInstruction def = du.getDef(inv.getUse(0));
							SSAProgramPoint pp = new SSAProgramPoint(n, def);
							WakeLockInfo wli = variousWakeLocks.get(new LocalWakeLock(pp));
							//TODO: this is not so precise... 
							//the def of inv could be a checkcast
							if(wli != null) {
								wli.setReferenceCounted(refCounted);
							}
							else {
								variousWakeLocks.put(new FieldWakeLock(field),
									new WakeLockInfo(null, refCounted));
							}
						}
					}
				}
			}			
		}		
		
		//Check to see if there are unresolved wakelocks - 
		//probably not needed. There could be unused WL
		/*if (getAllUnresolvedWakeLocks().size() > 0) {
			E.log(1, "Could not resolve some WakeLocks.");
		}*/
		
	}
	
	
	private FieldReference getFieldFromVar(IR ir, DefUse du, int use) {							
		SSAInstruction def = du.getDef(use);				
		if (def instanceof SSAGetInstruction) {
			SSAGetInstruction get = (SSAGetInstruction) def;
			FieldReference field = get.getDeclaredField();
			E.log(2, "Operating on field: " + field );				
			return field;
		}
		return null;
	}


	private Boolean getReferenceCounted(IR ir, DefUse du, int lockNum) {
		Iterator<SSAInstruction> uses = du.getUses(lockNum);
		while(uses.hasNext()) {
			SSAInstruction instr = uses.next();
			E.log(1, instr.toString());
			if (instr instanceof SSAInvokeInstruction) {				
				SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
				
				if (inv.toString().contains("setReferenceCounted")) {
					E.log(1, inv.toString());
				}
			}		
		}
		return Boolean.FALSE;
	}


	private Collection<LockType> resolveLockTypeFromVar(int use) {	
		try {
			int intValue = ir.getSymbolTable().getIntValue(use);
			return  getLockType(intValue);
		}
		catch (IllegalArgumentException e) {
		/*
		 * The 1st parameter of newWakeLock was not a constant int, 
		 * so we'll have to check what it is ...
		 */	
			SSAInstruction def = du.getDef(use);
			HashSet<LockType> ret = new HashSet<LockType>();
			
			/* TODO : will probably have to enhance this with more cases */
			if (def instanceof SSABinaryOpInstruction) {
				SSABinaryOpInstruction bin = (SSABinaryOpInstruction) def;
				IOperator operator = bin.getOperator();
				//Just tracks this easy case
				if (operator == Operator.OR) {
					int use0 = bin.getUse(0);
					int use1 = bin.getUse(1);
							
					ret.addAll(resolveLockTypeFromVar(use0));
					ret.addAll(resolveLockTypeFromVar(use1));
									
				}
				else {
					ret.add(LockType.UNKNOWN);
				}
			}
			else {
				ret.add(LockType.UNKNOWN);
			}
			return ret;
				 
		}
		
	}


	public void setAppCallGraph(AppCallGraph cg){
		this.cg = cg;
	}


	public void printAllWakeLocks() {		
		for(Entry<WakeLockInstance, WakeLockInfo> e : variousWakeLocks.entrySet()) {
			E.log(1, e.getKey().toString() + "\n" + e.getValue().toString()); 
		}
	} 
	
	
}
