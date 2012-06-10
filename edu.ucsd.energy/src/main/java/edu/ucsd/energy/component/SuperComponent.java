package edu.ucsd.energy.component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.contexts.Activity;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.contexts.Service;
import edu.ucsd.energy.interproc.SuperContextCFG;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.util.E;

public class SuperComponent extends AbstractComponent {

	private static final int DEBUG = 2;

	Set<Context> sComponent;

	Set<CallBack> sCallBack;

	public SuperComponent(GlobalManager gm, Set<Context> set) {
		super(gm);
		sComponent = set;
	}

	public Set<CallBack> getCallBacks() {
		if (sCallBack == null) {
			sCallBack = new HashSet<CallBack>();
			for (Context c : sComponent) {
				sCallBack.addAll(c.getCallbacks());
			}
		}
		return sCallBack;
	}

	private class SeedMap extends HashMap<SSAInstruction, Context> {

		private static final long serialVersionUID = 6969397892661845448L;

		public void registerSeeds(Context dest) {
			Map<Context, Set<SSAInstruction>> seeds = dest.getSeeds();
			if (seeds != null) {
				for (Entry<Context, Set<SSAInstruction>> e : seeds.entrySet()) {
					for (SSAInstruction i : e.getValue()) {
						put(i, dest);
					}
				}
			}
		}
		
		public String toString() {
			StringBuffer sb = new StringBuffer();
			if (!entrySet().isEmpty()) {
				sb.append("SEEDS:\n");
			}
			for (java.util.Map.Entry<SSAInstruction, Context> e : entrySet()) {
				sb.append(e.getKey().toString() + " -> " + e.getValue().toString() + "\n");			
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
		for (Context c : sComponent) {
			// Gather nodes
			Iterator<CGNode> nItr = c.getNodes();
			while (nItr.hasNext()) {
				nodeSet.add(nItr.next());	//duplicates are omitted
			}
			// Gather sensible node edges
			edgePairs.addAll(c.getImplicitEdges());
			if(DEBUG < 2) {
				for ( Pair<CGNode, CGNode> ie : c.getImplicitEdges()) {
					E.log(1, "IMPLICIT: " + 
							ie.fst.getMethod().getSelector().toString() + " -> " +
							ie.snd.getMethod().getSelector().toString());
				}
			}
			// Gather inter-component communication edges
			mSeeds.registerSeeds(c);
			if(DEBUG < 2) {
				E.log(1, mSeeds.toString()); 
			}
		}
		// These are edges between CGNodes that we are going to need
		return new SuperContextCFG(this, edgePairs, mSeeds);
	}

	protected void makeCallGraph() {
		Collection<CGNode> nodeSet = new HashSet<CGNode>();
		for (Context c : sComponent) {
			// Gather nodes
			Iterator<CGNode> nItr = c.getNodes();
			while (nItr.hasNext()) {
				nodeSet.add(nItr.next());
			}
		}
		componentCallgraph = PartialCallGraph.make(originalCallgraph, nodeSet);
	}

	//Will be used in case we want to dump something to a file
	private String fileName;

	public String toFileName() {
		if (fileName == null) {
			for (Context c : sComponent) {
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
			for (Context c : sComponent) {
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
		System.out.println("SuperComponent containing: ");
		for (Context c : sComponent) {
			System.out.println("\t" + c.toString());			
		}
	}


}
