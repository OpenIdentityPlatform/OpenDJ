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

package com.sun.opends.sdk.util;



import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;



/**
 * Additional {@code Collection} based utility methods.
 */
public final class Collections2
{
  private static final class TransformedCollection<M, N, P> extends
      AbstractCollection<N> implements Collection<N>
  {

    private final Collection<M> collection;

    private final Function<? super M, ? extends N, P> funcMtoN;

    private final Function<? super N, ? extends M, P> funcNtoM;

    private final P p;



    private TransformedCollection(Collection<M> collection,
        Function<? super M, ? extends N, P> funcMtoN,
        Function<? super N, ? extends M, P> funcNtoM, P p)
    {
      this.collection = collection;
      this.funcMtoN = funcMtoN;
      this.funcNtoM = funcNtoM;
      this.p = p;
    }



    /**
     * {@inheritDoc}
     */
    public boolean add(N e)
    {
      return collection.add(funcNtoM.apply(e, p));
    }



    /**
     * {@inheritDoc}
     */
    public void clear()
    {
      collection.clear();
    }



    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public boolean contains(Object o)
    {
      N tmp = (N) o;
      return collection.contains(funcNtoM.apply(tmp, p));
    }



    /**
     * {@inheritDoc}
     */
    public boolean isEmpty()
    {
      return collection.isEmpty();
    }



    /**
     * {@inheritDoc}
     */
    public Iterator<N> iterator()
    {
      return Iterators.transform(collection.iterator(), funcMtoN, p);
    }



    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public boolean remove(Object o)
    {
      N tmp = (N) o;
      return collection.remove(funcNtoM.apply(tmp, p));
    }



    /**
     * {@inheritDoc}
     */
    public int size()
    {
      return collection.size();
    }

  }



  /**
   * Returns a view of {@code collection} whose values have been mapped
   * to elements of type {@code N} using {@code funcMtoN}. The returned
   * collection supports all operations.
   *
   * @param <M>
   *          The type of elements contained in {@code collection}.
   * @param <N>
   *          The type of elements contained in the returned collection.
   * @param <P>
   *          The type of the additional parameter to the function's
   *          {@code apply} method. Use {@link java.lang.Void} for
   *          functions that do not need an additional parameter.
   * @param collection
   *          The collection to be transformed.
   * @param funcMtoN
   *          A function which maps values of type {@code M} to values
   *          of type {@code N}. This function will be used when
   *          retrieving values from {@code collection}.
   * @param funcNtoM
   *          A function which maps values of type {@code N} to values
   *          of type {@code M}. This function will be used when
   *          performing queries and adding values to {@code collection}
   *          .
   * @param p
   *          A predicate specified parameter.
   * @return A view of {@code collection} whose values have been mapped
   *         to elements of type {@code N} using {@code funcMtoN}.
   */
  public static <M, N, P> Collection<N> transform(
      Collection<M> collection,
      Function<? super M, ? extends N, P> funcMtoN,
      Function<? super N, ? extends M, P> funcNtoM, P p)
  {
    return new TransformedCollection<M, N, P>(collection, funcMtoN,
        funcNtoM, p);
  }



  /**
   * Returns a view of {@code collection} whose values have been mapped
   * to elements of type {@code N} using {@code funcMtoN}. The returned
   * collection supports all operations.
   *
   * @param <M>
   *          The type of elements contained in {@code collection}.
   * @param <N>
   *          The type of elements contained in the returned collection.
   * @param collection
   *          The collection to be transformed.
   * @param funcMtoN
   *          A function which maps values of type {@code M} to values
   *          of type {@code N}. This function will be used when
   *          retrieving values from {@code collection}.
   * @param funcNtoM
   *          A function which maps values of type {@code N} to values
   *          of type {@code M}. This function will be used when
   *          performing queries and adding values to {@code collection}
   *          .
   * @return A view of {@code collection} whose values have been mapped
   *         to elements of type {@code N} using {@code funcMtoN}.
   */
  public static <M, N> Collection<N> transform(
      Collection<M> collection,
      Function<? super M, ? extends N, Void> funcMtoN,
      Function<? super N, ? extends M, Void> funcNtoM)
  {
    return new TransformedCollection<M, N, Void>(collection, funcMtoN,
        funcNtoM, null);
  }



  // Prevent instantiation
  private Collections2()
  {
    // Do nothing.
  }

}
