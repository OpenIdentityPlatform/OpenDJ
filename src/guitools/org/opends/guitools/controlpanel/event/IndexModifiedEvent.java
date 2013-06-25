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

package org.opends.guitools.controlpanel.event;

import java.util.HashSet;
import java.util.Set;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;

/**
 * The event that describes a modification of the index.
 *
 */
public class IndexModifiedEvent
{
  private Set<AbstractIndexDescriptor> modifiedIndexes =
    new HashSet<AbstractIndexDescriptor>();

  /**
   * The constructor of the event.
   * @param modifiedIndex the modified indexes.
   */
  public IndexModifiedEvent(AbstractIndexDescriptor modifiedIndex)
  {
    this.modifiedIndexes.add(modifiedIndex);
  }

  /**
   * The event will contain all the indexes in a given backend.
   * @param backend the backend whose indexes have been modified.
   */
  public IndexModifiedEvent(BackendDescriptor backend)
  {
    this.modifiedIndexes.addAll(backend.getIndexes());
    this.modifiedIndexes.addAll(backend.getVLVIndexes());
  }

  /**
   * Returns list of indexes that have been modified.
   * @return list of indexes that have been modified.
   */
  public Set<AbstractIndexDescriptor> getIndexDescriptor()
  {
    return modifiedIndexes;
  }
}
