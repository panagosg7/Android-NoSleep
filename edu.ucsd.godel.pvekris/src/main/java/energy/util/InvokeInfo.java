package energy.util;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInvokeInstruction;

/**
 * 
 * Class encapsulating information about the invoke instruction  
 *
 */

public class InvokeInfo {
  
  private SSAInvokeInstruction invokeInstruction;
  private ISSABasicBlock invokeBasicBlock;
  
  public boolean isWakeLockInstruction() {
    return getInvokeInstruction().getDeclaredTarget().getSignature().toString().
        contains("android.os.PowerManager$WakeLock");
  }
  
  public boolean isWifiLockInstruction() {
    return getInvokeInstruction().getDeclaredTarget().getSignature().toString().
        contains("android.net.wifi.WifiManager$WifiLock");
  }
  
  public boolean isAcquireInstruction() {
    return getInvokeInstruction().getDeclaredTarget().getSignature().toString().
        contains("acquire");
  }
  
  public boolean isReleaseInstruction() {
    return getInvokeInstruction().getDeclaredTarget().getSignature().toString().
        contains("release");
  }
  
  public InvokeInfo(SSAInvokeInstruction ii, ISSABasicBlock bb) {
    this.setInvokeInstruction(ii); 
    this.setInvokeBasicBlock(bb);
  }

  public SSAInvokeInstruction getInvokeInstruction() {
    return invokeInstruction;
  }

  public void setInvokeInstruction(SSAInvokeInstruction invokeInstruction) {
    this.invokeInstruction = invokeInstruction;
  }

  public ISSABasicBlock getInvokeBasicBlock() {
    return invokeBasicBlock;
  }

  public void setInvokeBasicBlock(ISSABasicBlock invokeBasicBlock) {
    this.invokeBasicBlock = invokeBasicBlock;
  }
  
  public String toString() {
    StringBuffer buffer = new StringBuffer();
    buffer.append("BB" + invokeBasicBlock.getNumber());
    buffer.append(" - ");
    buffer.append(invokeInstruction.getDeclaredTarget().getSignature().toString());
    return buffer.toString();
  }
  
  
}  