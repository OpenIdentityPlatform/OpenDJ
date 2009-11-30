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



import java.util.Collection;

import org.opends.sdk.Attribute;
import org.opends.sdk.AttributeDescription;
import org.opends.sdk.DN;
import org.opends.sdk.Entry;
import org.opends.sdk.controls.Control;
import org.opends.sdk.ldif.ChangeRecord;
import org.opends.sdk.ldif.ChangeRecordVisitor;
import org.opends.sdk.schema.ObjectClass;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.LocalizedIllegalArgumentException;



/**
 * The Add operation allows a client to request the addition of an entry
 * into the Directory.
 * <p>
 * The RDN attribute(s) may or may not be included in the Add request.
 * NO-USER-MODIFICATION attributes such as the {@code createTimestamp}
 * or {@code creatorsName} attributes must not be included, since the
 * server maintains these automatically.
 * <p>
 * FIXME: clean up methods, clearly define schema behavior.
 */
public interface AddRequest extends Request, ChangeRecord, Entry
{
  /**
   * {@inheritDoc}
   */
  <R, P> R accept(ChangeRecordVisitor<R, P> v, P p);



  /**
   * {@inheritDoc}
   */
  boolean addAttribute(Attribute attribute)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  boolean addAttribute(Attribute attribute,
      Collection<ByteString> duplicateValues)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  AddRequest addAttribute(String attributeDescription, Object... values)
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
  AddRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  AddRequest clearAttributes() throws UnsupportedOperationException;



  /**
   * Removes all the controls included with this request.
   *
   * @return This request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be removed.
   */
  AddRequest clearControls() throws UnsupportedOperationException;



  /**
   * {@inheritDoc}
   */
  boolean containsAttribute(AttributeDescription attributeDescription)
      throws NullPointerException;



  /**
   * {@inheritDoc}
   */
  boolean containsAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  boolean containsObjectClass(ObjectClass objectClass)
      throws NullPointerException;



  /**
   * {@inheritDoc}
   */
  boolean containsObjectClass(String objectClass)
      throws NullPointerException;



  /**
   * {@inheritDoc}
   */
  Iterable<Attribute> findAttributes(
      AttributeDescription attributeDescription)
      throws NullPointerException;



  /**
   * {@inheritDoc}
   */
  Iterable<Attribute> findAttributes(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  Attribute getAttribute(AttributeDescription attributeDescription)
      throws NullPointerException;



  /**
   * {@inheritDoc}
   */
  Attribute getAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  int getAttributeCount();



  /**
   * {@inheritDoc}
   */
  Iterable<Attribute> getAttributes();



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
   * {@inheritDoc}
   */
  DN getName();



  /**
   * {@inheritDoc}
   */
  Iterable<String> getObjectClasses();



  /**
   * Indicates whether or not this request has any controls.
   *
   * @return {@code true} if this request has any controls, otherwise
   *         {@code false}.
   */
  boolean hasControls();



  /**
   * {@inheritDoc}
   */
  boolean removeAttribute(Attribute attribute,
      Collection<ByteString> missingValues)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  boolean removeAttribute(AttributeDescription attributeDescription)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  AddRequest removeAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  AddRequest removeAttribute(String attributeDescription,
      Object... values) throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



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
   * {@inheritDoc}
   */
  boolean replaceAttribute(Attribute attribute)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  AddRequest replaceAttribute(String attributeDescription,
      Object... values) throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  AddRequest setName(DN dn) throws UnsupportedOperationException,
      NullPointerException;



  /**
   * {@inheritDoc}
   */
  AddRequest setName(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;

}
