package edu.ucsd.energy;
//Authors: John C. McCullough and Panagiotis Vekris
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.debug.UnimplementedError;

import edu.ucsd.energy.ApkCollection.ApkApplication;
import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.apk.ApkInstance;
import edu.ucsd.energy.apk.ConfigurationException;
import edu.ucsd.energy.results.FailReport;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.results.ResultReporter;
import edu.ucsd.energy.results.Violation;
import edu.ucsd.energy.results.Warning.WarningType;
import edu.ucsd.energy.util.Log;
import edu.ucsd.energy.util.SystemUtil;

public class Main {

	private static File acqrelDatabaseFile;

	private static int numberOfThreads = 1;		//default is one thread

	private final static Logger LOGGER = Logger.getLogger(ApkInstance.class.getName());

	private static ApkCollection collection;

	private static ArrayList<String> theSet = new ArrayList<String>();
	
	public static final Object logLock = new Object();

	public static boolean AVOID_APK_CHECK = false;


	public static void runWakeLockAnalysis() 
			throws IOException, ApkException, RetargetException, WalaException, CancelException, InterruptedException {
		JobPool<WakeLockCreationTask> jobPool = new JobPool<WakeLockCreationTask>() {
			protected WakeLockCreationTask newTask(ApkInstance apk) { return new WakeLockCreationTask(apk); } 
		};
		runAnalysis(jobPool);
	}

	public static void runVerifyAnalysis() throws ApkException, IOException, RetargetException,	WalaException, CancelException, InterruptedException {
		JobPool<VerifyTask> jobPool = new JobPool<VerifyTask>() {
			protected VerifyTask newTask(ApkInstance apk) {
				return new VerifyTask(apk); 
			} 
		};
		callAnalysis(jobPool);
	}

	public static void runUsageAnalysis() 
			throws ApkException, IOException, RetargetException, WalaException, CancelException, InterruptedException {
		JobPool<UsageAnalysisTask> jobPool = new JobPool<UsageAnalysisTask>() {
			protected UsageAnalysisTask newTask(ApkInstance apk) { return new UsageAnalysisTask(apk); } 
		};
		runAnalysis(jobPool);
	}

	private static abstract class RunnableTask implements Runnable {

		public static JSONObject fullObject = new JSONObject();

		protected ApkInstance apk;

		RunnableTask(ApkInstance apk) {
			this.apk = apk;
		}

		public String getApkName() {
			return apk.getName();
		}

	}

	private static abstract class CallableTask implements Callable<IReport> {
		
		private static DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		
		//Common among threads - but must synchronize
		private static Integer counter = new Integer(0);
		
		protected InheritableThreadLocal<ApkInstance> apk = new InheritableThreadLocal<ApkInstance>();
		private ThreadLocal<Integer> myCounter = new ThreadLocal<Integer>();
		private ThreadLocal<Date> startTime 	 = new ThreadLocal<Date>();
		private ThreadLocal<Date> stopTime		 = new ThreadLocal<Date>();

		CallableTask(ApkInstance a) {
			apk.set(a);
		}

		protected synchronized void startTimer() {
			Date date = new Date();
			startTime.set(date);
			counter++;
			myCounter.set(new Integer(counter.intValue()));
			StringBuffer sb = new StringBuffer();
			ApkInstance a = apk.get();
			sb.append("\n>>> " + dateFormat.format(date) + "\n");						
			sb.append(">>> " + myCounter.get() + ". " +  a.getName() + " version: " + a.getVersion() + "\n");
			System.out.println(sb.toString());
		}

		protected synchronized void stopTimer() {
			Date date = new Date();
			stopTime.set(date);
			ApkInstance a = apk.get();
			long diff = stopTime.get().getTime() - startTime.get().getTime();
			double diffSeconds = (double) diff / 1000 % 60;  
			long diffMinutes = diff / (60 * 1000) % 60;
			System.out.println("\n<<< "+ dateFormat.format(date));						
			System.out.println("<<< " + myCounter.get() + ". " +  a.getName() + " version: " + 
					a.getVersion() + " (elapsed: " + diffMinutes + "m" + String.format("%.2f", diffSeconds) + "s)\n");
			//release threadlocals!
			apk.remove();
			myCounter.remove();
			startTime.remove();
			stopTime.remove();			
		}

	}



	// --verify
	private static class VerifyTask extends CallableTask {

		VerifyTask(ApkInstance a) {
			super(a);
		}

		public IReport call()  {
			ApkInstance a = apk.get();
			IReport res;
			startTimer();
			try {
				String app_name = a.getName();
				if (AVOID_APK_CHECK || a.successfullyOptimized()) {
					try {
						res = a.analyzeFull();
						//success
						SystemUtil.writeToCompleted(a.getName());
					} catch(Exception e) {
						//Any exception should be notified
						e.printStackTrace();		//XXX: keep this somewhere
						res = new FailReport(WarningType.ANALYSIS_FAILURE);
						Log.red("\n<<< "+ a.getName()+ " FAILURE");
					}
					catch (UnimplementedError e) {
						e.printStackTrace();
						LOGGER.warning(e.getMessage());
						res = new FailReport(WarningType.UNIMPLEMENTED_FAILURE);
					}								
				} else {
					LOGGER.warning("Optimization failed: " + app_name);
					res = new FailReport(WarningType.OPTIMIZATION_FAILURE);
				}
				JSONObject json = new JSONObject();
				res.appendTo(json);
				json.put("version", a.getVersion());
				SystemUtil.commitReport(a.getName(), json);
			} catch (IOException e) {
				e.printStackTrace();
				res = new FailReport(WarningType.IOEXCEPTION_FAILURE);
			}
			//Dump the output file in each intermediate step
			SystemUtil.writeToFile();
			stopTimer();
			//Hint to garbage collector
			System.gc();

			return res;
		}

	}


	// --usage
	private static class UsageAnalysisTask extends RunnableTask {

		UsageAnalysisTask(ApkInstance apk) {
			super(apk);
		}

		public void run() {
			try {
				LOGGER.info("Starting: " + apk.getName());
				String app_name = apk.getName();
				IReport res;
				if (apk.successfullyOptimized()) {
					try {
						res = apk.analyzeUsage();
						SystemUtil.writeToFile(
								String.format("\n%30s - %15s\n", apk.getName(), apk.getVersion()) + 
								res.toShortDescription());

					} catch(Exception e) {
						//Any exception should be notified
						e.printStackTrace();		//XXX: keep this somewhere
						res = new FailReport(WarningType.ANALYSIS_FAILURE);
					}
					catch (UnimplementedError e) {
						e.printStackTrace();
						LOGGER.warning(e.getMessage());
						res = new FailReport(WarningType.UNIMPLEMENTED_FAILURE);
					}								
				} else {
					LOGGER.warning("Optimization failed: " + app_name);
					res = new FailReport(WarningType.OPTIMIZATION_FAILURE);
				}
				JSONObject json = (JSONObject) res.toJSON();
				json.put("version", apk.getVersion());
				//SystemUtil.commitReport(apk.getName(), json);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}


	//--wakelock-info
	private static class WakeLockCreationTask extends RunnableTask {

		WakeLockCreationTask(ApkInstance apk) {
			super(apk);
		}

		private void updateJSON(String key, JSONObject obj) {
			synchronized (fullObject) {
				fullObject.put(key, obj);
			}
		}

		public void run() {
			JSONObject json = new JSONObject();
			try {
				LOGGER.info("Starting: " + apk.getName());
				if (apk.successfullyOptimized()) {
					try {
						IReport wakelockAnalyze = apk.wakelockAnalyze();
						if (wakelockAnalyze != null) {
							json = (JSONObject) wakelockAnalyze.toJSON();
						}
					} catch(Exception e) {
						json.put("result", WarningType.ANALYSIS_FAILURE.toString());
						System.err.println(apk.getName());
						e.printStackTrace();
					}
					catch (UnimplementedError e) {
						LOGGER.warning(e.getMessage());
						json.put("result", WarningType.UNIMPLEMENTED_FAILURE.toString());
					}								
				} else {
					json.put("result", WarningType.OPTIMIZATION_FAILURE.toString());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			json.put("version", apk.getVersion());
			SystemUtil.commitReport(apk.getName(), json);
			SystemUtil.writeToFile();
		}
	}



	private static abstract class JobPool<T> {
		Set<T> pool;
		JobPool() throws IOException {
			FileInputStream is = new FileInputStream(acqrelDatabaseFile);		
			JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
			//pool = new HashSet<RunnableTask>();
			pool = new LinkedHashSet<T>();
			for (Object key: theSet) {
				String app_name = ApkCollection.cleanApkName((String) key);
				String[] catsArray = acqrel_status.getString((String)key).split("[,]");			
				ArrayList<String> cats = new ArrayList<String>(catsArray.length);
				for (String cat: catsArray) {
					cats.add(cat);
				}					
				System.err.println("Adding: " + app_name + " to pool.");
				final ApkApplication application = collection.getApplication(app_name);
				if (application != null) {
					ApkInstance apk = application.getPreferred();
					T task = newTask(apk);
					pool.add(task);
				}
				else{
					System.err.println(app_name + " was not found in the collection.");
				}
			}// for theSet
			System.err.println("Added a total of: " + pool.size() + " apps in the pool.");
		}

		public Set<T> getPool() {
			return pool;
		}

		abstract protected T newTask(ApkInstance apk);
	}


	public static void runAnalysis(JobPool<? extends RunnableTask> jobPool) 
			throws ApkException, IOException, RetargetException, WalaException, CancelException, InterruptedException {
		//Initialize thread pool
		long keepAliveTime = 1;
		TimeUnit unit = TimeUnit.SECONDS;
		LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(1000);
		System.out.println("Thread pool size: " + numberOfThreads) ;
		System.out.println("Input set size: " + theSet.size()) ;
		ThreadPoolExecutor tPoolExec = new ThreadPoolExecutor(numberOfThreads, numberOfThreads, keepAliveTime, unit, workQueue);
		for (RunnableTask job: (Set<? extends RunnableTask>) jobPool.getPool()) {
			try {
				//tPoolExec.invokeAll(tasks, timeout, unit)
				tPoolExec.execute(job);
			}
			catch (RejectedExecutionException e) {
				LOGGER.info("Rejected: " + job.getApkName());
			}
		}
		tPoolExec.shutdown();
		tPoolExec.awaitTermination(20, TimeUnit.HOURS);
	}


	/**
	 * Version of the runAnalysis but for Callable tasks.
	 * This could return a list of reports - one for every application that is
	 * analyzed.
	 * @param jobPool
	 */
	public static void callAnalysis(JobPool<? extends CallableTask> jobPool) 
			throws ApkException, IOException, RetargetException, WalaException, CancelException, InterruptedException {
		System.out.println("==========================================");
		System.out.println("Thread pool size          :  " + numberOfThreads) ;
		System.out.println("Number of apps to analyze :  " + theSet.size());
		System.out.println("==========================================");

		//Initialize thread pool
		long keepAliveTime = 1;
		TimeUnit unit = TimeUnit.SECONDS;
		LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(1000);

		ThreadPoolExecutor tPoolExec = new ThreadPoolExecutor(numberOfThreads, numberOfThreads, keepAliveTime, unit, workQueue);
		Set<? extends CallableTask> tasks = jobPool.getPool();
		//we can get this result...
		
		tPoolExec.invokeAll(tasks);
		System.out.println(tPoolExec.getTaskCount());


		tPoolExec.shutdown();
		tPoolExec.awaitTermination(20, TimeUnit.HOURS);

	}



	/**
	 * Scans the collection of apks and checks whether each app is interesting 
	 * based on the calls we have declared as interesting (Interesting.java:mInterestingMethods)
	 * @throws ConfigurationException
	 */
	public static void findInteresting() throws ConfigurationException {
		if (collection == null) {
			collection = new ApkCollection();
		}
		List<ApkApplication> apps = collection.listApplications();
		int counter = 0;
		JSONObject obj = new JSONObject();		//The output object
		for (ApkApplication app : apps) {
			try {
				ApkInstance apkInstance = app.getPreferred();
				//apkInstance.requiresRetargeted();		//if we need to run retarget every time
				//might be a bit better to use checkRetargeted() cause smali might 
				//actually succeed when ded fails
				apkInstance.checkRetargeted();
				boolean hasWakelockCalls = apkInstance.hasWakelockCalls();
				System.out.println(String.format("%d. [%5b] %-50s - %s", (counter++), hasWakelockCalls, apkInstance.getName(), apkInstance.getVersion()));
				if (hasWakelockCalls) {
					obj.put(app.getName(), "");
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (RetargetException e) {
				e.printStackTrace();
			} catch (ApkException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Interesting: " + obj.size() + " / " + counter);
		SystemUtil.writeToFile(obj.toString());
	}

	public static String apkResultToString(ApkInstance apk, ArrayList<Violation> result) {
		StringBuffer sb = new StringBuffer();
		String res = String.format("%30s - %15s :: ", apk.getName(), apk.getVersion());
		sb.append(res);
		int length = res.length();
		if (result.size() > 0) {
			sb.append(result.get(0) + "\n");
			for (int i = 1 ; i < result.size(); i++ ) {
				sb.append(String.format("%" + length + "s", " ") + result.get(i) + "\n");
			}
		}
		else {
			sb.append("\n");
		}
		return sb.toString();
	}

	public static void outputAllResults(HashMap<ApkInstance, ArrayList<Violation>> result) {
		System.out.println();
		for (Entry<ApkInstance, ArrayList<Violation>> e : result.entrySet()) {
			System.out.println(apkResultToString(e.getKey(), e.getValue()));
		}
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

	public static void dumpPhantoms() throws ApkException, IOException, RetargetException, WalaException, CancelException {
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

	public static void dumpPhantomCounts() throws ApkException, IOException, RetargetException, WalaException, CancelException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));

		for (Object key: acqrel_status.keySet()) {
			String app_name = ApkCollection.cleanApkName((String)key);
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			System.out.println(app_name + ": " + apk.getOptPhantoms().size());
		}
	}

	/**
	 * Delete all the optimized files if there are phantom classes
	 * @param phantom
	 * @throws ApkException
	 * @throws IOException
	 * @throws RetargetException
	 * @throws WalaException
	 * @throws CancelException
	 */
	public static void flushPhantom(String phantom) throws ApkException, IOException, RetargetException, WalaException, CancelException {
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


	private static void findDiscrepancies() throws IOException, RetargetException {
		FileInputStream is = new FileInputStream(acqrelDatabaseFile);
		JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
		JSONObject successfullyOptimized = new JSONObject();
		int good = 0;
		int bad = 0;
		for (Object key: acqrel_status.keySet()) {
			String app_name = ApkCollection.cleanApkName((String)key);
			ApkInstance apk = collection.getApplication(app_name).getPreferred();
			if (apk.isSuccessfullyOptimized()) {
				int r = enumerateClasses(apk.getRetargetedJar());
				int o = enumerateClasses(apk.getOptimizedJar());
				if (r == o) {
					Log.green();
					good++;
					successfullyOptimized.put(app_name, "");
				}
				else {
					Log.red();
					bad++;
				}
				System.out.println(String.format("%40s %30s\t%4d vs %4d", apk.getName(), apk.getVersion(), r, o));
				Log.resetColor();
			}
		}
		System.out.println(String.format("Good: %d, Bad: %d Total: %d",good, bad, (good+bad)));

		//This will write by default to output.out
		SystemUtil.writeToFile(successfullyOptimized.toString());

	}


	private static int enumerateClasses(File file) throws IOException {
		Enumeration<JarEntry> entries = new JarFile(file.toString()).entries();
		int count = 0;
		while(entries.hasMoreElements()) {
			JarEntry nextElement = entries.nextElement();
			String string = nextElement.toString();
			if (string.endsWith(".class")) {
				count ++;
			}
		}
		return count;
	}

	public static void dumpExceptions() throws ApkException, IOException, RetargetException, WalaException, CancelException {
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

	public static void dumpFailedConversions() throws ApkException, IOException, RetargetException, WalaException, CancelException {
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

	public static void reoptimize() throws ApkException, IOException, RetargetException, WalaException, CancelException {
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
	public static void optimize() throws ApkException, IOException, RetargetException, WalaException, CancelException {
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
	public static void collectOptimized() throws ApkException, IOException, RetargetException, WalaException, CancelException {
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
		SystemUtil.writeToFile(obj.toString());
	}

	public static void readWorkConsumerResults() {
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

	private static void addToCollection(String[] values) {
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


	private static void setOutputFile(String optionValue) {
		String extension = FilenameUtils.getExtension(optionValue);
		String pureName = FilenameUtils.removeExtension(optionValue);
		SystemUtil.setOutputFileName(pureName + "_" + SystemUtil.getDateTime() + "." + extension);
	}

	private static void setInputJSONFile(String optionValue) {
		System.out.println("Using input: " + optionValue);
		acqrelDatabaseFile = new File(optionValue);
	}



	private static void reportResults(String[] optionValues) {
		ResultReporter resultReporter = new ResultReporter(optionValues[0]);
		resultReporter.fullResults();		

	}



	public static void main(String[] args) {

		Options options = new Options();
		CommandLineParser parser = new PosixParser();

		options.addOption(new Option("ph", "phantoms", false, "list phantom refs histogram"));
		options.addOption(new Option("pc", "phantom-counts", false, "list phantom counts per app"));
		options.addOption(new Option("e", "opt-exceptions", false, "list histogram of optimization exception types"));
		options.addOption(new Option("f", "failed", false, "list failed conversions"));
		options.addOption(new Option("fm", "find-missing", false, "find apps that are missing classes when optimized"));
		options.addOption(new Option("r", "optimize", false, "optimize unoptimized versions"));
		options.addOption(new Option("c", "collect-optimized", false, "collect optimized apps"));
		options.addOption(new Option("r", "reoptimize", false, "re-optimize failed conversions"));
		options.addOption(new Option("v", "verify", false, "verify"));
		options.addOption(new Option("u", "usage", false, "print the components that leave a callback locked or unlocked"));
		options.addOption(new Option("w", "wakelock-info", false, "gather info about wakelock creation"));
		options.addOption(new Option("o", "output", true, "specify an output filename (date will be included)"));
		options.addOption(new Option("unit", false, "run the unit tests"));
		options.addOption(new Option("i", "input", true, "specify the input json file"));
		options.addOption(new Option("rr", "report-results", true, "report the results that were found in json files in the given directory"));
		options.addOption(new Option("t", "threads", true, "run the analysis on t threads (works for pattern analysis only)"));
		options.addOption(new Option("skipN", true, "skip the first N application that are in line for analysis"));
		options.addOption(new Option("sp", "skip-prev", false, "skip application that are already analyzed"));
		
		options.addOption(new Option("ao", "avoid-apk-check", false, "avoid checking and if the apk is there (use with care as it will not be able to be retargeted)"));
		
		//Some applications may cause our analysis to hang - avoid them by writing them down in 
		//the properties file as "skip_apps = /home/pvekris/dev/apk_scratch/output/too_big.txt"
		options.addOption(new Option("sb", "skip-big", false, "skip the applications that are known to make the analysis hang"));
		options.addOption(new Option("s", "small-set", false, "run the analysis on a small set"));
		options.addOption(new Option("rf", "run-on-failed", false, "run the analysis on the previously failing (needs -i)"));
		options.addOption(new Option("fi", "find-interesting", false, "find the interesting application in the collection"));

		options.addOption(OptionBuilder.withLongOpt("create-json").hasArg()
				.withDescription("create a JSONObject from a file with a list of apps").create());

		options.addOption(OptionBuilder.withLongOpt("flush-phantom").hasArg()
				.withDescription("flush optimization files for apps with phantom named param").create());
		options.addOption(OptionBuilder.withLongOpt("add-to-collection").hasArgs(2)
				.withDescription("integrate into collection w/args path, collectionname").create());
		options.addOption(OptionBuilder.withLongOpt("read-consumer").hasArgs(0)
				.withDescription("process stats output by WorkConsumer").create());
		try {

			collection = new ApkCollection();
			CommandLine line = parser.parse(options,  args);
			if (line.hasOption("threads")) {
				setThreadNumber(line.getOptionValue("threads"));
			}
			if (line.hasOption("output")) {
				setOutputFile(line.getOptionValue("output"));
			}
			
			if (line.hasOption("avoid-apk-check")) {
				AVOID_APK_CHECK = true;
			}
			
			if (line.hasOption("input")) {
				setInputJSONFile(line.getOptionValue("input"));
			}
			else if (line.hasOption("unit")) {
				acqrelDatabaseFile = new File("/home/pvekris/dev/apk_scratch/output/unit.json");
			}
			else{
				acqrelDatabaseFile = new File("/home/pvekris/dev/apk_scratch/output/optimized.json");
			}

			//Define the set of apps to run the analysis on
			if (line.hasOption("small-set")) {
				/* The applications you specify here need to be in apk_collection !!! */
				theSet.add("NetCounter");
			}
			else if (line.hasOption("unit")) {
				theSet.add("Unit_01");
				theSet.add("Unit_02");
				theSet.add("Unit_03");
				theSet.add("Unit_04");
				theSet.add("Unit_05");
				theSet.add("Unit_06");
				theSet.add("Unit_Correct_01");
				theSet.add("Unit_Wrong_01");
				theSet.add("Unit_Wrong_02");
				theSet.add("Unit_Wrong_03");
				theSet.add("Unit_Wrong_04");
				theSet.add("Unit_Wrong_05");
				theSet.add("Unit_Wrong_06");
			}
			else {
				FileInputStream is = new FileInputStream(acqrelDatabaseFile);
				JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
				theSet.addAll((Set<String>) acqrel_status.keySet());
			}

			int c = 1;
			if (line.hasOption("skipN")) {
				Integer integer = Integer.parseInt(line.getOptionValue("skipN"));
				if (integer != null) {
					for(int i = 0; i < integer.intValue(); i ++) {
						String removed = theSet.remove(0);
						System.out.println(String.format("%3d. %s", (c++), "Skipping application: " + removed));
					}
				}
			}

			if (line.hasOption("skip-big")) {
				FileReader fileReader = new FileReader(ApkInstance.sSkipAppsFile);
				BufferedReader bufferedReader = new BufferedReader(fileReader);
				String app;
				System.out.println();
				while ((app = bufferedReader.readLine()) != null) {
					boolean remove = theSet.remove(app);
					if (remove) {
						System.out.println(String.format("%3d. %s", (c++), "Skipping big application: " + app));
					}
				}
				System.out.println();
				bufferedReader.close();
			}

			if (line.hasOption("skip-prev")) {
				try {
					FileReader fileReader = new FileReader(SystemUtil.completedFile);
					BufferedReader bufferedReader = new BufferedReader(fileReader);
					String app;
					System.out.println();
					while ((app = bufferedReader.readLine()) != null) {
						boolean remove = theSet.remove(app);
						if (remove) {
							System.out.println(String.format("%3d. %s", (c++), "Skipping already analyzed application: " + app));
						}
					}
					bufferedReader.close();
				} catch(FileNotFoundException e) {
					//If the file is not there don't do anything
					System.out.println("No completed application file present.\n");
				}
			}


			//TODO: Refine this - put it in a method 
			if (line.hasOption("run-on-failed")) {
				theSet = new ArrayList<String>();
				FileInputStream is = new FileInputStream(acqrelDatabaseFile);
				JSONObject acqrel_status = (JSONObject) JSONSerializer.toJSON(IOUtils.toString(is));
				int i = 1;
				for(String k : (Set<String>)acqrel_status.keySet()) {
					try {
						JSONObject o = acqrel_status.getJSONObject(k);
						Object res = o.get("result");
						if (res != null) {
							System.out.println((i++) + ". " +  k);
							theSet.add(k);
						}
					} 
					catch(Exception e) { }
				}
			}
			if (line.hasOption("find-missing")) {
				findDiscrepancies();
			} else if  (line.hasOption("flush-phantoms")) {
				flushPhantom(line.getOptionValue("flush-phantoms"));
			} else if (line.hasOption("phantoms")) {
				dumpPhantoms();
			} else if (line.hasOption("phantom-counts")) {
				dumpPhantomCounts();
			} else if (line.hasOption("opt-exceptions")) {
				dumpExceptions();
			} else if (line.hasOption("failed")) {
				dumpFailedConversions();
			} else if (line.hasOption("optimize")) {
				optimize();
			} else if (line.hasOption("wakelock-info")) {
				runWakeLockAnalysis();
			} else if (line.hasOption("collect-optimized")) {
				collectOptimized();
			} else if (line.hasOption("reoptimize")) {
				reoptimize();
			} else if (line.hasOption("verify")) {
				runVerifyAnalysis();
			} else if (line.hasOption("usage")) {
				runUsageAnalysis();
			} else if (line.hasOption("find-interesting")) {
				findInteresting();
			} else if (line.hasOption("unit")) {
				runUsageAnalysis();
			} else if (line.hasOption("add-to-collection")) {
				addToCollection(line.getOptionValues("add-to-collection"));
			} else if (line.hasOption("report-results")) {
				reportResults(line.getOptionValues("report-results"));				
			} else if (line.hasOption("read-consumer")) {
				readWorkConsumerResults();
			} else {
				throw new UnrecognizedOptionException(null);
			}

		} 
		catch (UnrecognizedOptionException e ) {
			for (Object opt: options.getOptions()) {
				System.err.println(opt);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}


}

