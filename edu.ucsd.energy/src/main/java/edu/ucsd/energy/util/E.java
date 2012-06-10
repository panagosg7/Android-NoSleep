package edu.ucsd.energy.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

import edu.ucsd.energy.analysis.Opts;

public class E {
  private static final int MAX_METHOD_NAME = 40;  
  static int DEBUG_LEVEL = 1;   /* higher means more details */

  private static String LOG_FILE = "log.out";

  public static void plog(int i, String f, String out) {
    if (i<=DEBUG_LEVEL) {
      if(!Opts.RUN_IN_PARALLEL) {
    	  System.out.println(out);
      }
      
      if (Opts.LOG_RESULTS) {
        try {        	        
	        File file = new File(SystemUtil.getResultDirectory() + 
	            File.separatorChar + f);
	        if(!file.exists()){	          
	          file.createNewFile();
	        }          
	        FileWriter fstream = new FileWriter(file.getAbsoluteFile(),true);
	        BufferedWriter logFile = new BufferedWriter(fstream);
	        logFile.write(out + "\n");
	        logFile.close();
	          
        } catch (Exception e) {
          System.err.println("Error writing to file " + f);
          e.printStackTrace();
        }
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
}