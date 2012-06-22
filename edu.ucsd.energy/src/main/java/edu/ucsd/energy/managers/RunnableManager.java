package edu.ucsd.energy.managers;

import java.util.HashSet;
import java.util.Map;

import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.contexts.RunnableThread;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.results.ManagerReport;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SSAProgramPoint;

public class RunnableManager extends AbstractRunnableManager<RunnableInstance> {

	private static final int DEBUG = 0;

	public RunnableManager(GlobalManager gm) {
		super(gm);
	}
	
	public Map<Pair<MethodReference, SSAInstruction>, RunnableInstance> getGlobalInvocations() {
		return mInstruction2Instance;
	}
	
	public void prepare() {
		super.prepare();
		//dumpInfo();
	}
	
	@Override
	boolean isNewInstruction(SSAInstruction instr) {
		if (instr instanceof SSANewInstruction) {
			SSANewInstruction newi = (SSANewInstruction) instr;
			TypeName concreteType = newi.getConcreteType().getName();
			Context targetComponent = cm.getComponent(concreteType);
			return (targetComponent instanceof RunnableThread);
		}
		return false;
	}
	
	protected RunnableInstance visitNewInstance(SSAInstruction instr) {
		RunnableInstance ri = super.visitNewInstance(instr);
		if (instr instanceof SSANewInstruction) {
			SSANewInstruction newi = (SSANewInstruction) instr;
			TypeName concreteType = newi.getConcreteType().getName();
			E.log(DEBUG, "FROM SUPER: " + ri.toString());
			ri.setCalledType(concreteType);
			Context target = cm.getComponent(concreteType);
			if (target instanceof RunnableThread) {
				E.log(DEBUG, "  Assoc: " + target.toString());
				ri.setCalledComponent(target);
			}
		}
		return ri;		
	}
	
	@Override
	public String getTag() {
		return "Runnables";
	}
	
	public IReport getReport() {
		return new ManagerReport<RunnableManager>(this);		
	}

	@Override
	public RunnableInstance newInstance(SSAProgramPoint pp) {
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
		interestingTypes = new HashSet<TypeName>();
		interestingTypes.add(Interesting.RunnableType);
	}	
}
