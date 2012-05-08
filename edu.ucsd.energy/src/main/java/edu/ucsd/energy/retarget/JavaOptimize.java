/**
 * 
 */
package edu.ucsd.energy.retarget;

import java.io.File;
import java.io.IOException;

import edu.ucsd.energy.entry.RetargetException;

public interface JavaOptimize {
	public void optimize(File retargetedTarget) throws IOException, RetargetException;
	public boolean attempted();
	public boolean success();
	public File getTarget();
	public File getJarTarget();
	
	public File getLogTarget();
	public File getErrTarget();
	
	public void clean();
}