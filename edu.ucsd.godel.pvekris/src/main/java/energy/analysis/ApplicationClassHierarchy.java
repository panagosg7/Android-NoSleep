package energy.analysis;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Properties;

import com.ibm.wala.classLoader.IClass;
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
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.viz.DotUtil;

import energy.util.Util;

public class ApplicationClassHierarchy {

  private static ClassHierarchy cha;
  private String appJar;
  private String exclusionFileName;
	
  private LockInvestigation lockFieldInfo = null;
  
	
  public ApplicationClassHierarchy(String appJar, String exclusionFileName) throws IOException, ClassHierarchyException {
	  this.appJar = appJar;
	  this.exclusionFileName = exclusionFileName;
	  File exclusionFile        = FileProvider.getFile(exclusionFileName);
	  AnalysisScope scope       = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar, exclusionFile);    
	  cha = ClassHierarchy.make(scope);
	}

  
  private void outputCHtoDot() throws WalaException {
	Graph<IClass> g = typeHierarchy2Graph();
    g = pruneForAppLoader(g);
    String dotFile = Util.getResultDirectory() + File.separatorChar + "ch.dot";    
    DotUtil.writeDotFile(g, null, "Class Hierarchy", dotFile);	    
  }


/**
   * Return a view of an {@link IClassHierarchy} as a {@link Graph}, with edges from classes to immediate subtypes
   */
  public Graph<IClass> typeHierarchy2Graph() throws WalaException {
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


public String getAppJar() {
	return appJar;
}

public String getExclusionFileName() {
	return exclusionFileName;
}

public ClassHierarchy getClassHierarchy() {
	return cha;
}


public LockInvestigation getLockFieldInfo() {
	if (lockFieldInfo == null) {
		lockFieldInfo = new LockInvestigation(cha);
		lockFieldInfo.traceLockFields();
	}
	
	return lockFieldInfo;
}
  
}