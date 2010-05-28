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



import java.util.List;

import org.opends.sdk.DecodeException;
import org.opends.sdk.DecodeOptions;
import org.opends.sdk.controls.Control;
import org.opends.sdk.controls.ControlDecoder;



/**
 * The Abandon operation allows a client to request that the server abandon an
 * uncompleted operation.
 * <p>
 * Abandon, Bind, Unbind, and StartTLS operations cannot be abandoned.
 */
public interface AbandonRequest extends Request
{
  /**
   * {@inheritDoc}
   */
  AbandonRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  <C extends Control> C getControl(ControlDecoder<C> decoder,
      DecodeOptions options) throws NullPointerException, DecodeException;



  /**
   * {@inheritDoc}
   */
  List<Control> getControls();



  /**
   * Returns the message ID of the request to be abandoned.
   *
   * @return The message ID of the request to be abandoned.
   */
  int getMessageID();



  /**
   * Sets the message ID of the request to be abandoned.
   *
   * @param id
   *          The message ID of the request to be abandoned.
   * @return This abandon request.
   * @throws UnsupportedOperationException
   *           If this abandon request does not permit the message ID to be set.
   */
  AbandonRequest setMessageID(int id) throws UnsupportedOperationException;

}
