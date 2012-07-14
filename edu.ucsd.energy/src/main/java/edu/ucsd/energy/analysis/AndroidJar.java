package edu.ucsd.energy.analysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.config.AnalysisScopeReader;

import edu.ucsd.energy.apk.ConfigurationException;
import edu.ucsd.energy.apk.Util;


public class AndroidJar {
	
	private static final String exclusionFileName = "/home/pvekris/dev/workspace/WALA_shared/" +
			"com.ibm.wala.core.tests/bin/Java60RegressionExclusions.txt";

	private static File exclusionFile;
	
	private static Map<Integer, ClassHierarchy> map = new HashMap<Integer, ClassHierarchy>();
	
	public static ClassHierarchy getAndroidCH(int androidVersion) throws ConfigurationException, IOException {
		Integer versInt = new Integer(androidVersion);
		ClassHierarchy androidJar = map.get(versInt);
		if (androidJar == null) {
			Properties prop = new Properties();
			File defaultProperties = new File ("properties.default");
			if (defaultProperties.exists()) {
				try {
					FileInputStream is = new FileInputStream(defaultProperties);
					if (is != null) {
						prop.load(is);
						
						File sAndroidSdkPath = Util.getAndCheckConfigPath(prop, "android_sdk_base");
						File androidFile = new File(sAndroidSdkPath + "/platforms/android-" + androidVersion  + "/android.jar");
						AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(androidFile.getAbsoluteFile().toString(), exclusionFile);	
						ClassHierarchy cha = ClassHierarchy.make(scope);
						map.put(versInt,cha);			
						is.close();
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ClassHierarchyException e) {
					e.printStackTrace();
				}
			}
		}
		return map.get(versInt);
	}


	
	public void dumpJar() throws IOException {
		
		

	}
	
}
