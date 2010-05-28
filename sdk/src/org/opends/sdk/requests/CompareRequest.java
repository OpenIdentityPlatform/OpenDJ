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



/**
 * The Compare operation allows a client to compare an assertion value with the
 * values of a particular attribute in a particular entry in the Directory.
 * <p>
 * Note that some directory systems may establish access controls that permit
 * the values of certain attributes (such as {@code userPassword} ) to be
 * compared but not interrogated by other means.
 */
public interface CompareRequest extends Request
{
  /**
   * {@inheritDoc}
   */
  CompareRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Returns the assertion value to be compared.
   *
   * @return The assertion value.
   */
  ByteString getAssertionValue();



  /**
   * Returns the assertion value to be compared decoded as a UTF-8 string.
   *
   * @return The assertion value decoded as a UTF-8 string.
   */
  String getAssertionValueAsString();



  /**
   * Returns the name of the attribute to be compared.
   *
   * @return The name of the attribute.
   */
  AttributeDescription getAttributeDescription();



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
   * Returns the distinguished name of the entry to be compared. The server
   * shall not dereference any aliases in locating the entry to be compared.
   *
   * @return The distinguished name of the entry.
   */
  DN getName();



  /**
   * Sets the assertion value to be compared.
   *
   * @param value
   *          The assertion value to be compared.
   * @return This compare request.
   * @throws UnsupportedOperationException
   *           If this compare request does not permit the assertion value to be
   *           set.
   * @throws NullPointerException
   *           If {@code value} was {@code null}.
   */
  CompareRequest setAssertionValue(ByteString value)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the assertion value to be compared.
   * <p>
   * If the assertion value is not an instance of {@code ByteString} then it
   * will be converted using the {@link ByteString#valueOf(Object)} method.
   *
   * @param value
   *          The assertion value to be compared.
   * @return This compare request.
   * @throws UnsupportedOperationException
   *           If this compare request does not permit the assertion value to be
   *           set.
   * @throws NullPointerException
   *           If {@code value} was {@code null}.
   */
  CompareRequest setAssertionValue(Object value)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the name of the attribute to be compared.
   *
   * @param attributeDescription
   *          The name of the attribute to be compared.
   * @return This compare request.
   * @throws UnsupportedOperationException
   *           If this compare request does not permit the attribute description
   *           to be set.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  CompareRequest setAttributeDescription(
      AttributeDescription attributeDescription)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the name of the attribute to be compared.
   *
   * @param attributeDescription
   *          The name of the attribute to be compared.
   * @return This compare request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} could not be decoded using the
   *           default schema.
   * @throws UnsupportedOperationException
   *           If this compare request does not permit the attribute description
   *           to be set.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  CompareRequest setAttributeDescription(String attributeDescription)
      throws LocalizedIllegalArgumentException, UnsupportedOperationException,
      NullPointerException;



  /**
   * Sets the distinguished name of the entry to be compared. The server shall
   * not dereference any aliases in locating the entry to be compared.
   *
   * @param dn
   *          The distinguished name of the entry to be compared.
   * @return This compare request.
   * @throws UnsupportedOperationException
   *           If this compare request does not permit the distinguished name to
   *           be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  CompareRequest setName(DN dn) throws UnsupportedOperationException,
      NullPointerException;



  /**
   * Sets the distinguished name of the entry to be compared. The server shall
   * not dereference any aliases in locating the entry to be compared.
   *
   * @param dn
   *          The distinguished name of the entry to be compared.
   * @return This compare request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} could not be decoded using the default schema.
   * @throws UnsupportedOperationException
   *           If this compare request does not permit the distinguished name to
   *           be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  CompareRequest setName(String dn) throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;

}
