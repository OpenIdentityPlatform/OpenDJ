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

import org.opends.sdk.*;
import org.opends.sdk.controls.Control;
import org.opends.sdk.controls.ControlDecoder;
import org.opends.sdk.ldif.ChangeRecord;
import org.opends.sdk.ldif.ChangeRecordVisitor;



/**
 * The Modify operation allows a client to request that a modification of an
 * entry be performed on its behalf by a server.
 */
public interface ModifyRequest extends Request, ChangeRecord
{
  /**
   * {@inheritDoc}
   */
  <R, P> R accept(ChangeRecordVisitor<R, P> v, P p);



  /**
   * {@inheritDoc}
   */
  ModifyRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Appends the provided modification to the list of modifications included
   * with this modify request.
   *
   * @param modification
   *          The modification to be performed.
   * @return This modify request.
   * @throws UnsupportedOperationException
   *           If this modify request does not permit modifications to be added.
   * @throws NullPointerException
   *           If {@code modification} was {@code null}.
   */
  ModifyRequest addModification(Modification modification)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Appends the provided modification to the list of modifications included
   * with this modify request.
   * <p>
   * If the attribute value is not an instance of {@code ByteString} then it
   * will be converted using the {@link ByteString#valueOf(Object)} method.
   *
   * @param type
   *          The type of modification to be performed.
   * @param attributeDescription
   *          The name of the attribute to be modified.
   * @param values
   *          The attribute values to be modified.
   * @return This modify request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} could not be decoded using the
   *           default schema.
   * @throws UnsupportedOperationException
   *           If this modify request does not permit modifications to be added.
   * @throws NullPointerException
   *           If {@code type}, {@code attributeDescription}, or {@code value}
   *           was {@code null}.
   */
  ModifyRequest addModification(ModificationType type,
      String attributeDescription, Object... values)
      throws LocalizedIllegalArgumentException, UnsupportedOperationException,
      NullPointerException;



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
   * Returns a {@code List} containing the modifications included with this
   * modify request. The returned {@code List} may be modified if permitted by
   * this modify request.
   *
   * @return A {@code List} containing the modifications.
   */
  List<Modification> getModifications();



  /**
   * Returns the distinguished name of the entry to be modified. The server
   * shall not perform any alias dereferencing in determining the object to be
   * modified.
   *
   * @return The distinguished name of the entry to be modified.
   */
  DN getName();



  /**
   * Sets the distinguished name of the entry to be modified. The server shall
   * not perform any alias dereferencing in determining the object to be
   * modified.
   *
   * @param dn
   *          The the distinguished name of the entry to be modified.
   * @return This modify request.
   * @throws UnsupportedOperationException
   *           If this modify request does not permit the distinguished name to
   *           be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  ModifyRequest setName(DN dn) throws UnsupportedOperationException,
      NullPointerException;



  /**
   * Sets the distinguished name of the entry to be modified. The server shall
   * not perform any alias dereferencing in determining the object to be
   * modified.
   *
   * @param dn
   *          The the distinguished name of the entry to be modified.
   * @return This modify request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} could not be decoded using the default schema.
   * @throws UnsupportedOperationException
   *           If this modify request does not permit the distinguished name to
   *           be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  ModifyRequest setName(String dn) throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;

}
