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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import java.util.Comparator;

/**
 * A comparator for index modifications.
 */
public class IndexModComparator implements Comparator<IndexMod>
{
  /**
   * The index key comparator.
   */
  private Comparator<byte[]> comparator;

  /**
   * Indicates when true that the entry ID should also factor into the
   * comparison, in addition to the index key.
   */
  private boolean compareID;

  /**
   * Create a new comparator for index modifications.
   * @param comparator The index key comparator.
   * @param compareID Indicates when true that the entry ID should also factor
   * into the comparison, in addition to the index key.
   */
  public IndexModComparator(Comparator<byte[]> comparator, boolean compareID)
  {
    this.comparator = comparator;
    this.compareID = compareID;
  }

  /**
   * Compares its two arguments for order.  Returns a negative integer,
   * zero, or a positive integer as the first argument is less than, equal
   * to, or greater than the second.
   *
   * @param a the first object to be compared.
   * @param b the second object to be compared.
   * @return a negative integer, zero, or a positive integer as the
   *         first argument is less than, equal to, or greater than the
   *         second.
   */
  public int compare(IndexMod a, IndexMod b)
  {
    int r = comparator.compare(a.key, b.key);
    if (compareID)
    {
      if (r == 0)
      {
        r = a.value.compareTo(b.value);
      }
    }
    return r;
  }
}
