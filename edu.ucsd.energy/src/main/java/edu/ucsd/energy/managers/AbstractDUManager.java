package edu.ucsd.energy.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.json.JSONObject;

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
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.collections.Iterator2List;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import edu.ucsd.energy.apk.AppCallGraph;
import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.GraphUtils;
import edu.ucsd.energy.util.SSAProgramPoint;

public abstract class AbstractDUManager<V extends ObjectInstance>  {

	private int DEBUG = 2;

	protected GlobalManager gm;
	
	protected AppCallGraph cg;
	
	protected ComponentManager cm;

	//Keep track of current method under test
	protected CGNode node;
	
	protected DefUse du;
	
	protected IMethod method;
	
	protected IR ir;
	
	protected Map<FieldReference, V> mFieldRefs;

	protected Map<SSAProgramPoint, V> mCreationRefs;

	protected Map<MethodReference, V> mMethodReturns;
	
	protected Set<TypeName> interestingTypes;

	protected Map<Pair<MethodReference, SSAInstruction>, V> mInstruction2Instance;
	
	Iterator2List<CGNode> bottomUpList;	//TODO: Compute this once for all managers (make static) 

	public AbstractDUManager(GlobalManager gm) {
		this.gm = gm;
		this.cg = gm.getAppCallGraph();
		this.cm = gm.getComponentManager();
		mCreationRefs = new HashMap<SSAProgramPoint, V>();
		mMethodReturns = new HashMap<MethodReference, V>();
		mInstruction2Instance = new HashMap<Pair<MethodReference,SSAInstruction>, V>();
		setInterestingType();
	}
	
	abstract protected void setInterestingType();

	public void prepare() {
		System.out.println("");
		E.log(1, "Preparing " + getTag() + " manager ...");
		//XXX: does this need to be bottom up??
		if (bottomUpList == null) {
			Iterator<CGNode> bottomUpIterator = GraphUtils.bottomUpIterator(cg);
			bottomUpList = new Iterator2List<CGNode>(bottomUpIterator, 
					new ArrayList<CGNode>(cg.getNumberOfNodes()));
		}
		firstPass();
		secondPass();
	}

	protected void firstPass() {
		for (CGNode n : bottomUpList) {
			node = n;
			/* Need to update these here */
			du = null;
			/* Null for JNI methods */
			method = n.getMethod();
			ir = n.getIR();
			if (ir == null) continue;
			//E.log(1, "Scanning for creation: " + n.getMethod().getSignature().toString());
			for (SSAInstruction instr : ir.getInstructions()) {
				visitInstruction(instr);
			}
		}
	}

	protected void secondPass() {
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
				visitInteresting(instr);
			}			
		}
	}
	
	public void dumpInfo() {
		System.out.println("==========================================\n");
		for (Entry<SSAProgramPoint, V> e : mCreationRefs.entrySet()) {
			SSAProgramPoint key = e.getKey();
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

	//abstract void visitNewInstruction(SSANewInstruction instruction);	//probably not going to need this
	
	/**
	 * Had to return the vi to be used by IntentManager...
	 * @param inv
	 * @return
	 */
	protected V visitNewInstance(SSAInstruction inv) {
		if (du == null) {
			du = new DefUse(ir);
		}
		SSAProgramPoint pp = new SSAProgramPoint(node, inv);
		V vi = findOrCreateInstance(pp);
		E.log(DEBUG, "NEW INSTANCE: " + vi.toString());
		//The use should be an instruction right next to the one creating the instance
		int defVar = inv.getDef();
		Iterator<SSAInstruction> uses = du.getUses(defVar);
		while(uses.hasNext()) {
			SSAInstruction useInstr = uses.next();
			if (useInstr instanceof SSAPutInstruction) {
				FieldReference field = ((SSAPutInstruction) useInstr).getDeclaredField();
				E.log(DEBUG, "TRY TO ASSOCIATE WITH: " + field.toString());
				associate(field,vi);				
				break;		//assume there's just an assignment to a single field
			}
		}
		return vi;
	}
	
	
	/**
	 * This refers to methods that are checked after the fields etc have
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
				if ((vi != null) &&	(interestingMethod(inv.getDeclaredTarget()) == null)
						&& (!(inv.toString().contains("<init>")))
						&& (!Interesting.ignoreSelectors.contains(inv.getDeclaredTarget().getSelector()))) {
					E.log(1, "MISSING: " + inv.getDeclaredTarget().getSignature() + " | " + i);
				}
				
			}
			
			//Is this an interesting method?
			Integer arg = interestingMethod(inv.getDeclaredTarget());
			if (arg != null) {
				try {
					int use = inv.getUse(arg);	//this should be the right argument
					V vi = traceInstanceNoCreate(use);
					if (vi != null) {
						E.log(DEBUG, "GOT INTERESTING (" + method.getName().toString() + ") : " + inv.toString());
						Pair<MethodReference, SSAInstruction> p = Pair.make(method.getReference(), instr);
						mInstruction2Instance.put(p, vi);
					}
				}
				catch (ArrayIndexOutOfBoundsException e) {
					//but if it's not, don't sweat it.
				}
			}
		}
	}

	/**
	 * Check to see if the invoked method is an interesting one based on the 
	 * analysis we are performing and return the interesting argument index
	 * if it is, otherwise null.
	 * @param declaredTarget
	 * @return
	 */
	abstract Integer interestingMethod(MethodReference declaredTarget);

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
		if (du==null) {
			du = new DefUse(ir);
		}
		SSAInstruction def = null;
		try {
			def = du.getDef(var);
		}
		catch (ArrayIndexOutOfBoundsException e) { }	//TODO: fix this	
		if (def == null) return null;
		if (def instanceof SSAGetInstruction) {
			SSAGetInstruction get = (SSAGetInstruction) def;
			V vi = create?
					findOrCreateInstance(get.getDeclaredField()):
					findInstance(get.getDeclaredField());
			return vi;
		}
		else if (def instanceof SSAInvokeInstruction) {
			//Try to see if this is a creation site
			V vi = create?
					findOrCreateInstance(new SSAProgramPoint(node, def)):
					findInstance(new SSAProgramPoint(node, def));
			//V vi = findInstance(new SSAProgramPoint(node, def));
			if (vi != null) {
				return vi;
			}
			//If not, descend deeper
			MethodReference methRef = ((SSAInvokeInstruction) def).getDeclaredTarget();
			return getMethodReturn(methRef);
		}
		else if (def instanceof SSANewInstruction) {
			//Try to see if this is a creation site for Intents (for example)
			V vi = create?
					findOrCreateInstance(new SSAProgramPoint(node, def)):
					findInstance(new SSAProgramPoint(node, def));
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
		return null;
	}
		
	private void visitInstruction(SSAInstruction instr ){
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

	/*
	 * We were able to make this generic
	 */
	private void visitReturnInstruction(SSAReturnInstruction ret) {
		if (isInterestingType(method.getReturnType().getName())) {
			int returnedValue = ret.getUse(0);
			if (du == null) {
				du = new DefUse(ir);
			}
			V vi = traceInstance(returnedValue);
			if (vi != null) {
				mMethodReturns.put(method.getReference(), vi);
				E.log(DEBUG, "RETURN: " + method.getName() + " :: " + vi.toString());
			}
			else {
				E.log(DEBUG, "Could not resolve return for: " + method.getSignature());
			}
		}
	}
	
	protected void associate(FieldReference fr, V vi) {
		E.log(DEBUG, "Trying to associate: " + fr.getFieldType().getName().toString() + " with " + interestingTypes.toString()); 
		if (!isInterestingType(fr.getFieldType().getName())) return;	//associate only interesting stuff 
		if (mFieldRefs == null) {
			mFieldRefs = new HashMap<FieldReference, V>();
		}
		
		vi.setField(fr);
		E.log(DEBUG, "Indeed associating");
		//mCreationRefs.put(vi.getPP(), vi);	//it's in mCreationRefs already
		mFieldRefs.put(fr, vi);
	}

	public V findOrCreateInstance(FieldReference field) {
		if (!isInterestingType(field.getFieldType().getName())) return null;	//only interesting stuff 
		if (mFieldRefs == null) {
			mFieldRefs = new HashMap<FieldReference, V>();
		}
		V vi = mFieldRefs.get(field);
		if (vi == null) {
			vi = newInstance(field);
			mFieldRefs.put(field, vi);
		}
		return vi;
	}

	protected V findOrCreateInstance(SSAProgramPoint pp) {
		V vi = findInstance(pp);
		if (vi != null) return vi;
		vi = newInstance(pp);
		mCreationRefs.put(pp, vi);
		return vi;
	}


	public V findInstance(FieldReference field) {
		if (mFieldRefs == null) {
			mFieldRefs = new HashMap<FieldReference, V>();
		}
		//Check to see if this is a lock created in this method
		V vi = mFieldRefs.get(field);
		return vi;
	}
	
	
	public V findInstance(SSAProgramPoint pp) {
		if (mCreationRefs == null) {
			mCreationRefs = new HashMap<SSAProgramPoint, V>();
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
	
	public V getInstance(SSAInstruction instr, CGNode node) {
		MethodReference reference = node.getMethod().getReference();		
		Pair<MethodReference, SSAInstruction> pair = Pair.make(reference, instr);
		return mInstruction2Instance.get(pair);		
	}
	
	public boolean isInterestingType(TypeName typeName) {
		//WARNING: TypeName does equals by object id
		for (TypeName t : interestingTypes) {
			if (typeName.toString().equals(t.toString())) {
				return true;
			}
		}
		return false;
	}
	
	public void setAppCallGraph(AppCallGraph cg){
		this.cg = cg;
	}

	abstract public V newInstance(SSAProgramPoint pp);

	abstract public V newInstance(FieldReference field);

	abstract public JSONObject toJSON();
	
	abstract public String getTag();
	
}
