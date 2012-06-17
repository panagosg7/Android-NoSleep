/**
 * 
 */
package edu.ucsd.energy.retarget;

import java.io.File;
import java.io.IOException;

import edu.ucsd.energy.RetargetException;

public interface DexConverter {
	public File getTarget();
	public File getJarTarget();
	public File getLogTarget();
	public File getErrTarget();
	public boolean conversionSuccess();
	public void convert() throws IOException, RetargetException;
}