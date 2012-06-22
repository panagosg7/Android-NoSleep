package edu.ucsd.energy.interproc;

import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IPartiallyBalancedFlowFunctions;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;

public interface ILockingFlowFunctionMap<T> extends	IPartiallyBalancedFlowFunctions<T> {
	
	//Maybe change the async field here
	public IUnaryFlowFunction getAsyncCallFlowFunction(T src, T dest, T ret);

	public IFlowFunction getAsyncReturnFlowFunction(T target,	T exit,	T returnSite);

}
