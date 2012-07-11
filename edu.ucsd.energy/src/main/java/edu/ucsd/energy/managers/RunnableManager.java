package edu.ucsd.energy.managers;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.results.ManagerReport;


/**
 * This manager tries to resolve Runnable calls/posts based on the classes that 
 * are associated with them.
 * 
 * This is a broad category that include all cases where an object that 
 * implements the class Runnable can be started/posted/run etc. 
 * 
 * See the supported list of method calls in Interesting.java
 * 
 * Cases that are not supported include:
 * 	- Cases were the object that is being started cannot be associated with 
 * 		a single class that implements java/lang/Runnable.
 * 
 * 
 * @author pvekris
 *
 */
public class RunnableManager extends AbstractRunnableManager<RunnableInstance> {

	private static final int DEBUG = 0;

	
	public void prepare() {
		super.prepare();
		if(DEBUG > 0) {
			dumpInfo();
		}
	}
	
	
	@Override
	Pair<Integer, Set<Selector>> getTargetMethods(MethodReference declaredTarget) {
		return Interesting.mRunnableMethods.get(declaredTarget.getSelector());
	}
	
	@Override
	boolean isNewInstruction(SSAInstruction instr) {
		if (instr instanceof SSANewInstruction) {
			SSANewInstruction newi = (SSANewInstruction) instr;
			TypeReference typeRef = newi.getConcreteType();
			return isInterestingType(typeRef);
		}
		return false;
	}
	
	protected RunnableInstance visitNewInstance(SSAInstruction instr) {
		RunnableInstance ri = super.visitNewInstance(instr);
		if (instr instanceof SSANewInstruction) {
			SSANewInstruction newi = (SSANewInstruction) instr;
			TypeReference typeRef = newi.getConcreteType();
			TypeName concreteType = typeRef.getName();
			if (DEBUG > 0) {
				System.out.println(getTag() + " new Instance: " + ri.toString());
			}
			ri.setCalledType(concreteType);
			Component target = cm.getComponent(concreteType);
			if (isInterestingType(typeRef)) {
				//This really should be a component
				if ((target != null) && (target instanceof Component)) {
					if (DEBUG > 0) {
						System.out.println("Associated with: " + target.toString());
					}
				}
				//We should not let go of this info for the sake of sanity check later on 
			}
		}
		return ri;		
	}
	
	@Override
	public String getTag() {
		return "Runnable";
	}
	
	public IReport getReport() {
		return new ManagerReport<RunnableManager>(this);		
	}

	@Override
	public RunnableInstance newInstance(CreationPoint pp) {
		return new RunnableInstance(pp);
	}

	@Override
	public RunnableInstance newInstance(FieldReference field) {
		return new RunnableInstance(field);
	}

	
	@Override
	void visitInvokeInstruction(SSAInvokeInstruction instruction) {	}

	@Override
	protected void setInterestingType() {
		interestingTypes = new HashSet<IClass>();
		interestingTypes.add(gm.getClassHierarchy().lookupClass(Interesting.RunnableTypeRef));
	}

	@Override
	protected void handleSpecialCalls(SSAInvokeInstruction inv) {
		//Special case that kept coming up often
		//Handle the case of:  t0.<init>(t1);
		//t1 sets the type for t0
		MethodReference declaredTarget = inv.getDeclaredTarget();
		if (declaredTarget.getSelector().getName().toString().equals("<init>"/*(Ljava/lang/Runnable;)V*/)) {
			if (inv.getNumberOfParameters() > 1) {
				RunnableInstance t0 = traceInstance(inv.getUse(0));
				RunnableInstance t1 = traceInstance(inv.getUse(1));
				if (t0 != null && t1 != null) {
					if (DEBUG > 1) {
						System.out.println("Method: " + method);
						System.out.println("handling special call to : " + declaredTarget);
						System.out.println("t0.calledType <- " + t1.getCalledType());
					}
					t0.setCalledType(t1.getCalledType());
				}
			}
		}
	}

	@Override
	public RunnableInstance newInstance(IMethod m, int v) {
		return new RunnableInstance(m,v);
	}
}
