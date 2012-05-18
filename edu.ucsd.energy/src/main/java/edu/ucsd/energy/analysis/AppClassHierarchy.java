package edu.ucsd.energy.analysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
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

import edu.ucsd.energy.util.SystemUtil;

public class AppClassHierarchy {

  private ClassHierarchy cha;
  private String appJar;
  private String exclusionFileName;
	
  
  public AppClassHierarchy(String appJar, String exclusionFileName) throws IOException, WalaException {
	  this.appJar = appJar;
	  this.exclusionFileName = exclusionFileName;
	  File exclusionFile        = FileProvider.getFile(exclusionFileName);
	  AnalysisScope scope       = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar, exclusionFile);    
	  cha = ClassHierarchy.make(scope);
	  if (Opts.OUTPUT_CLASS_HIERARCHY) {
		  outputClassHierarchy();
	  }
	}

  
  private void outputClassHierarchy() throws WalaException {
	Graph<IClass> g = typeHierarchy2Graph();
    g = pruneForAppLoader(g);
    String dotFile = SystemUtil.getResultDirectory() + File.separatorChar + "ch.dot";    
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
  

  /**
   * Compute the class ancestors until Object
   * 
   * @param klass
   * @return
   */
  public ArrayList<IClass> getClassAncestors(IClass klass) {
    ArrayList<IClass> classList = new ArrayList<IClass>();
    IClass currentClass = klass;
    IClass superClass;
    while ((superClass = currentClass.getSuperclass()) != null) {
      classList.add(superClass);
      currentClass = superClass;
    }
    return classList;
  }

  
  
  public  void validateCommandLine(Properties p) {
    if (p.get("appJar") == null) {
      throw new UnsupportedOperationException("expected command-line to include -appJar");
    }
  }
  
  
  public  <T> Graph<T> pruneGraph(Graph<T> g, Filter<T> f) throws WalaException {
    Collection<T> slice = GraphSlicer.slice(g, f);
    return GraphSlicer.prune(g, new CollectionFilter<T>(slice));
  }
  
  public  Graph<IClass> pruneForAppLoader(Graph<IClass> g) throws WalaException {
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
	

}