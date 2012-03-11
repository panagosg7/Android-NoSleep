package energy.analysis;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Properties;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.CollectionFilter;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.viz.DotUtil;

import energy.util.Util;

public class ClassHierarchyAnalysis {

  private static ClassHierarchy cha;   
  

  /**
   * Create an application specific class hierarchy, and output it
   * @param args
   * @throws IOException
   * @throws WalaException
   */
  public ClassHierarchyAnalysis(String[] args) throws IOException, WalaException {
    Properties p              = CommandLine.parse(args);    
    validateCommandLine(p);
    String appJar             = p.getProperty("appJar");
    String exclusionFileName  = p.getProperty("exclusionFile", CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    File exclusionFile        = FileProvider.getFile(exclusionFileName);
    AnalysisScope scope       = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar, exclusionFile);    
    setClassHierarchy(ClassHierarchy.make(scope));    
    Graph<IClass> g     = typeHierarchy2Graph(getClassHierarchy());
    
    /*
     * Need to have this or the graph will be enormous
     */
    g = pruneForAppLoader(g);
    
    String dotFile = Util.getResultDirectory() + File.separatorChar + "ch.dot";    
    DotUtil.writeDotFile(g, null, "Class Hierarchy", dotFile);    
          
  }

  
  /**
   * Return a view of an {@link IClassHierarchy} as a {@link Graph}, with edges from classes to immediate subtypes
   */
  public static Graph<IClass> typeHierarchy2Graph(IClassHierarchy cha) throws WalaException {
    Graph<IClass> result = SlowSparseNumberedGraph.make();
    for (IClass c : cha) {
      result.addNode(c);
    }
    for (IClass c : cha) {
      for (IClass x : cha.getImmediateSubclasses(c)) {
        result.addEdge(c, x);
      }
      if (c.isInterface()) {
        for (IClass x : cha.getImplementors(c.getReference())) {
          result.addEdge(c, x);
        }
      }
    }
    return result;
  }
  
  
  public static void validateCommandLine(Properties p) {
    if (p.get("appJar") == null) {
      throw new UnsupportedOperationException("expected command-line to include -appJar");
    }
  }
  
  
  public static <T> Graph<T> pruneGraph(Graph<T> g, Filter<T> f) throws WalaException {
    Collection<T> slice = GraphSlicer.slice(g, f);
    return GraphSlicer.prune(g, new CollectionFilter<T>(slice));
  }
  
  public static Graph<IClass> pruneForAppLoader(Graph<IClass> g) throws WalaException {
    Filter<IClass> f = new Filter<IClass>() {
      public boolean accepts(IClass c) {
        return (c.getClassLoader().getReference().equals(ClassLoaderReference.Application));
      }
    };
    return pruneGraph(g, f);
  }


  public ClassHierarchy getClassHierarchy() {
    return cha;
  }


  public void setClassHierarchy(ClassHierarchy cha) {
    ClassHierarchyAnalysis.cha = cha;
  }
  
}