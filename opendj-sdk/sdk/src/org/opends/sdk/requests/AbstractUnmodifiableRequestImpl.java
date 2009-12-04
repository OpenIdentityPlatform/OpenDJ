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

package org.opends.sdk.requests;



import org.opends.sdk.controls.Control;

import com.sun.opends.sdk.util.Iterables;



/**
 * Unmodifiable request implementation.
 * 
 * @param <R>
 *          The type of request.
 */
abstract class AbstractUnmodifiableRequestImpl<R extends Request>
    implements Request
{

  private final R impl;



  /**
   * Creates a new unmodifiable request implementation.
   * 
   * @param impl
   *          The underlying request implementation to be made
   *          unmodifiable.
   */
  AbstractUnmodifiableRequestImpl(R impl)
  {
    this.impl = impl;
  }



  /**
   * {@inheritDoc}
   */
  public final R addControl(Control control)
      throws UnsupportedOperationException, NullPointerException
  {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  public final R clearControls() throws UnsupportedOperationException
  {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  public final Control getControl(String oid)
      throws NullPointerException
  {
    // FIXME: ensure that controls are immutable.
    return impl.getControl(oid);
  }



  /**
   * {@inheritDoc}
   */
  public final Iterable<Control> getControls()
  {
    // FIXME: ensure that controls are immutable.
    return Iterables.unmodifiable(impl.getControls());
  }



  /**
   * {@inheritDoc}
   */
  public final boolean hasControls()
  {
    return impl.hasControls();
  }



  /**
   * {@inheritDoc}
   */
  public final Control removeControl(String oid)
      throws UnsupportedOperationException, NullPointerException
  {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  public final String toString()
  {
    return impl.toString();
  }

}
