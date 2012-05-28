package edu.ucsd.energy.util;

import java.util.Iterator;

import com.ibm.wala.util.collections.CompoundIterator;
import com.ibm.wala.util.graph.EdgeManager;

public class BoubleLinkingManager<T> implements EdgeManager<T> {

  private final EdgeManager<T> original;

  public BoubleLinkingManager(EdgeManager<T> original) {
    if (original == null) {
      throw new IllegalArgumentException("original is null");
    }
    this.original = original;
  }

  public Iterator<T> getPredNodes(T N) throws IllegalArgumentException {
    return new CompoundIterator<T>(original.getSuccNodes(N), original.getPredNodes(N));
  }

  public int getPredNodeCount(T N) throws IllegalArgumentException{
    return (original.getSuccNodeCount(N) + original.getPredNodeCount(N));
  }

  public Iterator<T> getSuccNodes(T N) throws IllegalArgumentException{
    return new CompoundIterator<T>(original.getSuccNodes(N), original.getPredNodes(N));
  }

  public int getSuccNodeCount(T N) throws IllegalArgumentException{
    return (original.getSuccNodeCount(N) + original.getPredNodeCount(N));
  }

  public void addEdge(T src, T dst)throws IllegalArgumentException {
    original.addEdge(dst, src);
    original.addEdge(src, dst);
  }

  public void removeEdge(T src, T dst) throws IllegalArgumentException{
    original.removeEdge(dst, src);
    original.removeEdge(src, dst);
  }

  public boolean hasEdge(T src, T dst) {
    return (original.hasEdge(dst, src) || original.hasEdge(src, dst));
  }

  public void removeAllIncidentEdges(T node) throws IllegalArgumentException {
    original.removeAllIncidentEdges(node);
  }

  public void removeIncomingEdges(T node) throws IllegalArgumentException{
    original.removeAllIncidentEdges(node);
  }

  public void removeOutgoingEdges(T node)throws IllegalArgumentException {
	  original.removeAllIncidentEdges(node);
  }

}