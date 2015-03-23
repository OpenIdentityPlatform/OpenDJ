/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

/**
 * Class that describes the VLV sort order.
 */
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

  /** {@inheritDoc} */
  public int hashCode()
  {
    return hashCode;
  }

  /** {@inheritDoc} */
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
