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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.io.Serializable;
import java.util.Comparator;
import org.opends.server.types.ByteSequence;



/**
 * This interface defines the set of methods that must be implemented
 * by a Directory Server module that implements a matching
 * rule used for determining the correct order of values when sorting
 * or processing range filters.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface OrderingMatchingRule
        extends MatchingRule,Comparator<byte[]>,Serializable
{
  /**
   * Compares the first value to the second and returns a value that
   * indicates their relative order.
   *
   * @param  value1  The normalized form of the first value to
   *                 compare.
   * @param  value2  The normalized form of the second value to
   *                 compare.
   *
   * @return  A negative integer if {@code value1} should come before
   *          {@code value2} in ascending order, a positive integer if
   *          {@code value1} should come after {@code value2} in
   *          ascending order, or zero if there is no difference
   *          between the values with regard to ordering.
   */
  public abstract int compareValues(ByteSequence value1,
                                    ByteSequence value2);
}
