package edu.ucsd.energy.managers;

import java.util.HashSet;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.contexts.Context;
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

	public RunnableManager(GlobalManager gm) {
		super(gm);
	}
	
	public void prepare() {
		super.prepare();
		if(DEBUG > 0) {
			dumpInfo();
		}
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
			Context target = cm.getComponent(concreteType);
			if (isInterestingType(typeRef)) {
				if (target != null) {
					if (DEBUG > 0) {
						System.out.println("Associated with: " + target.toString());
					}
					ri.setCalledComponent(target);
				}
				else {
					//If we cannot find the specific context that is called here, 
					//we should not really have the Runnable Instance in our maps.
					forgetInstance(ri);
					if (DEBUG > 0) {
						System.out.println("Forgetting: " + ri.toString());
					}
				}
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
		// TODO Auto-generated method stub
		
	}

	@Override
	public RunnableInstance newInstance(IMethod m, int v) {
		return new RunnableInstance(m,v);
	}	
}
