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
 * used to compare RDNs, particularly for inclusion in a sorted list.
 * The comparison will be done in order of the RDN components.  It
 * will attempt to use an ordering matching rule for the associated
 * attributes (if one is provided), but will fall back on a bytewise
 * comparison of the normalized values if necessary.
 */
public class RDNComparator
       implements Comparator<RDN>
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.RDNComparator";



  /**
   * Creates a new instance of this RDN comparator.
   */
  public RDNComparator()
  {
    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Compares the provided RDNs and returns an integer value that
   * reflects the relative order between them.
   *
   * @param  rdn1  The first RDN to compare.
   * @param  rdn2  The second RDN to compare.
   *
   * @return  A negative value if the first RDN should come before the
   *          second in an ordered list, a positive value if they
   *          first RDN should come after the second in an ordered
   *          list, or zero if there is no difference between their
   *          order (i.e., the RDNs are equal).
   */
  public int compare(RDN rdn1, RDN rdn2)
  {
    assert debugEnter(CLASS_NAME, "compare", String.valueOf(rdn1),
                      String.valueOf(rdn2));

    AttributeType[]  types1  = rdn1.getAttributeTypes();
    AttributeType[]  types2  = rdn2.getAttributeTypes();
    AttributeValue[] values1 = rdn1.getAttributeValues();
    AttributeValue[] values2 = rdn2.getAttributeValues();

    int index = 0;

    while (true)
    {
      if (index < types1.length)
      {
        if (index < types1.length)
        {
          if (types1[index].equals(types2[index]))
          {
            AttributeValueComparator valueComparator =
                 new AttributeValueComparator(types1[index]);
            int value = valueComparator.compare(values1[index],
                                                values2[index]);
            if (value != 0)
            {
              return value;
            }
          }
          else
          {
            return types1[index].getLowerName().compareTo(
                        types2[index].getLowerName());
          }
        }
        else
        {
          return 1;
        }
      }
      else if (index < types1.length)
      {
        return -1;
      }
      else
      {
        return 0;
      }

      index++;
    }
  }
}

