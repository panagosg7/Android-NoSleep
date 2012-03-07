/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.dataflow.graph;

import com.ibm.wala.fixpoint.BooleanVariable;
import com.ibm.wala.fixpoint.FixedPointConstants;

/**
 * Operator U(n) = U(n) `and` U(j)
 */
public class BooleanIntersection extends AbstractMeetOperator<BooleanVariable> implements FixedPointConstants {

  private final static BooleanIntersection SINGLETON = new BooleanIntersection();

  public static BooleanIntersection instance() {
    return SINGLETON;
  }

  private BooleanIntersection() {
  }

  /**
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return "INTERSECTION";
  }

  @Override
  public int hashCode() {
    return 9901;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof BooleanIntersection);
  }

  @Override
  public byte evaluate(BooleanVariable lhs, BooleanVariable[] rhs) throws NullPointerException {
    if (rhs == null) {
      throw new IllegalArgumentException("null rhs");
    }
    BooleanVariable U = new BooleanVariable();
    U.copyState(lhs);
    for (int i = 0; i < rhs.length; i++) {
      BooleanVariable R = (BooleanVariable) rhs[i];
      if (R != null) {
        U.and(R);
      }
    }
    if (!lhs.sameValue(U)) {
      lhs.copyState(U);
      return CHANGED;
    } else {
      return NOT_CHANGED;
    }
  }
}