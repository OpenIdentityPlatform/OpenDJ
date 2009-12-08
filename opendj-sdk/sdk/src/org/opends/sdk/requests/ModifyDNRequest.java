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



import org.opends.sdk.DN;
import org.opends.sdk.LocalizedIllegalArgumentException;
import org.opends.sdk.RDN;
import org.opends.sdk.controls.Control;
import org.opends.sdk.ldif.ChangeRecord;
import org.opends.sdk.ldif.ChangeRecordVisitor;




/**
 * The Modify DN operation allows a client to change the Relative
 * Distinguished Name (RDN) of an entry in the Directory and/or to move
 * a subtree of entries to a new location in the Directory.
 */
public interface ModifyDNRequest extends Request, ChangeRecord
{
  /**
   * {@inheritDoc}
   */
  <R, P> R accept(ChangeRecordVisitor<R, P> v, P p);



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
  ModifyDNRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Removes all the controls included with this request.
   * 
   * @return This request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be removed.
   */
  ModifyDNRequest clearControls() throws UnsupportedOperationException;



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
   * Returns the distinguished name of the entry to be renamed. This
   * entry may or may not have subordinate entries. The server shall not
   * dereference any aliases in locating the entry to be renamed.
   * 
   * @return The distinguished name of the entry.
   */
  DN getName();



  /**
   * Returns the new RDN of the entry to be renamed. The value of the
   * old RDN is supplied when moving the entry to a new superior without
   * changing its RDN. Attribute values of the new RDN not matching any
   * attribute value of the entry are added to the entry, and an
   * appropriate error is returned if this fails.
   * 
   * @return The new RDN of the entry.
   */
  RDN getNewRDN();



  /**
   * Returns the distinguished name of an existing entry that will
   * become the immediate superior (parent) of the entry to be renamed.
   * The server shall not dereference any aliases in locating the new
   * superior entry.
   * 
   * @return The distinguished name of the new superior entry, or
   *         {@code null} if the entry is to remain under the same
   *         parent entry.
   */
  DN getNewSuperior();



  /**
   * Indicates whether or not this request has any controls.
   * 
   * @return {@code true} if this request has any controls, otherwise
   *         {@code false}.
   */
  boolean hasControls();



  /**
   * Indicates whether the old RDN attribute values are to be retained
   * as attributes of the entry or deleted from the entry.
   * 
   * @return {@code true} if the old RDN attribute values are to be
   *         deleted from the entry, or {@code false} if they are to be
   *         retained.
   */
  boolean isDeleteOldRDN();



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
   * Specifies whether the old RDN attribute values are to be retained
   * as attributes of the entry or deleted from the entry.
   * 
   * @param deleteOldRDN
   *          {@code true} if the old RDN attribute values are to be
   *          deleted from the entry, or {@code false} if they are to be
   *          retained.
   * @return This modify DN request.
   * @throws UnsupportedOperationException
   *           If this modify DN request does not permit the delete old
   *           RDN parameter to be set.
   */
  ModifyDNRequest setDeleteOldRDN(boolean deleteOldRDN)
      throws UnsupportedOperationException;



  /**
   * Sets the distinguished name of the entry to be renamed. This entry
   * may or may not have subordinate entries. The server shall not
   * dereference any aliases in locating the entry to be renamed.
   * 
   * @param dn
   *          The distinguished name of the entry to be renamed.
   * @return This modify DN request.
   * @throws UnsupportedOperationException
   *           If this modify DN request does not permit the
   *           distinguished name to be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  ModifyDNRequest setName(DN dn) throws UnsupportedOperationException,
      NullPointerException;



  /**
   * Sets the distinguished name of the entry to be renamed. This entry
   * may or may not have subordinate entries. The server shall not
   * dereference any aliases in locating the entry to be renamed.
   * 
   * @param dn
   *          The distinguished name of the entry to be renamed.
   * @return This modify DN request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} could not be decoded using the default
   *           schema.
   * @throws UnsupportedOperationException
   *           If this modify DN request does not permit the
   *           distinguished name to be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  ModifyDNRequest setName(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * Sets the new RDN of the entry to be renamed. The value of the old
   * RDN is supplied when moving the entry to a new superior without
   * changing its RDN. Attribute values of the new RDN not matching any
   * attribute value of the entry are added to the entry, and an
   * appropriate error is returned if this fails.
   * 
   * @param rdn
   *          The new RDN of the entry to be renamed.
   * @return This modify DN request.
   * @throws UnsupportedOperationException
   *           If this modify DN request does not permit the new RDN to
   *           be set.
   * @throws NullPointerException
   *           If {@code rdn} was {@code null}.
   */
  ModifyDNRequest setNewRDN(RDN rdn)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the new RDN of the entry to be renamed. The value of the old
   * RDN is supplied when moving the entry to a new superior without
   * changing its RDN. Attribute values of the new RDN not matching any
   * attribute value of the entry are added to the entry, and an
   * appropriate error is returned if this fails.
   * 
   * @param rdn
   *          The new RDN of the entry to be renamed.
   * @return This modify DN request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code rdn} could not be decoded using the default
   *           schema.
   * @throws UnsupportedOperationException
   *           If this modify DN request does not permit the new RDN to
   *           be set.
   * @throws NullPointerException
   *           If {@code rdn} was {@code null}.
   */
  ModifyDNRequest setNewRDN(String rdn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * Sets the distinguished name of an existing entry that will become
   * the immediate superior (parent) of the entry to be renamed. The
   * server shall not dereference any aliases in locating the new
   * superior entry.
   * 
   * @param dn
   *          The distinguished name of an existing entry that will
   *          become the immediate superior (parent) of the entry to be
   *          renamed, may be {@code null}.
   * @return This modify DN request.
   * @throws UnsupportedOperationException
   *           If this modify DN request does not permit the new
   *           superior to be set.
   */
  ModifyDNRequest setNewSuperior(DN dn)
      throws UnsupportedOperationException;



  /**
   * Sets the distinguished name of an existing entry that will become
   * the immediate superior (parent) of the entry to be renamed. The
   * server shall not dereference any aliases in locating the new
   * superior entry.
   * 
   * @param dn
   *          The distinguished name of an existing entry that will
   *          become the immediate superior (parent) of the entry to be
   *          renamed, may be {@code null}.
   * @return This modify DN request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} could not be decoded using the default
   *           schema.
   * @throws UnsupportedOperationException
   *           If this modify DN request does not permit the new
   *           superior to be set.
   */
  ModifyDNRequest setNewSuperior(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException;

}
