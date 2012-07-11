package edu.ucsd.energy.managers;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.json.JSONObject;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Acyclic;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.util.Log;
import edu.ucsd.energy.util.GraphUtils;

public abstract class AbstractRunnableManager<V extends AbstractRunnableInstance> extends AbstractDUManager<V> { 

	private final int DEBUG = 0;


	private SparseNumberedGraph<Context> constraintGraph;

	private Collection<Pair<MethodReference, SSAInvokeInstruction>> unresolvedCallSites;

	//We needed a map to keep the "this" object whenever used. 
	protected Map<IClass, V> mClassRefs;


	public AbstractRunnableManager() {
		super();
		unresolvedCallSites = new HashSet<Pair<MethodReference,SSAInvokeInstruction>>();
	}


	public void computeConstraintGraph() {
		if (DEBUG > 1) {
			Log.println("Computing constraint graph for " + getTag());
		}
		constraintGraph = new SparseNumberedGraph<Context>(1);

		Collection<Context> sComponents = cm.getComponents();
		for(Context c : sComponents) {
			constraintGraph.addNode(c);
		}

		for(Entry<Pair<MethodReference, SSAInstruction>, V> e : mInstruction2Instance.entrySet()) {
			MethodReference mr = e.getKey().fst;
			Set<Context> sFrom = cm.getContainingComponents(mr);
			//Avoid adding calls to self component - will cause issues with 
			//super-component CFG!!! What we want to avoid is adding the seed 
			//to this component by itself.
			if (e.getValue().isSelfCall()) continue;
			
			if (DEBUG > 1) {
				Log.println("Calls from: " + mr.toString());
			}
			if (sFrom != null) {
				TypeName calledType = e.getValue().getCalledType();
				if (calledType != null) {
					Context to = cm.getComponent(calledType);
					if (to != null) {
						if (DEBUG > 1) {
							Log.println("\tCalled Type: " + calledType);
						}
						for (Context from : sFrom) {
							if (DEBUG > 1) {
								Log.println("\t" + to.toString() + " <-- " + from.toString());
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
			Log.println("Building abstract " + getTag() + " constraint graph... " + GraphUtil.countEdges(constraintGraph) + " edge(s).");
		};
		testAcyclic(constraintGraph);
	}

	private void testAcyclic(final SparseNumberedGraph<Context> g) {
		
		//Assert that there are no cycles in the component constraint graph.
		com.ibm.wala.util.Predicate<Context> p = 
				new com.ibm.wala.util.Predicate<Context>() {		
			@Override
			public boolean test(Context c) {
				return Acyclic.isAcyclic(g, c);
			}
		};

	} 


	public void dumpInfo() {
		Log.println("\nDumping " + getTag() + " Manager");
		Log.println("------------------------------------------");
		for (Entry<CreationPoint, V> e : mCreationRefs.entrySet()) {
			V value = e.getValue();
			Log.println(value.toString());
		}
		Log.println("------------------------------------------");
		for (Entry<Pair<MethodReference, SSAInstruction>, V> e : mInstruction2Instance.entrySet()) {
			Pair<MethodReference, SSAInstruction> key = e.getKey();
			V value = e.getValue();
			Log.println(key.toString() + " :: " + value.toString());
		}
		Log.println("==========================================\n");		
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
							Log.flog(getTag() + " in method" + method + " was not resolved successfully.");
							Log.flog("  target instruction: " +	inv.toString());
							Log.flog("");
						}
					}
				}
			}			
		}
		int size = mInstruction2Instance.size();
		if (unresolvedCallSites.size() == 0) {
			Log.green();
		}
		else {
			Log.yellow();
		}
		Log.println((size - unresolvedCallSites.size()) + " / " + size + " " + getTag() +" call sites were resolved successfully.");
		Log.resetColor();
	}

	public Collection<Pair<MethodReference, SSAInvokeInstruction>> getUnresolvedCallSites() {
		if (unresolvedCallSites == null) {
			sanityCheck();
		}
		return unresolvedCallSites;
	}

}
