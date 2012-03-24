package energy.components;

import java.util.Arrays;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.util.collections.Pair;

import energy.analysis.ApplicationCallGraph;

public class Activity extends Component {
  
  static String elements[] = {
    "onCreate", 
    "onDestroy",
    "onPause",
    "onResume", 
    "onStart", 
    "onStop", 
    "onRestart"
    };
 
  
  
  public Activity(ApplicationCallGraph originalCG, IClass declaringClass, CGNode root) {
    super(originalCG, declaringClass, root);
  
    componentName = "Activity";
    
    callbackNames.addAll(Arrays.asList(elements));
    callbackEdges.add(Pair.make("onCreate", "onStart"));
    callbackEdges.add(Pair.make("onStart", "onResume"));
    callbackEdges.add(Pair.make("onResume", "onPause"));
    //callbackEdges.add(Pair.make("onPause", "onResume"));
    callbackEdges.add(Pair.make("onPause", "onStop"));
    callbackEdges.add(Pair.make("onStop", "onDestroy"));
    //callbackEdges.add(Pair.make("onRestart", "onStart"));
    //callbackEdges.add(Pair.make("onStop", "onRestart"));
    callbackEdges.add(Pair.make("onStop", "onDestroy"));      
    
    
    /* Deprecating this - not very useful*/
    /*
    callbackExpectedState.add(Pair.make(
        "onPause", 
        Arrays.asList("lightgreen", "green","lightgrey")));
    callbackExpectedState.add(Pair.make(
        "onStop", 
        Arrays.asList("lightgreen", "green","lightgrey")));
    */
  }
  

  public String toString() {    
    StringBuffer b = new StringBuffer();
    b.append( componentName + ": ");
    b.append(getKlass().getName().toString());
    return b.toString();
  }  
}
