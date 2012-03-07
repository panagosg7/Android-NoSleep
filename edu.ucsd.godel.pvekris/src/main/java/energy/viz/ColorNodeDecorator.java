package energy.viz;

import com.ibm.wala.util.WalaException;
import com.ibm.wala.viz.NodeDecorator;

public interface ColorNodeDecorator extends NodeDecorator {

  
  public String getLabel(Object o) throws WalaException;
  
  public String getFillColor(Object o);

  public String getFontColor(Object n);

}
