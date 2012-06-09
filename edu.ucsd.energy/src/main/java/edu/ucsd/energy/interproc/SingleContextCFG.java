package edu.ucsd.energy.interproc;

import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.component.AbstractComponent;

public class SingleContextCFG extends AbstractContextCFG {

	  /**
	   * Constructor that the component and the pairs of methods (Signatures) that need to 
	   * be connected.
	   */
	  public SingleContextCFG(AbstractComponent component, Set<Pair<CGNode, CGNode>> packedEdges) {
		  super(component.getCallGraph());
		  this.component = component;
		  this.callgraph = component.getCallGraph();
		  /* Will only work like this - loses laziness. */
		  constructFullGraph();
		  addReturnToEntryEdge(packedEdges);  
		  cacheCallbacks(packedEdges);
	  }

}
