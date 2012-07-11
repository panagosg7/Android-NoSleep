package edu.ucsd.energy.managers;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.results.ManagerReport;
import edu.ucsd.energy.util.Log;


/**
 * This manager tries to resolve Intent calls based on the classes that 
 * are associated with them.
 * 
 *  Cases that are not going to work:
 *  - Wherever the corresponding component is determined by 
 *  	PackageManager.resolveActivity()
 *  - The class passed as a parameter to the creation of the Intent cannot 
 *  	be resolved
 *  - The constructor of the intent is Intent(). In these it is hard to guess 
 *  	what the target Component is.
 *  - Intent info is passed as a parameter to the current method, and therefore 
 *  	cannot be specified, as we're not performing an inter-procedural analysis
 *  	at this point. 
 *  
 *  Also, cases where an explicit Action is specified at the creation of 
 *  the Intent are ignored, as the target Component cannot be determined. 
 *
 *  TODO: for the cases where the Component called cannot be resolved, we can 
 *  try all possible Components.
 *  
 *  ***OR, only report a problem if the call-site of the Intent (startActivity, 
 *  etc), is done while the lock is held. Otherwise, it will be the called Context's 
 *  responsibility to behave well, and that Context will be examined separately.
 *  So what we need to do is remember the unresolved call-sites and perform the 
 *  checks after the analysis has run (in all possible contexts)
 *  
 *   
 * @author pvekris
 *
 */
public class IntentManager extends AbstractRunnableManager<IntentInstance> {

	private static int DEBUG = 0;	

	/**
	 * Using the Selector for the comparison because the class might 
	 * not be the default android one
	 */
	@Override
	Pair<Integer, Set<Selector>> getTargetMethods(MethodReference declaredTarget) {
		return Interesting.mIntentMethods.get(declaredTarget.getSelector());
	}

	public void prepare() {
		super.prepare();
		if(DEBUG > 0) {
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
				MethodReference declaredTarget = inv.getDeclaredTarget();
				if (declaredTarget.isInit()) {
					visitIntentInit(inv, ii);
				}
			}
		}
		return ii;
	}


	/**
	 * Try to figure out what kind of class is associated with this Intent. 
	 */
	private void visitIntentInit(SSAInvokeInstruction inv, IntentInstance ii) {
		MethodReference declaredTarget = inv.getDeclaredTarget();
		Selector selector = declaredTarget.getSelector();

		if (selector.equals(Selector.make("<init>(Landroid/content/Context;Ljava/lang/Class;)V"))) {
			//Create an intent for a specific component.
			//The second argument specifies the called component class
			setCalledType(inv, 2, ii);
		}
		else if (	selector.equals(Selector.make("<init>(Ljava/lang/String;Landroid/net/Uri;)V")) ||
				selector.equals(Selector.make("<init>(Ljava/lang/String;)V"))) {
			//Create an intent with a given action (and data Uri)
			int use = inv.getUse(1);
			if (ir.getSymbolTable().isConstant(use)) {
				Object constantValue = ir.getSymbolTable().getConstantValue(use);
				ii.setActionString((String) constantValue);
			}
		}
		else if (	selector.equals(Selector.make("<init>()V"))) {
			//No information is passed here
		}
		else if (	selector.equals(Selector.make("<init>(Ljava/lang/String;" +
				"Landroid/net/Uri;Landroid/content/Context;Ljava/lang/Class;)V"))) {
			//Create an intent for a specific component with a specified action and data.
			//The fourth argument specifies the called component class
			setCalledType(inv, 4, ii);
		}
		else {
			Log.flog("Intent selector not handled: " + selector);
			Log.flog(inv.toString());
		}

	}

	private void setCalledType(SSAInvokeInstruction inv, int i, IntentInstance ii) {
		SSAInstruction def = du.getDef(inv.getUse(2));
		if (def instanceof SSALoadMetadataInstruction) {
			SSALoadMetadataInstruction meta = (SSALoadMetadataInstruction) def;
			try {
				TypeReference calledType = (TypeReference) meta.getToken();
				ii.setCalledType(calledType.getName());
				if (DEBUG > 0) {
					System.out.println("Added meta to: " + ii.toString());
				}
			}
			catch(Exception e) {
				//This is to act against the case that 
				//token is not a TypeReference
			}
		}		
	}

	@Override
	protected void handleSpecialCalls(SSAInvokeInstruction inv) {
		Selector methSel = inv.getDeclaredTarget().getSelector();

		if (methSel.equals(Selector.make("setComponent(Landroid/content/ComponentName;)Landroid/content/Intent"))) {
			//TODO: this will not be so easy due to resolving ComponentName
			Log.flog("Setting Component: " + method);
			Log.flog("  " + inv.toString());
		}
		if (methSel.toString().contains("setClassName")) {
			//TODO


			Log.flog("Could not handle special Intent call to: " + methSel.toString());
			Log.flog("  in method: " + method);
		}

		if (methSel.toString().contains("setClass")) {
			//the Intent is the 0th parameter
			IntentInstance ii = traceInstanceNoCreate(inv.getUse(0));
			if (ii != null) {
				if (DEBUG > 0) {
					System.out.println("Meth: " + methSel.toString());
					System.out.println("Setting Component: " + inv.toString());
				}
				setCalledType(inv, 2, ii);	
			}
			else {
				//XXX: throw a better warning
				Log.flog("Could not resolve: " + method);
				Log.flog("  calling: " + inv.toString());
			}
		}

		//Explicit stop of a service
		if (methSel.equals(Selector.make("stopService(Landroid/content/Intent;)Z"))) {

			IntentInstance ii = traceInstanceNoCreate(inv.getUse(0));
			if (ii != null) {
				if (DEBUG > 0) {
					Log.green();
					System.out.println("Meth: " + methSel.toString());
					System.out.println("Stopping Component: " + inv.toString());
					System.out.println(ii.toString());
					Log.resetColor();
				}
			}
			else {
				Log.flog("Stopping An Unresolved Intent Component: " + inv.toString());
			}
		}
	}


	protected IntentInstance traceInstance(int var, MutableSparseIntSet set, boolean create) {
		if (du == null) {
			du = new DefUse(ir);
		}
		SSAInstruction def = null;
		try {
			def = du.getDef(var);
		}
		catch (ArrayIndexOutOfBoundsException e) { }	//TODO: fix this	

		IntentInstance ii = super.traceInstance(var, set, create);
		if (ii == null) {
			if (def instanceof SSAInvokeInstruction) {
				//If not, descend deeper
				MethodReference methRef = ((SSAInvokeInstruction) def).getDeclaredTarget();
				//Search for the *this* value of a putExtra method. PutExtra methods
				//should not be treated as Intent creation methods, just as 
				//reflector of the Intent passed as *this*.
				//This could get a bit more precise, but there are too many cases
				if (methRef.getName().toString().contains("putExtra")) {
					//System.out.println("AAAA: " + method);
					//System.out.println("AAAA: " + def);
					int use = def.getUse(0);
					ii = traceInstance(use, set, create);
					//System.out.println("AAAA: using: " + ii);
				}
			}
		}
		return ii;
	}


	@Override
	public IntentInstance newInstance(CreationPoint pp) {
		return new IntentInstance(pp);
	}

	@Override
	public IntentInstance newInstance(FieldReference field) {
		return new IntentInstance(field);
	}

	@Override
	public IntentInstance newInstance(IMethod m, int v) {
		return new IntentInstance(m,v);
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
		return "Intent";
	}

	@Override
	void visitInvokeInstruction(SSAInvokeInstruction instruction) {	}


	@Override
	protected void setInterestingType() {
		interestingTypes = new HashSet<IClass>();
		interestingTypes.add(gm.getClassHierarchy().lookupClass(Interesting.IntentTypeRef));		
	}



}

