package edu.ucsd.energy.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import edu.ucsd.energy.analysis.Opts;

public class E {
	private static final int MAX_METHOD_NAME = 40;  
	static int DEBUG_LEVEL = 1;   /* higher means more details */

	private static String LOG_FILE = "log.out";

	/**
	 * Print to standard output and to log file, if this is enabled.
	 */
	public static void plog(int i, String f, String out) {
		if (i<=DEBUG_LEVEL) {
			if(!Opts.RUN_IN_PARALLEL) {
				System.out.println(out);
			}
			if (Opts.LOG_RESULTS) {
				flog(out);
			}
		}
	}

	public static void slog(int i, String f, String str) {
		if (i<=DEBUG_LEVEL) {
			String methName = getLastRealMethod(Thread.currentThread().getStackTrace().clone());
			if (methName.length() >= MAX_METHOD_NAME) {
				methName = methName.substring(0, MAX_METHOD_NAME - 3) + "...";
			}      
			String out = String.format("[%" + MAX_METHOD_NAME + "s] ", methName);
			String spaces = String.format("%" + (MAX_METHOD_NAME + 3) + "s", "");
			String strAndSpaces = str.replaceAll("\n", "\n" + spaces);
			out+=strAndSpaces;
			plog(i,f,out);     
		}
	}

	/**
	 * Print only to the application's log file
	 * @param s
	 */
	public static void flog(String s) {
		File file = new File(SystemUtil.getResultDirectory() + File.separatorChar + LOG_FILE);
		try {
			if(!file.exists()) {
				file.createNewFile();
			}
			FileWriter fstream = new FileWriter(file.getAbsoluteFile(),true);
			BufferedWriter logFile = new BufferedWriter(fstream);
			logFile.write(s + "\n");
			logFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}


	}


	private static String getLastRealMethod(StackTraceElement[] clone) {
		int i;    
		for(i = 1; clone[i].getClassName () == E.class.getName(); i++);
		String className = clone[i].getClassName().toString();
		String cn = className.substring(className.lastIndexOf('.') + 1);
		return cn + " $ "  + clone[i].getMethodName().toString();
	}

	/**
	 * Like log, but also define the file in which to write the output.
	 * @param i
	 * @param file
	 * @param str
	 */
	public static void log(int i, String str) {
		slog(i, LOG_FILE, str);    
	}


	public static void log(int i, List<String> sl) {
		for (String s : sl) {
			slog(i, LOG_FILE, s);
		}
	}

	public static void plog(int i, String s) {    
		plog(i, LOG_FILE, s);    
	}



	public static void err(String string) {
		System.err.println(string);    
	}


	public static void yellow() {
		System.out.print("\033[33m");
	}

	public static void resetColor() {
		System.out.print("\033[0m");
	}

	public static void green() {
		System.out.print("\033[32m");		
	}

	public static void red() {
		System.out.print("\033[31m");		
	}

	public static void boldGreen() {
		System.out.print("\033[1;32m");		
	}

	public static void boldRed() {
		System.out.print("\033[1;31m");		
	}

	public static void grey() {
		System.out.print("\033[1;30;40m");		
	}

}
