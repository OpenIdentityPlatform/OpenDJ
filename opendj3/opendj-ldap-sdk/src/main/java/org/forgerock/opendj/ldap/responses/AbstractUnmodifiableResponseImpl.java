/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;



import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldap.controls.GenericControl;

import com.forgerock.opendj.util.Collections2;
import com.forgerock.opendj.util.Function;
import com.forgerock.opendj.util.Functions;
import com.forgerock.opendj.util.Validator;



/**
 * Unmodifiable response implementation.
 *
 * @param <S>
 *          The type of response.
 */
abstract class AbstractUnmodifiableResponseImpl<S extends Response> implements
    Response
{

  protected final S impl;



  /**
   * Creates a new unmodifiable response implementation.
   *
   * @param impl
   *          The underlying response implementation to be made unmodifiable.
   */
  AbstractUnmodifiableResponseImpl(final S impl)
  {
    Validator.ensureNotNull(impl);
    this.impl = impl;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final S addControl(final Control control)
  {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final <C extends Control> C getControl(
      final ControlDecoder<C> decoder, final DecodeOptions options)
      throws DecodeException
  {
    Validator.ensureNotNull(decoder, options);

    final List<Control> controls = impl.getControls();

    // Avoid creating an iterator if possible.
    if (controls.isEmpty())
    {
      return null;
    }

    for (final Control control : controls)
    {
      if (control.getOID().equals(decoder.getOID()))
      {
        // Got a match. Return a defensive copy only if necessary.
        final C decodedControl = decoder.decodeControl(control, options);

        if (decodedControl != control)
        {
          // This was not the original control so return it immediately.
          return decodedControl;
        }
        else if (decodedControl instanceof GenericControl)
        {
          // Generic controls are immutable, so return it immediately.
          return decodedControl;
        }
        else
        {
          // Re-decode to get defensive copy.
          final GenericControl genericControl = GenericControl
              .newControl(control);
          return decoder.decodeControl(genericControl, options);
        }
      }
    }

    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final List<Control> getControls()
  {
    // We need to make all controls unmodifiable as well, which implies making
    // defensive copies where necessary.
    final Function<Control, Control, Void> function = new Function<Control, Control, Void>()
    {

      @Override
      public Control apply(final Control value, final Void p)
      {
        // Return defensive copy.
        return GenericControl.newControl(value);
      }

    };

    final List<Control> unmodifiableControls = Collections2.transformedList(
        impl.getControls(), function, Functions.<Control> identityFunction());
    return Collections.unmodifiableList(unmodifiableControls);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final String toString()
  {
    return impl.toString();
  }
}
