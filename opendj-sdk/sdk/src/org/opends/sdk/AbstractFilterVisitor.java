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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.util.List;

import org.opends.sdk.util.ByteSequence;


/**
 * An abstract filter visitor whose default implementation for all
 * {@code Visitor} methods is to invoke
 * {@link #visitDefaultFilter(Object)}.
 * <p>
 * Implementations can override the methods on a case by case behavior.
 *
 * @param <R>
 *          The return type of this visitor's methods. Use
 *          {@link java.lang.Void} for visitors that do not need to
 *          return results.
 * @param <P>
 *          The type of the additional parameter to this visitor's
 *          methods. Use {@link java.lang.Void} for visitors that do not
 *          need an additional parameter.
 */
public abstract class AbstractFilterVisitor<R, P> implements
    FilterVisitor<R, P>
{

  /**
   * Default constructor.
   */
  protected AbstractFilterVisitor()
  {
    // Nothing to do.
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to call
   * {@link #visitDefaultFilter(Object)}.
   */
  public R visitAndFilter(P p, List<Filter> subFilters)
  {
    return visitDefaultFilter(p);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to call
   * {@link #visitDefaultFilter(Object)}.
   */
  public R visitApproxMatchFilter(P p, String attributeDescription,
      ByteSequence assertionValue)
  {
    return visitDefaultFilter(p);
  }



  /**
   * Visits any filters which are not explicitly handled by other
   * visitor methods.
   * <p>
   * The default implementation of this method is to return {@code null}.
   *
   * @param p
   *          A visitor specified parameter.
   * @return A visitor specified result.
   */
  public R visitDefaultFilter(P p)
  {
    return null;
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to call
   * {@link #visitDefaultFilter(Object)}.
   */
  public R visitEqualityMatchFilter(P p, String attributeDescription,
      ByteSequence assertionValue)
  {
    return visitDefaultFilter(p);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to call
   * {@link #visitDefaultFilter(Object)}.
   */
  public R visitExtensibleMatchFilter(P p, String matchingRule,
      String attributeDescription, ByteSequence assertionValue,
      boolean dnAttributes)
  {
    return visitDefaultFilter(p);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to call
   * {@link #visitDefaultFilter(Object)}.
   */
  public R visitGreaterOrEqualFilter(P p, String attributeDescription,
      ByteSequence assertionValue)
  {
    return visitDefaultFilter(p);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to call
   * {@link #visitDefaultFilter(Object)}.
   */
  public R visitLessOrEqualFilter(P p, String attributeDescription,
      ByteSequence assertionValue)
  {
    return visitDefaultFilter(p);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to call
   * {@link #visitDefaultFilter(Object)}.
   */
  public R visitNotFilter(P p, Filter subFilter)
  {
    return visitDefaultFilter(p);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to call
   * {@link #visitDefaultFilter(Object)}.
   */
  public R visitOrFilter(P p, List<Filter> subFilters)
  {
    return visitDefaultFilter(p);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to call
   * {@link #visitDefaultFilter(Object)}.
   */
  public R visitPresentFilter(P p, String attributeDescription)
  {
    return visitDefaultFilter(p);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to call
   * {@link #visitDefaultFilter(Object)}.
   */
  public R visitSubstringsFilter(P p, String attributeDescription,
      ByteSequence initialSubstring, List<ByteSequence> anySubstrings,
      ByteSequence finalSubstring)
  {
    return visitDefaultFilter(p);
  }



  /**
   * {@inheritDoc}
   * <p>
   * The default implementation is to call
   * {@link #visitDefaultFilter(Object)}.
   */
  public R visitUnrecognizedFilter(P p, byte filterTag,
      ByteSequence filterBytes)
  {
    return visitDefaultFilter(p);
  }
}
