package edu.ucsd.energy.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import edu.ucsd.energy.analysis.Options;

public class Log {
	private static final int MAX_METHOD_NAME = 40;  
	static int DEBUG_LEVEL = 1;   /* higher means more details */

	private static String LOG_FILE = "log.out";
	
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	/**
	 * Print to standard output and to log file, if this is enabled.
	 */
	public static void plog(int i, String f, String out) {
		if (i<=DEBUG_LEVEL) {
			if(!Options.RUN_IN_PARALLEL) {
				System.out.println(out);
			}
			if (Options.LOG_RESULTS) {
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
		File file = new File(SystemUtil.getResultDirectory(), LOG_FILE);
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
		for(i = 1; clone[i].getClassName () == Log.class.getName(); i++);
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
	
	public static void println(String string) {
		if (!Options.RUN_IN_PARALLEL) {
			System.out.println(string);
		}
	}

	public static void println() {
		if (!Options.RUN_IN_PARALLEL) {
			System.out.println();
		}
	}

	public static void print(String format) {
		if (!Options.RUN_IN_PARALLEL) {
			System.out.print(format);
		}		
	}

	public static void time() {
		if (!Options.RUN_IN_PARALLEL) {
			System.out.println(dateFormat.format(new Date()));
		}		
	}
	

	public static void time(String str) {
		if (!Options.RUN_IN_PARALLEL) {
			System.out.print("[" + dateFormat.format(new Date()) + "] "+ str);
		}		
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
	
	public static void lightGreen() {
		System.out.print("\033[0;32m");		
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

	public static void lightGrey() {
		System.out.print("\033[37;40m");		
	}

	public static void grey() {
		System.out.print("\033[1;30;40m");		
	}
	
	public static void red(String string) {
		red();
		println(string);
		resetColor();
	}
	
	public static void green(String string) {
		green();
		println(string);
		resetColor();
	}

	public static void grey(String string) {
		grey();
		println(string);
		resetColor();		
	}

	public static void timeln(String string) {
		if (!Options.RUN_IN_PARALLEL) {
			System.out.println("[" + dateFormat.format(new Date()) + "] "+ string);
		}
	}

	public static void yellow(String string) {
		yellow();
		println(string);
		resetColor();
	}

	public static void boldYellow() {
		System.out.print("\033[1;33m");		
	}

	public static void lightGreen(String str) {
		lightGreen();
		println(str);
		resetColor();
	}

	public static void lightGrey(String str) {
		lightGrey();
		println(str);
		resetColor();		
	}


}
