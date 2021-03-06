package edu.ucsd.energy.managers;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.json.JSONObject;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.IOperator;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.managers.WakeLockInstance.LockType;
import edu.ucsd.energy.managers.WakeLockInstance.WakeLockInfo;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.results.ManagerReport;
import edu.ucsd.energy.util.Log;
import edu.ucsd.energy.util.SSAProgramPoint;

/**
 * Interesting methods for this class are the wakelock operations
 * So after this module has run, mInstruction2Instance should have 
 * the acquire/release operations to instance mappings.  
 * @author pvekris
 *
 */
public class WakeLockManager extends AbstractDUManager<WakeLockInstance> {
	
	private static int DEBUG = 0;
	
	public enum RefCount {
		UNSET,
		TRUE,
		FALSE;		
	}

	private boolean sanityChecked = false;

	public void prepare() {
		super.prepare();
		if(DEBUG > 0) {
			dumpInfo();
		}
	}
	
	
	public JSONObject toJSON() {
		JSONObject result = new JSONObject();
		JSONObject obj = new JSONObject();
		if (mFieldRefs != null) {
			for (Entry<FieldReference, WakeLockInstance> e : mFieldRefs.entrySet()) {
				obj.put(e.getKey().toString(), e.getValue().toJSON());
			}
		}
		result.put("fields", obj);
		obj = new JSONObject();
		if (mCreationRefs != null) {
			for (Entry<CreationPoint, WakeLockInstance> e : mCreationRefs.entrySet()) {
				obj.put(e.getKey().toString(), e.getValue().toJSON());
			}
		}
		result.put("creation_sites", obj);
		obj = new JSONObject();
		if (mInstruction2Instance != null) {
			int i = 1;
			for (Entry<Pair<MethodReference, SSAInstruction>, WakeLockInstance> e : mInstruction2Instance.entrySet()) {
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
		result.put("calls", obj);
		return result;
	}

	
	public WakeLockInstance newInstance() {
		return null;
	}

	public IReport getReport() {
		return new ManagerReport<WakeLockManager>(this);		
	}


	@Override
	Pair<Integer, Set<Selector>> getTargetMethods(MethodReference declaredTarget) {
		return Interesting.mWakelockMethods.get(declaredTarget);
	}

	@Override
	public	WakeLockInstance newInstance(CreationPoint pp) {
		return new WakeLockInstance(pp);
	}

	@Override
	public WakeLockInstance newInstance(FieldReference field) {
		return new WakeLockInstance(field);
	}


	void visitInvokeInstruction(SSAInvokeInstruction inv) {
		//Reference Count
		 if (inv.toString().contains("WakeLock, setReferenceCounted")) {
			 visitReferenceCounted(inv);
		 }
	}

	private void visitReferenceCounted(SSAInvokeInstruction inv) {
		if (du == null) {
			du = new DefUse(ir);
		}						
		int bit = inv.getUse(1);
		boolean refCounted = ir.getSymbolTable().isTrue(bit);
		int use = inv.getUse(0);
		//WakeLockInstance wli = traceInstanceNoCreate(use);
		WakeLockInstance wli = traceInstance(use);
		WakeLockInfo info = wli.getInfo();
		info.setReferenceCounted(refCounted?RefCount.TRUE:RefCount.FALSE);
	}

	
	public WakeLockInstance visitNewInstance(SSAInstruction instr) {
		WakeLockInstance wi = super.visitNewInstance(instr);
		//Also track the type of the wakelock
		if (instr instanceof SSAInvokeInstruction) {
			SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
			Collection<LockType> lockType = resolveLockTypeFromVar(inv.getUse(1));
			wi.setLockType(lockType);
		}
		return wi;
	}
	
	private Collection<LockType> resolveLockTypeFromVar(int use) {	
		try {
			int intValue = ir.getSymbolTable().getIntValue(use);
			return  getLockType(intValue);
		}
		catch (IllegalArgumentException e) {
		//The 1st parameter of newWakeLock was not a constant int, 
		//so we'll have to check what it is ...
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

	private Collection<LockType> getLockType(int i) {		
		HashSet<LockType> result = new HashSet<LockType>();
		for (LockType lt : LockType.values()) {
			if ((i & lt.getCode()) == lt.getCode() ) {
				result.add(lt);
			}
		}
		return result;		
	}


	@Override
	boolean isNewInstruction(SSAInstruction instr) {
		if (instr instanceof SSAInvokeInstruction) {
			return instr.toString().contains("newWakeLock");
		}
		return false;
	}


	protected void sanityCheck() {
		int unresolved = (unresolvedCallSites != null)?unresolvedCallSites.size():0;
		if (unresolved == 0) {
			Log.green();
		}
		else {
			Log.yellow();
		}
		int size = mInstruction2Instance.size();
		Log.println(size + " / " + (size + unresolved) + " " + getTag() +" call sites were resolved successfully.");
		Log.resetColor();
		sanityChecked  = true;
	}
	
	
	
	public boolean hasUnresolvedWakeLockOperations() {
		if (!sanityChecked) {
			prepare();
		}
		int unresolved = (unresolvedCallSites != null)?unresolvedCallSites.size():0;
		return (unresolved > 0);
	}
	
	public boolean hasWakeLockOperations() {
		if (!sanityChecked) {
			prepare();
		}
		return (mInstruction2Instance.size() > 0);
	}
	
	
	
	@Override
	public String getTag() {
		return "WakeLock";
	}

	@Override
	protected void setInterestingType() {
		interestingTypes = new HashSet<IClass>();
		IClass lookupClass = gm.getClassHierarchy().lookupClass(Interesting.WakeLockTypeRef);
		if (lookupClass == null) {
			System.out.println("Setting null interesting");
		}
		IClass lookupClass1 = gm.getClassHierarchy().lookupClass(Interesting.WakeLockTypeRefExt);
		if (lookupClass1 == null) {
			System.out.println("Setting null interesting extension");
		}
		interestingTypes.add(lookupClass);		
	}


	@Override
	protected void handleSpecialCalls(SSAInvokeInstruction inv) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public WakeLockInstance newInstance(IMethod m, int v) {
		return new WakeLockInstance(m,v);
	}


	@Override
	void accountUntraceable(SSAInvokeInstruction inv) {
		Log.flog(getTag() + "operation in method" + method + " was not resolved successfully.");
		Log.flog("  target instruction: " +	inv.toString());
		Log.flog("");
		
		if (unresolvedCallSites == null) {
			unresolvedCallSites = new HashSet<Pair<MethodReference,SSAInvokeInstruction>>();
		}
		Pair<MethodReference, SSAInvokeInstruction> key = Pair.make(method.getReference(), inv);
		unresolvedCallSites.add(key);
	}

}
