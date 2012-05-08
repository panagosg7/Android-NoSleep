package edu.ucsd.energy.viz;

import java.util.ArrayList;
import java.util.Set;

import com.ibm.wala.util.WalaException;
import com.ibm.wala.viz.NodeDecorator;

import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.interproc.SingleLockState.LockStateColor;

public interface ColorNodeDecorator extends NodeDecorator {

  
  public String getLabel(Object o) throws WalaException;
  
  public LockStateColor getFillColor(Object o);
  
  public Set<SingleLockState> getFillColors(Object o);

  public String getFontColor(Object n);

}
