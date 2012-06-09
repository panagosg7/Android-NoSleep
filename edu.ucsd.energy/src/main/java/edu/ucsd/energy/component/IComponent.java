package edu.ucsd.energy.component;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;

import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.AbstractContextCFG;

public interface IComponent {

	public AbstractContextCFG makeCFG();
	
	public CallGraph getCallGraph();
	
	public void solve();
	
	public CompoundLockState getReturnState(CGNode cgNode);
	
	public CompoundLockState getState(IExplodedBasicBlock i);
	
	public CompoundLockState getState(SSAInstruction i);
	
	public String toFileName();	

}
