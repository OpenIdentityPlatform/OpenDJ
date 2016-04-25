/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

/** Abstract class used to describe the configuration of an index. */
public abstract class AbstractIndexDescriptor
implements Comparable<AbstractIndexDescriptor>
{
  private final String name;
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

  /** Method used to minimize the times the hashcode is calculated. */
  protected abstract void recalculateHashCode();
}
