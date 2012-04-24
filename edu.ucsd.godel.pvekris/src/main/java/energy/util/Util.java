package energy.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import com.ibm.wala.util.intset.IntIterator;

import energy.analysis.Opts;

public class Util {

  
  private static String resultDirectory; 
  
  public static <T> Set<T> iteratorToSet(Iterator<T> itr) {
    HashSet<T> hashSet = new HashSet<T>();
    while (itr.hasNext()) {
      hashSet.add(itr.next());
    }
    return hashSet;
  }
  
  public Collection<Integer> intIteratorToSet(IntIterator itr) {
    HashSet<Integer> hashSet = new HashSet<Integer>();
    while (itr.hasNext()) {
      hashSet.add(itr.next());
    }
    return hashSet;
  }
  
  
  public static <T> ArrayList<T> iteratorToArrayList(Iterator<T> itr) {
    ArrayList<T> arrayList = new ArrayList<T>(0);
    while (itr.hasNext()) {
      arrayList.add(itr.next());
    }
    return arrayList;
  }


  public <T> Collection<T> flattenCollection(Collection<Collection<T>> s) {
	  HashSet<T> result = new HashSet<T>();
	  for (Collection<T> a : s) {
		  for(T b : a) {
			  result.add(b);
		  }
	  }
	  return result;	  
  }
  
  
  public static String getResultDirectory() {
    return resultDirectory;
  }


  public static void setResultDirectory(String appJar) {
    File file = new File(appJar);
    if (!Pattern.matches(".+\\.jar", appJar)) {
      throw new IllegalArgumentException("Input file must be a jar file.");
    };    
    String name = file.toString();    
    resultDirectory = Opts.OUTPUT_FOLDER + File.separatorChar + name.split(File.separatorChar+"")[5];
    //XXX: This is very specific - won't work forever
    
    File newDir = new File(resultDirectory);
    if (!removeDirectory(newDir)) {
      System.err.println("Wrong result directory.");
    };
    newDir.mkdir();    
  
  }
  
  public static boolean removeDirectory(File directory) {
    // System.out.println("removeDirectory " + directory);
    if (directory == null) return false;
    if (!directory.exists()) return true;
    if (!directory.isDirectory()) return false;
    String[] list = directory.list();
    // Some JVMs return null for File.list() when the
    // directory is empty.
    if (list != null) {
      for (int i = 0; i < list.length; i++) {
        File entry = new File(directory, list[i]);
        //System.out.println("\tremoving entry " + entry);
        if (entry.isDirectory()) {
          if (!removeDirectory(entry))
            return false;
        }
        else {
          if (!entry.delete())
            return false;
        }
      }
    }
    return directory.delete();
  }

  public static void printLabel(String arg) {
	System.out.println();
	System.out.println();
	E.plog(1, "==========================================\n");       
    
    System.out.println("\t" + arg.split(File.separatorChar+"")[5]);
    System.out.println("\t" + arg.split(File.separatorChar+"")[6]);
    System.out.println("\n==========================================\n");
    
  }
  
}

