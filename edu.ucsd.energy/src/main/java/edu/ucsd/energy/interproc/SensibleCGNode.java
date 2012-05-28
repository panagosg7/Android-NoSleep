package edu.ucsd.energy.interproc;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.graph.impl.NodeWithNumber;

public class SensibleCGNode extends NodeWithNumber {
  private Selector name = null;
  private CGNode realNode = null;
  
  public static SensibleCGNode makeNonEmpty(CGNode n) {
	  return new SensibleCGNode(n);
  }
  
  public static SensibleCGNode makeEmpty(Selector s) {
	  return new SensibleCGNode(s);
  }
  
  private SensibleCGNode(CGNode node) {
   this.setRealNode(node);
   this.name = node.getMethod().getSelector();
  }        
  
  private SensibleCGNode(Selector s) {
	  this.name = s;
  }

public Selector getSelector() {
    return this.name;
  } 
  
  @Override
  public String toString() {
    return name.toString();    
  }

  public boolean isEmpty() {
    return (getRealNode() == null);
  }

  public CGNode getRealNode() {
    return realNode;
  }

  public void setRealNode(CGNode realNode) {
    this.realNode = realNode;
  }
}
