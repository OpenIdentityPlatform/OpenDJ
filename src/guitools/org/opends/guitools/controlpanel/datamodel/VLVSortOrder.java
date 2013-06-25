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
    boolean equals = o == this;
    if (!equals)
    {
      equals = o instanceof VLVSortOrder;
      if (equals)
      {
        VLVSortOrder sortOrder = (VLVSortOrder)o;
        equals = sortOrder.getAttributeName().equalsIgnoreCase(attributeName) &&
          sortOrder.isAscending() == isAscending;
      }
    }
    return equals;
  }
}
