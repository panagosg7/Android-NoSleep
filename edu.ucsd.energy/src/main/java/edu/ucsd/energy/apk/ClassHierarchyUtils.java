package edu.ucsd.energy.apk;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

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

import edu.ucsd.energy.Main;
import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.util.SystemUtil;

public class ClassHierarchyUtils {

	private static final String exclusionFileName = "/home/pvekris/dev/workspace/WALA_shared/" +
			"com.ibm.wala.core.tests/bin/Java60RegressionExclusions.txt";

	private static File exclusionFile;

	static {
		try {
			exclusionFile = FileProvider.getFile(exclusionFileName);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static ClassHierarchy make(String appJar) throws IOException, WalaException {		
		AnalysisScope scope;
		synchronized(Main.logLock) {
			scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar, exclusionFile);	
		}
		ClassHierarchy cha = ClassHierarchy.make(scope);
		if (Opts.OUTPUT_CLASS_HIERARCHY) {
			outputClassHierarchy(cha);
		}
		return cha;
	}

	
	private static void outputClassHierarchy(ClassHierarchy ch) throws WalaException {
		Graph<IClass> g = typeHierarchy2Graph(ch);
		g = pruneForAppLoader(g);
		String dotFile = SystemUtil.getResultDirectory() + File.separatorChar + "ch.dot";    
		DotUtil.writeDotFile(g, null, "Class Hierarchy", dotFile);	    
	}


	/**
	 * Return a view of an {@link IClassHierarchy} as a {@link Graph}, with edges from classes to immediate subtypes
	 */
	public static Graph<IClass> typeHierarchy2Graph(ClassHierarchy ch) throws WalaException {
		Graph<IClass> result = SlowSparseNumberedGraph.make();
		for (IClass c : ch) {
			result.addNode(c);
		}
		for (IClass c : ch) {          
			for (IClass x : ch.getImmediateSubclasses(c)) {
				result.addEdge(c, x);
			}
			if (c.isInterface()) {
				for (IClass x : ch.getImplementors(c.getReference())) {
					result.addEdge(c, x);
				}
			}
		}
		return result;
	}

	public static ArrayList<IClass> getClassAncestors(IClass klass) {
		ArrayList<IClass> classList = new ArrayList<IClass>();
		IClass currentClass = klass;
		IClass superClass;
		while ((superClass = currentClass.getSuperclass()) != null) {
			classList.add(superClass);
			currentClass = superClass;
		}
		return classList;
	}


	private static  <T> Graph<T> pruneGraph(Graph<T> g, Filter<T> f) throws WalaException {
		Collection<T> slice = GraphSlicer.slice(g, f);
		return GraphSlicer.prune(g, new CollectionFilter<T>(slice));
	}

	private static  Graph<IClass> pruneForAppLoader(Graph<IClass> g) throws WalaException {
		Filter<IClass> f = new Filter<IClass>() {
			public boolean accepts(IClass c) {
				return (c.getClassLoader().getReference().equals(ClassLoaderReference.Application));
			}
		};
		return pruneGraph(g, f);
	}

}