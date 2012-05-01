package energy.components;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

import energy.analysis.AppCallGraph;
import energy.analysis.WakeLockManager.WakeLockInstance;
import energy.interproc.SingleLockState;

/**
 * TODO: This is not really a component
 * Using it just for now  
 * @author progsys
 *
 */
public class Callable extends Component {

  static String elements[] = { "run" };
  
  public Callable(AppCallGraph originalCG, IClass declaringClass, CGNode root) {
    super(originalCG, declaringClass, root);
    
    callbackNames.addAll(Arrays.asList(elements));          
  }    

  
  public Map<WakeLockInstance,Set<SingleLockState>> getThreadExitState() {
	CGNode runNode = super.getCallBackByName("call").getNode();	
	return super.getExitState(runNode);
  }
  
  
}
