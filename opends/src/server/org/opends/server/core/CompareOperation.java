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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;

import org.opends.server.types.*;


/**
 * This interface defines an operation that may be used to determine whether a
 * specified entry in the Directory Server contains a given attribute-value
 * pair.
 */
public interface CompareOperation extends Operation
{
  /**
   * Retrieves the raw, unprocessed entry DN as included in the client request.
   * The DN that is returned may or may not be a valid DN, since no validation
   * will have been performed upon it.
   *
   * @return  The raw, unprocessed entry DN as included in the client request.
   */
  public ByteString getRawEntryDN();


  /**
   * Specifies the raw, unprocessed entry DN as included in the client request.
   * This should only be called by pre-parse plugins.
   *
   * @param  rawEntryDN  The raw, unprocessed entry DN as included in the client
   *                     request.
   */
  public void setRawEntryDN(ByteString rawEntryDN);


  /**
   * Retrieves the DN of the entry to compare.  This should not be called by
   * pre-parse plugins because the processed DN will not be available yet.
   * Instead, they should call the <CODE>getRawEntryDN</CODE> method.
   *
   * @return  The DN of the entry to compare, or <CODE>null</CODE> if the raw
   *          entry DN has not yet been processed.
   */
  public DN getEntryDN();


  /**
   * Retrieves the raw attribute type for this compare operation.
   *
   * @return  The raw attribute type for this compare operation.
   */
  public String getRawAttributeType();


  /**
   * Specifies the raw attribute type for this compare operation.  This should
   * only be called by pre-parse plugins.
   *
   * @param  rawAttributeType  The raw attribute type for this compare
   *                           operation.
   */
  public void setRawAttributeType(String rawAttributeType);


  /**
   * Retrieves the attribute type for this compare operation.  This should not
   * be called by pre-parse plugins because the processed attribute type will
   * not be available yet.
   *
   * @return  The attribute type for this compare operation.
   */
  public AttributeType getAttributeType();


  /**
   * Specifies the attribute type for this compare operation.
   *
   * @param attributeType  The attribute type for this compare operation.
   */
  public void setAttributeType(AttributeType attributeType);


  /**
   * Retrieves the assertion value for this compare operation.
   *
   * @return  The assertion value for this compare operation.
   */
  public ByteString getAssertionValue();


  /**
   * Specifies the assertion value for this compare operation.  This should only
   * be called by pre-parse and pre-operation plugins.
   *
   * @param  assertionValue  The assertion value for this compare operation.
   */
  public void setAssertionValue(ByteString assertionValue);


  /**
   * Retrieves the proxied authorization target DN for this compare operation.
   *
   * @return  The proxied authorization target DN for this compare operation
   */
  public DN getProxiedAuthorizationDN();


  /**
   * Specifies the proxied authorization target DN for this compare operation.
   *
   * @param proxiedAuthorizationDN  The proxied authorization target DN for
   *                                this compare operation
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN);

}
