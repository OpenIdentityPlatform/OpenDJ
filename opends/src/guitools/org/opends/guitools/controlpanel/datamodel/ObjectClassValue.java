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

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class represent all the objectclass values for a given entry.  It is
 * used by the entry editors (SimplifiedEntryView and TableEntryView) to edit
 * and display the objectclass.
 *
 */
public class ObjectClassValue
{
  private String structural;
  private SortedSet<String> auxiliary = new TreeSet<String>();
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
    hashCode = structural.hashCode();
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

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return hashCode;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object o)
  {
    boolean equals;
    if (o != this)
    {
      if (o != null)
      {
        if (o instanceof ObjectClassValue)
        {
          ObjectClassValue oc = (ObjectClassValue)o;
          if (structural != null)
          {
            equals = structural.equals(oc.getStructural());
          }
          else
          {
            equals = oc.getStructural() == null;
          }
          if (equals)
          {
            equals = auxiliary.equals(oc.getAuxiliary());
          }
        }
        else
        {
          equals = false;
        }
      }
      else
      {
        equals = false;
      }
    }
    else
    {
      equals = true;
    }
    return equals;
  }
}
