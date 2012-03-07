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
package com.ibm.wala.eclipse.headless;

import java.util.Collection;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IJavaProject;

import com.ibm.wala.ide.util.EclipseProjectPath;
import com.ibm.wala.ide.util.JdtUtil;

/**
 * A dummy main class that runs WALA in a headless Eclipse platform.
 * 
 * This is for expository purposes, and tests some WALA eclipse functionality.
 * 
 * @author sjfink
 * 
 */
public class Main implements IApplication  {

  public Object start(IApplicationContext context) throws Exception {
    Collection<IJavaProject> jp = JdtUtil.getWorkspaceJavaProjects();
    for (IJavaProject p : jp) {
      System.out.println(p);
      EclipseProjectPath path = EclipseProjectPath.make(p);
      System.out.println("Path: " + path);
    }
    return null;
  }

  public void stop() {
  }
}
