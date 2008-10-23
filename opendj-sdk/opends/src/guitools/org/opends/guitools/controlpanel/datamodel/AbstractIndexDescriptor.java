/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.datamodel;

/**
 * Abstract class used to describe the configuration of an index.
 *
 */
public abstract class AbstractIndexDescriptor implements Comparable
{
  private String name;
  private BackendDescriptor backend;
  /**
   * Constructor.
   * @param name the name of the index.
   * @param backend the backend where the index is defined.
   */
  protected AbstractIndexDescriptor(String name, BackendDescriptor backend)
  {
    this.name = name;
    this.backend = backend;
  }

  /**
   * Returns the name of the index.
   * @return the name of the index.
   */
  public String getName()
  {
    return name;
  }

  /**
   * Returns the backend where the index is defined.
   * @return the backend where the index is defined.
   */
  public BackendDescriptor getBackend()
  {
    return backend;
  }

  /**
   * Sets which is the backend where the index is defined.
   * @param backend the backend where the index is defined.
   */
  public void setBackend(BackendDescriptor backend)
  {
    this.backend = backend;
    recalculateHashCode();
  }

  /**
   * Method used to minimize the times the hashcode is calculated.
   *
   */
  protected abstract void recalculateHashCode();
}
