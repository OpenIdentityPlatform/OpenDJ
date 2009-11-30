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



import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.opends.sdk.controls.Control;
import org.opends.sdk.util.Validator;



/**
 * Abstract request implementation.
 * 
 * @param <R>
 *          The type of request.
 */
abstract class AbstractRequestImpl<R extends Request> implements
    Request
{
  private final List<Control> controls = new LinkedList<Control>();



  /**
   * Creates a new abstract request implementation.
   */
  AbstractRequestImpl()
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public final R addControl(Control control)
      throws NullPointerException
  {
    Validator.ensureNotNull(control);
    controls.add(control);
    return getThis();
  }



  /**
   * {@inheritDoc}
   */
  public final R clearControls()
  {
    controls.clear();
    return getThis();
  }



  /**
   * {@inheritDoc}
   */
  public final Control getControl(String oid)
  {
    Validator.ensureNotNull(oid);

    // Avoid creating an iterator if possible.
    if (controls.isEmpty())
    {
      return null;
    }

    for (final Control control : controls)
    {
      if (control.getOID().equals(oid))
      {
        return control;
      }
    }

    return null;
  }



  /**
   * {@inheritDoc}
   */
  public final Iterable<Control> getControls()
  {
    return controls;
  }



  /**
   * {@inheritDoc}
   */
  public final boolean hasControls()
  {
    return !controls.isEmpty();
  }



  /**
   * {@inheritDoc}
   */
  public final Control removeControl(String oid)
      throws NullPointerException
  {
    Validator.ensureNotNull(oid);

    // Avoid creating an iterator if possible.
    if (controls.isEmpty())
    {
      return null;
    }

    final Iterator<Control> iterator = controls.iterator();
    while (iterator.hasNext())
    {
      final Control control = iterator.next();
      if (control.getOID().equals(oid))
      {
        iterator.remove();
        return control;
      }
    }

    return null;
  }



  public abstract String toString();



  abstract R getThis();

}
