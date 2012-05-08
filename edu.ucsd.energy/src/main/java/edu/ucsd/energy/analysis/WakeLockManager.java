package edu.ucsd.energy.analysis;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.demandpa.AbstractPtrTest;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.AbstractPointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.IOperator;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.intset.OrdinalSet;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SSAProgramPoint;

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
	
	//Cache the wakelock refs
	private Map<FieldReference, FieldWakeLock> mFieldRefs;
	private FieldWakeLock findOrCreateFieldWakeLock(FieldReference fr) {
		if (mFieldRefs == null) {
			mFieldRefs = new HashMap<FieldReference, WakeLockManager.FieldWakeLock>();
		}
		FieldWakeLock fWL = mFieldRefs.get(fr);
		if (fWL == null) {	
			FieldWakeLock newFWL = new FieldWakeLock(fr);
			mFieldRefs.put(fr, newFWL);
			return newFWL;
		}
		return fWL;
	}
	
	private Map<SSAProgramPoint, LocalWakeLock> mLocalRefs;
	private LocalWakeLock findOrCreateLocalWakeLock(SSAProgramPoint pp) {
		if (mLocalRefs == null) {
			mLocalRefs = new HashMap<SSAProgramPoint, WakeLockManager.LocalWakeLock>();
		}
		LocalWakeLock lWL = mLocalRefs.get(pp);
		if (lWL == null) {	
			LocalWakeLock newLWL = new LocalWakeLock(pp);
			mLocalRefs.put(pp, newLWL);
			return newLWL;
		}
		return lWL;
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
	
	
	public enum RefCount {
		UNSET,
		TRUE,
		FALSE;		
	} 
	
	public class WakeLockInfo {
		
		private Collection<LockType> types;
		private RefCount referenceCounted;		
				
		WakeLockInfo() {}
			
		WakeLockInfo(Collection<LockType> t , RefCount r) {			
			this.types = t;
			this.referenceCounted = r;
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
	
	
	public WakeLockManager(AppCallGraph appCallGraph) {
		this.cg = appCallGraph;
		mWakeLocks = new HashMap<WakeLockInstance, WakeLockInfo>();
		mMethodReturns = new HashMap<MethodReference, WakeLockManager.WakeLockInstance>();
		mInstrToWakeLock = new HashMap<SSAInstruction, WakeLockManager.WakeLockInstance>();
	}
	
	
	HashSet<FieldReference> powerManagers = null;
	HashMap<WakeLockInstance, WakeLockInfo> mWakeLocks = null;
	HashMap<MethodReference, WakeLockInstance> mMethodReturns = null;
	
	//SSAInstruction should work as a key since it does not change ever
	HashMap<SSAInstruction, WakeLockInstance> mInstrToWakeLock = null;
	
	private IR ir;
	private DefUse du;
	private IMethod method;
	private CGNode node;
	private IClass declaringClass;
	

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
					E.log(2, reference.toString());
					addWakeLockField(reference);
				}
				else if (fieldType.equals(TypeReference.PrimordialWakeLock)) {
					E.log(2, reference.toString());
					addWakeLockField(reference);
				}				
			};
		}
	}
	

	private void addWakeLockField(FieldReference reference) {
		//Wake locks are reference counted by default.
		mWakeLocks.put(findOrCreateFieldWakeLock(reference), new WakeLockInfo(null, RefCount.UNSET));		
	}

	private void addPowerManager(FieldReference reference) {
		if (powerManagers== null) {
			powerManagers =new HashSet<FieldReference>(); 
		}
		powerManagers.add(reference);	
	}
	
	private HashSet<WakeLockInfo> getAllUnresolvedWakeLocks() {
		if (mWakeLocks == null) {
			mWakeLocks = new HashMap<WakeLockInstance, WakeLockInfo>();
		}
		HashSet<WakeLockInfo> result = new HashSet<WakeLockManager.WakeLockInfo>();
		for (WakeLockInfo wl : mWakeLocks.values()) {
			if (wl.getLockType() == null) {
				result.add(wl);
			}			
		}
		return result;
	}
	
	public  boolean isWakeLock(FieldReference fr) {		
		FieldWakeLock fieldWakeLock = findOrCreateFieldWakeLock(fr);
		return mWakeLocks.containsKey(fieldWakeLock);		
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
	
	
	
	private void scanInvokeInstruction(SSAInvokeInstruction inv) {
		if (inv.toString().contains("newWakeLock")) {

			E.log(2, method.getSignature().toString());						
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
				WakeLockInfo wli = mWakeLocks.get(findOrCreateFieldWakeLock(field));
				if(wli != null) {
					wli.setLockType(lockType);
				}
				else {
					wli = new WakeLockInfo(lockType, RefCount.UNSET);
					//Wake locks are reference counted by default. 
					mWakeLocks.put(new FieldWakeLock(field),wli);
				}
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
		          ...
		          SystemClock.sleep(5000L);
		          ((PowerManager.WakeLock)localObject1).release();
		        } 
			 */
				if (du == null) {
					du = new DefUse(ir);
				}
				SSAInstruction def = du.getDef(lockNum);
				E.log(2, "Local WakeLock Variable: " + def.toString());
				//Use the program point of the newWakeLock() to specify this
				
				SSAProgramPoint pp = new SSAProgramPoint(node, inv);
				WakeLockInfo wli = mWakeLocks.get(findOrCreateLocalWakeLock(pp));
				if(wli != null) {
					wli.setLockType(lockType);
				}
				else {
					//Wake locks are reference counted by default.
					wli = new WakeLockInfo(lockType, RefCount.UNSET);
					mWakeLocks.put(findOrCreateLocalWakeLock(pp),	wli);
				}							
			}						
		}
		//Check setReferenceCounted
		else if (inv.toString().contains("setReferenceCounted")) {
			if (du == null) {
				du = new DefUse(ir);
			}						
			//E.log(1, n.getMethod().getSignature().toString());
			//E.log(1, inv.toString());
			FieldReference field = getFieldFromVar(inv.getUse(0));
			int bit = inv.getUse(1);
			boolean refCounted = ir.getSymbolTable().isTrue(bit);
			//Is it a field?
			if (field != null) {														
				WakeLockInfo wli = mWakeLocks.get(new FieldWakeLock(field));							
				if(wli != null) {
					wli.setReferenceCounted(refCounted?RefCount.TRUE:RefCount.FALSE);
				}
				else {
					mWakeLocks.put(new FieldWakeLock(field),
						new WakeLockInfo(null, refCounted?RefCount.TRUE:RefCount.FALSE));
				}
			}
			//Is is local?
			else {
				SSAInstruction def = du.getDef(inv.getUse(0));
				SSAProgramPoint pp = new SSAProgramPoint(node, def);
				LocalWakeLock localWakeLock = findOrCreateLocalWakeLock(pp);
				WakeLockInfo wli = mWakeLocks.get(localWakeLock);
				//TODO: this is not so precise... 
				//the def of inv could be a checkcast
				if(wli != null) {
					wli.setReferenceCounted(refCounted?RefCount.TRUE:RefCount.FALSE);
				}
				else if (localWakeLock != null) {
					mWakeLocks.put(localWakeLock,
						new WakeLockInfo(null, refCounted?RefCount.TRUE:RefCount.FALSE));
				}
			}
		}
	}

	
	private void scanInteresting(SSAInstruction instr) {
		if (instr instanceof SSAInvokeInstruction ) {
			SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
			//Check if it is an interesting method using the wakelock
			if (Interesting.sWakelockMethods.contains(inv.getDeclaredTarget())) {
				int use = inv.getUse(0);	//this should be the right argument
				
				
				//Try PTA
				/*
				PointerAnalysis pointerAnalysis = cg.getPointerAnalysis();
				LocalPointerKey lpk = new LocalPointerKey(node, use);
				OrdinalSet<InstanceKey> pointsToSet = pointerAnalysis.getPointsToSet(lpk);
				for(InstanceKey ik : pointsToSet) {
					E.log(1, "&&&& " + ik.toString());
				}
				*/
								
				//CGNode node = bb.getNode();
				if (du == null) {
					du = new DefUse(ir);
				}
				SSAInstruction def = du.getDef(use);
				E.log(1, method.getSignature());
				if (def instanceof SSAGetInstruction) {
					SSAGetInstruction get = (SSAGetInstruction) def;
					FieldWakeLock fWL = findOrCreateFieldWakeLock(get.getDeclaredField());
					E.log(1, fWL.toString());
					mInstrToWakeLock.put(inv, fWL);
				}
				else if (def instanceof SSAInvokeInstruction) {
					MethodReference methRef = ((SSAInvokeInstruction) def).getDeclaredTarget();
					WakeLockInstance wli = mMethodReturns.get(methRef);
					
					if (wli == null) {
						SSAInvokeInstruction defInv = (SSAInvokeInstruction) def;
						SSAProgramPoint pp = new SSAProgramPoint(node, defInv);				
						wli = findOrCreateLocalWakeLock(pp); 
					}
					E.log(1, wli.toString());
					mInstrToWakeLock.put(inv, wli);
				}
			}
		}
	}
	
	private void scanReturnInstruction(SSAReturnInstruction ret) {
		TypeReference returnType = method.getReturnType();
		if (returnType.equals(TypeReference.PrimordialWakeLock) ||
			returnType.equals(TypeReference.ApplicationWakeLock)) {
			int returnedValue = ret.getUse(0);
			if (du == null) {
				du = new DefUse(ir);
			}
			SSAInstruction def = du.getDef(returnedValue);
			if (def instanceof SSAGetInstruction) {	//if we're lucky...
				FieldWakeLock fwl = findOrCreateFieldWakeLock(((SSAGetInstruction) def).getDeclaredField());
				E.log(1, "Adding: " + method.getSignature() + " :: " + fwl.toString());
				mMethodReturns.put(method.getReference(), fwl);
			}
			else if(def instanceof SSAInvokeInstruction) {
				
			}
			else if(def instanceof SSAPhiInstruction) {
				//TODO: this will make things more complicated...
			}  
		}
	}

	private void scanInstruction(SSAInstruction instr ){
		/* PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
		 * 
		 * PowerManager.WakeLock wl = pm.newWakeLock(
		 * 		  PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, 
		 * 		  TAG);
		 */
		if (instr instanceof SSAInvokeInstruction) {
			SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
			scanInvokeInstruction(inv);	
		}
		//Keep the info: a method returns a lock instance as result. 
		else if (instr instanceof SSAReturnInstruction) {
			SSAReturnInstruction ret = (SSAReturnInstruction) instr;
			scanReturnInstruction(ret);
		}
	} 
	
	public void prepare() {	
		E.log(1, "Scanning source for lock creation...");
		for (CGNode n : cg) {
			node = n;
			declaringClass = node.getMethod().getDeclaringClass();
			/* Need to update these here */
			ir = n.getIR();
			du = null;
			/* Null for JNI methods */
			method = n.getMethod();
			if (ir == null) {
				E.log(2, "Skipping: " + method.toString());
				continue;				
			}			
			//E.log(1, "Scanning: " + n.getMethod().getSignature().toString());
			for (SSAInstruction instr : ir.getInstructions()) {	
				scanInstruction(instr);
			}			
		}	
		E.log(1, "Scanning source for lock usage...");
		for (CGNode n : cg) {
			node = n;
			/* Need to update these here */
			ir = n.getIR();
			du = null;
			/* Null for JNI methods */
			method = n.getMethod();
			if (ir == null) {
				E.log(2, "Skipping: " + method.toString());
				continue;				
			}			
			//E.log(1, "Scanning: " + n.getMethod().getSignature().toString());
			for (SSAInstruction instr : ir.getInstructions()) {	
				scanInteresting(instr);
			}			
		}
		dumpWakeLockInfo();
	}
	
	
	private void dumpWakeLockInfo() {
		E.log(1, "==========================================\n");
		for (Entry<WakeLockInstance, WakeLockInfo> e : mWakeLocks.entrySet()) {
			WakeLockInstance key = e.getKey();
			WakeLockInfo value = e.getValue();
			E.log(1, key.toString() + " :: " + value.toString());
		}
		E.log(1, "==========================================\n");
		for (Entry<MethodReference, WakeLockInstance> e : mMethodReturns.entrySet()) {
			MethodReference key = e.getKey();
			WakeLockInstance value = e.getValue();
			E.log(1, key.toString() + " :: " + value.toString());
		}
		E.log(1, "==========================================\n");		
		for (Entry<SSAInstruction, WakeLockInstance> e : mInstrToWakeLock.entrySet()) {
			SSAInstruction key = e.getKey();
			WakeLockInstance value = e.getValue();
			E.log(1, key.toString() + " :: " + value.toString());
		}
		E.log(1, "==========================================\n");
	}


	private FieldReference getFieldFromVar(int use) {							
		SSAInstruction def = du.getDef(use);				
		if (def instanceof SSAGetInstruction) {
			SSAGetInstruction get = (SSAGetInstruction) def;
			FieldReference field = get.getDeclaredField();
			E.log(2, "Operating on field: " + field );				
			return field;
		}
		return null;
	}


	private Boolean getReferenceCounted(int lockNum) {
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
		for(Entry<WakeLockInstance, WakeLockInfo> e : mWakeLocks.entrySet()) {
			E.log(1, e.getKey().toString() + "\n" + e.getValue().toString()); 
		}
	} 

	public WakeLockInstance getWakeLockFromInstruction(SSAInstruction instr) {
		return mInstrToWakeLock.get(instr);		
	}
	
}
