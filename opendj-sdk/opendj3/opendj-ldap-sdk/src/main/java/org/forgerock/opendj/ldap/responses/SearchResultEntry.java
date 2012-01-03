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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;



import java.util.Collection;
import java.util.List;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;



/**
 * A Search Result Entry represents an entry found during a Search operation.
 * <p>
 * Each entry returned in a Search Result Entry will contain all appropriate
 * attributes as specified in the Search request, subject to access control and
 * other administrative policy.
 * <p>
 * Note that a Search Result Entry may hold zero attributes. This may happen
 * when none of the attributes of an entry were requested or could be returned.
 * <p>
 * Note also that each returned attribute may hold zero attribute values. This
 * may happen when only attribute types are requested, access controls prevent
 * the return of values, or other reasons.
 */
public interface SearchResultEntry extends Response, Entry
{
  /**
   * {@inheritDoc}
   */
  boolean addAttribute(Attribute attribute);



  /**
   * {@inheritDoc}
   */
  boolean addAttribute(Attribute attribute,
      Collection<ByteString> duplicateValues);



  /**
   * {@inheritDoc}
   */
  SearchResultEntry addAttribute(String attributeDescription, Object... values);



  /**
   * {@inheritDoc}
   */
  SearchResultEntry addControl(Control control);



  /**
   * {@inheritDoc}
   */
  SearchResultEntry clearAttributes();



  /**
   * {@inheritDoc}
   */
  boolean containsAttribute(Attribute attribute,
      Collection<ByteString> missingValues);



  /**
   * {@inheritDoc}
   */
  boolean containsAttribute(String attributeDescription, Object... values);



  /**
   * {@inheritDoc}
   */
  Iterable<Attribute> getAllAttributes();



  /**
   * {@inheritDoc}
   */
  Iterable<Attribute> getAllAttributes(AttributeDescription attributeDescription);



  /**
   * {@inheritDoc}
   */
  Iterable<Attribute> getAllAttributes(String attributeDescription);



  /**
   * {@inheritDoc}
   */
  Attribute getAttribute(AttributeDescription attributeDescription);



  /**
   * {@inheritDoc}
   */
  Attribute getAttribute(String attributeDescription);



  /**
   * {@inheritDoc}
   */
  int getAttributeCount();



  /**
   * {@inheritDoc}
   */
  <C extends Control> C getControl(ControlDecoder<C> decoder,
      DecodeOptions options) throws DecodeException;



  /**
   * {@inheritDoc}
   */
  List<Control> getControls();



  /**
   * {@inheritDoc}
   */
  DN getName();



  /**
   * {@inheritDoc}
   */
  boolean removeAttribute(Attribute attribute,
      Collection<ByteString> missingValues);



  /**
   * {@inheritDoc}
   */
  boolean removeAttribute(AttributeDescription attributeDescription);



  /**
   * {@inheritDoc}
   */
  SearchResultEntry removeAttribute(String attributeDescription,
      Object... values);



  /**
   * {@inheritDoc}
   */
  boolean replaceAttribute(Attribute attribute);



  /**
   * {@inheritDoc}
   */
  SearchResultEntry replaceAttribute(String attributeDescription,
      Object... values);



  /**
   * {@inheritDoc}
   */
  SearchResultEntry setName(DN dn);



  /**
   * {@inheritDoc}
   */
  SearchResultEntry setName(String dn);

}
