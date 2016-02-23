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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
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
  private Set<AbstractIndexDescriptor> modifiedIndexes = new HashSet<>();

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
