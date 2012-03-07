package energy.conditions;

import java.util.HashSet;

import com.ibm.wala.shrikeBT.IConditionalBranchInstruction.Operator;
import com.ibm.wala.ssa.ISSABasicBlock;



public class SimpleCondition extends GeneralCondition {
  
  /**
   * Block in the original method where the condition was found
   */
  private ISSABasicBlock block;
  
  private Operator op;
  private int var1;
  private int var2;
  
  public SimpleCondition(ISSABasicBlock bl, Operator iOperator, int var1, int var2) {
    this.setBlockIndex(bl);
    this.op = iOperator;
    this.var1 = var1;
    this.var2 = var2;
  }
  
  private boolean equalSimpleCondition(SimpleCondition condB) {
    Operator opA = this.op;
    int vA1 = this.var1;
    int vA2 = this.var2;
    
    Operator opB = condB.op;
    int vB1 = condB.var1;
    int vB2 = condB.var2;
    
    if (equivalentOperators(opA, opB)) {
      return (vA1 == vB1 && vA2 == vB2);       
    }
    else if (inverseOperators(opA, opB)) {
      return (vA1 == vB2 && vA2 == vB1);
    }
    else
      return false;
  }


  private boolean equivalentOperators(Operator opA, Operator opB) {
    return opA.equals(opB);
  }

  private boolean inverseOperators(Operator opA, Operator opB) {
    if      (opA.toString().equals("lt")) {
      return opB.toString().equals("ge");
    }
    else if (opA.toString().equals("ge")) {
      return opB.toString().equals("lt");
    }
    else if (opA.toString().equals("gt")) {
      return opB.toString().equals("le");
    }
    else if (opA.toString().equals("le")) {
      return opB.toString().equals("gt");
    }
    else 
      return false;    
  }

  public String toString() {
    StringBuffer buffer = new StringBuffer();
    buffer.append(var1);
    buffer.append(" ");    
    buffer.append(op.toString());
    buffer.append(" ");
    buffer.append(var2);
    buffer.append(" (");
    buffer.append(block.getNumber());
    buffer.append(")");
    return buffer.toString();    
  }

  public int getFirstArgument() {
    return var1;
  }

  public int getSecondArgument() {
    return var2;
  }

  public int getBlockIndex() {
    return block.getNumber();
  }

  public void setBlockIndex(ISSABasicBlock block) {
    this.block = block;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof SimpleCondition) {
      SimpleCondition cond = (SimpleCondition) o;
      return equalSimpleCondition(cond);      
    }
    else if (o instanceof CompoundCondition) {
      CompoundCondition genCond = (CompoundCondition) o;
      HashSet<SimpleCondition> conditionSet = genCond.getConditionSet();
      if (conditionSet.size() == 1) {
        SimpleCondition cond = (SimpleCondition) conditionSet.toArray()[0];
        return equalSimpleCondition(cond);
      }
    }
    
    return false;
  }
}
  
