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
package com.ibm.wala.ipa.callgraph.propagation;

/**
 * This class exists to force {@link PointerKey} implementations to implement equals() and hashCode()s.
 *
 */
public abstract class AbstractPointerKey implements PointerKey {
  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();
}
