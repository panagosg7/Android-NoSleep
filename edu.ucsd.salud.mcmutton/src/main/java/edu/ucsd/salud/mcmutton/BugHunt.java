package edu.ucsd.salud.mcmutton;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.IOUtils;

import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

import edu.ucsd.salud.mcmutton.apk.Wala;

public class BugHunt {
	
	private static File acqrelDatabaseFile;

	public static void dumpReachability(Map<MethodReference, Set<MethodReference>> reach, String dst) throws IOException {
		JSONObject obj = new JSONObject();

		
		for (MethodReference dstMr: reach.keySet()) {
			JSONArray arr = new JSONArray();
			for (MethodReference srcMr: reach.get(dstMr)) {
				arr.add(srcMr.toString());
			}
			obj.put(dstMr.toString(), arr);
		}
		
		File f = new File(dst);
		FileOutputStream fos = new FileOutputStream(f);
		OutputStreamWriter w = new OutputStreamWriter(fos);
		
		w.write(obj.toString());
		
		w.close();
	}
	
	public static void dumpModified(Set<String> modified, String dst) throws IOException {
		JSONArray arr = new JSONArray();
		for (String m: modified) {
			arr.add(m);
		}

		File f = new File(dst);
		FileOutputStream fos = new FileOutputStream(f);
		OutputStreamWriter w = new OutputStreamWriter(fos);
		
		w.write(arr.toString());
		
		w.close();
	}
	
	public static void runFacebookDiff(ApkCollection collection) throws IOException, ApkException {
		String versionA = "1.3.0";
        String versionB = "1.3.2";
		
		ApkInstance fb132 = collection.getApplication("Facebook").getVersion(versionB).getPreferred();
		ApkInstance fb130 = collection.getApplication("Facebook").getVersion(versionA).getPreferred();
		
		fb132.requiresExtraction();
		fb130.requiresExtraction();
                
		DiffParser dp = new DiffParser(new FileInputStream(new File("/home/jcm/working/apk-crawl.hg/facebook_" + versionA + "-" + versionB + ".diff")));
		
		String fbPath = fb130.getWorkPath().getParentFile().getParentFile().getAbsolutePath();
		
		dp.writeModifiedMethods(fbPath);
		
		Set<String> modifiedMethods = dp.modifiedMethods(fbPath);

		dumpModified(modifiedMethods, "modified.json");

		dumpReachability(fb132.interestingCallSites(), "call_132.json");
		dumpReachability(fb130.interestingCallSites(), "call_130.json");
		
//		Map<MethodReference, Set<MethodReference>> reach132 = fb132.interestingReachability();
//		Map<MethodReference, Set<MethodReference>> reach130 = fb130.interestingReachability();
		
//		dumpReachability(reach132, "reach_132.json");
//		dumpReachability(reach130, "reach_130.json");
		
//		for (MethodReference mr: reach132.keySet()) {
//			if (!reach130.containsKey(mr)) {
//				System.out.println("130 missing: " + mr + " :: " + reach132.get(mr));
//			} else {
//				Set<MethodReference> s132 = reach132.get(mr);
//				Set<MethodReference> s130 = reach130.get(mr);
//				
//				Set<MethodReference> union = new HashSet<MethodReference>();
//				union.addAll(s132);
//				union.addAll(s130);
//				
//				StringBuilder sb = new StringBuilder();
//				
//				sb.append(mr);
//				sb.append(" :: ");
//				
//				boolean difference = false;
//				
//				for (MethodReference smr: union) {
//					boolean in132 = s132.contains(smr);
//					boolean in130 = s130.contains(smr);
//					
//					if (in130 != in132) {
//						if (in130) sb.append(" -" + mr.toString());
//						else sb.append(" +" + mr.toString());
//						difference = true;
//					}
//					
//				}
//				
//				if (difference) System.out.println(sb.toString());
//			}
//		} 
	}
	

	public static void runPatternAnalysis(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
		runPatternAnalysis(collection, acqrel_status.keySet());
	}
	
	public static void runTestPatternAnalysis(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		Set<String> theSet = new HashSet<String>();
		
		//theSet.add("AndTweet");
		//theSet.add("aFlashlight");	//conversion error
		//theSet.add("FartDroid");
		//theSet.add("AndroBOINC");
		//theSet.add("");
		//theSet.add("");
		//theSet.add("");
		//theSet.add("FBReader");		//conversion error
		//theSet.add("Phonebook_2.0");
		//theSet.add("ICQ");
		//theSet.add("DISH");
		theSet.add("JuiceDefender");
		//theSet.add("Twidroyd");
		//theSet.add("WebSMS");		
		//theSet.add("3D_Level");
		
		runPatternAnalysis(collection, theSet);
	}

	
	private final static Logger LOGGER = Logger.getLogger(ApkInstance.class.getName());
	
	public static void runPatternAnalysis(ApkCollection collection, Set<String> theSet) 
			throws ApkException, IOException, RetargetException, WalaException, CancelException {
		acqrelDatabaseFile = new File("/home/pvekris/dev/apk_scratch/acqrel_status.json");
		
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));		
		
		Map<Wala.UsageType, Integer> histogram = new HashMap<Wala.UsageType, Integer>();
		
		for (Object key: theSet) {
						
			String app_name = collection.cleanApkName((String)key);
			
			String[] catsArray = acqrel_status.getString((String)key).split("[,]");			
			
			ArrayList<String> cats = new ArrayList<String>(catsArray.length);
			
			for (String cat: catsArray) cats.add(cat);			
			
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			
			Wala.UsageType usageType = Wala.UsageType.UNKNOWN;
			Set<String> panosResult = null;
			
			boolean successfullyOptimized = false;
			try {
				successfullyOptimized = apk.successfullyOptimized();
			} catch (RetargetException e) {
				System.err.println("Retarget failed: " + e.toString());
			}
			
			if (apk.successfullyOptimized()) {
				panosResult = apk.panosAnalyze();				
				usageType = Wala.UsageType.FAILURE;
			
			} else {
				LOGGER.warning("Failed to optimize: " + key.toString());
				usageType = Wala.UsageType.CONVERSION_FAILURE;
				//panosResult = usageType;
			}
			
			//TODO
			/*
			System.out.println(key + ":" + StringUtils.join(cats, ", ") + " -- " + 
					apk.successfullyOptimized() + " -- " + usageType + "==" + panosUsageType);
			System.out.println();
			*/
			//assert usageType == panosUsageType;

			if (histogram.containsKey(usageType)) {
				histogram.put(usageType, histogram.get(usageType) + 1);
			} else {
				histogram.put(usageType, 1);
			}
		
		}
		
		Map<Wala.BugLikely, Integer> likelihood = new HashMap<Wala.BugLikely, Integer>();
		
		long total = 0;
		for (Integer i: histogram.values()) {
			total += i;
		}
		for (Map.Entry<Wala.UsageType, Integer> e: histogram.entrySet()) {
			System.out.println(e.getKey() + " : " + e.getValue());
			
			if (!likelihood.containsKey(e.getKey().bugLikely)) {
				likelihood.put(e.getKey().bugLikely, e.getValue());
			} else {
				likelihood.put(e.getKey().bugLikely, e.getValue() + likelihood.get(e.getKey().bugLikely));
			}
		}
		
		
		
		for (Map.Entry<Wala.BugLikely, Integer> e: likelihood.entrySet()) {
			System.out.println(e.getKey() + " -- " + e.getValue() + " (" + e.getValue()/(float)total + ")");
		}
		
//		
//		
//		ApkInstance apk = collection.getApplication("ColorDict").getPreferred();
//		
//		Map<MethodReference, Set<MethodReference>> interesting = apk.interestingCallSitesWala();
//		
//		for (Map.Entry<MethodReference, Set<MethodReference>> e: interesting.entrySet()) {
//			System.out.println(e.getKey() + " -- " + e.getValue());
//		}
	}
	
	private static class PhantomTracker {
		private Map<String, Integer> mPhantoms = new HashMap<String, Integer>();
		
		public void addPhantoms(Set<String> set) {
			for (String s: set) {
				addPhantom(s);
			}
		}
		
		public void addPhantom(String s) {
			if (s == null) return;
			
			Integer v = mPhantoms.get(s);
			if (v == null) v = new Integer(1);
			else v += 1;
			mPhantoms.put(s, v);
		}
		
		public void dump() {
			List<String> rankedPhantoms = new ArrayList<String>();
			rankedPhantoms.addAll(mPhantoms.keySet());
			Collections.sort(rankedPhantoms, new Comparator<String>(){
				public int compare(String arg0, String arg1) {
						return mPhantoms.get(arg0) - mPhantoms.get(arg1);
				} });
			for (String s: rankedPhantoms) {
				System.out.println(s + " -- " + mPhantoms.get(s));
			}
		}
	}
	
	public static void dumpPhantoms(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));

		PhantomTracker t = new PhantomTracker();
		
		for (Object key: acqrel_status.keySet()) {
			String app_name = ApkCollection.cleanApkName((String)key);
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			t.addPhantoms(apk.getOptPhantoms());
		}
		
		t.dump();
	}
	
	public static void dumpPhantomCounts(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));

		for (Object key: acqrel_status.keySet()) {
			String app_name = ApkCollection.cleanApkName((String)key);
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			System.out.println(app_name + ": " + apk.getOptPhantoms().size());
		}
	}
	
	public static void flushPhantom(ApkCollection collection, String phantom) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));

		for (Object key: acqrel_status.keySet()) {
			String app_name = ApkCollection.cleanApkName((String)key);
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			if (apk.getOptPhantoms().contains(phantom)) {
				System.err.println("flushing " + app_name);
				apk.cleanOptimizations();
			}
		}
	}
	
	public static void dumpExceptions(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));

		PhantomTracker t = new PhantomTracker();
		
		for (Object key: acqrel_status.keySet()) {
			String app_name = ApkCollection.cleanApkName((String)key);
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			t.addPhantom(apk.getOptException());
		}
		
		t.dump();
	}
	
	public static void dumpFailedConversions(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));

		for (Object key: acqrel_status.keySet()) {
			String app_name = ApkCollection.cleanApkName((String)key);
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			
			if (!apk.successfullyOptimized()) {
				System.out.println(apk.getDedTarget());
				try {
					apk.buildOptimizedJava();
				} catch (RetargetException e) {
					System.err.println("failed");
				}
			}
		}
	}
	
	public static void reoptimize(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
	
		for (Object key: acqrel_status.keySet()) {
			String app_name = ApkCollection.cleanApkName((String)key);
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			apk.buildOptimizedJava();
		}
	}
	
	public static void runPanos(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
//		ApkInstance apk = collection.getApplication("DISH").getPreferred();
//		ApkInstance apk = collection.getApplication(ApkCollection.cleanApkName("Robo Defense FREE")).getPreferred();
//		System.out.println(apk.panosAnalyze());
		
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
	
		Map<String, Integer> colorCount = new HashMap<String, Integer>();
		
		for (Object key: acqrel_status.keySet()) {
			try {
				String app_name = ApkCollection.cleanApkName((String)key);
				ApkInstance apk = collection.getApplication(app_name).getPreferred();
				Set<String> colors = apk.panosAnalyze();
				
				for (String color: colors) {
					if (!colorCount.containsKey(color)) colorCount.put(color,  new Integer(1));
					else colorCount.put(color, colorCount.get(color) + 1);
				}
				System.out.println(app_name + ": " + colors);
			} catch (Exception e) {
				System.out.println("explode!");
				String color = "failed";
				if (!colorCount.containsKey(color)) colorCount.put(color,  new Integer(1));
				else colorCount.put(color, colorCount.get(color) + 1);
			} catch (Error e) {
				System.out.println("more explode!");
				String color = "failed";
				if (!colorCount.containsKey(color)) colorCount.put(color,  new Integer(1));
				else colorCount.put(color, colorCount.get(color) + 1);
			}
		}
		
		System.out.println(colorCount);	
	}
	
	public static void readWorkConsumerResults(ApkCollection collection) {
		File resultsPath = new File(ApkInstance.sScratchPath + File.separator + "results");
		
		FileFilter filter = new FileFilter() {
			public boolean accept(File arg0) {
				return arg0.getName().matches(".*[.]json");
			}
		};
		

		int totalCount = 0;
		int hasWakelockCalls = 0;
		int retargeted = 0;
		int optimized = 0;
		
		int panosFailure = 0;
		int patternFailure = 0;
		
		Set<String> panosExceptions = new HashSet<String>();
		
		for (File resultsJson: resultsPath.listFiles(filter)) {
			try {
				FileInputStream is = new FileInputStream(resultsJson);
				JSONObject obj = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
				is.close();
				++totalCount;
//				System.err.println(resultsJson);
				
				if (obj.getBoolean("hasWakelockCalls")) {
					++hasWakelockCalls;
					
					if (obj.getBoolean("successfullyRetargeted")) {
						++retargeted;
						if (obj.getBoolean("successfullyOptimized")) {
							++optimized;
							
							JSONObject panos_result = (JSONObject)obj.get("panosResult");
							if (panos_result.containsKey("_exception")) {
								++panosFailure;
								panosExceptions.add(panos_result.getString("_exception"));
							} else if (panos_result.containsKey("_error")) {
								++panosFailure;
								panosExceptions.add(panos_result.getString("_error"));
							}
							
							JSONObject pattern_result = (JSONObject)obj.getJSONObject("patternResult");
							if (pattern_result.containsKey("_exception") | pattern_result.containsKey("_error")) ++ patternFailure;
						}
					}
				}
			} catch (IOException e) {
				System.err.println("Error reading " + resultsJson + " " + e.toString());
			} 
		}
		
		
		System.out.println("totalCount: " + totalCount);
		System.out.println("wakeLockCount: " + hasWakelockCalls);
		System.out.println("retargeted: " + retargeted);
		System.out.println("optimized: " + optimized);
		System.out.println("panosFailure: " + panosFailure);
		System.out.println("patternFailure: " + patternFailure);
		
		for (String err: panosExceptions) {
			System.out.println("panos: " + err);
		}
	}
		
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Options options = new Options();
		CommandLineParser parser = new PosixParser();
		
		options.addOption(new Option("p", "phantoms", false, "list phantom refs histogram"));
		options.addOption(new Option("P", "phantom-counts", false, "list phantom counts per app"));
		options.addOption(new Option("e", "opt-exceptions", false, "list histogram of optimization exception types"));
		options.addOption(new Option("f", "failed", false, "list failed conversions"));
		options.addOption(new Option("r", "reoptimize", false, "re-optimize failed conversions"));
		options.addOption(new Option("s", "patterns", false, "perform patterns analysis and print results"));
		options.addOption(new Option("S", "test-patterns", false, "perform pattern analysis on a small subset of apks"));
		options.addOption(OptionBuilder.withLongOpt("panos")
				   .withDescription("run john's interpretation of panos' code")
				   .create());
		options.addOption(OptionBuilder.withLongOpt("flush-phantom")
									   .hasArg()
									   .withDescription("flush optimization files for aps with phantom named param")
									   .create());
		options.addOption(OptionBuilder.withLongOpt("add-to-collection")
									   .hasArgs(2)
									   .withDescription("integrate into collection w/args path, collectionname")
									   .create());
		options.addOption(OptionBuilder.withLongOpt("read-consumer")
				   .hasArgs(0)
				   .withDescription("process stats output by WorkConsumer")
				   .create());
		
		try {
	    	ApkCollection collection = new ApkCollection();
	    	
	    	CommandLine line = parser.parse(options,  args);

	    	if (line.hasOption("flush-phantoms")) {
	    			flushPhantom(collection, line.getOptionValue("flush-phantoms"));
	    		} else if (line.hasOption("phantoms")) {
	    			dumpPhantoms(collection);
	    		} else if (line.hasOption("phantom-counts")) {
	    			dumpPhantomCounts(collection);
	    		} else if (line.hasOption("opt-exceptions")) {
	    			dumpExceptions(collection);
	    		} else if (line.hasOption("failed")) {
	    			dumpFailedConversions(collection);
	    		} else if (line.hasOption("reoptimize")) {
	    			reoptimize(collection);
	    		} else if (line.hasOption("patterns")) {
	    			runPatternAnalysis(collection);
	    		} else if (line.hasOption("test-patterns")) {
	    			runTestPatternAnalysis(collection);
	    		} else if (line.hasOption("add-to-collection")) {
	    			String values[] = line.getOptionValues("add-to-collection");
	    			File basePath = new File(values[0]);
	    			if (!basePath.exists()) {
	    				System.err.println("base apk path does not exist: " + basePath);
	    				return;
	    			}
	    			
	    			String collectionName = values[1];
	    			if (collectionName.length() == 0) {
	    				System.err.println("must specify collection name");
	    				return;
	    			}
	    			collection.integrateApks(basePath, collectionName);
	    		} else if (line.hasOption("panos")) {
	    			runPanos(collection);
	    		} else if (line.hasOption("read-consumer")) {
	    			readWorkConsumerResults(collection);
	    		} else {
	    			for (Object opt: options.getOptions()) {
	    				System.err.println(opt);
	    			}
	    		}

	    	//runFacebookDiff(collection);
//	    	runPatternAnalysis(collection);
//	    	dumpFailedConversions(collection);
//	    	dumpPhantoms(collection);
	    	
             	
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
