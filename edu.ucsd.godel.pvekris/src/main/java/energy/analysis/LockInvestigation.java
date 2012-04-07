package energy.analysis;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.IOperator;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.debug.Assertions;

import energy.util.E;

public class LockInvestigation {
	
	private ApplicationCallGraph cg;
	private ComponentManager cm;
	private ClassHierarchy ch; 
	
	
	public LockInvestigation(ClassHierarchy classHierarchy) {
		this.ch = classHierarchy;
	}
	
	HashSet<FieldReference> powerManagers = null;
	HashMap<FieldReference, Collection<LockType>> variousWakeLocks = null;
	

	public void traceLockFields() {
		for(IClass c : ch) {
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
					addWakeLock(reference);
				}
				else if (fieldType.equals(TypeReference.PrimordialWakeLock)) {
					E.log(1, reference.toString());
					addWakeLock(reference);
				}				
				
			};
			
		}
	}
	

	private void addWakeLock(FieldReference reference) {
		if (variousWakeLocks == null) {
			variousWakeLocks = new HashMap<FieldReference, Collection<LockType>>();
			
		}
		variousWakeLocks.put(reference, null);		
	}

	private void addPowerManager(FieldReference reference) {
		if (powerManagers== null) {
			powerManagers =new HashSet<FieldReference>(); 
		}
		powerManagers.add(reference);	
	}
	

	private Set<FieldReference> getAllWakeLocks() {
		if (variousWakeLocks == null) {
			variousWakeLocks = new HashMap<FieldReference, Collection<LockType>>();
		}
		return variousWakeLocks.keySet();		
	}
	
	private Map<FieldReference, Collection<LockType>> getAllUnresolvedWakeLocks() {
		if (variousWakeLocks == null) {
			variousWakeLocks = new HashMap<FieldReference, Collection<LockType>>();
		}
		Set<Entry<FieldReference, Collection<LockType>>> entrySet = variousWakeLocks.entrySet();
		
		HashMap<FieldReference, Collection<LockType>> result = 
				new HashMap<FieldReference, Collection<LockType>>();
		
		for (Entry<FieldReference, Collection<LockType>> e : entrySet) {
			if (e.getValue() == null) {
				result.put(e.getKey(), e.getValue());
			}			
		}
		return result;		
	}
	
	
	public Collection<LockType> getWakeLockType(FieldReference fr) {
		
		if (variousWakeLocks == null) {
			traceLockCreation();
		}		
		return variousWakeLocks.get(fr);	
		
	}
	
	public  boolean isWakeLock(FieldReference fr) {
		
		if (variousWakeLocks == null) {
			traceLockCreation();
		}		
		return variousWakeLocks.containsKey(fr);	
		
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
	

	public void traceLockCreation() {
		
		for (CGNode n : cg) {
			
			IR ir = n.getIR();
			DefUse du = null;
			
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
						//Assertions.productionAssertion(call.getNumberOfParameters() == 3);
						E.log(2, n.getMethod().getSignature().toString());
						
						E.log(2,inv.toString());
						  
						if (du == null) {
							du = new DefUse(ir);
						}
						
						Collection<LockType> lockType = null;
																		
						lockType = resolveLockTypeFromVar(ir, du, inv.getUse(1));
												
						//The WakeLock is being assigned to the Def part of the instruction
						int lockNum = inv.getDef();
						
						//The use should be an instruction right next to the one creating the lock 
						Iterator<SSAInstruction> uses = du.getUses(lockNum);						
						
						SSAInstruction useInstr = uses.next();				
					
						if (useInstr instanceof SSAPutInstruction) {
						/*	This is the case that the lock is a field, so we expect a put 
							instruction right after the creation of the wakelock.	*/ 
							
							SSAPutInstruction put = (SSAPutInstruction) useInstr;						
							FieldReference field = put.getDeclaredField();
							
							Assertions.productionAssertion(variousWakeLocks.containsKey(field));
							
							variousWakeLocks.put(field, lockType);
							
							E.log(1, "Field: " + field + "\nType = " + lockType);
						}
						else {
						/* TODO :
						 * This is probably a local variable, like the case of:
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
							E.log(1, "Locking a local variable (???)");
							
							
						}
						
					}					
				}
			}			
		}		
		
		//Check to see if there are unresolved wakelocks
		if (getAllUnresolvedWakeLocks().size() > 0) {
			E.log(1, "Could not resolve some WakeLocks.");
		}
		
	}
	
	
	private Collection<LockType> resolveLockTypeFromVar(IR ir, DefUse du, int use) {	
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
							
					ret.addAll(resolveLockTypeFromVar(ir,du,use0));
					ret.addAll(resolveLockTypeFromVar(ir,du,use1));
									
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


	public void setAppCallGraph(ApplicationCallGraph cg){
		this.cg = cg;
	} 
	
	
}
