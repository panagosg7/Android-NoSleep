package edu.ucsd.energy.managers;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.json.JSONObject;

import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Acyclic;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;

import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.apk.Interesting;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.GraphUtils;
import edu.ucsd.energy.util.SSAProgramPoint;

public abstract class AbstractRunnableManager<V extends AbstractRunnableInstance> extends AbstractDUManager<V> { 

	private final int DEBUG = 1;

	
	private SparseNumberedGraph<Context> constraintGraph;

	public AbstractRunnableManager(GlobalManager gm) {
		super(gm);
	}

	@Override
	Integer interestingMethod(MethodReference declaredTarget) {
		return Interesting.mRunnableMethods.get(declaredTarget.getSelector());
	}


	/**
	 * Should we fix the call graph here to incorporate the asynchronous intent calls?
	 * This is not going to be easy...
	 */
	/*
	private void fixCallGraph() {
		Map<Selector, Set<CallSiteReference>> map = new HashMap<Selector, Set<CallSiteReference>>();
		for (Entry<Pair<MethodReference, SSAInstruction>, V> e : mInstruction2Instance.entrySet()) {
			SSAInstruction instr = e.getKey().snd;
			if(instr instanceof SSAInvokeInstruction) {
				SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
				Set<Selector> entryPoints = e.getValue().getCalledComponent().getEntryPoints();
				for(Selector ep : entryPoints) {
					Set<CallSiteReference> set = map.get(ep);
					if (set == null) {
						set = new HashSet<CallSiteReference>();
					}
					set.add(inv.getCallSite());
					map.put(ep, set);	
				}
			}
		}
		for(Iterator<CGNode> it = cg.iterator(); it.hasNext();) {
			CGNode next = it.next();
			Selector selector = next.getMethod().getSelector();
			Set<CallSiteReference> set = map.get(selector);
			for (CallSiteReference cs : set) {
				//TODO: add the edges in the callgraph!!!
			}
		}
	}
	*/
	
	
	public void computeConstraintGraph() {
		if (DEBUG > 1) {
			System.out.println("Computing constraint graph for " + getTag());
		}
		constraintGraph = new SparseNumberedGraph<Context>(1);

		Collection<Context> sComponents = cm.getComponents();
		for(Context c : sComponents) {
			constraintGraph.addNode(c);
		}

		for(Entry<Pair<MethodReference, SSAInstruction>, V> e : mInstruction2Instance.entrySet()) {
			MethodReference mr = e.getKey().fst;
			Set<Context> sFrom = cm.getContainingComponents(mr);
			if (DEBUG > 1) {
				System.out.println("Calls from: " + mr.toString());
			}
			if (sFrom != null) {
				TypeName calledType = e.getValue().getCalledType();
				if (calledType != null) {
					Context to = cm.getComponent(calledType);
					if (to != null) {
						if (DEBUG > 1) {
							System.out.println("\tCalled Type: " + calledType);
						}
						for (Context from : sFrom) {
							if (DEBUG > 1) {
								System.out.println("\t" + to.toString() + " <-- " + from.toString());
							}
							//Component "to" will be started with the state of 
							//component "from" at the specific program point
							SSAInstruction instr = e.getKey().snd;
							//Pair.make(e.getKey().snd, method)
							to.addSeed(from, instr);
							constraintGraph.addEdge(from, to);
						}
					}
				}
			}
		}
		if (DEBUG > 1) {
			System.out.println("Building abstract " + getTag() + " constraint graph... " + GraphUtil.countEdges(constraintGraph) + " edge(s).");
		};
		testAcyclic(constraintGraph);
	}

	private void testAcyclic(final SparseNumberedGraph<Context> g) {
		if (DEBUG > 1) {
			System.out.println("Testing for cycles... ");
		}
		//Assert that there are no cycles in the component constraint graph.
		com.ibm.wala.util.Predicate<Context> p = 
				new com.ibm.wala.util.Predicate<Context>() {		
			@Override
			public boolean test(Context c) {
				return Acyclic.isAcyclic(g, c);
			}
		};

		if (Opts.FAIL_AT_DEPENDENCY_CYCLES &&
				(!com.ibm.wala.util.collections.Util.forAll(GraphUtil.inferRoots(g), p))) {
			//GraphUtils.dumpConstraintGraph(g, getTag());
			Assertions.UNREACHABLE("Cannot handle circular dependencies in thread calls. ");  
		}
	} 


	public void dumpInfo() {
		System.out.println("\nDumping " + getTag());
		System.out.println("==========================================");
		for (Entry<SSAProgramPoint, V> e : mCreationRefs.entrySet()) {
			V value = e.getValue();
			System.out.println(value.toString());
		}
		System.out.println("==========================================");
		for (Entry<Pair<MethodReference, SSAInstruction>, V> e : mInstruction2Instance.entrySet()) {
			Pair<MethodReference, SSAInstruction> key = e.getKey();
			V value = e.getValue();
			System.out.println(key.toString() + " :: " + value.toString());
		}
		System.out.println("==========================================\n");		
	}



	public JSONObject toJSON() {
		JSONObject result = new JSONObject();
		JSONObject obj = new JSONObject();
		/*
		if (mFieldRefs != null) {
			for (Entry<FieldReference, V> e : mFieldRefs.entrySet()) {
				obj.put(e.getKey().toString(), e.getValue().toJSON());
			}
		}
		result.put("fields", obj);
		 */
		obj = new JSONObject();
		if (mCreationRefs != null) {
			for (Entry<SSAProgramPoint, V> e : mCreationRefs.entrySet()) {
				obj.put(e.getKey().toString(), e.getValue().toJSON());
			}
		}
		result.put("creation_sites", obj);
		obj = new JSONObject();
		if (mInstruction2Instance != null) {
			int i = 1;
			for (Entry<Pair<MethodReference, SSAInstruction>, V> e : mInstruction2Instance.entrySet()) {
				MethodReference methRef = e.getKey().fst;
				SSAInstruction instr = e.getKey().snd;
				AbstractRunnableInstance ri = e.getValue();
				if (instr instanceof SSAInvokeInstruction) {
					JSONObject o = new JSONObject();
					o.put("caller", methRef.getSignature());
					TypeName comp = ri.getCalledType();
					o.put("target type", (comp==null)?"NO_TARGET":comp.toString());
					FieldReference field = ri.getField();
					o.put("field", (field==null)?"NO_FIELD":field.toString());
					SSAProgramPoint pp = ri.getPP();
					o.put("created", (pp==null)?"NO_PROG_POINT":pp.toString());
					obj.put(Integer.toString(i++),o);
				}
			}
		}
		result.put("calls", obj);
		return result;
	}

	abstract public IReport getReport();

	public SparseNumberedGraph<Context> getConstraintGraph() {
		if (constraintGraph == null) {
			computeConstraintGraph();
			GraphUtils.dumpConstraintGraph(constraintGraph, getTag());
		}
		return constraintGraph;
	}

	
	protected void sanityCheck() {
		E.yellow();
		for (V v : mCreationRefs.values()) {
			if (!v.isResolved()) {
				System.out.println("Unresolved " + getTag() + " instance: " + v.toString());
			}
		}
		E.resetColor();
	}	
	
}
