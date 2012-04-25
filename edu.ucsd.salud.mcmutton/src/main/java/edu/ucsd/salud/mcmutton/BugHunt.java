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
import java.util.Map.Entry;
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
import com.ibm.wala.util.debug.UnimplementedError;

import edu.ucsd.salud.mcmutton.ApkCollection.ApkApplication;
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

	}
	

	public static void runPatternAnalysis(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
		runPatternAnalysis(collection, acqrel_status.keySet());
	}
	
	public static void runTestPatternAnalysis(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		Set<String> theSet = new HashSet<String>();
		
		/* The applications you specify here need to be in apk_collection !!! */
		
//		theSet.add("AndTweet");
//		theSet.add("K-9 Mail");
//		theSet.add("aFlashlight");			//conversion error
//		theSet.add("Maxthon");				//conversion error
//		theSet.add("FBReader");				//conversion error
//		theSet.add("Phonebook 2.0");		//conversion error
//		theSet.add("Twidroyd");				//conversion error
//		theSet.add("WebSMS");				//conversion error
//		theSet.add("Call Control");			//conversion error
//		theSet.add("Geohash Droid");		//conversion error
//		theSet.add("AdvancedMapViewer");	//conversion error
//		theSet.add("TagReader");			//conversion error
//		theSet.add("ReChat");				//conversion error
//		theSet.add("Strawberry Shortcake PhoneImage");		//conversion error

//		theSet.add("InstaFetch PRO");		//has recursive threads
//		theSet.add("2 Player Reactor");		//has recursive threads
//		theSet.add("Slice Slice");			//Runnable exception
//		theSet.add("aLogcat");				//getter for wakelock
		
		
//		theSet.add("InstaFetch PRO");		//has recursive threads
//		theSet.add("DISH");					//OK
//		theSet.add("JuiceDefender");		//OK
//		theSet.add("3D Level");				//OK
//		theSet.add("ColorNote");			//OK
		theSet.add("Adobe AIR");				//OK
//		theSet.add("Pikachu");				//OK
//		theSet.add("Google Sky Map");		//OK
//		theSet.add("RMaps");				//OK
//		theSet.add("Foursquare");			//OK
//		theSet.add("Brain Sooth");			//OK
//		theSet.add("Craigslist");			//OK
//		theSet.add("AllBinary Arcade One");	//OK
//		theSet.add("Soccer Livescores");	//OK

//		theSet.add("ICQ");					
//		theSet.add("Alchemy");
//		theSet.add("Farm Tower");
//		theSet.add("AndroBOINC");		
//		theSet.add("Tangram");
//		theSet.add("Google Voice");		
//		theSet.add("Farm Tower");


//		theSet.add("YouTube");
//		theSet.add("imo");
//		theSet.add("Android Agenda Widget");
//		theSet.add("ServicesDemo");			//toy example
		
		
		
		runPatternAnalysis(collection, theSet);
	}

	
	private final static Logger LOGGER = Logger.getLogger(ApkInstance.class.getName());
	
	public static void runPatternAnalysis(ApkCollection collection, Set<String> theSet) 
			throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);		
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
		Map<Wala.UsageType, Integer> histogram = new HashMap<Wala.UsageType, Integer>();
		
		int limit = 50;
		
		HashMap<ApkInstance, ArrayList<String>> result = new HashMap<ApkInstance, ArrayList<String>>();
		
		for (Object key: theSet) {
			String app_name = collection.cleanApkName((String)key);			
			String[] catsArray = acqrel_status.getString((String)key).split("[,]");			
			ArrayList<String> cats = new ArrayList<String>(catsArray.length);			
			for (String cat: catsArray) cats.add(cat);
			ApkApplication application = collection.getApplication(app_name);
			ApkInstance apk = application.getPreferred();
			Wala.UsageType usageType = Wala.UsageType.UNKNOWN;			
						
			if (apk.successfullyOptimized()) {
				try {
					result.put(apk, apk.panosAnalyze());
				} catch(Exception e) {
					//Any exception should be notified
					e.printStackTrace();
					usageType = Wala.UsageType.FAILURE;
					ArrayList<String> res = new ArrayList<String>();
					res.add("FAILURE: Analysis failed.");
					result.put(apk, res);
				}
				catch (UnimplementedError e) {
					e.printStackTrace();
					LOGGER.warning(e.getMessage());
					ArrayList<String> res = new ArrayList<String>();
					res.add("UNIMPLEMENTED: " + e.getMessage());
					result.put(apk, res);
					usageType = Wala.UsageType.UNIMPLEMENTED_FAILURE;
				}								
			
			} else {
				LOGGER.warning("\nOptimization failed.\n");
				ArrayList<String> res = new ArrayList<String>();
				res.add("FAILURE: Optimization failed.");
				result.put(apk, res);
				usageType = Wala.UsageType.CONVERSION_FAILURE;
			}

			if (histogram.containsKey(usageType)) {
				histogram.put(usageType, histogram.get(usageType) + 1);
			} else {
				histogram.put(usageType, 1);
			}
			
			if (--limit < 1) break;
		
		}
		
		System.out.println();
		System.out.println();

		for (Entry<ApkInstance, ArrayList<String>> e : result.entrySet()) {
			ApkInstance apk = e.getKey();
			String res = String.format("%30s - %15s :: ", apk.getName(), apk.getVersion());
			int length = res.length();
			System.out.print(res);
			ArrayList<String> value = e.getValue();
			if (value.size() > 0) {
				System.out.println(value.get(0));
				for (int i = 1 ; i < value.size(); i++ ) {
					System.out.println(String.format("%" + length + "s", " ") + value.get(i));					
				}
			}
			else {
				System.out.println();
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
			
			if (!apk.isSuccessfullyOptimized()) {
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
			System.out.println("\n" + app_name + "\n");			
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			apk.buildOptimizedJava();
			System.out.println("------------------------------------------");
		}
	}
	
	/**
	 * Just do the optimization.
	 * If the version we have is optimized do not try to optimize again.
	 */
	public static void optimize(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
	
		for (Object key: acqrel_status.keySet()) {
			String app_name = ApkCollection.cleanApkName((String)key);
			System.out.println("\n" + app_name + "\n");			
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			if (apk.isSuccessfullyOptimized()) {
				ApkInstance.LOGGER.info("Already optimized");
			}
			else {
				try {
					apk.buildOptimizedJava();
				}
				catch (RetargetException e) {
					ApkInstance.LOGGER.warning("Optimization failed");
				}
			}
			System.out.println("------------------------------------------");
		}
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
		options.addOption(new Option("r", "optimize", false, "optimize unoptimized versions"));
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
			acqrelDatabaseFile = new File("/home/pvekris/dev/apk_scratch/acqrel_status.json");		
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
	    		} else if (line.hasOption("optimize")) {
	    			optimize(collection);
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
