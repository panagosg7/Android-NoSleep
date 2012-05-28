package edu.ucsd.energy.viz;

import java.util.Set;

import com.ibm.wala.util.WalaException;
import com.ibm.wala.viz.NodeDecorator;

import edu.ucsd.energy.interproc.SingleLockState.LockStateColor;

public interface IColorNodeDecorator extends NodeDecorator {
  
  public String getLabel(Object o) throws WalaException;
  
  public Set<LockStateColor> getFillColors(Object o);

  public String getFontColor(Object n);

}
