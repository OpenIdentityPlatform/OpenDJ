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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class represent all the objectclass values for a given entry.  It is
 * used by the entry editors (SimplifiedEntryView and TableEntryView) to edit
 * and display the objectclass.
 */
public class ObjectClassValue
{
  private String structural;
  private SortedSet<String> auxiliary = new TreeSet<>();
  private int hashCode;

  /**
   * Constructor of the object class value.
   * @param structural the name of the structural objectclass.
   * @param auxiliary the auxiliary objectclasses.
   */
  public ObjectClassValue(String structural, Set<String> auxiliary)
  {
    this.structural = structural;
    this.auxiliary.addAll(auxiliary);
    if (structural != null)
    {
      // This can happen when the schema checking is not enabled.
      hashCode = structural.hashCode();
    }
    for (String oc : auxiliary)
    {
      hashCode += oc.hashCode();
    }
  }

  /**
   * Returns the names of the auxiliary objectclasses.
   * @return the names of the auxiliary objectclasses.
   */
  public SortedSet<String> getAuxiliary()
  {
    return auxiliary;
  }

  /**
   * Returns the name of the structural objectclass.
   * @return the name of the structural objectclass.
   */
  public String getStructural()
  {
    return structural;
  }

  @Override
  public int hashCode()
  {
    return hashCode;
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (o instanceof ObjectClassValue)
    {
      ObjectClassValue oc = (ObjectClassValue)o;
      return Objects.equals(structural, oc.getStructural())
          && auxiliary.equals(oc.getAuxiliary());
    }
    return false;
  }
}
