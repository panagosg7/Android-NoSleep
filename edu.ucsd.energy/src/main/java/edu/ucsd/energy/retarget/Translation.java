/**
 * 
 */
package edu.ucsd.energy.retarget;

import java.io.IOException;

import edu.ucsd.energy.entry.RetargetException;
import edu.ucsd.energy.util.SystemUtil;

public class Translation {
	public DexConverter mConverter;
	public JavaOptimize mOptimize;
	
	public Translation(DexConverter converter, JavaOptimize optimize) {
		mConverter = converter;
		mOptimize = optimize;
	}

	public boolean retargetSuccess() throws IOException {
	    return mConverter.conversionSuccess();
	}

	public void requiresRetargeted() throws IOException, RetargetException {
	    if (!retargetSuccess()) mConverter.convert();
	}

	public boolean optimizationSuccess() throws IOException {
	    return mOptimize.success();
	}

	public void requiresOptimized() throws IOException, RetargetException {
	    if (!optimizationSuccess()) buildOptimizedJava();
	}

	public void buildOptimizedJava() throws IOException, RetargetException {
	    requiresRetargeted();
	    if (mConverter.getTarget() != null) {
			mOptimize.optimize(mConverter.getTarget());
			if (mOptimize.getJarTarget().exists()) {
				mOptimize.getJarTarget().delete();
			}
	    } else {
	    	mOptimize.optimize(mConverter.getJarTarget());
	    }
	}

	public boolean hasRetargetedJar() throws IOException {
	    return mConverter.getJarTarget().exists();
	}

	public void buildRetargetedJar() throws IOException, RetargetException {
	    requiresRetargeted();
	    if (mConverter.getTarget() != null) {
		SystemUtil.buildJar(mConverter.getJarTarget(),
			    	    mConverter.getTarget());
	    }
	}

	public boolean hasOptimizedJar() throws IOException {
	    return mOptimize.getJarTarget().exists();
	}

	public void buildOptimizedJar() throws IOException, RetargetException {
//	    requiresOptimized();		//XXX: changed this for now (Apr 23 12:53 AM) - revert it to uncommented
	    SystemUtil.buildJar(mOptimize.getJarTarget(), mOptimize.getTarget());
	}

	public boolean successfullyOptimized() throws IOException, RetargetException {
	    if (!mOptimize.attempted()) requiresOptimized();
	    return mOptimize.success();
	}
}