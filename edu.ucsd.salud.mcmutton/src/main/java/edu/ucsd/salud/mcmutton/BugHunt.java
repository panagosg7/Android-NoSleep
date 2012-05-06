package edu.ucsd.salud.mcmutton;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.debug.UnimplementedError;

import edu.ucsd.salud.mcmutton.ApkCollection.ApkApplication;
import energy.analysis.Opts;
import energy.analysis.ProcessResults.ResultType;
import energy.analysis.Result;

public class BugHunt {
	
	private static File acqrelDatabaseFile;
	private static String outputFileName;
	private static File outputFile;
	
	private static int numberOfThreads;

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
	

	public static void runPatternAnalysis(ApkCollection collection) 
			throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
		LOGGER.info("acq_rel size: " + is.toString());
		
		runPatternAnalysis(collection, acqrel_status.keySet());	
	}
	
	public static void runTestPatternAnalysis(ApkCollection collection) 
			throws ApkException, IOException, RetargetException, WalaException, CancelException {
		Set<String> theSet = new HashSet<String>();
		/* The applications you specify here need to be in apk_collection !!! */
		theSet.add("BeyondPod");			//OK
		runPatternAnalysis(collection, theSet);
	}
	
	private final static Logger LOGGER = Logger.getLogger(ApkInstance.class.getName());
	
	public static void runPatternAnalysis(ApkCollection collection, Set<String> theSet) 
			throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);		
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
		
		HashMap<ApkInstance, ArrayList<Result>> result = new HashMap<ApkInstance, ArrayList<Result>>();

		//Initialize thread pool
		long keepAliveTime = 1;
		TimeUnit unit = TimeUnit.SECONDS;
		LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(1000);
		LOGGER.info("Thread pool size: " + numberOfThreads) ;
		LOGGER.info("Input set size: " + theSet.size()) ;
		ThreadPoolExecutor tPoolExec = new ThreadPoolExecutor(numberOfThreads, numberOfThreads,
				keepAliveTime, unit, workQueue);
		for (Object key: theSet) {
			String app_name = collection.cleanApkName((String)key);
			String[] catsArray = acqrel_status.getString((String)key).split("[,]");			
			ArrayList<String> cats = new ArrayList<String>(catsArray.length);
			for (String cat: catsArray) cats.add(cat);
			final ApkApplication application = collection.getApplication(app_name);
			try {
				tPoolExec.execute(new Runnable() {
					public void run() {
						ArrayList<Result> res;
						try {
							ApkInstance apk = application.getPreferred();
							LOGGER.info("Starting: " + apk.getName());
							res = runPatternAnalysisOnApk(apk);
							synchronized (outputFileName) {
								SystemUtil.writeToFile(outputFileName, apkResultToString(apk, res));	
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});
			}
			catch (RejectedExecutionException e) {
				LOGGER.info("Rejected: " + app_name);
			}
		}
		
		tPoolExec.shutdown();
		outputAllResults(result);
				
		Map<ResultType, Integer> histogram = makeHistogram(result);
		long total = 0;
		for (Integer i: histogram.values()) {
			total += i;
		}
		for (Entry<ResultType, Integer> e: histogram.entrySet()) {
			System.out.println(e.getKey() + " : " + e.getValue());
		}
	}
	
	
	private static ArrayList<Result> runPatternAnalysisOnApk(ApkInstance apk) throws IOException {		
		String app_name = apk.getName();
		ArrayList<Result> res = new ArrayList<Result>();
		if (apk.successfullyOptimized()) {
			try {
				res = apk.panosAnalyze();
				return res;
			} catch(Exception e) {
				//Any exception should be notified
				//e.printStackTrace();		//XXX: keep this somewhere
				res.add(new Result(ResultType.ANALYSIS_FAILURE, app_name));
				return res;
			}
			catch (UnimplementedError e) {
				//e.printStackTrace();
				LOGGER.warning(e.getMessage());
				res.add(new Result(ResultType.UNIMPLEMENTED_FAILURE, app_name));
				return res;
			}								
		} else {
			LOGGER.warning("Optimization failed: " + app_name);
			res.add(new Result(ResultType.OPTIMIZATION_FAILURE, app_name));
		}
		return res;
	}

	
	public static void findInteresting(ApkCollection collection) {
		List<ApkApplication> apps = collection.listApplications();
		for (ApkApplication app : apps) {
			//List<ApkVersion> vers = app.listVersions();
			//boolean determined = false;
			//for (ApkVersion ver : vers) {
				try {
					//if(determined) continue;					
					//ApkInstance apkInstance = ver.getPreferred();
					ApkInstance apkInstance = app.getPreferred();
					apkInstance.requiresRetargeted();
					if (apkInstance.hasWakelockCalls()) {
						//determined = true;
						System.out.println("++++++" + apkInstance.getName() + " - " + apkInstance.getVersion());
						SystemUtil.writeToFile("interesting.txt", apkInstance.getName() + "\n");
					}
					else {
						System.out.println("------" + apkInstance.getName() + " - " + apkInstance.getVersion());
					}
					
				} catch (IOException e) {
					e.printStackTrace();
				} catch (RetargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ApkException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			//}
		}
	}
	
	
	
	public static String apkResultToString(ApkInstance apk, ArrayList<Result> result) {
		StringBuffer sb = new StringBuffer();
		String res = String.format("%30s - %15s :: ", apk.getName(), apk.getVersion());
		sb.append(res);
		int length = res.length();
		//System.out.print(res);
		if (result.size() > 0) {
			sb.append(result.get(0) + "\n");
			//System.out.println(result.get(0));
			for (int i = 1 ; i < result.size(); i++ ) {
				sb.append(String.format("%" + length + "s", " ") + result.get(i) + "\n");
				//System.out.println(String.format("%" + length + "s", " ") + result.get(i));					
			}
		}
		else {
			sb.append("\n");
			//System.out.println();
		}
		return sb.toString();
	}

	public static void outputAllResults(HashMap<ApkInstance, ArrayList<Result>> result) {
		System.out.println();
		for (Entry<ApkInstance, ArrayList<Result>> e : result.entrySet()) {
			System.out.println(apkResultToString(e.getKey(), e.getValue()));
		}
	}

	
	
	private static Map<ResultType, Integer> makeHistogram(HashMap<ApkInstance, ArrayList<Result>> result) {
		Map<ResultType, Integer> histogram = new HashMap<ResultType, Integer>();
		for (Entry<ApkInstance, ArrayList<Result>> e : result.entrySet()) {
			ArrayList<Result> value = e.getValue();
			for (Result r : value) {
				ResultType resultType = r.getResultType();
				Integer integer = histogram.get(resultType);
				if (integer == null) {
					histogram.put(resultType, new Integer(0));
				}
				histogram.put(resultType, histogram.get(resultType) + 1);
			}
		}
		return histogram;
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
	
	/**
	 * Check to see how many apps have been successfully optimized
	 * Output a json file containing them
	 */
	public static void collectOptimized(ApkCollection collection) throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
		JSONObject obj = new JSONObject();		//The output object			
		int ok = 0;
		int fail = 0;
		for (Object key: acqrel_status.keySet()) {
			String app_name = /*ApkCollection.cleanApkName*/((String)key);
			System.out.print(app_name + " ... ");
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			if (apk.isSuccessfullyOptimized()) {
				obj.put(apk.getName(), "");
				System.out.println("[OK]");
				ok++;
			}
			else {
				System.out.println("[FAIL]");
				fail++;
			}
		}
		System.out.println("OK/FAIL/TOTAL = " + ok + "/" + fail + "/" + (ok+fail));
		SystemUtil.writeToFile(outputFileName, obj.toString());
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
	 * Value[1] should be a collection folder
	 * @param values
	 * @param collection
	 */
	private static void addToCollection(String[] values, ApkCollection collection) {
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
	}
	
	private static void setThreadNumber(String optionValue) {
		Integer integer = Integer.parseInt(optionValue);
		if (integer != null) {
			numberOfThreads = integer.intValue();
			if (numberOfThreads > 1) {
				Opts.RUN_IN_PARALLEL = true;
			}
		}
		else{
			numberOfThreads = 1;
		}
	}
	

	private static void createJSON(String input) {
		try {
			File file = new File(input);
			FileInputStream stream = new  FileInputStream(file);
			DataInputStream in = new DataInputStream(stream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			JSONObject obj = new JSONObject();			
			while ((strLine = br.readLine()) != null)   {
				obj.put(strLine.replace(" ", ""), "");
			}
			in.close();
			SystemUtil.writeToFile(outputFileName, obj.toString());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	private static void setOutputFile(String optionValue) {
		String extension = FilenameUtils.getExtension(optionValue);
		String pureName = FilenameUtils.removeExtension(optionValue);
		outputFileName = pureName + "_" + SystemUtil.getDateTime() + "." + extension;
		outputFile = new File(outputFileName);
	}

	private static void setInputJSONFile(String optionValue) {
		System.out.println("Using input: " + optionValue);
		acqrelDatabaseFile = new File(optionValue);
	}

	
	public static void main(String[] args) {

		Options options = new Options();
		CommandLineParser parser = new PosixParser();
		
		options.addOption(new Option("p", "phantoms", false, "list phantom refs histogram"));
		options.addOption(new Option("P", "phantom-counts", false, "list phantom counts per app"));
		options.addOption(new Option("e", "opt-exceptions", false, "list histogram of optimization exception types"));
		options.addOption(new Option("f", "failed", false, "list failed conversions"));
		options.addOption(new Option("r", "optimize", false, "optimize unoptimized versions"));
		options.addOption(new Option("c", "collect-optimized", false, "collect optimized apps"));
		options.addOption(new Option("r", "reoptimize", false, "re-optimize failed conversions"));
		options.addOption(new Option("s", "patterns", false, "perform patterns analysis and print results"));
		options.addOption(new Option("S", "test-patterns", false, "perform pattern analysis on a small subset of apks"));
		options.addOption(new Option("o", "output", true, "specify an output filename (date will be included)"));
		options.addOption(new Option("i", "input", true, "specify the input json file"));
		options.addOption(new Option("t", "threads", true, "run the analysis on t threads (works for pattern analysis only)"));
		
		options.addOption(OptionBuilder.withLongOpt("create-json").hasArg()
				   .withDescription("create a JSONObject from a file with a list of apps").create());
		
		options.addOption(OptionBuilder.withLongOpt("flush-phantom").hasArg()
									   .withDescription("flush optimization files for apps with phantom named param").create());
		options.addOption(OptionBuilder.withLongOpt("add-to-collection").hasArgs(2)
									   .withDescription("integrate into collection w/args path, collectionname").create());
		options.addOption(OptionBuilder.withLongOpt("read-consumer").hasArgs(0)
				.withDescription("process stats output by WorkConsumer").create());
		try {
	    	ApkCollection collection = new ApkCollection();
	    	CommandLine line = parser.parse(options,  args);
	    	if (line.hasOption("threads")) {
				setThreadNumber(line.getOptionValue("threads"));
	    	}
			if (line.hasOption("output")) {
				setOutputFile(line.getOptionValue("output"));
			}
			if (line.hasOption("input")) {
				setInputJSONFile(line.getOptionValue("input"));
			}
			else{
				acqrelDatabaseFile = new File("/home/pvekris/dev/apk_scratch/input.json");
			}
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
    		} else if (line.hasOption("collect-optimized")) {
    			collectOptimized(collection);
    		} else if (line.hasOption("reoptimize")) {
    			reoptimize(collection);
    		} else if (line.hasOption("patterns")) {
    			runPatternAnalysis(collection);
    		} else if (line.hasOption("create-json")) {
    			String input = line.getOptionValue("create-json");
    			createJSON(input);
    		} else if (line.hasOption("test-patterns")) {
    			runTestPatternAnalysis(collection);
    		} else if (line.hasOption("add-to-collection")) {
    			addToCollection(line.getOptionValues("add-to-collection"), collection);
    		} else if (line.hasOption("read-consumer")) {
    			readWorkConsumerResults(collection);
    		} else {
    			for (Object opt: options.getOptions()) {
    				System.err.println(opt);
    			}
    		}
             	
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


}
