package edu.ucsd.energy.util;

import com.ibm.wala.util.graph.AbstractGraph;
import com.ibm.wala.util.graph.EdgeManager;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.NodeManager;

public class DoubleLinkedGraph<T> extends AbstractGraph<T> {
	
	  final private NodeManager<T> nodes;

	  @Override
	  protected NodeManager<T> getNodeManager() {
	    return nodes;
	  }

	  final private EdgeManager<T> edges;

	  @Override
	  protected EdgeManager<T> getEdgeManager() {
	    return edges;
	  }

	  public DoubleLinkedGraph(Graph<T> G) {
	    nodes = G;
	    edges = new BoubleLinkingManager<T>(G);
	  }
}