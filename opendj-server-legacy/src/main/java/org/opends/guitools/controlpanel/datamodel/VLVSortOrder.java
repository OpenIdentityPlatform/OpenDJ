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

/** Class that describes the VLV sort order. */
public class VLVSortOrder
{
  private String attributeName;
  private boolean isAscending;
  private int hashCode;

  /**
   * Constructor of the VLVSortOrder.
   * @param attributeName the attribute name to be used to sort.
   * @param isAscending whether the sorting is ascending or descending.
   */
  public VLVSortOrder(String attributeName, boolean isAscending)
  {
    this.attributeName = attributeName;
    this.isAscending = isAscending;
    hashCode = ("vlvsortorder"+attributeName+isAscending).hashCode();
  }

  /**
   * Returns the name of the attribute.
   * @return the name of the attribute.
   */
  public String getAttributeName()
  {
    return attributeName;
  }

  /**
   * Returns whether the sorting is ascending or descending.
   * @return <CODE>true</CODE> if the sorting is ascending and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isAscending()
  {
    return isAscending;
  }

  @Override
  public int hashCode()
  {
    return hashCode;
  }

  @Override
  public boolean equals(Object o)
  {
    if (o == this) {
      return  true;
    }
    if (o instanceof VLVSortOrder)
    {
      VLVSortOrder sortOrder = (VLVSortOrder)o;
      return sortOrder.getAttributeName().equalsIgnoreCase(attributeName)
          && sortOrder.isAscending() == isAscending;
    }
    return false;
  }
}
