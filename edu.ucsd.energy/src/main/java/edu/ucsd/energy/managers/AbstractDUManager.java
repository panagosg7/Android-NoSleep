package edu.ucsd.energy.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.json.JSONObject;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Iterator2List;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import edu.ucsd.energy.apk.AppCallGraph;
import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.GraphUtils;

public abstract class AbstractDUManager<V extends ObjectInstance>  {

	private int DEBUG = 0;

	protected GlobalManager gm;

	protected AppCallGraph cg;

	protected ComponentManager cm;

	//Keep track of current method under test
	protected CGNode node;

	protected DefUse du;

	protected IMethod method;

	protected IR ir;

	
	//TODO: check that these do not turn into leaks...
	// --> check sizes in the end 
	protected Map<FieldReference, V> mFieldRefs;

	protected Map<CreationPoint, V> mCreationRefs;

	protected Map<MethodReference, V> mMethodReturns;

	protected Map<Pair<IMethod, Integer>, V> mParamRefs;

	protected Set<IClass> interestingTypes;

	//A mapping for the instructions that perform the target action on the
	//particular instance. This would be a startActivity(), etc for Intents, 
	//run() for Runnables, and so on.
	protected Map<Pair<MethodReference, SSAInstruction>, V> mInstruction2Instance;

	Iterator2List<CGNode> bottomUpList;	//TODO: Compute this once for all managers (make static) 


	public AbstractDUManager(GlobalManager gm) {
		this.gm = gm;
		this.cg = gm.getAppCallGraph();
		this.cm = gm.getComponentManager();
		mCreationRefs = new HashMap<CreationPoint, V>();
		mMethodReturns = new HashMap<MethodReference, V>();
		mInstruction2Instance = new HashMap<Pair<MethodReference,SSAInstruction>, V>();
		setInterestingType();
	}

	abstract protected void setInterestingType();

	public void prepare() {
		if (DEBUG > 0) {
			System.out.println("Preparing " + getTag() + " manager ...");
		}
		//XXX: does this need to be bottom up??
		if (bottomUpList == null) {
			Iterator<CGNode> bottomUpIterator = GraphUtils.bottomUpIterator(cg);
			bottomUpList = new Iterator2List<CGNode>(bottomUpIterator, 
					new ArrayList<CGNode>(cg.getNumberOfNodes()));
		}
		firstPass();
		secondPass();
		sanityCheck();
	}

	abstract protected void sanityCheck();

	protected void firstPass() {
		for (CGNode n : bottomUpList) {
			node = n;
			du = null;
			method = n.getMethod();
			ir = n.getIR();
			if (ir == null) continue;		// Null for JNI methods
			
			scanMethodParameters();

			for (SSAInstruction instr : ir.getInstructions()) {
				visitInstruction(instr);
			}
		}
	}

	
	protected void secondPass() {
		for (CGNode n : bottomUpList) {
			node = n;
			ir = n.getIR();
			du = null;
			method = n.getMethod();
			if (ir == null) continue;		// Null for JNI methods
			for (SSAInstruction instr : ir.getInstructions()) {	
				visitInteresting(instr);
			}			
		}
	}

	public void dumpInfo() {
		System.out.println("==========================================\n");
		for (Entry<CreationPoint, V> e : mCreationRefs.entrySet()) {
			CreationPoint key = e.getKey();
			V value = e.getValue();
			System.out.println(key.toString() + " :: " + value.toString());
		}
		System.out.println("==========================================\n");
		for (Entry<MethodReference, V> e : mMethodReturns.entrySet()) {
			MethodReference key = e.getKey();
			V value = e.getValue();
			System.out.println(key.toString() + " :: " + value.toString());
		}
		System.out.println("==========================================\n");		
		for (Entry<Pair<MethodReference, SSAInstruction>, V> e : mInstruction2Instance.entrySet()) {
			Pair<MethodReference, SSAInstruction> key = e.getKey();
			V value = e.getValue();
			System.out.println(key.toString() + " :: " + value.toString());
		}
		System.out.println("==========================================\n");		
	}

	/**
	 * this is needed because the creators for Intents and Locks are not 
	 * the same (NewInstruction and InvokeInstructions respectively).
	 * @param instr
	 * @return
	 */
	abstract boolean isNewInstruction(SSAInstruction instr);

	abstract void visitInvokeInstruction(SSAInvokeInstruction instruction);

	/**
	 * Had to return the vi to be used by IntentManager...
	 * @param inv
	 * @return
	 */
	protected V visitNewInstance(SSAInstruction inv) {
		if (du == null) {
			du = new DefUse(ir);
		}
		CreationPoint cp = new CreationPoint(node, inv);
		if (DEBUG > 1) {
			System.out.println("In Method: " + method.getSignature());
			System.out.println("CP: " + cp.toString());
			System.out.println("new Inv: " + inv.toString());
		}
		
		V vi = findOrCreateInstance(cp);
		if (DEBUG > 0) {
			System.out.println("New instance: " + vi.toString());
		}
		//The use should be an instruction right next to the one creating the instance
		int defVar = inv.getDef();
		Iterator<SSAInstruction> uses = du.getUses(defVar);
		while(uses.hasNext()) {
			SSAInstruction useInstr = uses.next();
			if (useInstr instanceof SSAPutInstruction) {
				FieldReference field = ((SSAPutInstruction) useInstr).getDeclaredField();
				if (DEBUG > 1) {
					System.out.println("Try to associate with: " + field.toString());
				}
				associate(field,vi);
				break;		//assume there's just an assignment to a single field
			}
		}
		return vi;
	}

	
	/**
	 * Scan the typical parameters of the method and add the interesting ones 
	 * in the mCreationRefs mapping for future reference.
	 */
	private void scanMethodParameters() {
		for (int i = 0; i < method.getNumberOfParameters() ; i++) {
			
			int paramIndex = method.isStatic()?i:(i+1);
			
			findOrCreateInstance(method, paramIndex);
		}
	}
	

	/**
	 * This refers to methods that are checked after fields, etc. have
	 * been resolved.
	 * @param instr
	 */
	protected void visitInteresting(SSAInstruction instr) {
		if (instr instanceof SSAInvokeInstruction ) {
			SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
			//Gather interesting methods
			for(int i = 0 ; i < inv.getNumberOfUses(); i++) {
				V vi = traceInstanceNoCreate(inv.getUse(i));
				//This is an interesting method for which we don't know anything yet
				if ((vi != null) &&	(getTargetMethods(inv.getDeclaredTarget()) == null)
						//A lot of calls are pruned by this 
						&& (!(inv.toString().contains("<init>")))
						&& (!Interesting.ignoreIntentSelectors.contains(inv.getDeclaredTarget().getSelector()))) {
					if (DEBUG > 1) {
						System.out.println("Could not identify " + getTag() + 
								" method: " + inv.getDeclaredTarget().getSignature() + " arg: " + i);
					}
				}
			}

			//Is this an target (interesting) method?
			Pair<Integer, Set<Selector>> targs = getTargetMethods(inv.getDeclaredTarget());
			if (targs != null) {
				try {
					int use = inv.getUse(targs.fst);
					V vi = traceInstanceNoCreate(use);
					if (vi != null) {
						if (DEBUG > 1) {
							System.out.println("Got interesting method(" + method.getName().toString() + ") : " + inv.toString());
						}
						Pair<MethodReference, SSAInstruction> p = Pair.make(method.getReference(), instr);
						mInstruction2Instance.put(p, vi);
					}
				}
				catch (ArrayIndexOutOfBoundsException e) {
					//but if it's not, don't sweat it.
				}
			}

			//Handle some special cases
			handleSpecialCalls(inv);

		}
	}


	/**
	 * Give the opportunity to the specific manager to handle some 
	 * special method calls. 
	 * @param inv
	 */
	abstract protected void handleSpecialCalls(SSAInvokeInstruction inv);

	/**
	 * Check to see if the invoked method is an interesting one based on the 
	 * analysis we are performing and return the interesting argument index
	 * if it is, otherwise null.
	 * @param declaredTarget
	 * @return
	 */
	abstract Pair<Integer, Set<Selector>> getTargetMethods(MethodReference declaredTarget);

	public V getMethodReturn(MethodReference mr) {
		if (mMethodReturns != null) {
			return mMethodReturns.get(mr);
		}
		return null;
	}

	/**
	 * To be called from outside
	 * @return
	 */
	public V traceInstance(CGNode n, int var) {
		node = n;
		du = new DefUse(n.getIR());
		method = n.getMethod();
		//We probably don't want outsiders creating new instances
		return traceInstanceNoCreate(var);
	}

	/**
	 * Trace an SSA variable to an Instance
	 * This method creates an Instance if nothing is found.
	 * @param var
	 * @return
	 */
	protected V traceInstance(int var) {
		return traceInstance(var, MutableSparseIntSet.makeEmpty(), true);
	}

	/**
	 * traceInstance variant that does not create a new Instance if nothing is 
	 * found. This will return null in that case.
	 * @param var
	 * @return
	 */
	protected V traceInstanceNoCreate(int var) {
		return traceInstance(var, MutableSparseIntSet.makeEmpty(), false);
	}

	private V traceInstance(int var, MutableSparseIntSet set, boolean create) {
		if (du == null) {
			du = new DefUse(ir);
		}

		SSAInstruction def = null;
		try {
			def = du.getDef(var);
		}
		catch (ArrayIndexOutOfBoundsException e) { }	//TODO: fix this	
		if (def instanceof SSAGetInstruction) {
			SSAGetInstruction get = (SSAGetInstruction) def;
			V vi = create?findOrCreateInstance(get.getDeclaredField()):
				findInstance(get.getDeclaredField());
			return vi;
		}
		else if (def instanceof SSAInvokeInstruction) {
			//Try to see if this is a creation site
			V vi = create?
					findOrCreateInstance(new CreationPoint(node, def)):
						findInstance(new CreationPoint(node, def));
					if (vi != null) {
						return vi;
					}
					//If not, descend deeper
					MethodReference methRef = ((SSAInvokeInstruction) def).getDeclaredTarget();
					return getMethodReturn(methRef);
		}
		else if (def instanceof SSANewInstruction) {
			//Try to see if this is a creation site for Intents (for example)
			V vi = create?findOrCreateInstance(new CreationPoint(node, def)):
				findInstance(new CreationPoint(node, def));
			return vi;
		}
		else if (def instanceof SSAPiInstruction) {
			SSAPiInstruction phi = (SSAPiInstruction) def;
			for (int i = 0 ; i < phi.getNumberOfUses(); i++) {
				int use = phi.getUse(i);
				set.add(use);
				V vi = traceInstance(use, set, create);
				//Return the first non null
				if (vi != null) {
					return vi;
				}
			}
			return null;
		}
		else if (def instanceof SSAPhiInstruction) {
			SSAPhiInstruction phi = (SSAPhiInstruction) def;
			for (int i = 0 ; i < phi.getNumberOfUses(); i++) {
				int use = phi.getUse(i);
				//Be careful of recursive desf
				if (!set.contains(use)) {
					set.add(use);
					V vi = traceInstance(use, set, create);
					//Return the first non null
					if (vi != null) {
						return vi;
					}
				}
			}
			return null;
		}

		//Finally check if var is a method parameter or local variable
		//(def might be null here, so don't exit before checking this)
		V vi = create ? findOrCreateInstance(method, var) : findInstance(method, var);

		return vi;
		
	}


	private void visitInstruction(SSAInstruction instr ) {
		if (isNewInstruction(instr)) {
			visitNewInstance(instr);	
		}
		else if (instr instanceof SSAReturnInstruction) {
			visitReturnInstruction((SSAReturnInstruction) instr);
		}
		else if (instr instanceof SSAInvokeInstruction) {
			visitInvokeInstruction((SSAInvokeInstruction) instr);		    		
		}
	}

	
	private void visitReturnInstruction(SSAReturnInstruction ret) {
		if (isInterestingType(method.getReturnType())) {
			int returnedValue = ret.getUse(0);
			if (du == null) {
				du = new DefUse(ir);
			}
			V vi = traceInstance(returnedValue);
			if (vi != null) {
				mMethodReturns.put(method.getReference(), vi);
				if (DEBUG > 1) {
					System.out.println("Associating Return: " + method.getName() + " :: " + vi.toString());
				}
			}
			else {
				if (DEBUG > 1) {
					System.out.println("Could not resolve return for: " + method.getSignature());
				}
			}
		}
	}

	protected void associate(FieldReference fr, V vi) {
		if (DEBUG > 1) {
			System.out.println("Trying to associate: " + fr.getFieldType().getName().toString() + " with " + interestingTypes.toString());
		}
		if (!isInterestingType(fr.getFieldType())) return;	//associate only interesting stuff 
		if (mFieldRefs == null) {
			mFieldRefs = new HashMap<FieldReference, V>();
		}
		
		if (vi == null) return;
		
		vi.setField(fr);
		if (DEBUG > 1) {
			System.out.println("Indeed associating");
		}
		//mCreationRefs.put(vi.getPP(), vi);	//it's in mCreationRefs already
		mFieldRefs.put(fr, vi);
	}

	public V findOrCreateInstance(FieldReference field) {
		TypeReference fieldType = field.getFieldType();
		if (!isInterestingType(fieldType)) return null;	//only interesting stuff
		V vi = findInstance(field);
		if (vi == null) {
			vi = newInstance(field);
			mFieldRefs.put(field, vi);
		}
		return vi;
	}

	protected V findOrCreateInstance(CreationPoint cp) {
		if (!isInterestingType(cp.getType())) return null;	//only interesting stuff
		V vi = findInstance(cp);
		if (vi != null) return vi;
		vi = newInstance(cp);
		mCreationRefs.put(cp, vi);
		return vi;
	}

	/**	Find or create instances that are passed as parameters to
	 *	the current method.  
	 * @param method
	 * @param var
	 * @return null if the var is not a parameter to this method.
	 */
	private V findOrCreateInstance(IMethod method, int var) {
		
		int paramIndex = method.isStatic()?var:(var-1);	//getParameterType checks for static methods anyway.
		
		try {
			TypeReference parameterType = method.getParameterType(paramIndex);
			
			if (!isInterestingType(parameterType)) {
				return null;	//only interesting stuff
			}
			
			V vi = findInstance(method, var);
			if (vi != null) {
				return vi;
			}
			vi = newInstance(method, var);
			Pair<IMethod, Integer> p = Pair.make(method, new Integer(var));
			mParamRefs.put(p, vi);
			if (DEBUG > 0) {
				System.out.println("Method: " + method.getSignature());
				System.out.println("Adding method param: " + vi.toString() + " in mParamRefs");
				System.out.println("Adding to map: " + p);
			}
			return vi;
		} catch (IllegalArgumentException e) {
			
			if (DEBUG > 0) {
				E.yellow();
				System.out.println("Method: " + method.getSignature());
				System.out.println("Static: " + method.isStatic());
				System.out.println("Var: " + var + " -> " + paramIndex);
				for (int i = 0; i < method.getNumberOfParameters(); i++) {
					System.out.println("Param"+ i + ": " + method.getParameterType(i));
				}
				System.out.println("Value from symtab " + ir.getSymbolTable().getValueString(var));
				E.resetColor();
			}
			
			//drop exception
			return null;
			//throw e;
		}
	}

	
	public V findInstance(FieldReference field) {
		if (mFieldRefs == null) {
			mFieldRefs = new HashMap<FieldReference, V>();
		}
		return mFieldRefs.get(field);
	}


	public V findInstance(CreationPoint pp) {
		if (mCreationRefs == null) {
			mCreationRefs = new HashMap<CreationPoint, V>();
		}
		//Check to see if this is a lock created in this method
		V vi = mCreationRefs.get(pp);
		if (vi != null) {
			return vi;
		}
		//Check if to see if it is returned by a function.
		SSAInstruction instr = pp.getInstruction();
		if (instr instanceof SSAInvokeInstruction) {
			SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
			vi = mMethodReturns.get(inv.getDeclaredTarget());
			if (vi != null) {
				return vi;
			}			
		}
		return null;
	}


	private V findInstance(IMethod method, int var) {
		if (mParamRefs == null) {
			mParamRefs = new HashMap<Pair<IMethod,Integer>, V>();
		}
		Pair<IMethod, Integer> p = Pair.make(method, new Integer(var));
		return mParamRefs.get(p);
	}

	
	protected void forgetInstance(V vi) {
		CreationPoint pp = vi.getPP();
		mCreationRefs.remove(pp);
	}
	
	
	public V getInstance(SSAInstruction instr, CGNode node) {
		MethodReference reference = node.getMethod().getReference();		
		Pair<MethodReference, SSAInstruction> pair = Pair.make(reference, instr);
		return mInstruction2Instance.get(pair);		
	}

	
	public boolean isInterestingType(TypeReference fieldType) {
		if (fieldType == null) return false;
		IClass fieldClass = gm.getClassHierarchy().lookupClass(fieldType);
		if (fieldClass == null) return false;
		for (IClass interClass : interestingTypes) {
			if (gm.getClassHierarchy().isSubclassOf(fieldClass, interClass)) {
				return true;
			}
			if (gm.getClassHierarchy().implementsInterface(fieldClass, interClass)) {
				return true;
			}
		}
		return false;
	}

	
	public void setAppCallGraph(AppCallGraph cg){
		this.cg = cg;
	}

	abstract public V newInstance(CreationPoint pp);

	abstract public V newInstance(FieldReference field);

	abstract public V newInstance(IMethod m, int v);

	abstract public JSONObject toJSON();

	abstract public String getTag();

}

