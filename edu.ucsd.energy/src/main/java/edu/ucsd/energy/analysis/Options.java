package edu.ucsd.energy.analysis;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;

import edu.ucsd.energy.util.SystemUtil;

public class Options {

  /*******************************************
   * Class Hierarchy Options
   */

  public static final boolean OUTPUT_CLASS_HIERARCHY = true;
  
  
  /*******************************************
   * Call Graph Options
   */
  
  /**
   * Prune the callgraph and keep only nodes descending to wakelock operations 
   */
  public static final boolean KEEP_ONLY_WAKELOCK_SPECIFIC_NODES = false;
  
  
  
  /**
   * Keep primordial nodes in the callgraph
   * Having this true introduces ~3500 nodes in the callgraph and cancels some
   * of the later analyses.
   */
  public static final boolean KEEP_PRIMORDIAL       = false;  
  
  /**
   * Output the callgraph for the whole app
   */
  public static final boolean OUTPUT_CG_DOT_FILE    = true;  
  public static final boolean OUTPUT_CALLGRAPH_PDF  = false; 
  

  /**
   * Customize the Call Graph dot output:
   * 
   * If the nodes in the graph are more than NODE_THRESHOLD, 
   * output just numbers - and dump their correspondence in the output 
   * (may introduce big outputs...)
   * 
   * FORCE_OUTPUT_GRAPH forces the output even if the graph is very 
   * large
   */
  public static final int       NODE_THRESHOLD      = 1000;
  public static final boolean   FORCE_OUTPUT_GRAPH  = true;  
  public static final boolean   FORCE_LABELS  = true;
  
  
  

  /**
   * Output dot files for the control-flow and control-dependence graphs
   */
  public static final boolean OUTPUT_SIMPLE_CFG_DOT  = false;
  public static final boolean OUTPUT_SIMPLE_CDG_DOT  = false;
  
  
  
  /**
   * The created graph will contain nodes only k hops from the target
   * -1 means keep everything
   */
  public static int             MAX_HOPS_FROM_TARGET  = -1;
  
  
  /*******************************************
   * Android Components
   */
  
  /**
   * Resolve component callgraphs
   * 
   * Will ONLY work with KEEP_PRIMORDIAL = false
   * (cause it is based on android callbacks that are the roots in the 
   * prunned call graph)
   * 
   * Define any new components in com.energy.components
   */
  public static final boolean   PROCESS_ANDROID_COMPONENTS  = true;
  
  /**
   * This will help reduce the number of analyzed components
   */
  public static final boolean   ONLY_ANALYSE_LOCK_REACHING_CALLBACKS  = false;
  
  /*
   * The following are invalid if this one is false.  
   */
  
  /**
   * Do you want to apply the component analysis for 
   * every/none/only_the_interesting components 
   * (atm: interesting = ones that have wake/wifi locks)
   */
  public enum ApplyTo {ALL, NONE, ONLY_INTERESTING };
  
  public static final boolean OUTPUT_COMPONENT_CALLGRAPH  = true;
  
  /**
   * Output colored CFGs for each function of the interesting components
   */  
  public static final boolean OUTPUT_COLOR_CFG_DOT        = true;
  
  /*
   * This will generally be too big to display 
   */
  public static final boolean OUTPUT_COLORED_SUPERGRAPHS = true;
  
  
  /*******************************************
   * NOT VERY USEFUL AT THE MOMENT
   * SCC Options
   */  
  public static final boolean RESOLVE_SCC           = false;
  public static final boolean DO_SCC_ANALYSIS       = false;
  public static final boolean OUTPUT_SCC_DOT_FILE   = false;
  
  
  /*******************************************
   * Intra-procedural Analysis Options
   */
  
  /**
   * Limit the analysis to the first LIMIT_ANALYSIS methods in the callgraph
   * -1 means this doesn't apply
   */
  public static int LIMIT_ANALYSIS = -1;
  
  /**
   * Add here explicitly methods that will be removed from the 
   * condition sets
   */
  public static final HashSet<String> filterOutMethods = new HashSet<String>(
      Arrays.asList(new String[] {         
          //"android.os.PowerManager$WakeLock.isHeld()Z" 
          }));
  
  /**
   * Prune exceptional edges in CFG - not really important
   */
  public static final boolean PRUNE_EXCEPTION_EDGES_IN_GFG = false;

  
  /**
   * Special treatment for isHeld() and null-checks on interesting objects
   */
  public static final boolean ENFORCE_SPECIAL_CONDITIONS = true;
	
  
  /**
   * Do not propagate flow from exceptional edges
   * -- Causes soundness issues when true !!!  
   */
  public static final boolean DATAFLOW_IGNORE_EXCEPTIONAL = true;
	

  /**
   * Do you want the analysis to be run on the supercomponents 
   * (true) or just the components (false)? 
   */
	public static boolean ANALYZE_SUPERCOMPONENTS = true;


	/**
	 * We should not be using timed acquire as a seed...
	 */
	public static final boolean USE_TIMED_ACQUIRE_AS_SEED = false;
	
	/**
	 * This will be set automatically if multiple threads are used for 
	 * the analysis  
	 */
  public static boolean RUN_IN_PARALLEL = false;



  /*******************************************
   * Other Options
   */
  
  /**
   * Define the leaves of the call graph (e.g. WakeLock.acquire)
   */
  public static File TARGET_FUNCTIONS = new File(SystemUtil.walaROOT, "com.ibm.wala.core.tests/bin/AndroidAnalysisTargetFunctions.txt");
  

  /**
   * Need to change this depending on where it is run
   * Folders of each application will be created here, overwriting old ones.
   */
  public static File OUTPUT_FOLDER = new File(SystemUtil.walaROOT, "results"); 
    
  /**
   * Log analysis output in file log.out
   */
  public static boolean LOG_RESULTS = true;

  
  
}
