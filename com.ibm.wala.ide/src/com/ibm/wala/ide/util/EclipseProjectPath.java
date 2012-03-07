/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.ide.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.ClasspathUtilCore;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.PDEStateHelper;

import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.client.AbstractAnalysisEngine;
import com.ibm.wala.ide.classloader.EclipseSourceDirectoryTreeModule;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.MapUtil;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;

/**
 * Representation of an analysis scope from an Eclipse project.
 * 
 * We set up classloaders as follows:
 * <ul>
 * <li>The project being analyzed is in the Application Loader
 * <li>Frameworks, application libraries, and linked projects on which the main project depends are in the Extension loader
 * <li>System libraries are in the primordial loader.
 * <li>All source modules go in a special Source loader. This includes source from linked projects if
 * SOURCE_FOR_PROJ_AND_LINKED_PROJS is specified.
 * </ul>
 */
@SuppressWarnings("restriction")
public class EclipseProjectPath {

  /**
   * Eclipse projects are modelled with 3 loaders, as described above.
   */
  public enum Loader {
    APPLICATION(ClassLoaderReference.Application), EXTENSION(ClassLoaderReference.Extension), PRIMORDIAL(
        ClassLoaderReference.Primordial);

    private ClassLoaderReference ref;

    Loader(ClassLoaderReference ref) {
      this.ref = ref;
    }
  };

  public enum AnalysisScopeType {
    NO_SOURCE, SOURCE_FOR_PROJ, SOURCE_FOR_PROJ_AND_LINKED_PROJS
  }

  /**
   * The project whose path this object represents
   */
  private final IJavaProject project;

  /**
   * names of OSGi bundles already processed.
   */
  private final Set<String> bundlesProcessed = HashSetFactory.make();

  // SJF: Intentionally do not use HashMapFactory, since the Loader keys in the following must use
  // identityHashCode. TODO: fix this source of non-determinism?
  private final Map<Loader, List<Module>> modules = new HashMap<Loader, List<Module>>();

  /**
   * Classpath entries that have already been resolved and added to the scope.
   */
  private final Collection<IClasspathEntry> alreadyResolved = HashSetFactory.make();

  /**
   * Which source files, if any, should be included in the analysis scope.
   */
  private final AnalysisScopeType scopeType;

  protected EclipseProjectPath(IJavaProject project, AnalysisScopeType scopeType) throws IOException, CoreException {
    if (project == null) {
      throw new IllegalArgumentException("null project");
    }
    this.scopeType = scopeType;
    this.project = project;
    assert project != null;
    for (Loader loader : Loader.values()) {
      MapUtil.findOrCreateList(modules, loader);
    }
    boolean includeSource = (scopeType != AnalysisScopeType.NO_SOURCE);
    resolveProjectClasspathEntries(includeSource);
    if (isPluginProject(project)) {
      resolvePluginClassPath(project.getProject(), includeSource);
    }
  }

  public static EclipseProjectPath make(IJavaProject project) throws IOException, CoreException {
    return make(project, AnalysisScopeType.NO_SOURCE);
  }

  public static EclipseProjectPath make(IJavaProject project, AnalysisScopeType scopeType) throws IOException, CoreException {
    return new EclipseProjectPath(project, scopeType);
  }

  /**
   * Figure out what a classpath entry means and add it to the appropriate set of modules
   */
  private void resolveClasspathEntry(IClasspathEntry entry, Loader loader, boolean includeSource, boolean cpeFromMainProject)
      throws JavaModelException, IOException {
    IClasspathEntry e = JavaCore.getResolvedClasspathEntry(entry);
    if (alreadyResolved.contains(e)) {
      return;
    } else {
      alreadyResolved.add(e);
    }

    if (e.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
      IClasspathContainer cont = JavaCore.getClasspathContainer(entry.getPath(), project);
      IClasspathEntry[] entries = cont.getClasspathEntries();
      resolveClasspathEntries(entries, cont.getKind() == IClasspathContainer.K_APPLICATION ? loader : Loader.PRIMORDIAL,
          includeSource, false);
    } else if (e.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
      File file = makeAbsolute(e.getPath()).toFile();
      JarFile j;
      try {
        j = new JarFile(file);
      } catch (ZipException z) {
        // a corrupted file. ignore it.
        return;
      } catch (FileNotFoundException z) {
        // should ignore directories as well..
        return;
      }
      if (isPrimordialJarFile(j)) {
        List<Module> s = MapUtil.findOrCreateList(modules, loader);
        s.add(file.isDirectory() ? (Module) new BinaryDirectoryTreeModule(file) : (Module) new JarFileModule(j));
      }
    } else if (e.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
      if (includeSource) {
        List<Module> s = MapUtil.findOrCreateList(modules, Loader.APPLICATION);
        s.add(new EclipseSourceDirectoryTreeModule(e.getPath()));
      } else if (e.getOutputLocation() != null) {
        File output = makeAbsolute(e.getOutputLocation()).toFile();
        List<Module> s = MapUtil.findOrCreateList(modules, cpeFromMainProject ? Loader.APPLICATION : loader);
        s.add(new BinaryDirectoryTreeModule(output));
      }
    } else if (e.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
      IPath projectPath = makeAbsolute(e.getPath());
      IWorkspace ws = ResourcesPlugin.getWorkspace();
      IWorkspaceRoot root = ws.getRoot();
      IProject project = (IProject) root.getContainerForLocation(projectPath);
      try {
        if (project.hasNature(JavaCore.NATURE_ID)) {
          IJavaProject javaProject = JavaCore.create(project);
          if (isPluginProject(javaProject)) {
            resolvePluginClassPath(javaProject.getProject(), includeSource);
          }
          resolveClasspathEntries(javaProject.getRawClasspath(), loader,
              scopeType == AnalysisScopeType.SOURCE_FOR_PROJ_AND_LINKED_PROJS ? includeSource : false, false);
          File output = makeAbsolute(javaProject.getOutputLocation()).toFile();
          List<Module> s = MapUtil.findOrCreateList(modules, loader);
          if (!includeSource) {
            if (output.exists()) {
              s.add(new BinaryDirectoryTreeModule(output));
            }
          }
        }
      } catch (CoreException e1) {
        e1.printStackTrace();
        Assertions.UNREACHABLE();
      }
    } else {
      throw new RuntimeException("unexpected entry " + e);
    }
  }

  /**
   * traverse the bundle description for an Eclipse project and populate the analysis scope accordingly
   */
  private void resolvePluginClassPath(IProject p, boolean includeSource) throws CoreException, IOException {
    IPluginModelBase model = findModel(p);
    if (!model.isInSync() || model.isDisposed()) {
      model.load();
    }
    BundleDescription bd = model.getBundleDescription();

    if (bd == null) {
      // temporary debugging code; remove once we figure out what the heck is going on here --MS
      System.err.println("model.isDisposed(): " + model.isDisposed());
      System.err.println("model.isInSync(): " + model.isInSync());
      System.err.println("model.isEnabled(): " + model.isEnabled());
      System.err.println("model.isLoaded(): " + model.isLoaded());
      System.err.println("model.isValid(): " + model.isValid());
    }
    for (int i = 0; i < 3 && bd == null; i++) {
      // Uh oh. bd is null. Go to sleep, cross your fingers, and try again.
      // This is horrible. We can't figure out the race condition yet which causes this to happen.
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        // whatever.
      }
      bd = findModel(p).getBundleDescription();
    }

    if (bd == null) {
      throw new IllegalStateException("bundle description was null for " + p);
    }
    resolveBundleDescriptionClassPath(bd, Loader.APPLICATION, includeSource);
  }

  /**
   * traverse a bundle description and populate the analysis scope accordingly
   */
  @SuppressWarnings("unchecked")
  private void resolveBundleDescriptionClassPath(BundleDescription bd, Loader loader, boolean includeSource) throws CoreException,
      IOException {
    assert bd != null;
    if (alreadyProcessed(bd)) {
      return;
    }
    bundlesProcessed.add(bd.getName());

    // handle the classpath entries for bd
    ArrayList l = new ArrayList();
    ClasspathUtilCore.addLibraries(findModel(bd), l);
    IClasspathEntry[] entries = new IClasspathEntry[l.size()];
    int i = 0;
    for (Object o : l) {
      IClasspathEntry e = (IClasspathEntry) o;
      entries[i++] = e;
    }
    resolveClasspathEntries(entries, loader, includeSource, false);

    // recurse to handle dependencies. put these in the Extension loader
    for (BundleDescription b : PDEStateHelper.getImportedBundles(bd)) {
      resolveBundleDescriptionClassPath(b, Loader.EXTENSION, includeSource);
    }
    for (BundleDescription b : bd.getResolvedRequires()) {
      resolveBundleDescriptionClassPath(b, Loader.EXTENSION, includeSource);
    }
    for (BundleDescription b : bd.getFragments()) {
      resolveBundleDescriptionClassPath(b, Loader.EXTENSION, includeSource);
    }
  }

  /**
   * have we already processed a particular bundle description?
   */
  private boolean alreadyProcessed(BundleDescription bd) {
    return bundlesProcessed.contains(bd.getName());
  }

  /**
   * Is javaProject a plugin project?
   */
  private boolean isPluginProject(IJavaProject javaProject) {
    IPluginModelBase model = findModel(javaProject.getProject());
    if (model == null) {
      return false;
    }
    if (model.getPluginBase().getId() == null) {
      return false;
    }
    return true;
  }

  /**
   * @return true if the given jar file should be handled by the Primordial loader. If false, other provisions should be made to add
   *         the jar file to the appropriate component of the AnalysisScope. Subclasses can override this method.
   */
  protected boolean isPrimordialJarFile(JarFile j) {
    return true;
  }

  protected void resolveClasspathEntries(IClasspathEntry[] entries, Loader loader, boolean includeSource,
      boolean entriesFromTopLevelProject) throws JavaModelException, IOException {
    for (int i = 0; i < entries.length; i++) {
      resolveClasspathEntry(entries[i], loader, includeSource, entriesFromTopLevelProject);
    }
  }

  public static IPath makeAbsolute(IPath p) {
    IPath absolutePath = p;
    if (p.toFile().exists()) {
      return p;
    }

    IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(p);
    if (resource != null && resource.exists()) {
      absolutePath = resource.getLocation();
    }
    return absolutePath;
  }

  private void resolveProjectClasspathEntries(boolean includeSource) throws JavaModelException, IOException {

    resolveClasspathEntries(project.getRawClasspath(), Loader.EXTENSION, includeSource, true);

    if (!includeSource) {
      File dir = makeAbsolute(project.getOutputLocation()).toFile();
      if (!dir.isDirectory()) {
        System.err.println("PANIC: project output location is not a directory: " + dir);
      } else {
        MapUtil.findOrCreateList(modules, Loader.APPLICATION).add(new BinaryDirectoryTreeModule(dir));
      }
    }

  }

  /**
   * Convert this path to a WALA analysis scope
   * 
   * @throws IOException
   */
  public AnalysisScope toAnalysisScope(ClassLoader classLoader, File exclusionsFile) throws IOException {
    AnalysisScope scope = AnalysisScopeReader.readJavaScope(AbstractAnalysisEngine.SYNTHETIC_J2SE_MODEL, exclusionsFile,
        classLoader);
    return toAnalysisScope(scope);
  }

  public AnalysisScope toAnalysisScope(AnalysisScope scope) {

    for (Loader loader : Loader.values()) {
      for (Module m : modules.get(loader)) {
        scope.addToScope(loader.ref, m);
      }
    }
    return scope;

  }

  public AnalysisScope toAnalysisScope(final File exclusionsFile) throws IOException {
    return toAnalysisScope(getClass().getClassLoader(), exclusionsFile);
  }

  public AnalysisScope toAnalysisScope() throws IOException {
    return toAnalysisScope(getClass().getClassLoader(), null);
  }

  public Collection<Module> getModules(Loader loader, boolean binary) {
    return Collections.unmodifiableCollection(modules.get(loader));
  }

  @Override
  public String toString() {
    try {
      return toAnalysisScope((File) null).toString();
    } catch (IOException e) {
      e.printStackTrace();
      return "Error in toString()";
    }
  }

  private IPluginModelBase findModel(IProject p) {
    // PluginRegistry is specific to Eclipse 3.3+. Use PDECore for compatibility with 3.2
    // return PluginRegistry.findModel(p);
    return PDECore.getDefault().getModelManager().findModel(p);
  }

  private IPluginModelBase findModel(BundleDescription bd) {
    // PluginRegistry is specific to Eclipse 3.3+. Use PDECore for compatibility with 3.2
    // return PluginRegistry.findModel(bd);
    return PDECore.getDefault().getModelManager().findModel(bd);
  }
}
