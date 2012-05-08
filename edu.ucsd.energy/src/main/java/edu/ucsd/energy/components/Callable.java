package edu.ucsd.energy.components;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;

import edu.ucsd.energy.analysis.AppCallGraph;
import edu.ucsd.energy.analysis.WakeLockManager.WakeLockInstance;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;

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

  
  public CompoundLockState getThreadExitState() {
	CGNode runNode = super.getCallBackByName("call").getNode();	
	return super.getExitState(runNode);
  }
  
  
}
