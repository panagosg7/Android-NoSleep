package edu.ucsd.energy.managers;

import java.util.HashSet;
import java.util.Iterator;

import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.results.ManagerReport;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SSAProgramPoint;

public class IntentManager extends AbstractRunnableManager<IntentInstance> {

	//Intent specific	
	private static final Selector INIT_SELECTOR = 
			Selector.make("<init>(Landroid/content/Context;Ljava/lang/Class;)V");
	
	/**
	 * Using the Selector for the comparison because the class might 
	 * not be the default android one
	 */
	@Override
	Integer interestingMethod(MethodReference declaredTarget) {
		return Interesting.mIntentMethods.get(declaredTarget.getSelector());
	}
	///
	
	private static int DEBUG = 2;
	
	public IntentManager(GlobalManager cm) {
		super(cm);
	}


	public void prepare() {
		super.prepare();
		if(DEBUG < 2) {
			dumpInfo();
		}
	}

	
	
	protected IntentInstance visitNewInstance(SSAInstruction instr) {
		IntentInstance ii = super.visitNewInstance(instr);
		//Get all uses just to check...
		int def = instr.getDef();
		for (Iterator<SSAInstruction> uses = du.getUses(def); uses.hasNext(); ) {
			SSAInstruction use = uses.next();
			if (use instanceof SSAInvokeInstruction) {
				SSAInvokeInstruction inv = (SSAInvokeInstruction) use;
				if (inv.getDeclaredTarget().getSelector().equals(INIT_SELECTOR)) {
					visitIntentInit(use, ii);
				}
			}
		}
		return ii;
	}
	
	/**
	 * Try to figure out what kind of class is associated with this Intent. 
	 * There are cases where this depends heavily on dynamic info, like for 
	 * example:
	 * NetCounter:
	 * net/jaqpot/netcounter/activity/NetCounterActivity$2.onClick:
	 * 	paramInt = Uri.parse(this.this$0.getString(2131099665));
     * 	paramDialogInterface = new android/content/Intent;
     * 	paramDialogInterface.<init>("android.intent.action.VIEW", paramInt);
	 */
	private void visitIntentInit(SSAInstruction inv, IntentInstance ii) {		
		SSAInstruction def = du.getDef(inv.getUse(2));
		if (def instanceof SSALoadMetadataInstruction) {
			SSALoadMetadataInstruction meta = (SSALoadMetadataInstruction) def;
			try {
				TypeReference calledType = (TypeReference) meta.getToken();
				ii.setCalledType(calledType.getName());
				E.log(DEBUG, "META: " + ii.toString());
			}
			catch(Exception e) {
				//This is to act against the case that 
				//token is not a TypeReference
			}
		}
	}

	@Override
	public IntentInstance newInstance(SSAProgramPoint pp) {
		return new IntentInstance(pp);
	}

	@Override
	public IntentInstance newInstance(FieldReference field) {
		return new IntentInstance(field);
	}

	@Override
	boolean isNewInstruction(SSAInstruction instr) {
		if (instr instanceof SSANewInstruction) {
			SSANewInstruction newi = (SSANewInstruction) instr;
			/*
			//TODO: this should work - adds some extra precision... 
			AppClassHierarchy appch = cg.getAppClassHierarchy();
			ClassHierarchy ch = appch.getClassHierarchy();
			IClass lookupClass = ch.lookupClass(newi.getConcreteType());
			if (lookupClass != null) {
				ArrayList<IClass> classAncestors = appch.getClassAncestors(lookupClass);
				for (IClass c : classAncestors) {
					if (c.getReference().toString().contains("Intent")) {
						return true;
					}
				}
			}
			*/
			return newi.getConcreteType().toString().
					equals("<Application,Landroid/content/Intent>");
		}
		return false;
	}

	public IReport getReport() {
		return new ManagerReport<IntentManager>(this);		
	}

	@Override
	public String getTag() {
		return "Intents";
	}

	@Override
	void visitInvokeInstruction(SSAInvokeInstruction instruction) {	}


	@Override
	protected void setInterestingType() {
		interestingTypes = new HashSet<TypeName>();
		interestingTypes.add(Interesting.IntentType);		
	}

	
}
