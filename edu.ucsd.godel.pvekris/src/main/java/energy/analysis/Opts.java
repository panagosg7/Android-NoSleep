package energy.analysis;

import java.util.Arrays;
import java.util.HashSet;

public class Opts {

  /*******************************************
   * Class Hierarchy Options
   */
  
  
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
  
  public static final boolean DO_CS_ANALYSIS      = true;

  /**
   * Output CFGs for each component method 
   */
  public static final boolean OUTPUT_CFG_DOT = true;
  
  /**
   * Output colored CFGs for each function of the interesting components
   */  
  public static final boolean OUTPUT_COLOR_CFG_DOT        = true;
  
  /*
   * This will generally be too big to display 
   */
  public static final boolean OUTPUT_SOLVED_EICFG         = false;
  public static final boolean CHECK_LOCKING_POLICY        = true;
  
  
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
   * Toggle intra-procedural analysis
   */
  public static final boolean DO_INTRA_PROC_ANALYSIS  = false;
  
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
   * Prune exceptional edges in CFG
   */
  public static final boolean PRUNE_EXCEPTION_EDGES_IN_GFG = false;



  public static final boolean OUTPUT_PLAIN_CFGS = false;



  public static final boolean OUTPUT_CLASSHIERARCHY = true;



public static final boolean ENFORCE_SPECIAL_CONDITIONS = true;



public static final boolean DATAFLOW_IGNORE_EXCEPTIONAL = true;



public static final boolean PRINT_HIGH_STATE = false;



public static final boolean OUTPUT_ALL_NODE_INFO = false;



public static boolean RUN_IN_PARALLEL = false;






  
  /*******************************************
   * Other Options
   */
  
  /**
   * Define the leaves of the call graph (e.g. WakeLock.acquire)
   */
  public static String TARGET_FUNCTIONS = "/home/pvekris/dev/workspace/WALA_shared/" +
  		"com.ibm.wala.core.tests/bin/AndroidAnalysisTargetFunctions.txt";
  

  /**
   * Need to change this depending on where it is run
   * Folders of each application will be created here, overwriting old ones.
   */
  public static String OUTPUT_FOLDER = "/home/pvekris/dev/WALA/results"; 
    
  /**
   * Log analysis output in file log.out
   */
  public static boolean LOG_RESULTS = true;

  
  
}
