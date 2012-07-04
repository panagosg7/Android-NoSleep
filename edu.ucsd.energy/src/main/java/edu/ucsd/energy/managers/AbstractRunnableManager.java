package edu.ucsd.energy.managers;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.json.JSONObject;

import com.ibm.wala.ipa.callgraph.CGNode;
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
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.GraphUtils;

public abstract class AbstractRunnableManager<V extends AbstractRunnableInstance> extends AbstractDUManager<V> { 

	private final int DEBUG = 0;


	private SparseNumberedGraph<Context> constraintGraph;

	private Collection<Pair<MethodReference, SSAInvokeInstruction>> unresolvedCallSites;


	public AbstractRunnableManager(GlobalManager gm) {
		super(gm);
		unresolvedCallSites = new HashSet<Pair<MethodReference,SSAInvokeInstruction>>();
	}




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
		System.out.println("\nDumping " + getTag() + " Manager");
		System.out.println("------------------------------------------");
		for (Entry<CreationPoint, V> e : mCreationRefs.entrySet()) {
			V value = e.getValue();
			System.out.println(value.toString());
		}
		System.out.println("------------------------------------------");
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
			for (Entry<CreationPoint, V> e : mCreationRefs.entrySet()) {
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
					CreationPoint pp = ri.getPP();
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

		for (CGNode n : bottomUpList) {
			node = n;
			ir = n.getIR();
			method = n.getMethod();
			if (ir == null) continue;		// Null for JNI methods
			for (SSAInstruction instr : ir.getInstructions()) {
				if (instr instanceof SSAInvokeInstruction) {
					SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
					if (getTargetMethods(inv.getDeclaredTarget()) != null) {
						Pair<MethodReference, SSAInvokeInstruction> key = Pair.make(method.getReference(), inv);
						V v = mInstruction2Instance.get(key);
						//XXX: the stronger case here is needed !
						if (v != null) {
							TypeName calledType = v.getCalledType();
							if (calledType != null) {
								Context calledContext = cm.getComponent(calledType);
								if (calledContext != null) {
									continue;
								}
							}
							unresolvedCallSites.add(key);
							//we missed this interesting call
							E.flog(getTag() + " in method" + method + " was not resolved successfully.");
							E.flog("  target instruction: " +	inv.toString());
							E.flog("");
						}
					}
				}
			}			
		}
		int size = mInstruction2Instance.size();
		if (unresolvedCallSites.size() == 0) {
			E.green();
		}
		else {
			E.yellow();
		}
		System.out.println((size - unresolvedCallSites.size()) + " / " + size + " " + getTag() +" call sites were resolved successfully.");
		E.resetColor();
	}

	public Collection<Pair<MethodReference, SSAInvokeInstruction>> getUnresolvedCallSites() {
		if (unresolvedCallSites == null) {
			sanityCheck();
		}
		return unresolvedCallSites;
	}

}
