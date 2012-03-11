package energy.components;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.util.collections.Pair;

/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author progsys
 *
 */
public class RunnableThread extends Component{

  static String elements[] = { "run" };
  
  public RunnableThread(IClass declaringClass, CGNode root) {
    super(declaringClass, root);
    
    callbackNames.addAll(Arrays.asList(elements));          
    callbackExpectedState = new HashSet<Pair<String,List<String>>>();
    callbackExpectedState.add(Pair.make(
        "run", 
        Arrays.asList("lightgreen", "green","lightgrey")));   
  }
  
  public String toString() {
    StringBuffer b = new StringBuffer();
    b.append("Runnable thread: ");
    b.append(getKlass().getName().toString());
    return b.toString();
  }  
  
}
