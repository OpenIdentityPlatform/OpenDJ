/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Operation;

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
  ByteString getRawEntryDN();


  /**
   * Specifies the raw, unprocessed entry DN as included in the client request.
   * This should only be called by pre-parse plugins.
   *
   * @param  rawEntryDN  The raw, unprocessed entry DN as included in the client
   *                     request.
   */
  void setRawEntryDN(ByteString rawEntryDN);


  /**
   * Retrieves the DN of the entry to compare.  This should not be called by
   * pre-parse plugins because the processed DN will not be available yet.
   * Instead, they should call the <CODE>getRawEntryDN</CODE> method.
   *
   * @return  The DN of the entry to compare, or <CODE>null</CODE> if the raw
   *          entry DN has not yet been processed.
   */
  DN getEntryDN();


  /**
   * Retrieves the raw attribute type for this compare operation.
   *
   * @return  The raw attribute type for this compare operation.
   */
  String getRawAttributeType();


  /**
   * Specifies the raw attribute type for this compare operation.  This should
   * only be called by pre-parse plugins.
   *
   * @param  rawAttributeType  The raw attribute type for this compare
   *                           operation.
   */
  void setRawAttributeType(String rawAttributeType);

  /**
   * Retrieves the attribute description for this compare operation. This should not be called by
   * pre-parse plugins because the processed attribute description will not be available yet.
   *
   * @return The attribute description for this compare operation.
   */
  AttributeDescription getAttributeDescription();

  /**
   * Retrieves the assertion value for this compare operation.
   *
   * @return  The assertion value for this compare operation.
   */
  ByteString getAssertionValue();


  /**
   * Specifies the assertion value for this compare operation.  This should only
   * be called by pre-parse and pre-operation plugins.
   *
   * @param  assertionValue  The assertion value for this compare operation.
   */
  void setAssertionValue(ByteString assertionValue);


  /**
   * Retrieves the proxied authorization target DN for this compare operation.
   *
   * @return  The proxied authorization target DN for this compare operation
   */
  @Override
  DN getProxiedAuthorizationDN();


  /**
   * Specifies the proxied authorization target DN for this compare operation.
   *
   * @param proxiedAuthorizationDN  The proxied authorization target DN for
   *                                this compare operation
   */
  @Override
  void setProxiedAuthorizationDN(DN proxiedAuthorizationDN);

}
