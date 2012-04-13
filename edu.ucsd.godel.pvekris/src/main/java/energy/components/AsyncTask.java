package energy.components;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

import energy.analysis.AppCallGraph;

public class AsyncTask extends Component{

  public AsyncTask(AppCallGraph originalCG, IClass declaringClass, CGNode root) {
    super(originalCG, declaringClass, root);

  }

  public String toString() {
    
    StringBuffer b = new StringBuffer();
    b.append("AsyncTask: ");
    b.append(getKlass().getName().toString());

    return b.toString();
  }
  
}
