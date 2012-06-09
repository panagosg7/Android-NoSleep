package edu.ucsd.energy.managers;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;

import net.sf.json.JSONObject;

import com.ibm.wala.shrikeBT.IBinaryOpInstruction.IOperator;
import com.ibm.wala.shrikeBT.IBinaryOpInstruction.Operator;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.managers.WakeLockInstance.LockType;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.results.ManagerReport;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SSAProgramPoint;


/**
 * Interesting methods for this class are the wakelock operations
 * So after this module has run, mInstruction2Instance should have 
 * the acquire/release operations to instance mappings.  
 * @author pvekris
 *
 */

public class WakeLockManager extends AbstractDUManager<WakeLockInstance> {
	
	private static int DEBUG = 2;
	
	public enum RefCount {
		UNSET,
		TRUE,
		FALSE;		
	}

	public WakeLockManager(GlobalManager globalManager) {
		super(globalManager);
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
			for (Entry<SSAProgramPoint, WakeLockInstance> e : mCreationRefs.entrySet()) {
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
	Integer interestingMethod(MethodReference declaredTarget) {
		return Interesting.mWakelockMethods.get(declaredTarget);
	}

	@Override
	public	WakeLockInstance newInstance(SSAProgramPoint pp) {
		return new WakeLockInstance(pp);
	}

	@Override
	public WakeLockInstance newInstance(FieldReference field) {
		return new WakeLockInstance(field);
	}


	void visitInvokeInstruction(SSAInvokeInstruction inv) {
		//Reference Count
		 if (inv.toString().contains("setReferenceCounted")) {
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
		wli.getInfo().setReferenceCounted(refCounted?RefCount.TRUE:RefCount.FALSE);
		E.log(DEBUG, "Setting refCount");
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

	public void dumpInfo() {
		//super.dumpInfo();
	}

	@Override
	public String getTag() {
		return "WakeLocks";
	}

	@Override
	protected void setInterestingType() {
		interestingTypes = new HashSet<TypeName>();
		interestingTypes.add(Interesting.WakeLockType);		
	}

}
