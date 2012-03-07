package com.ibm.wala.dataflow.graph;


import com.ibm.wala.fixpoint.BooleanVariable;
import com.ibm.wala.fixpoint.UnaryOperator;

public class BooleanTrue extends UnaryOperator<BooleanVariable> {


  private final static BooleanTrue SINGLETON = new BooleanTrue();
  
  public static BooleanTrue instance() {
    return SINGLETON;
  }
  
  @Override
  public byte evaluate(BooleanVariable lhs, BooleanVariable rhs) {
    if (lhs == null) {
      throw new IllegalArgumentException("lhs == null");
    }

    if (lhs.sameValue(rhs)) {
      lhs.set(true);
      return NOT_CHANGED;
    } else {
      lhs.set(true);
      return CHANGED;
    }
  }

  @Override
  public int hashCode() {
    return 9803;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof BooleanTrue);
  }

  @Override
  public String toString() {
    return "true";
  }

}
