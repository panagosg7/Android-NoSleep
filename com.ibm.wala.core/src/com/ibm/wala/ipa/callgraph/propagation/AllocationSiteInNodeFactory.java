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

import com.ibm.wala.classLoader.ArrayClass;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.ProgramCounter;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallerContext;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ContainerContextSelector;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.TypeReference;

/**
 * A factory which tries by default to create {@link InstanceKey}s which are {@link AllocationSiteInNode}s.
 * 
 * Notes:
 * <ul>
 * <li>This class checks to avoid creating recursive contexts when {@link CGNode}s are based on {@link ReceiverInstanceContext}, as
 * in object-sensitivity.
 * <li>Up till recursion, this class will happily create unlimited object sensitivity, so be careful.
 * <li>This class resorts to {@link ClassBasedInstanceKeys} for exceptions from PEIs and class objects.
 * <li>This class consults the {@link AnalysisOptions} to determine whether to disambiguate individual constants.
 * </ul>
 */
public class AllocationSiteInNodeFactory implements InstanceKeyFactory {

  /**
   * Governing call graph construction options
   */
  private final AnalysisOptions options;

  /**
   * Governing class hierarchy
   */
  private final IClassHierarchy cha;

  private final ClassBasedInstanceKeys classBased;

  /**
   * @param options Governing call graph construction options
   */
  public AllocationSiteInNodeFactory(AnalysisOptions options, IClassHierarchy cha) {
    this.options = options;
    this.cha = cha;
    this.classBased = new ClassBasedInstanceKeys(options, cha);
  }

  public InstanceKey getInstanceKeyForAllocation(CGNode node, NewSiteReference allocation) {
    IClass type = options.getClassTargetSelector().getAllocatedTarget(node, allocation);
    if (type == null) {
      return null;
    }

    // disallow recursion in contexts.
    if (node.getContext() instanceof ReceiverInstanceContext || node.getContext() instanceof CallerContext) {
      IMethod m = node.getMethod();
      CGNode n = ContainerContextSelector.findNodeRecursiveMatchingContext(m, node.getContext());
      if (n != null) {
        return new NormalAllocationInNode(n, allocation, type);
      }
    }

    InstanceKey key = new NormalAllocationInNode(node, allocation, type);

    return key;
  }

  public InstanceKey getInstanceKeyForMultiNewArray(CGNode node, NewSiteReference allocation, int dim) {
    ArrayClass type = (ArrayClass) options.getClassTargetSelector().getAllocatedTarget(node, allocation);
    if (type == null) {
      return null;
    }
    InstanceKey key = new MultiNewArrayInNode(node, allocation, type, dim);

    return key;
  }

  public <T> InstanceKey getInstanceKeyForConstant(TypeReference type, T S) {
    if (options.getUseConstantSpecificKeys()) {
      return new ConstantKey<T>(S, cha.lookupClass(type));
    } else {
      return new ConcreteTypeKey(cha.lookupClass(type));
    }
  }

  public InstanceKey getInstanceKeyForPEI(CGNode node, ProgramCounter pei, TypeReference type) {
    return classBased.getInstanceKeyForPEI(node, pei, type);
  }

  public InstanceKey getInstanceKeyForClassObject(TypeReference type) {
    return classBased.getInstanceKeyForClassObject(type);
  }

}
