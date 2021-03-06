/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.util.graph;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import com.ibm.wala.util.collections.HashSetFactory;


/**
 * Utility methods for graphs.
 */
public class GraphUtil {

  /**
   * count the number of edges in g
   */
  public static <T> long countEdges(Graph<T> g) {
    if (g == null) {
      throw new IllegalArgumentException("g is null");
    }
    long edgeCount = 0;
    for (T t : g) {
      edgeCount += g.getSuccNodeCount(t);
    }
    return edgeCount;
  }
    

  public static <T> Collection<T> inferRoots(Graph<T> g){
    if (g == null) {
      throw new IllegalArgumentException("g is null");
    }
    HashSet<T> s = HashSetFactory.make();
    for (Iterator<? extends T> it = g.iterator(); it.hasNext();) {
      T node = it.next();
      if (g.getPredNodeCount(node) == 0) {
        s.add(node);
      }
    }
    return s;
  }


  public static <T> Collection<T> inferLeaves(Graph<T> g){
    if (g == null) {
      throw new IllegalArgumentException("g is null");
    }
    HashSet<T> s = HashSetFactory.make();
    for (Iterator<? extends T> it = g.iterator(); it.hasNext();) {
      T node = it.next();
      if (g.getSuccNodeCount(node) == 0) {
        s.add(node);
      }
    }
    return s;
  }

  
}
