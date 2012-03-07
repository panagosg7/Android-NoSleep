package com.ibm.wala.dataflow.graph;

import com.ibm.wala.fixpoint.BooleanVariable;
import com.ibm.wala.fixpoint.UnaryOperator;

public class BooleanFalse extends UnaryOperator<BooleanVariable> {



  private final static BooleanFalse SINGLETON = new BooleanFalse();
  
  public static BooleanFalse instance() {
    return SINGLETON;
  }
  
  @Override
  public byte evaluate(BooleanVariable lhs, BooleanVariable rhs) {
    if (lhs == null) {
      throw new IllegalArgumentException("lhs == null");
    }

    if (lhs.sameValue(rhs)) {
      lhs.set(false);
      return NOT_CHANGED;
    } else {
      lhs.set(false);
      return CHANGED;
    }
  }

  @Override
  public int hashCode() {
    return 9804;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof BooleanFalse);
  }

  @Override
  public String toString() {
    return "false";
  }


}
