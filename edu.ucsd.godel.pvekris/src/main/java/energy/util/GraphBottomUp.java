package energy.util;

import java.util.Collection;

import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.FilterIterator;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.InferGraphRoots;
import com.ibm.wala.util.graph.impl.InvertedGraph;
import com.ibm.wala.util.graph.traverse.BFSIterator;

public class GraphBottomUp {
	/**
	 * Returns a list of T elements in a bottom up order
	 * @param g the graph to traverse
	 * @param f filter some of the root nodes
	 * @return
	 */
	public static <T> BFSIterator<T> bottomUpIterator(Graph<T> g, Filter<T> f) {
	    InvertedGraph<T> invertedGraph = new InvertedGraph<T>(g);
	    //Roots of the inverted are leaves in the initial graph 
	    Collection<T> leaves = InferGraphRoots.inferRoots(invertedGraph);
	    FilterIterator<T> fit = new FilterIterator<T>(leaves.iterator(), f);
	    BFSIterator<T> bfsIter = new BFSIterator<T>(invertedGraph, fit);
	    return bfsIter;
	}
	
	
}
