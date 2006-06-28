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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.Comparator;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a <CODE>Comparator</CODE> object that may be
 * used to compare DNs, particularly for inclusion in a sorted list.
 * The DNs will be compared first by hierarchy (so a child will always
 * be ordered after its parent) and then using RDN comparators for DN
 * components from suffix to leaf.
 */
public class DNComparator
       implements Comparator<DN>
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.DNComparator";



  // The RDN comparator that will be used to compare RDN components.
  private RDNComparator rdnComparator;



  /**
   * Creates a new instance of this DN comparator.
   */
  public DNComparator()
  {
    assert debugConstructor(CLASS_NAME);

    rdnComparator = new RDNComparator();
  }



  /**
   * Compares the provided DNs and returns an integer value that
   * reflects the relative order between them.
   *
   * @param  dn1  The first DN to compare.
   * @param  dn2  The second DN to compare.
   *
   * @return  A negative value if the first DN should come before the
   *          second in an ordered list, a positive value if they
   *          first DN should come after the second in an ordered
   *          list, or zero if there is no difference between their
   *          order (i.e., the DNs are equal).
   */
  public int compare(DN dn1, DN dn2)
  {
    assert debugEnter(CLASS_NAME, "compare", String.valueOf(dn1),
                      String.valueOf(dn2));

    RDN[] rdns1 = dn1.getRDNComponents();
    RDN[] rdns2 = dn2.getRDNComponents();

    int index1 = rdns1.length - 1;
    int index2 = rdns2.length - 1;

    while (true)
    {
      if (index1 >= 0)
      {
        if (index2 >= 0)
        {
          int value = rdnComparator.compare(rdns1[index1],
                                            rdns2[index2]);
          if (value != 0)
          {
            return value;
          }
        }
        else
        {
          return 1;
        }
      }
      else if (index2 >= 0)
      {
        return -1;
      }
      else
      {
        return 0;
      }

      index1--;
      index2--;
    }
  }
}

