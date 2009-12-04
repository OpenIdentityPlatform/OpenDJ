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



import java.util.Collection;

import org.opends.sdk.*;
import org.opends.sdk.controls.Control;
import org.opends.sdk.schema.ObjectClass;

import com.sun.opends.sdk.util.LocalizedIllegalArgumentException;



/**
 * A Search Result Entry represents an entry found during a Search
 * operation.
 * <p>
 * Each entry returned in a Search Result Entry will contain all
 * appropriate attributes as specified in the Search request, subject to
 * access control and other administrative policy.
 * <p>
 * Note that a Search Result Entry may hold zero attributes. This may
 * happen when none of the attributes of an entry were requested or
 * could be returned.
 * <p>
 * Note also that each returned attribute may hold zero attribute
 * values. This may happen when only attribute types are requested,
 * access controls prevent the return of values, or other reasons.
 */
public interface SearchResultEntry extends Response, Entry
{
  boolean addAttribute(Attribute attribute)
      throws UnsupportedOperationException, NullPointerException;



  boolean addAttribute(Attribute attribute,
      Collection<ByteString> duplicateValues)
      throws UnsupportedOperationException, NullPointerException;



  SearchResultEntry addAttribute(String attributeDescription,
      Object... values) throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  SearchResultEntry addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  SearchResultEntry clearAttributes()
      throws UnsupportedOperationException;



  /**
   * {@inheritDoc}
   */
  SearchResultEntry clearControls()
      throws UnsupportedOperationException;



  boolean containsAttribute(AttributeDescription attributeDescription)
      throws NullPointerException;



  boolean containsAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException;



  boolean containsObjectClass(ObjectClass objectClass)
      throws NullPointerException;



  boolean containsObjectClass(String objectClass)
      throws NullPointerException;



  Iterable<Attribute> findAttributes(
      AttributeDescription attributeDescription)
      throws NullPointerException;



  Iterable<Attribute> findAttributes(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException;



  Attribute getAttribute(AttributeDescription attributeDescription)
      throws NullPointerException;



  Attribute getAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException;



  int getAttributeCount();



  Iterable<Attribute> getAttributes();



  /**
   * {@inheritDoc}
   */
  Control getControl(String oid) throws NullPointerException;



  /**
   * {@inheritDoc}
   */
  Iterable<Control> getControls();



  DN getName();



  Iterable<String> getObjectClasses();



  /**
   * {@inheritDoc}
   */
  boolean hasControls();



  boolean removeAttribute(Attribute attribute,
      Collection<ByteString> missingValues)
      throws UnsupportedOperationException, NullPointerException;



  boolean removeAttribute(AttributeDescription attributeDescription)
      throws UnsupportedOperationException, NullPointerException;



  SearchResultEntry removeAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  SearchResultEntry removeAttribute(String attributeDescription,
      Object... values) throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * {@inheritDoc}
   */
  Control removeControl(String oid)
      throws UnsupportedOperationException, NullPointerException;



  boolean replaceAttribute(Attribute attribute)
      throws UnsupportedOperationException, NullPointerException;



  SearchResultEntry replaceAttribute(String attributeDescription,
      Object... values) throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  SearchResultEntry setName(DN dn)
      throws UnsupportedOperationException, NullPointerException;



  SearchResultEntry setName(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;

}
