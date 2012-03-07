package com.ibm.wala.shrikeBT;

public interface IConditionalBranchInstruction extends IInstruction {

  public interface IOperator {
  }

  public enum Operator implements IConditionalBranchInstruction.IOperator {
    EQ, NE, LT, GE, GT, LE;

    @Override
    public String toString() {
      return super.toString().toLowerCase();
    }
    //Panagiotis Vekris added this method
    public Operator getInverseOperator() {    
      if (this.equals(Operator.EQ))
        return Operator.NE;
      else if (this.equals(Operator.NE))
        return Operator.EQ;
      else if (this.equals(Operator.LT))
        return Operator.GE;
      else if (this.equals(Operator.GE))
        return Operator.LT;
      else if (this.equals(Operator.LE))
        return Operator.GT;
      else if (this.equals(Operator.GT))
        return Operator.LE;
      else {
        return null;
      }
    }

  }

  int getTarget();

  IOperator getOperator();

  String getType();
}
