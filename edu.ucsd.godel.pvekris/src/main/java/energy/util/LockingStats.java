package energy.util;

import java.util.HashMap;
import java.util.Map.Entry;

import energy.components.Component;

public class LockingStats {

  private class ComponentResults {
    private String className = null;
    public HashMap<String,Integer> h = null;    
    int instances;       
    public ComponentResults(String s) {
      className = s;
      instances = 1;
      h = new HashMap<String, Integer>();
    }
    
    public String toString () {
      StringBuffer sb = new StringBuffer();
      sb.append(String.format("%-40s [%d]", className, instances));
      sb.append(h.toString());            
      return sb.toString();
    }    
  }  
  
  
  HashMap<String, ComponentResults> components = new HashMap<String, ComponentResults>();
  
  public void registerComponent(Component c) {
    String className = c.getClass().toString();
    try {
      ComponentResults cr = components.get(className);
      cr.instances++;      
      try {
        Integer i = cr.h.get(c.policyResult);
        cr.h.put(c.policyResult, i + 1);
      }
      //The result policy was not found
      catch (NullPointerException e) {
        cr.h.put(c.policyResult, 1);
      }      
    }
    catch (NullPointerException e ) {
      ComponentResults cr = new ComponentResults(className);
      cr.h.put(c.policyResult, 1);
      components.put(className, cr);
    }    
  } 
  
  public void dumpStats() {

    E.log(0, "############################################################");
    for(Entry<String, ComponentResults> c : components.entrySet()) {
      //String fst = String.format("%-40s: %d", c.getKey() , c.getValue());
      E.log(0, c.getValue().toString());
    }
    //E.log(0, "------------------------------------------------------------");    
    E.log(0, "############################################################");
    
  }  
  

}