package edu.ucsd.energy.conditions;

import java.util.HashSet;
import java.util.Iterator;


public class CompoundCondition extends GeneralCondition {

  private HashSet<SimpleCondition> conditionSet;
  
  public CompoundCondition(HashSet<SimpleCondition> simpleSet) {    
    this.conditionSet =  new HashSet<SimpleCondition>(simpleSet);  
  }

  public CompoundCondition() {
    this.conditionSet =  new HashSet<SimpleCondition>();
  }

  @Override
  public String toString() {
    return this.conditionSet.toString();
  }

  public HashSet<SimpleCondition> getConditionSet() {
    return conditionSet;
  }

  public Iterator<SimpleCondition> iterator() {
    return this.conditionSet.iterator();
  }

  public void add(GeneralCondition edgeCondition) {
    if (edgeCondition instanceof SimpleCondition) {
      conditionSet.add((SimpleCondition) edgeCondition);      
    }
    else if (edgeCondition instanceof CompoundCondition) {
      conditionSet.addAll(((CompoundCondition) edgeCondition).getConditionSet());      
    }
  }  
  
}  
