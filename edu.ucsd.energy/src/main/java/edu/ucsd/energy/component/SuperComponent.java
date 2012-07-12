package edu.ucsd.energy.component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.collections.HashSetMultiMap;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.INodeWithNumber;

import edu.ucsd.energy.contexts.Activity;
import edu.ucsd.energy.contexts.Service;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SuperContextCFG;
import edu.ucsd.energy.util.Log;

public class SuperComponent extends AbstractContext implements INodeWithNumber {

	private static final int DEBUG = 0;

	//The contexts that constitute this SuperComponent
	Set<Component> sComponent;

	Set<CallBack> sCallBack;

	public SuperComponent(Set<Component> set) {
		super();
		setGraphNodeId(getNextId());
		sComponent = set;
	}

	public Set<CallBack> getCallBacks() {
		if (sCallBack == null) {
			sCallBack = new HashSet<CallBack>();
			for (Component c : sComponent) {
				sCallBack.addAll(c.getRoots());
			}
		}
		return sCallBack;
	}

	private class SeedMap extends HashMap<SSAInstruction, Component> {

		private static final long serialVersionUID = 6969397892661845448L;

		public void registerSeeds(Component dest) {
			//This will work only for components, so ...
			if (dest instanceof Component) {
				Component component = (Component) dest;
				HashSetMultiMap<Component, SSAInstruction> seeds = component.getSeeds();
				if (seeds != null) {
					Set<Component> ctxs = seeds.keySet();
					for (Component c : ctxs) {
						Set<SSAInstruction> insts = seeds.get(c);
						for (SSAInstruction i : insts) {
							put(i, component);
						}
					}
				}
			}
		}

		public String toString() { 
			StringBuffer sb = new StringBuffer();
			if (!entrySet().isEmpty()) {
				sb.append("SEEDS:\n");
			}
			for (Entry<SSAInstruction, Component> e : entrySet()) {
				if (e.getKey() instanceof SSAInvokeInstruction) {
					SSAInvokeInstruction inv = (SSAInvokeInstruction) e.getKey();
					sb.append(inv.getDeclaredTarget().getSelector().toString() 
							+ " -> " + e.getValue().toString() + "\n");
				}
			}
			return sb.toString();
		}
	}

	public SuperContextCFG makeCFG() {
		//Gathers all nodes
		Collection<CGNode> nodeSet = new HashSet<CGNode>();
		//Context life-cycle edges among methods
		Set<Pair<CGNode, CGNode>> edgePairs = new HashSet<Pair<CGNode, CGNode>>();
		//Inter-context edges
		SeedMap mSeeds = new SeedMap();
		for (Component c : sComponent) {
			// Gather nodes
			Iterator<CGNode> nItr = c.getNodes();
			while (nItr.hasNext()) {
				nodeSet.add(nItr.next());	//duplicates are omitted
			}
			// Gather sensible node edges
			edgePairs.addAll(c.getImplicitEdges());
			if(DEBUG > 1) {
				for ( Pair<CGNode, CGNode> ie : c.getImplicitEdges()) {
					System.out.println("IMPLICIT: " + 
							ie.fst.getMethod().getSelector().toString() + " -> " +
							ie.snd.getMethod().getSelector().toString());
				}
			}
			// Gather inter-component communication edges
			mSeeds.registerSeeds(c);
			if(DEBUG > 1) {
				System.out.println(mSeeds.toString());
			}
		}
		// These are edges between CGNodes that we are going to need
		return new SuperContextCFG(this, edgePairs, mSeeds);
	}

	//Will be used in case we want to dump something to a file
	private String fileName;

	public String toFileName() {
		if (fileName == null) {
			for (Component c : sComponent) {
				if ((c instanceof Activity) || (c instanceof Service)) {
					fileName = c.toFileName();
					break;
				}
			}
			if (fileName == null) {
				fileName = sComponent.iterator().next().toFileName();;
			}
		}
		return fileName;
	}

	private String name;

	public String toString() {
		if (name == null) {
			for (Component c : sComponent) {
				if ((c instanceof Activity) || (c instanceof Service)) {
					name = "SUPER_" + c.toString();
					break;
				}
			}
			if (name == null) {
				//There should be at least one element in the supercomponent
				name = "SUPER_" + sComponent.iterator().next().toString();
			}
		}
		return name;
	}

	public void dumpContainingComponents() {
		Log.println("========================================");
		Log.println("SuperComponent (#nodes: " + getContextCallGraph().getNumberOfNodes() + ") containing:");
		for (Component c : sComponent) {
			Log.println("\t" + c.toString());			
		}
		Log.println();
	}

	public Set<Component> getContexts() {
		return sComponent;
	}


	//ID accounting	
	private static int counter = 0;

	private int id; 

	private int getNextId() {
		return counter ++;
	}

	public int getGraphNodeId() {
		return id;
	}

	public void setGraphNodeId(int number) {
		id = number;		
	}

	@Override
	public CompoundLockState getReturnState(CGNode cgNode) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CallGraph getContextCallGraph() {
		if (componentCallgraph == null) {
			Collection<CGNode> nodeSet = new HashSet<CGNode>();
			for (Component c : sComponent) {
				c.setContainingSuperComponent(this);
				// Gather nodes
				Iterator<CGNode> nItr = c.getNodes();
				while (nItr.hasNext()) {
					nodeSet.add(nItr.next());
				}
			}
			componentCallgraph = PartialCallGraph.make(originalCallgraph, nodeSet);
		}
		return componentCallgraph;
	}


	private Boolean callsInteresting = null;

	public boolean callsInteresting() {
		if (callsInteresting == null) {
			for (Component c : sComponent) {
				if (c.callsInteresting()) {
					callsInteresting = new Boolean(true);
					return true;
				}
			}
			callsInteresting = new Boolean(false);
			return false;
		}
		return callsInteresting.booleanValue();
	}

	/**
	 * Return a set with all the contexts that contain node in them
	 */
	public Set<Component> getContainingContexts(CGNode node) {
		HashSet<Component> hashSet = new HashSet<Component>();
		for (Component c : sComponent) {
			if (c.getContextCallGraph().containsNode(node)) {
				hashSet.add(c);
			}
		}
		return hashSet;
	}
	

	
}
