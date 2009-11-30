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



import org.opends.sdk.Change;
import org.opends.sdk.DN;
import org.opends.sdk.ModificationType;
import org.opends.sdk.controls.Control;
import org.opends.sdk.ldif.ChangeRecord;
import org.opends.sdk.ldif.ChangeRecordVisitor;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.LocalizedIllegalArgumentException;



/**
 * The Modify operation allows a client to request that a modification
 * of an entry be performed on its behalf by a server.
 */
public interface ModifyRequest extends Request, ChangeRecord
{
  /**
   * {@inheritDoc}
   */
  <R, P> R accept(ChangeRecordVisitor<R, P> v, P p);



  /**
   * Appends the provided change to the list of changes included with
   * this modify request.
   * 
   * @param change
   *          The change to be performed.
   * @return This modify request.
   * @throws UnsupportedOperationException
   *           If this modify request does not permit changes to be
   *           added.
   * @throws NullPointerException
   *           If {@code change} was {@code null}.
   */
  ModifyRequest addChange(Change change)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Appends the provided change to the list of changes included with
   * this modify request.
   * <p>
   * If the attribute value is not an instance of {@code ByteString}
   * then it will be converted using the
   * {@link ByteString#valueOf(Object)} method.
   * 
   * @param type
   *          The type of change to be performed.
   * @param attributeDescription
   *          The name of the attribute to be modified.
   * @param values
   *          The attribute values to be modified.
   * @return This modify request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} could not be decoded
   *           using the default schema.
   * @throws UnsupportedOperationException
   *           If this modify request does not permit changes to be
   *           added.
   * @throws NullPointerException
   *           If {@code type}, {@code attributeDescription}, or {@code
   *           value} was {@code null}.
   */
  ModifyRequest addChange(ModificationType type,
      String attributeDescription, Object... values)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * Adds the provided control to this request.
   * 
   * @param control
   *          The control to be added to this request.
   * @return This request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be added.
   * @throws NullPointerException
   *           If {@code control} was {@code null}.
   */
  ModifyRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Removes all the changes included with this modify request.
   * 
   * @return This modify request.
   * @throws UnsupportedOperationException
   *           If this modify request does not permit changes to be
   *           removed.
   */
  ModifyRequest clearChanges() throws UnsupportedOperationException;



  /**
   * Removes all the controls included with this request.
   * 
   * @return This request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be removed.
   */
  ModifyRequest clearControls() throws UnsupportedOperationException;



  /**
   * Returns the number of changes included with this modify request.
   * 
   * @return The number of changes.
   */
  int getChangeCount();



  /**
   * Returns an {@code Iterable} containing the changes included with
   * this modify request. The returned {@code Iterable} may be used to
   * remove changes if permitted by this modify request.
   * 
   * @return An {@code Iterable} containing the changes.
   */
  Iterable<Change> getChanges();



  /**
   * Returns the first control contained in this request having the
   * specified OID.
   * 
   * @param oid
   *          The OID of the control to be returned.
   * @return The control, or {@code null} if the control is not included
   *         with this request.
   * @throws NullPointerException
   *           If {@code oid} was {@code null}.
   */
  Control getControl(String oid) throws NullPointerException;



  /**
   * Returns an {@code Iterable} containing the controls included with
   * this request. The returned {@code Iterable} may be used to remove
   * controls if permitted by this request.
   * 
   * @return An {@code Iterable} containing the controls.
   */
  Iterable<Control> getControls();



  /**
   * Returns the distinguished name of the entry to be modified. The
   * server shall not perform any alias dereferencing in determining the
   * object to be modified.
   * 
   * @return The distinguished name of the entry to be modified.
   */
  DN getName();



  /**
   * Indicates whether or not this modify request has any changes.
   * 
   * @return {@code true} if this modify request has any changes,
   *         otherwise {@code false}.
   */
  boolean hasChanges();



  /**
   * Indicates whether or not this request has any controls.
   * 
   * @return {@code true} if this request has any controls, otherwise
   *         {@code false}.
   */
  boolean hasControls();



  /**
   * Removes the first control contained in this request having the
   * specified OID.
   * 
   * @param oid
   *          The OID of the control to be removed.
   * @return The removed control, or {@code null} if the control is not
   *         included with this request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be removed.
   * @throws NullPointerException
   *           If {@code oid} was {@code null}.
   */
  Control removeControl(String oid)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the distinguished name of the entry to be modified. The server
   * shall not perform any alias dereferencing in determining the object
   * to be modified.
   * 
   * @param dn
   *          The the distinguished name of the entry to be modified.
   * @return This modify request.
   * @throws UnsupportedOperationException
   *           If this modify request does not permit the distinguished
   *           name to be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  ModifyRequest setName(DN dn) throws UnsupportedOperationException,
      NullPointerException;



  /**
   * Sets the distinguished name of the entry to be modified. The server
   * shall not perform any alias dereferencing in determining the object
   * to be modified.
   * 
   * @param dn
   *          The the distinguished name of the entry to be modified.
   * @return This modify request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} could not be decoded using the default
   *           schema.
   * @throws UnsupportedOperationException
   *           If this modify request does not permit the distinguished
   *           name to be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  ModifyRequest setName(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;

}
