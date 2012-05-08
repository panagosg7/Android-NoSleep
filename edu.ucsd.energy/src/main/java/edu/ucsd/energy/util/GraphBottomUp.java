package edu.ucsd.energy.util;

import java.util.Iterator;

import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.FilterIterator;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.impl.InvertedGraph;
import com.ibm.wala.util.graph.traverse.Topological;

public class GraphBottomUp {
	/**
	 * Returns a list of T elements in a bottom up order
	 * @param g the graph to traverse
	 * @param f filter some of the root nodes
	 * @return
	 */
	public static <T> Iterator<T> bottomUpIterator(Graph<T> g, Filter<T> f) {
		InvertedGraph<T> invertedGraph = new InvertedGraph<T>(g);
		//A topological sort preserves the edge constraints
	    Iterator<T> topoIter = Topological.makeTopologicalIter(invertedGraph);
	    FilterIterator<T> filterIter = new FilterIterator<T>(topoIter, f);	    
	    return filterIter;
	}

	public static <T> Iterator<T> bottomUpIterator(Graph<T> g) {
		InvertedGraph<T> invertedGraph = new InvertedGraph<T>(g);
		//A topological sort preserves the edge constraints
	    return Topological.makeTopologicalIter(invertedGraph);
	}
	
}
