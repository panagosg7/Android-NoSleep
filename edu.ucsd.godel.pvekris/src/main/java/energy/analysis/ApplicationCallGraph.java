/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package energy.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;

import com.ibm.wala.cfg.cdg.ControlDependenceGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.examples.drivers.PDFTypeHierarchy;
import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.ExplicitCallGraph;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.cfg.ExceptionPrunedCFG;
import com.ibm.wala.ipa.cfg.PrunedCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.CollectionFilter;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphUtil;
import com.ibm.wala.util.graph.InferGraphRoots;
import com.ibm.wala.util.graph.impl.InvertedGraph;
import com.ibm.wala.util.graph.traverse.BFSIterator;
import com.ibm.wala.util.graph.traverse.BoundedBFSIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.io.FileUtil;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.PDFViewUtil;

import energy.components.Component;
import energy.intraproc.IntraProcAnalysis;
import energy.util.E;
import energy.util.GraphBottomUp;
import energy.util.SSAProgramPoint;
import energy.viz.GraphDotUtil;

@SuppressWarnings("deprecation")
public class ApplicationCallGraph implements CallGraph {


  // Output Files
  private final static String PDF_FILE = "cg.pdf";
  private final static String DOT_FILE = "cg.dot";

  // Analysis parameters
  private static AnalysisScope scope = null;
  private static AnalysisOptions options = null;
  private static ClassHierarchy cha = null;
  private static HashSet<Entrypoint> entrypoints = null;
  private static AnalysisCache cache = null;
  private static CallGraph g = null;

  // WakeLock parameters
  private static Hashtable<String, IClass> targetClassHash;
  private static Hashtable<String, IMethod> targetMethodHash;
  private static Hashtable<String, CGNode> targetCGNodeHash;
  private static ArrayList<String> targetMethods = null;

  // /////////////////////////////////////////////////////////

  public Hashtable<String, IClass> getTargetClassHash() {
    return targetClassHash;
  }

  public Hashtable<String, IMethod> getTargetMethodHash() {
    return targetMethodHash;
  }

  public Hashtable<String, CGNode> getTargetCGNodeHash() {
    return targetCGNodeHash;
  }

  public static boolean isDirectory(String appJar) {
    return (new File(appJar).isDirectory());
  }

  public static String findJarFiles(String[] directories) throws WalaException {
    Collection<String> result = HashSetFactory.make();
    for (int i = 0; i < directories.length; i++) {
      for (Iterator<File> it = FileUtil.listFiles(directories[i], ".*\\.jar", true).iterator(); it.hasNext();) {
        File f = it.next();
        result.add(f.getAbsolutePath());
      }
    }
    return composeString(result);
  }

  private static String composeString(Collection<String> s) {
    StringBuffer result = new StringBuffer();
    Iterator<String> it = s.iterator();
    for (int i = 0; i < s.size() - 1; i++) {
      result.append(it.next());
      result.append(';');
    }
    if (it.hasNext()) {
      result.append(it.next());
    }
    return result.toString();
  }

  /**
   * Usage: args =
   * "-appJar [jar file name] {-exclusionFile [exclusionFileName]}" The
   * "jar file name" should be something like "c:/temp/testdata/java_cup.jar"
   * 
   * @param classHierarchy
   * @return
   * 
   * @throws CancelException
   * @throws IllegalArgumentException
   * @throws IOException
   * @throws WalaException
   */
  public ApplicationCallGraph(String[] args, ClassHierarchy classHierarchy)
      throws IllegalArgumentException, CancelException,
      WalaException, IOException {
    Properties p = CommandLine.parse(args);
    validateCommandLine(p);
    String appJar = p.getProperty("appJar");
    String exclusionFile = p.getProperty("exclusionFile", 
        CallGraphTestUtil.REGRESSION_EXCLUSIONS);

    cha = classHierarchy;

    // Get the graph on which we will work
    g = buildPrunedCallGraph(appJar, FileProvider.getFile(exclusionFile));

    if (Opts.OUTPUT_CG_DOT_FILE) {
      outputCallGraphToDot(g);
    }
  }

  public void outputCallGraphToDot(CallGraph g) {
    try {
      Properties p = null;
      try {
        p = WalaExamplesProperties.loadProperties();
        p.putAll(WalaProperties.loadProperties());
      } catch (WalaException e) {
        e.printStackTrace();
        Assertions.UNREACHABLE();
      }
      String pdfFile = null;
      if (Opts.OUTPUT_CALLGRAPH_PDF) {
        pdfFile = /*p.getProperty(WalaProperties.OUTPUT_DIR)*/ 
            energy.util.Util.getResultDirectory() + File.separatorChar + PDF_FILE;
      }
      String dotFile = energy.util.Util.getResultDirectory() + File.separatorChar + DOT_FILE;
      String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
      GraphDotUtil.dotify(g, null, dotFile, pdfFile, dotExe);
      // String gvExe = p.getProperty(WalaExamplesProperties.PDFVIEW_EXE);
      // return PDFViewUtil.launchPDFView(pdfFile, gvExe);
      return;
    } catch (WalaException e) {
      e.printStackTrace();
      return;
    }
  }

  /**
   * buildPrunedCallGraph : create a callgraph and eliminate nodes that do not
   * end in a Wakelock acquire or release operation
   * 
   * @param appJar
   *          something like "c:/temp/testdata/java_cup.jar"
   * @return a call graph
   * @throws CancelException
   * @throws IllegalArgumentException
   * @throws IOException
   */
  private CallGraph buildPrunedCallGraph(String appJar, File exclusionFile)
      throws WalaException, IllegalArgumentException, CancelException, IOException {

    // Get application scope
    scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(
        appJar, exclusionFile != null ? exclusionFile : new File(
        CallGraphTestUtil.REGRESSION_EXCLUSIONS));

    // Must use this - WALA assumes that entry point is just main
    entrypoints = new AllApplicationEntrypoints(scope, cha);    
    options     = new AnalysisOptions(scope, entrypoints);
    cache       = new AnalysisCache();
    targetCGNodeHash = new Hashtable<String, CGNode>();
    
    E.log(0,"#Nodes: " + entrypoints.size());
    
    /* 
     * Build the call graph
     */
    com.ibm.wala.ipa.callgraph.CallGraphBuilder builder = Util.makeZeroCFABuilder(options, cache, cha, scope);
    ExplicitCallGraph cg = (ExplicitCallGraph) builder.makeCallGraph(options, null);

    /*
     * Add wakelock methods and their callsites to the call graph 
     */
    insertTargetMethodsToCallgraph(cg);    
    
    if (Opts.KEEP_ONLY_WAKELOCK_SPECIFIC_NODES) {        
      /*
       * Create a prunded call graph that contains only those nodes that descend to lock operations
       */
      CallGraph pcg = pruneLockIrrelevantNodes(cg);
      return pcg;     
    }
    
    if (!Opts.KEEP_PRIMORDIAL) {      
      CallGraph pcg = prunePrimordialNodes(cg);
      return pcg;      
    }    
    return cg;    
  }

 
  private CallGraph prunePrimordialNodes(ExplicitCallGraph cg) {
    HashSet<CGNode> keepers = new HashSet<CGNode>();
    Iterator<CGNode> iterator = cg.iterator();
    
    while (iterator.hasNext()){
      CGNode node = iterator.next();
      if (isAppNode(node) 
    		  || isTargetMethod(node)
    		  //Toggling this will make it difficult to 
    		  //resolve most thread classes
    		  //|| isThreadStart(node)
    	) {
        keepers.add(node);
        E.log(2, "Keep: " + node.getMethod().toString());
      }
      else {
        E.log(2, "Prune: " + node.getMethod().toString());
      }
    }
    return PartialCallGraph.make(cg, keepers, keepers);
  }
  
  
  /**
   * We may wanto to keep the thread start method in the call graph
   * WARNING: this will make it hard to resolve threads ...
   * @param node
   * @return
   */
  private boolean isThreadStart(CGNode node) {
	 boolean found = node.getMethod().getSignature().toString().
			 equals("java.lang.Thread.start()V");	 
	 return found;
  }

/**
   * Returns a partial graph that contains only those nodes that descend to a
   * WakeLock acquire/release method node.
   * 
   * TODO: It might be more efficient to invert the call graph from the
   * beginning
   * 
   * @param cg
   * @return
   */
  private static CallGraph pruneLockIrrelevantNodes(CallGraph cg) {

    HashSet<CGNode> keepNodes = new HashSet<CGNode>();
    Queue<CGNode> q = new LinkedList<CGNode>();
    for (Entry<String, IMethod> entry : targetMethodHash.entrySet()) {
      q.add(cg.getNode(entry.getValue(), Everywhere.EVERYWHERE));
    }
    E.log(1,"Initial worklist size: " + q.size() +
        " (normal = 4)" );
      callGraphStats(cg);
    

    /*
     * Keep nodes that descend to acq/rel nodes and are application nodes
     */
    while (!q.isEmpty()) {
      CGNode n = q.remove();
      if (n != null && n.getMethod() != null && n.getMethod().getName() != null && !keepNodes.contains(n)) {
        keepNodes.add(n);
        Iterator<CGNode> pnIter = cg.getPredNodes(n);
        while (pnIter.hasNext()) {
          CGNode pn = pnIter.next();
          /*
           * All but the first nodes (acq/rel) will have to be app nodes
           */
          if (Opts.KEEP_PRIMORDIAL) {
            q.add(pn);
          } else if (isAppNode(pn)) {
              q.add(pn);            
          }
        }
      }
    }
    /*
     * Check if there are any new nodes added - apart from acquire/release
     */
    if (keepNodes.size() < 5) {
      E.err("No calls to WakeLock/WifiLock functions");
    }

    // printNodeCollectionFull(keepNodes, "Keep Nodes");
    E.log(1,"Number of nodes in analysis graph: " + keepNodes.size());

    PartialCallGraph pcg = null;


    /*
     * Now remove the fake root node - if primordial nodes have not been
     * excepted
     */
    removeFakeNodes(keepNodes);

    /*
     * Create the partial graph - all nodes in this graph descend to the target
     * nodes
     */
    pcg = PartialCallGraph.make(cg, keepNodes, keepNodes);

    /*
     * Keep nodes that are at most MAX_HOPS_FROM_TARGET from target nodes.
     */
    CallGraph boundedGraph = getBoundedGraph(pcg);

    /*
     * Leaf nodes should just be the lock operations
     */
    Collection<CGNode> leaves = GraphUtil.inferLeaves(boundedGraph);
    E.log(1,"Leaf nodes: " + leaves.size());
    for (CGNode leave : leaves) {
      E.log(1,"Leaf: " + leave.getMethod().getSignature().toString());
    }
    return boundedGraph;
  }


  private static void callGraphStats(CallGraph cg) {
    Iterator<CGNode> iterator = cg.iterator();
    int appNodes = 0;
    int primNodes = 0;
    while (iterator.hasNext()) {
      if (isAppNode(iterator.next())) {        
        appNodes++;
      }
      else {
        primNodes++;        
      } 
    }
    E.log(1,"");
    E.log(1,"AppNodes:\t" + appNodes + "/" + (appNodes+primNodes));
    E.log(1,"PrimNodes:\t" + primNodes + "/" + (appNodes+primNodes));
    E.log(1,"");
  }

  @SuppressWarnings("unused")
  private static HashSet<CGNode> getFakeRootSuccessors(CallGraph cg) {
    HashSet<CGNode> roots = new HashSet<CGNode>();
    Iterator<CGNode> rootIter = cg.getSuccNodes(cg.getFakeRootNode());
    while (rootIter.hasNext()) {
      roots.add(rootIter.next());
    }
    return roots;
  }

  
  /**
   * Bound the callgraph so that it contains only nodes with distance no greater
   * than MAX_HOPS_FROM_TARGET from the target (lock) nodes.
   * 
   * @param pcg
   * @return
   */
  private static CallGraph getBoundedGraph(CallGraph pcg) {
    HashSet<CGNode> nearSet = new HashSet<CGNode>();

    if (Opts.MAX_HOPS_FROM_TARGET < 0) {
      return pcg;
    }

    /*
     * Iterate over the target nodes
     */
    for (Entry<String, IMethod> entry : targetMethodHash.entrySet()) {
      CGNode root = pcg.getNode(entry.getValue(), Everywhere.EVERYWHERE);

      /*
       * Get the inverted graph first, before calling bounded BFS
       */
      Graph<CGNode> invPCG = new InvertedGraph<CGNode>(pcg);
      BoundedBFSIterator<CGNode> bBFSIter = new BoundedBFSIterator<CGNode>(invPCG, root,
          Opts.MAX_HOPS_FROM_TARGET);

      while (bBFSIter.hasNext()) {
        nearSet.add(bBFSIter.next());
      }
    }

    PartialCallGraph partial = PartialCallGraph.make(pcg, GraphUtil.inferRoots(pcg), nearSet);
    return partial;
  }

  /*
   * Function that prints information about the root nodes of the call graph
   */
  @SuppressWarnings("unused")
  private static void getRootInformation(Graph<CGNode> graph, HashSet<CGNode> keepNodes) {
    Collection<CGNode> roots = InferGraphRoots.inferRoots(graph);
    for (CGNode root : roots) {
      printRootInfo(root);
    }
  }

  private static void printRootInfo(CGNode root) {
    E.log(0,"Examining root:");
    E.log(0,root.getMethod().getDeclaringClass().toString());
    E.log(0,root.getMethod().getSignature().toString());
    E.log(0,"");
  }

  /**
   * This function removes from @param finalKeepNodes, nodes that contain
   * specific strings in their signature (eg. fakeRootNode).
   * 
   * @param finalKeepNodes
   */
  private static void removeFakeNodes(HashSet<CGNode> nodeset) {
    Iterator<CGNode> iterator = nodeset.iterator();
    HashSet<CGNode> toRemove = new HashSet<CGNode>();
    while (iterator.hasNext()) {
      CGNode node = iterator.next();

      String name = node.getMethod().getSignature().toString();
      if (name.contains("fakeWorldClinit") || name.contains("fakeRootMethod") || name.contains("Thread.start")) {
        toRemove.add(node);
      }
    }
    for (CGNode node : toRemove) {
      nodeset.remove(node);
    }
  }

  @SuppressWarnings("unused")
  private static void printNodeCollectionFull(Collection<CGNode> nodes, String title) {
    int i = 0;
    E.log(0,title);
    for (CGNode kn : nodes) {
      E.log(0,i++ + " : " + kn.getMethod().toString());
    }
    E.log(0,"");
  }

  @SuppressWarnings("unused")
  private static void printNodeCollectionNames(Collection<CGNode> nodes, String title) {
    int i = 0;
    E.log(0,title);
    for (CGNode kn : nodes) {
      E.log(0,i++ + " : " + kn.getMethod().getName().toString());
    }
    E.log(0,"");
  }

  @SuppressWarnings("unused")
  private static Collection<CGNode> getApplicationNodes(Collection<CGNode> nodeSet) {
    Collection<CGNode> appNodes = new HashSet<CGNode>();
    for (CGNode n : nodeSet) {
      if (isAppNode(n)) {
        appNodes.add(n);
      }
    }
    return appNodes;
  }

  private static boolean isAppNode(CGNode n) {
    if (n.getMethod().toString().contains("< Application")) {
      return true;
    }
    return false;
  }

  /**
   * Inserts WakeLock methods to the callgraph
   * 
   * @param cg
   * @throws IOException 
   * @throws CancelException 
   */
  private static void insertTargetMethodsToCallgraph(CallGraph cg) throws IOException, CancelException {
    
    File targetsFile = FileProvider.getFile(Opts.TARGET_FUNCTIONS, FileProvider.class.getClassLoader());
    BufferedReader targetBuffer = new BufferedReader(new FileReader(targetsFile));
    String str;
    targetMethods = new ArrayList<String>();
    targetClassHash = new Hashtable<String, IClass>();
    targetMethodHash = new Hashtable<String, IMethod>();
    while ((str = targetBuffer.readLine()) != null) {
      targetMethods.add(str);
    }
    for (IClass iclass : cha) {
      TypeName clname = iclass.getName();
      for (String targetMethodName : targetMethods) {
        String targetClassName = "L" + targetMethodName.substring(0, targetMethodName.lastIndexOf('.'));
        targetClassName = targetClassName.replace('.', '/');
        if (clname.toString().equals(targetClassName)) {
          // Iterate over the methods
          for (IMethod imethod : iclass.getAllMethods()) {
            String methSig = imethod.getSignature();
            if (methSig.equals(targetMethodName)) {
              targetClassHash.put(targetMethodName, iclass);
              targetMethodHash.put(targetMethodName, imethod);              
              E.log(2,"Found target method: " + targetMethodName.toString());              
            }
          }
        }
      }
    }
    // TODO : insert assertions (?)
    // ===========================================================================
    
    for (Entry<String, IMethod> entry : targetMethodHash.entrySet()) {
      CGNode targetNode = ((ExplicitCallGraph) cg).findOrCreateNode(
          entry.getValue(), (Context) Everywhere.EVERYWHERE);
      targetCGNodeHash.put(entry.getKey(), targetNode);
    }
    
    E.log(0,"Created target methods");
    
    // ===========================================================================
    
    Iterator<CGNode> iter = cg.iterator();
    while (iter.hasNext()) {
      CGNode currNode = iter.next();
      // Set up options which govern analysis choices. In particular, we
      // will use all Pi nodes when building the IR.
      options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
      // Create an object which caches IRs and related information,
      // reconstructing them lazily on demand.
      AnalysisCache cache = new AnalysisCache();
      // Build the IR and cache it.
      IR ir = cache.getSSACache().findOrCreateIR(currNode.getMethod(), 
          Everywhere.EVERYWHERE, options.getSSAOptions());
      if (ir != null) {
        SSAInstruction[] insts = ir.getInstructions();
        for (SSAInstruction inst : insts) {
          if (inst instanceof SSAInvokeInstruction) {
            SSAInvokeInstruction invInstr = (SSAInvokeInstruction) inst;
            String methSig = invInstr.getDeclaredTarget().getSignature().toString();
            for (Entry<String, CGNode> entry : targetCGNodeHash.entrySet()) {
              // We got a target call instruction
              if (methSig.equals(entry.getKey())) {
                // THIS IS THE WAY TO ADD A NODE AND AN EDGE TO THE GRAPH !!!
                CallSiteReference site = CallSiteReference.make(1, 
                    entry.getValue().getMethod().getReference(),
                    IInvokeInstruction.Dispatch.STATIC);
                site = invInstr.getCallSite();
                // WARNING: This is deprecated and intended to be used only by builder
                // Might have to change
                currNode.addTarget(site, entry.getValue());
                E.log(2,"Adding \"" + 
                    site.getDeclaredTarget().getSignature().toString() + "\" edge.");
                
              }
            }
          }
        }
      }
    }
  }

  public static Graph<CGNode> pruneForAppLoader(CallGraph g) throws WalaException {
    return PDFTypeHierarchy.pruneGraph(g, new ApplicationLoaderFilter());
  }

  /**
   * Validate that the command-line arguments obey the expected usage.
   * 
   * Usage:
   * <ul>
   * <li>args[0] : "-appJar"
   * <li>args[1] : something like "c:/temp/testdata/java_cup.jar"
   * </ul
   * ?
   * 
   * @throws UnsupportedOperationException
   *           if command-line is malformed.
   */
  public static void validateCommandLine(Properties p) {
    if (p.get("appJar") == null) {
      throw new UnsupportedOperationException("expected command-line to include -appJar");
    }
  }

  /**
   * A filter that accepts WALA objects that "belong" to the application loader.
   * 
   * Currently supported WALA types include
   * <ul>
   * <li> {@link CGNode}
   * <li> {@link LocalPointerKey}
   * </ul>
   */
  private static class ApplicationLoaderFilter implements Filter<CGNode> {
    public boolean accepts(CGNode o) {
      if (o instanceof CGNode) {
        CGNode n = (CGNode) o;
        return n.getMethod().getDeclaringClass().getClassLoader().getReference().equals(ClassLoaderReference.Application);
      } else if (o instanceof LocalPointerKey) {
        LocalPointerKey l = (LocalPointerKey) o;
        return accepts(l.getNode());
      } else {
        return false;
      }
    }
  }
  
  
  
  /**
   * 
   * Run an analysis per node
   * 
   * Traverse the call graph bottom up and apply intraProcAnalysis to every node
   * of it
   * @param analysis
   * @param cg
   * @throws WalaException
   */
  public void doBottomUpAnalysis(IntraProcAnalysis ipa) throws WalaException {    
    int methodCount = Opts.LIMIT_ANALYSIS;
    /* Filter that filters non-target methods */
    CollectionFilter<CGNode> targetFilter = 
    		new CollectionFilter<CGNode>(targetCGNodeHash.values());
    BFSIterator<CGNode> bottomUpIterator = GraphBottomUp.bottomUpIterator(this, targetFilter);
    while (bottomUpIterator.hasNext() && (methodCount < 0 || methodCount-- > 0)) {
      /* Run the intra-procedural analysis */
      CGNode n = bottomUpIterator.next();
      ipa.run(this, n);
    }
  }
   

  
  public void outputDotFiles() throws WalaException {
    Properties p = null;
    try {
      p = WalaExamplesProperties.loadProperties();
      p.putAll(WalaProperties.loadProperties());
    } catch (WalaException e) {
      e.printStackTrace();
      Assertions.UNREACHABLE();
    }
    
    Iterator<CGNode> iter = this.iterator();
    while (iter.hasNext()) {      
      CGNode n = iter.next();
      IR ir = n.getIR();
      String bareFileName = ir.
          getMethod().
          getDeclaringClass().
          getName().
          toString().replace('/', '.') + "_"
          + ir.getMethod().getName().toString();
      
      PrunedCFG<SSAInstruction, ISSABasicBlock> epCFG = null;
            
          
      if (Opts.OUTPUT_SIMPLE_CFG_DOT || 
          Opts.OUTPUT_SIMPLE_CDG_DOT) {
            epCFG = ExceptionPrunedCFG.make(ir.getControlFlowGraph());
      }
      
      if (Opts.OUTPUT_SIMPLE_CFG_DOT) {
        
        String cfgs = energy.util.Util.getResultDirectory() 
            + File.separatorChar + "cfg";
        new File(cfgs).mkdirs();
        // Output the CFG
        String cfgFileName = cfgs + File.separatorChar + bareFileName + ".dot";
                
        if (epCFG != null) {
          DotUtil.writeDotFile(epCFG, 
            PDFViewUtil.makeIRDecorator(ir), 
            ir.getMethod().getSignature(), 
            cfgFileName);
        }
        else {
          System.out.println("Exception pruned graph is null.");
        }
      }
  
      if (Opts.OUTPUT_SIMPLE_CDG_DOT) {        
        String cdgs = energy.util.Util.getResultDirectory() 
            + File.separatorChar + "cdgs/";
        new File(cdgs).mkdirs();
        // Output the CDG
        String cdgFileName = cdgs + File.separatorChar + bareFileName + ".dot";        
        ControlDependenceGraph<SSAInstruction, ISSABasicBlock> cdg = 
            new ControlDependenceGraph<SSAInstruction, ISSABasicBlock>(epCFG, true);
        DotUtil.writeDotFile(cdg, 
            PDFViewUtil.makeIRDecorator(ir), 
            ir.getMethod().getSignature(), 
            cdgFileName);
      }
    }
  }
  
  

  /**
   * 
   * Overridden CallGraph methods, substituted by dummy bodies
   * 
   */
  @Override
  public void removeNodeAndEdges(CGNode n) throws UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<CGNode> iterator() {
    return g.iterator();
  }

  @Override
  public int getNumberOfNodes() {
    return g.getNumberOfNodes();
  }

  @Override
  public void addNode(CGNode n) {
    throw new UnsupportedOperationException();

  }

  @Override
  public void removeNode(CGNode n) throws UnsupportedOperationException {
    throw new UnsupportedOperationException();

  }

  @Override
  public boolean containsNode(CGNode n) {
    return g.containsNode(n);
  }

  @Override
  public Iterator<CGNode> getPredNodes(CGNode n) {
    return g.getPredNodes(n);
  }

  @Override
  public int getPredNodeCount(CGNode n) {
    return g.getPredNodeCount(n);
  }

  @Override
  public Iterator<CGNode> getSuccNodes(CGNode n) {
    return g.getSuccNodes(n);
  }

  @Override
  public int getSuccNodeCount(CGNode N) {
    return g.getSuccNodeCount(N);
  }

  @Override
  public void addEdge(CGNode src, CGNode dst) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeEdge(CGNode src, CGNode dst) throws UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeAllIncidentEdges(CGNode node) throws UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeIncomingEdges(CGNode node) throws UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeOutgoingEdges(CGNode node) throws UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasEdge(CGNode src, CGNode dst) {
    return g.hasEdge(src, dst);
  }

  @Override
  public int getNumber(CGNode N) {
    return g.getNumber(N);
  }

  @Override
  public CGNode getNode(int number) {
    return g.getNode(number);
  }

  @Override
  public int getMaxNumber() {
    return g.getMaxNumber();
  }

  @Override
  public Iterator<CGNode> iterateNodes(IntSet s) {
    return g.iterateNodes(s);
  }

  @Override
  public IntSet getSuccNodeNumbers(CGNode node) {
    return g.getSuccNodeNumbers(node);
  }

  @Override
  public IntSet getPredNodeNumbers(CGNode node) {
    return g.getPredNodeNumbers(node);
  }

  @Override
  public CGNode getFakeRootNode() {
    return null;
}

  @Override
  public Collection<CGNode> getEntrypointNodes() {
    return g.getEntrypointNodes();
  }

  @Override
  public CGNode getNode(IMethod method, Context C) {
    return g.getNode(method, C);
  }

  @Override
  public Set<CGNode> getNodes(MethodReference m) {
    return g.getNodes(m);
  }

  @Override
  public IClassHierarchy getClassHierarchy() {
    return g.getClassHierarchy();
  }

  @Override
  public Set<CGNode> getPossibleTargets(CGNode node, CallSiteReference site) {
    return g.getPossibleTargets(node, site);
  }

  @Override
  public int getNumberOfTargets(CGNode node, CallSiteReference site) {
    return g.getNumberOfTargets(node, site);
  }

  @Override
  public Iterator<CallSiteReference> getPossibleSites(CGNode src, CGNode target) {
    return g.getPossibleSites(src, target);
  }

  public boolean isTargetMethod(CGNode root) {
    return targetCGNodeHash.contains(root);
  }
}