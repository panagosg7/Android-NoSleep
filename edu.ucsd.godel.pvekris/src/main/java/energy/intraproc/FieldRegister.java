package energy.intraproc;

/**
 * NOT USED AT THE MOMENT
 */


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;
import org.apache.commons.lang.builder.HashCodeBuilder;

public class FieldRegister {
  
  private class FieldInfo{
    TypeReference declClass;
    Atom name;
   
    FieldInfo (TypeReference dc, Atom n) {
      this.declClass = dc;
      this.name = n;    
    }
    
    public String toString() {
      StringBuffer result = new StringBuffer();
      result.append("(");
      result.append(declClass.toString());
      result.append(", ");
      result.append(name.toString());
      result.append(")");
      return result.toString();
    }
    //Need to override Object's hashCode and equals for the 
    //hashing to work. 
    //TODO : improve these functions
    public int hashCode() {
      return new HashCodeBuilder(17, 31). // two randomly chosen prime numbers
          // if deriving: appendSuper(super.hashCode()).
            append(declClass.toString()).
            append(name.toString()).
            toHashCode();
    }
    
    public boolean equals (Object o) {
      if (o instanceof FieldInfo) {        
        FieldInfo fi = (FieldInfo) o;        
        return this.toString().equals(fi.toString());
      }
      else {
        return false;
      }
    }
  }
  
  
  //The map itself
  private Map<FieldInfo, HashSet<Integer>> regMap;
  
  FieldRegister() {
    regMap = new HashMap<FieldInfo, HashSet<Integer>>();
  }
  
  public void registerField(TypeReference dc, Atom n, int id) {
    FieldInfo fi = new FieldInfo(dc,n);
    HashSet<Integer> keyColl = regMap.get(fi);    
    if (keyColl == null) {     
      HashSet<Integer> newColl = new HashSet<Integer>();
      newColl.add(new Integer(id));
      regMap.put(fi,newColl);      
    }  
    else {      
      keyColl.add(new Integer(id));
      regMap.put(fi,keyColl);
    }
  }
  
/*
  public TypeReference getDeclaringClass (int id) {
    FieldInfo fi = regMap.get(id);
    if (fi != null)
      return fi.declClass;      
    else
      return null;
  }
  
  public Atom getFieldName (int id) {
    FieldInfo fi = regMap.get(id);
    if (fi != null) 
      return fi.name;
    else
      return null;
  }
  */
  
  public boolean isRegistered (TypeReference dc, Atom n) {
    FieldInfo fi = new FieldInfo(dc, n);
    return regMap.containsKey(fi);
  }
  
  
  public String toString() {
    StringBuffer result = new StringBuffer();
    result.append("\n");
    
    for (FieldInfo key : regMap.keySet()) {
      result.append(key.toString());
      result.append(" -> ");
      HashSet<Integer> vals = regMap.get(key);
      for (Integer val : vals) {
        result.append(val + " ");        
      }
      result.append("\n");
    }    
    return result.toString();
  }
  
  
}