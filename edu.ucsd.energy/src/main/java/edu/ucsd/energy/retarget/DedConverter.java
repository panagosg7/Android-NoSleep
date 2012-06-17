/**
 * 
 */
package edu.ucsd.energy.retarget;

import java.io.File;
import java.io.IOException;

import edu.ucsd.energy.RetargetException;
import edu.ucsd.energy.apk.ApkInstance;
import edu.ucsd.energy.apk.ApkPaths;
import edu.ucsd.energy.util.SystemUtil;

public class DedConverter implements DexConverter {
	private ApkPaths mPaths;
	
	public DedConverter(ApkPaths paths) {
		mPaths = paths;
	}
	
	public void convert() throws IOException, RetargetException {
		File target = mPaths.dedRetargeted;
		File source = mPaths.apk;
		
		ApkInstance.LOGGER.info("Attempting to ded retarget to " + target);
		
		target.mkdirs();
				
		String cmd[] = {
				mPaths.dedPath + "/ded-0.7.1",
				"-d", target.getAbsolutePath(),
				source.getAbsolutePath()
		};
		
		SystemUtil.runCommand(cmd, mPaths.dedLog, mPaths.dedErrLogTarget, mPaths.dedSuccess, 
				new File(mPaths.dedPath.getAbsolutePath()));
	}

	public File getTarget() {
		return mPaths.dedRetargeted;
	}

	public boolean conversionSuccess() {
		return mPaths.dedSuccess.exists();
	}

	public File getJarTarget() {
		return mPaths.dedRetargetedJar;
	}

	public File getErrTarget() {
		return mPaths.dedErrLogTarget;
	}

	public File getLogTarget() {
		return mPaths.dedLog;
	}
}