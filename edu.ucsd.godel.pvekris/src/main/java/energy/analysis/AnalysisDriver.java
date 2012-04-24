package energy.analysis;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

import energy.intraproc.IntraProcAnalysis;

public class AnalysisDriver {

  public static int retCode = 0; // Default exit code

  /**
   * Usage: args =
   * "-appJar [jar file name] {-exclusionFile [exclusionFileName]}" The
   * "jar file name" should be something like * /home/<name>/java_cup.jar"
   * 
   * @throws CancelException
   * @throws IllegalArgument
   */
  public static void main(String[] args) throws WalaException, IllegalArgumentException, CancelException {
    try {
//XXX: This will obviously fail...
      String appJar = null;
      String exclusionFile = null;
      AppClassHierarchy	ch = new AppClassHierarchy(appJar, exclusionFile);		
	  AppCallGraph 		cg = new AppCallGraph(ch);

	  if (Opts.PROCESS_ANDROID_COMPONENTS) {
        ComponentManager componentManager = new ComponentManager(cg);
        componentManager.prepareReachability();
        componentManager.resolveComponents();
        componentManager.processComponents();
      }

      /*
       * SCC analysis
       */
      if (Opts.RESOLVE_SCC) {
        SCCManager sccManager = new SCCManager(cg);
        if (Opts.DO_SCC_ANALYSIS) {
          sccManager.analyze();
        }
      }

      /*
       * Output the CFG's for each method
       */
      if (Opts.OUTPUT_PLAIN_CFGS) {
        cg.outputDotFiles();
      }

      /*
       * Apply the analysis on each node of the graph in a bottom up manner
       */
      if (Opts.DO_INTRA_PROC_ANALYSIS) {
        IntraProcAnalysis ipa = new IntraProcAnalysis();
        cg.doBottomUpAnalysis(ipa);
      }

      System.exit(retCode);

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

}
