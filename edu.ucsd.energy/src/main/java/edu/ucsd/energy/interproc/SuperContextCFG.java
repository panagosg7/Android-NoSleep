package edu.ucsd.energy.interproc;

import java.util.Map;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.component.AbstractComponent;
import edu.ucsd.energy.contexts.Context;

public class SuperContextCFG extends AbstractContextCFG {

	private static final int DEBUG = 1;

	public SuperContextCFG(
		//the abstract component
		AbstractComponent component,
		//Pairs of edges within the same context
		Set<Pair<CGNode, CGNode>> packedEdges,
		//Edges between different contexts
		Map<SSAInstruction, Context> seeds) {
		
		  super(component.getCallGraph());
		  this.component = component;
		  this.callgraph = component.getCallGraph();
		  /* Will only work like this - loses laziness. */
		  constructFullGraph();
		  addReturnToEntryEdge(packedEdges);
		  cacheCallbacks(packedEdges);
		  //Add edges from Intent calls etc
		  //Using as packed edges the total of the edges for every component
		  addCallToEntryAndReturnEdges(seeds);
	  }




}
