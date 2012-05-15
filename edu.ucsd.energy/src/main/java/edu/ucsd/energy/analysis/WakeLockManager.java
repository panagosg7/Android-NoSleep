package edu.ucsd.energy.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.IOperator;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Iterator2List;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.results.WakeLockReport;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.GraphBottomUp;
import edu.ucsd.energy.util.SSAProgramPoint;

public class WakeLockManager {
	
	private int DEBUG = 1;
	
	private AppCallGraph cg;
	private ComponentManager cm;

	private WakeLockReport report = null;
	
	
	public class WakeLockInstance {

		//The program point where WakeLock was created
		private SSAProgramPoint pp;
		
		private FieldReference field;
	
		private WakeLockInfo info;
		
		public WakeLockInstance(SSAProgramPoint pp2) {
			this.pp = pp2;
			this.info = new WakeLockInfo();
		}

		public WakeLockInstance(FieldReference field2) {
			this.field = field2;
		}

		public int hashCode() {
			return pp.hashCode();
		} 
		
		public boolean equals(Object o) {
			if (o instanceof WakeLockInstance){
				WakeLockInstance wli = (WakeLockInstance) o;
				return pp.equals(wli.getPP());
			}
			return false;				
		}
		
		public SSAProgramPoint getPP() {
			return pp;
		}

		public FieldReference getField() {
			return field;
		}
		
		public String toString() {
			return (((field!=null)?field.toString():"NO_FIELD") + 
					" Created: " + 
					((pp!=null)?pp.toString():"null")); 
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
			if (pp!=null) {
				String methSig = pp.getMethod().getSignature();
				pp.getInstruction();				
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
		
	public enum RefCount {
		UNSET,
		TRUE,
		FALSE;		
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

	public class WakeLockInfo {
		
		private Collection<LockType> types;
		private RefCount referenceCounted;		
				
		WakeLockInfo() {
			this.types = new HashSet<LockType>();
			this.referenceCounted = RefCount.UNSET;
		}

		WakeLockInfo(Collection<LockType> t , RefCount r) {
			//Assertions.productionAssertion(pp != null, "inserting WakeLockInfo without creation");
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
	
		
	public JSONObject getJSON() {
		JSONObject result = new JSONObject();

		JSONObject obj = new JSONObject();
		if (mFieldRefs != null) {
			for (Entry<FieldReference, WakeLockInstance> e : mFieldRefs.entrySet()) {
				obj.put(e.getKey().toString(), e.getValue().toJSON());
			}
		}
		E.log(1, "Adding fields");
		result.put("fields", obj);
		
		obj = new JSONObject();
		if (mCreationRefs != null) {
			for (Entry<SSAProgramPoint, WakeLockInstance> e : mCreationRefs.entrySet()) {
				obj.put(e.getKey().toString(), e.getValue().toJSON());
			}
		}
		E.log(1, "Adding creation");
		result.put("creation_sites", obj);
		
		obj = new JSONObject();
		if (mInstrToWakeLock != null) {
			int i = 1;
			for (Entry<Pair<MethodReference, SSAInstruction>, WakeLockInstance> e : mInstrToWakeLock.entrySet()) {
				MethodReference methRef = e.getKey().fst;
				SSAInstruction instr = e.getKey().snd;
				WakeLockInstance wli = e.getValue();
				if (instr instanceof SSAInvokeInstruction) {
					SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
					JSONObject o = new JSONObject();
					o.put("caller", methRef.getSignature());
					o.put("target method", inv.getDeclaredTarget().getName().toString());
					FieldReference field = wli.getField();
					o.put("field", (field==null)?"NO_FIELD":field.toString());
					SSAProgramPoint pp = wli.getPP();
					o.put("created", (pp==null)?"NO_PROG_POINT":pp.toString());
					obj.put(Integer.toString(i++),o);
				}
			}
		}
		E.log(1, "Adding use");
		result.put("calls", obj);
		
		return result;
	}

	
	public WakeLockManager(AppCallGraph appCallGraph) {
		this.cg = appCallGraph;
		mMethodReturns = new HashMap<MethodReference, WakeLockInstance>();
		mInstrToWakeLock = new HashMap<Pair<MethodReference,SSAInstruction>, WakeLockInstance>();
	}

	
	
	//Cache the wakelock refs
	private Map<FieldReference, WakeLockInstance> mFieldRefs;
	
	private Map<SSAProgramPoint, WakeLockInstance> mCreationRefs;

	private Map<MethodReference, WakeLockInstance> mMethodReturns;
	
	//SSAInstruction should work as a key since it does not change ever
	HashMap<Pair<MethodReference, SSAInstruction>, WakeLockInstance> mInstrToWakeLock = null;
	
	private IR ir;

	private DefUse du;

	private IMethod method;

	private CGNode node;


	public void prepare() {
		E.log(1, "Scanning source for lock creation...");
		Iterator<CGNode> bottomUpIterator = GraphBottomUp.bottomUpIterator(cg);
		Iterator2List<CGNode> bottomUpList = 
				new Iterator2List<CGNode>(bottomUpIterator, 
				new ArrayList<CGNode>(cg.getNumberOfNodes()));
	    for (CGNode n : bottomUpList ){
			node = n;
			/* Need to update these here */
			du = null;
			/* Null for JNI methods */
			method = n.getMethod();
			ir = n.getIR();
			if (ir == null) continue;
			//E.log(1, "Scanning for creation: " + n.getMethod().getSignature().toString());
			for (SSAInstruction instr : ir.getInstructions()) {
				scanInstruction(instr);
			}
		}
		E.log(1, "Scanning source for lock usage (acquire, release, etc.) ...");
		bottomUpIterator = GraphBottomUp.bottomUpIterator(cg);
	    for (CGNode n : bottomUpList) {
			node = n;
			/* Need to update these here */
			ir = n.getIR();
			du = null;
			/* Null for JNI methods */
			method = n.getMethod();
			if (ir == null) continue;				
			//E.log(1, "Scanning for interesting: " + n.getMethod().getSignature().toString());
			for (SSAInstruction instr : ir.getInstructions()) {	
				scanInteresting(instr);
			}			
		}
		dumpWakeLockInfo();
	}

	public void associateFieldWithLock(FieldReference fr, WakeLockInstance wli) {
		if (!isWakeLock(fr.getFieldType())) return;	//only interesting stuff 
		if (mFieldRefs == null) {
			mFieldRefs = new HashMap<FieldReference, WakeLockInstance>();
		}
		if (mFieldRefs.get(fr) == null) {	
			//this is the first time we encounter this field
			mFieldRefs.put(fr, wli);
			wli.setField(fr);
		}
	}
	
	public WakeLockInstance findOrCreateWakeLock(FieldReference field) {
		if (mFieldRefs == null) {
			mFieldRefs = new HashMap<FieldReference, WakeLockManager.WakeLockInstance>();
		}
		WakeLockInstance wli = mFieldRefs.get(field);
		if (wli == null) {
			wli = new WakeLockInstance(field);
			mFieldRefs.put(field, wli);
		}
		return wli;
	}
	
	public WakeLockInstance findOrCreateWakeLock(SSAProgramPoint pp) {
		WakeLockInstance wli = findWakeLock(pp);
		if (wli != null) return wli;
		//When all else fails, create a new WL instance.
		wli = new WakeLockInstance(pp);
		mCreationRefs.put(pp, wli);
		return wli;
	}

	public WakeLockInstance findWakeLock(SSAProgramPoint pp) {
		if (mCreationRefs == null) {
			mCreationRefs = new HashMap<SSAProgramPoint, WakeLockInstance>();
		}
		//Check to see if this is a lock created in this method
		WakeLockInstance wli = mCreationRefs.get(pp);
		if (wli != null) {
			return wli;
		}
		//Check if to see if it is returned by a function.
		SSAInstruction instr = pp.getInstruction();
		if (instr instanceof SSAInvokeInstruction) {
			SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
			wli = mMethodReturns.get(inv.getDeclaredTarget());
			if (wli != null) {
				return wli;
			}			
		}
		return null;
	}
	
	public void setAppCallGraph(AppCallGraph cg){
		this.cg = cg;
	}

	public WakeLockInstance getWakeLockFromInstruction(SSAInstruction instr, MethodReference methodReference) {
		Pair<MethodReference, SSAInstruction> pair = Pair.make(methodReference, instr);
		return mInstrToWakeLock.get(pair);		
	}

	public WakeLockReport getWakeLockReport() {
		if (report == null) {
			E.log(1, "Creating new report");
			return new WakeLockReport(this);
		}  
		return report;		
	}
	
	public WakeLockInstance getMethodReturn(MethodReference mr) {
		if (mMethodReturns != null) {
			return mMethodReturns.get(mr);
		}
		return null;
	}

	private boolean isWakeLock(TypeReference tr) {
		if (tr != null) {
			return (tr.equals(TypeReference.ApplicationWakeLock) ||
			tr.equals(TypeReference.PrimordialWakeLock));
		}
		return false;
		
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
		//Check newWakeLock
		if (inv.toString().contains("newWakeLock")) {
			scanNewWakeLock(inv);
		}
		//Check setReferenceCounted
		else if (inv.toString().contains("setReferenceCounted")) {
			scanReferenceCounted(inv);
		}
	}
	
	private void scanReferenceCounted(SSAInvokeInstruction inv) {
		if (du == null) {
			du = new DefUse(ir);
		}						
		int bit = inv.getUse(1);
		boolean refCounted = ir.getSymbolTable().isTrue(bit);
		int use = inv.getUse(0);
		WakeLockInstance wli = traceWakeLockDef(use);
		wli.getInfo().setReferenceCounted(refCounted?RefCount.TRUE:RefCount.FALSE);
	}

	/**
	 * Despite the order in which we issue the scanning of newWakeLock and 
	 * return statements, these can be executed in the other way, so we make 
	 * sure we insert the necessary wakelock instances in the mapping. 
	 * @param inv
	 */
	private void scanNewWakeLock(SSAInvokeInstruction inv) {
		if (du == null) {
			du = new DefUse(ir);
		}
		Collection<LockType> lockType = resolveLockTypeFromVar(inv.getUse(1));
		SSAProgramPoint pp = new SSAProgramPoint(node, inv);
		int defVar = inv.getDef();
				
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
		
		//Use the program point of the newWakeLock() to specify this
		WakeLockInstance wli = findOrCreateWakeLock(pp);
		wli.setLockType(lockType);
		
		E.log(DEBUG, "Creating WakeLock: " + wli.toString());
		
		//The use should be an instruction right next to the one creating the lock 
		Iterator<SSAInstruction> uses = du.getUses(defVar);
		if (!uses.hasNext()) return;
		SSAInstruction useInstr = uses.next();
		if (useInstr instanceof SSAPutInstruction) {
		/*	This is the case that the lock is a field, so we expect a put
			instruction right after the creation of the wakelock.	*/
			FieldReference field = ((SSAPutInstruction) useInstr).getDeclaredField();
			associateFieldWithLock(field, wli);
		}
	}

	private void scanInteresting(SSAInstruction instr) {
		if (instr instanceof SSAInvokeInstruction ) {
			SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
			if (Interesting.sWakelockMethods.contains(inv.getDeclaredTarget())) {
				E.log(DEBUG, "CALLER: " + method.getSignature());
				E.log(DEBUG, "CALLEE: " + inv.getDeclaredTarget().getName().toString());
				int use = inv.getUse(0);	//this should be the right argument
				//CGNode node = bb.getNode();
				if (du == null) {
					du = new DefUse(ir);
				}
				WakeLockInstance wli = traceWakeLockDef(use);
				if (wli != null) {
					Pair<MethodReference, SSAInstruction> p = Pair.make(method.getReference(), instr);
					mInstrToWakeLock.put(p, wli);
				}
				else {
					E.log(DEBUG, "Could not trace that.");
				}
			}
		}
	}
	
	/**
	 * Trace a variable to a WakeLockInstance
	 * @param var
	 * @return
	 */
	private WakeLockInstance traceWakeLockDef(int var) {
		SSAInstruction def = du.getDef(var);
		if (def == null) return null;
		if (def instanceof SSAGetInstruction) {
			SSAGetInstruction get = (SSAGetInstruction) def;
			WakeLockInstance wli = findOrCreateWakeLock(get.getDeclaredField());
			return wli;
		}
		else if (def instanceof SSAInvokeInstruction) {
			//Try to see if this is a creation site
			WakeLockInstance wli = findOrCreateWakeLock(new SSAProgramPoint(node, def));
			if (wli != null) {
				return wli;
			}
			//If not, descend deeper
			MethodReference methRef = ((SSAInvokeInstruction) def).getDeclaredTarget();
			return getMethodReturn(methRef);
		}
		else if (def instanceof SSAPiInstruction) {
			SSAPiInstruction phi = (SSAPiInstruction) def;
			for (int i = 0 ; i < phi.getNumberOfUses(); i++) {
				int use = phi.getUse(i);
				WakeLockInstance wli = traceWakeLockDef(use);
				//Return the first non null
				if (wli != null) {
					return wli;
				}
			}
			return null;
		}
		else if (def instanceof SSAPhiInstruction) {
			SSAPhiInstruction phi = (SSAPhiInstruction) def;
			for (int i = 0 ; i < phi.getNumberOfUses(); i++) {
				int use = phi.getUse(i);
				WakeLockInstance wli = traceWakeLockDef(use);
				//Return the first non null
				if (wli != null) {
					return wli;
				}
			}
			return null;
		}
		else {
			E.log(DEBUG, "COULD NOT MATCH: " + def.toString());			
		}
		return null;
	}
	
	/**
	 * Check the return instruction of each method and keep the info if it is 
	 * of interesting type. 
	 * @param ret
	 */
	private void scanReturnInstruction(SSAReturnInstruction ret) {
		TypeReference returnType = method.getReturnType();
		if (returnType.equals(TypeReference.PrimordialWakeLock) ||
			returnType.equals(TypeReference.ApplicationWakeLock)) {
			int returnedValue = ret.getUse(0);
			if (du == null) {
				du = new DefUse(ir);
			}
			WakeLockInstance wli = traceWakeLockDef(returnedValue);
			if (wli != null) {
				mMethodReturns.put(method.getReference(), wli);
				E.log(DEBUG, "RETURN: " + method.getName() + " :: " + wli.toString());
			}
			else {
				E.log(1, "Could not resolve return for: " + method.getSignature());
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
	
	private void dumpWakeLockInfo() {
		E.log(1, "==========================================\n");
		for (Entry<SSAProgramPoint, WakeLockInstance> e : mCreationRefs.entrySet()) {
			SSAProgramPoint key = e.getKey();
			WakeLockInstance value = e.getValue();
			E.log(1, key.toString() + " :: " + value.toString());
		}
		E.log(1, "==========================================\n");
		for (Entry<MethodReference, WakeLockInstance> e : mMethodReturns.entrySet()) {
			MethodReference key = e.getKey();
			WakeLockInstance value = e.getValue();
			E.log(1, key.toString() + " :: " + value.toString());
		}
		E.log(1, "==========================================\n");		
		for (Entry<Pair<MethodReference, SSAInstruction>, WakeLockInstance> e : mInstrToWakeLock.entrySet()) {
			Pair<MethodReference, SSAInstruction> key = e.getKey();
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
					ret.addAll(resolveLockTypeFromVar(bin.getUse(0)));
					ret.addAll(resolveLockTypeFromVar(bin.getUse(1)));
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
	
}
