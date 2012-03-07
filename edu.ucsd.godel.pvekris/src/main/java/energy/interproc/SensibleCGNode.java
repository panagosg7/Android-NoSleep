package energy.interproc;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.util.graph.impl.NodeWithNumber;


public class SensibleCGNode extends NodeWithNumber {
  private String name = null;
  private CGNode realNode = null;
  
  public SensibleCGNode(String name, CGNode node) {
   this.name = name;
   this.setRealNode(node);     
  }        

  public String getName() {
    return this.name;
  } 
  
  @Override
  public String toString() {
    return name;    
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
