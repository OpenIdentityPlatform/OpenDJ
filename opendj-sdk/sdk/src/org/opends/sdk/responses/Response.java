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

package org.opends.sdk.responses;



import org.opends.sdk.controls.Control;



/**
 * The base class of all Responses provides methods for querying and
 * manipulating the set of Controls included with a Response.
 * <p>
 * TODO: added complete description including sub-types.
 */
public interface Response
{

  /**
   * Adds the provided control to this response.
   * 
   * @param control
   *          The control to be added.
   * @return This response.
   * @throws UnsupportedOperationException
   *           If this response does not permit controls to be added.
   * @throws NullPointerException
   *           If {@code control} was {@code null}.
   */
  Response addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Removes all the controls included with this response.
   * 
   * @return This response.
   * @throws UnsupportedOperationException
   *           If this response does not permit controls to be removed.
   */
  Response clearControls() throws UnsupportedOperationException;



  /**
   * Returns the first control contained in this response having the
   * specified OID.
   * 
   * @param oid
   *          The OID of the control to be returned.
   * @return The control, or {@code null} if the control is not included
   *         with this response.
   * @throws NullPointerException
   *           If {@code oid} was {@code null}.
   */
  Control getControl(String oid) throws NullPointerException;



  /**
   * Returns an {@code Iterable} containing the controls included with
   * this response. The returned {@code Iterable} may be used to remove
   * controls if permitted by this response.
   * 
   * @return An {@code Iterable} containing the controls.
   */
  Iterable<Control> getControls();



  /**
   * Indicates whether or not this response has any controls.
   * 
   * @return {@code true} if this response has any controls, otherwise
   *         {@code false}.
   */
  boolean hasControls();



  /**
   * Removes the first control contained in this response having the
   * specified OID.
   * 
   * @param oid
   *          The OID of the control to be removed.
   * @return The removed control, or {@code null} if the control is not
   *         included with this response.
   * @throws UnsupportedOperationException
   *           If this response does not permit controls to be removed.
   * @throws NullPointerException
   *           If {@code oid} was {@code null}.
   */
  Control removeControl(String oid)
      throws UnsupportedOperationException, NullPointerException;

}
