package energy.intraproc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import com.ibm.wala.cfg.cdg.ControlDependenceGraph;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.ConstantValue;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.FieldReference;

public class VariableManager {

  private static final boolean DEBUG = false;
  
  private static IMethod  currMethod;  
  private static IR       currIR;
  private static SSACFG   currCFG;  
  
  /**
   * These are the variables that we are used in conditions in the 
   * current method
   */
  private static HashSet<Integer> variableSet;
  private static HashMap<Integer,ConditionVariable> variableMap;

  
  
  public boolean contains (int i) {
    return variableSet.contains(i);
  }
  
  public VariableManager(CGNode node) {   
    
    currMethod        = node.getMethod();    
    currIR            = node.getIR();
    currCFG           = currIR.getControlFlowGraph();
    new ControlDependenceGraph<SSAInstruction, ISSABasicBlock>(currCFG, true);    
        
    variableSet = new HashSet<Integer>();
    variableMap = new HashMap<Integer, VariableManager.ConditionVariable>();
    
  }

  public void register(int var) {
    variableSet.add(var);    
  }

  
  public void register(int var, ConstantValue cv) {
    Constant c = new Constant(cv);
    variableMap.put(var,c);
  }


  /**
   * Update the variable manager with information about the values that are used 
   * in conditions, i.e. whether the value is:  
   *  - Formal parameter
   *  - Result of a function call
   *  - Static field
   *  - Constant
   */
  public void collectVariableInformation() {    
    /*
     * Gather parameters and constants from symbol table 
     */
    SymbolTable symbolTable = currIR.getSymbolTable();
        
    for (int i = 0; i <= symbolTable.getMaxValueNumber(); i++) {
      //This is a parameter
      if (variableSet.contains(i)) {
        if (symbolTable.isParameter(i)) {
          FormalParameter fp = new FormalParameter(currMethod, i);
          variableMap.put(i,fp);
        }
        else if (symbolTable.isConstant(i)) {          
          Object constantObject = symbolTable.getValue(i);
          if (constantObject instanceof ConstantValue) {            
            ConstantValue constantValue = (ConstantValue) constantObject;
            Constant cv = new Constant(constantValue);        
            variableMap.put(i,cv);
          }
        }   
        
      }
    }
    
    //Gather invoke instructions and assignments
    SSAInstruction[] instructions = currIR.getInstructions();
    for (SSAInstruction instr : instructions) {
      if (instr instanceof SSAInvokeInstruction) {
        SSAInvokeInstruction invInstr = (SSAInvokeInstruction) instr;
        int numberOfReturnValues = invInstr.getNumberOfReturnValues();
        if (numberOfReturnValues > 0) {
          int returnValue = invInstr.getReturnValue(0);
          if (variableSet.contains(returnValue)) {
            FunctionCallResult fcr = new FunctionCallResult(invInstr);
            variableMap.put(returnValue,fcr);
          }
        }
      }
      /*
       * Get field accesses
       */
      else if (instr instanceof SSAFieldAccessInstruction) {
        SSAFieldAccessInstruction fieldAccessInstr = (SSAFieldAccessInstruction) instr;
        int assignValue = fieldAccessInstr.getDef();
        if (variableSet.contains(assignValue)) {          
          StaticField sf = new StaticField(fieldAccessInstr.getDeclaredField());
          variableMap.put(assignValue,sf);
        }
      } else if (instr instanceof SSANewInstruction) {}
      // TODO: probably need more here
    }
  }

  
  public void update(int i, ConditionVariable cv) {
    variableMap.put(i, cv);
  }
  
  public void clear() {
    variableSet.clear();
    variableMap.clear();
  }
  
  public void outputVariableInformation() {
    
    System.out.println("Relevant variable information:");    
    for (Entry<Integer, ConditionVariable> entry : variableMap.entrySet()) {
      System.out.println("V " + entry.getKey() + ": " + entry.getValue().toString());
    }
    System.out.println();
  }
  
  public ConditionVariable getInfoOfVariable (int v) {
    return variableMap.get(v);
    
  }
  
  
  /**
   * The variable that takes part in conditions
   */
  public interface ConditionVariable {    
    public String toString ();
  }  
  
  public static class FormalParameter implements ConditionVariable {
    
    public int arg;
    public IMethod method;
    
    FormalParameter(IMethod meth, int i) {
      method = meth;
      arg = i;      
    }
    
    public String toString () {
      StringBuffer result = new StringBuffer();
      result.append("Formal parameter: " + arg);
      return result.toString();      
    }
    
  }
  
  public static class FunctionCallResult implements ConditionVariable {
    public SSAInvokeInstruction invInstr;
    
    public FunctionCallResult(SSAInvokeInstruction ii) {
      invInstr = ii;
    }

    public String toString () {
      return invInstr.toString();
    }    
  }
  
  public static class StaticField implements ConditionVariable {
    /*
     * Subsumes type reference
     */
    public FieldReference fieldRef;
    
    public StaticField(FieldReference declaredField) {
      fieldRef = declaredField;
    }

    public String toString () {
      return fieldRef.toString();
    }
    
  }
  
  public static class Constant implements ConditionVariable  {
    public Constant(ConstantValue cv) {
      constValue = cv;
    }

    public ConstantValue constValue;
    
    public String toString () {
      StringBuffer result = new StringBuffer();
      result.append("Constant: ");
      result.append(constValue.toString());
      return result.toString();
    }        
  }

}
