package edu.ucsd.energy.analysis;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Acyclic;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;

import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.components.Component;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SSAProgramPoint;

public class IntentManager extends AbstractManager<IntentInstance>{
	
	private static int DEBUG = 1;
	
	public IntentManager(ComponentManager cm) {
		super(cm);
		cm.setIntentManager(this);
	}
	
	protected void firstPass() {
		super.firstPass();
	}
		
	public boolean isInterestingType(TypeReference typeReference) {
		if (typeReference != null) {
			return typeReference.equals(TypeReference.IntentType);
		}
		return false;
	}

	/**
	 * Using the Selector for the comparison because the class might 
	 * not be the default android one
	 */
	@Override
	Integer interestingMethod(MethodReference declaredTarget) {
		return Interesting.mIntentMethods.get(declaredTarget.getSelector());
	}

	@Override
	void visitInvokeInstruction(SSAInvokeInstruction instruction) {
		
		// TODO Auto-generated method stub
		
	}

	protected IntentInstance visitNewInstance(SSAInstruction instr) {
		IntentInstance ii = super.visitNewInstance(instr);
		//Get all uses just to check...
		int def = instr.getDef();
		for (Iterator<SSAInstruction> uses = du.getUses(def); uses.hasNext(); ) {
			SSAInstruction use = uses.next();
			if (use.toString().contains("<init>(Landroid/content/Context;Ljava/lang/Class;)V")) {
				visitIntentInit(use, ii);
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
				ii.setCalledType(calledType);
				E.log(DEBUG, "META: " + calledType.toString());
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
	
	public void dumpInfo() {
		E.log(1, "Dumping ...");
		E.log(1, "==========================================\n");
		for (Entry<SSAProgramPoint, IntentInstance> e : mCreationRefs.entrySet()) {
			IntentInstance value = e.getValue();
			E.log(1, value.toString());
		}
		E.log(1, "==========================================\n");
		for (Entry<Pair<MethodReference, SSAInstruction>, IntentInstance> e : mInstruction2Instance.entrySet()) {
			Pair<MethodReference, SSAInstruction> key = e.getKey();
			IntentInstance value = e.getValue();
			E.log(1, key.toString() + " :: " + value.toString());
		}
		E.log(1, "==========================================\n");		
	}

	public Graph<Component> getConstraintGraph() {
		SparseNumberedGraph<Component> g = new SparseNumberedGraph<Component>(1);
		
		for(Component c : cm.getComponents().values()) {
			g.addNode(c);
		}
		
		for(Entry<Pair<MethodReference, SSAInstruction>, IntentInstance> e : mInstruction2Instance.entrySet()) {
			MethodReference mr = e.getKey().fst;
			Set<Component> sComponent = cm.getContainingComponents(mr);
			for (Component c : sComponent) {
				//g.addEdge(c, c)
			}
		}
		/*
		for (Component src : cm.getComponents()) {
			  Collection<Component> threadDependencies = getThreadConstraints(src);
			  Assertions.productionAssertion(threadDependencies != null);
			  for (Component dst : threadDependencies) {
				  if ((src != null) && (dst != null)) {
					g.addEdge(src,dst);
				}
			  }
		  }
		  //Assert that there are no cycles in the component constraint graph.
		  com.ibm.wala.util.Predicate<Component> p = 
					new com.ibm.wala.util.Predicate<Component>() {		
				@Override
				public boolean test(Component c) {
					return Acyclic.isAcyclic(g, c);
				}
		  };
		  E.log(1, "Building constraint graph... " + GraphUtil.countEdges(g) + " edge(s).");
		  dumpConstraintGraph(g);
		  if (!com.ibm.wala.util.collections.Util.forAll(cc, p)) {
			  Assertions.UNREACHABLE("Cannot handle circular dependencies in thread calls. ");  
		  }
		  return g;
		*/
		return g;
	
	}

	
	
}
