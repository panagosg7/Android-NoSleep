package edu.ucsd.energy.component;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;

import edu.ucsd.energy.interproc.AbstractContextCFG;
import edu.ucsd.energy.interproc.CompoundLockState;

public interface IContext {

	public AbstractContextCFG makeCFG();
	
	public CallGraph getContextCallGraph();
	
	public void solve();
	
	public boolean solved();
	
	public CompoundLockState getReturnState(CGNode cgNode);
	
	public CompoundLockState getState(IExplodedBasicBlock i);
	
	public CompoundLockState getState(SSAInstruction i);
	
	public String toFileName();

	public boolean callsInteresting();
	
	public boolean extendsAndroid();

	
}
