/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.core;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Operation;

/**
 * This interface defines an operation that may be used to remove an entry from
 * the Directory Server.
 */
public interface DeleteOperation extends Operation
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
   * This should only be called by pre-parse plugins.  All other code that needs
   * to set the entry DN should use the <CODE>setEntryDN</CODE> method.
   *
   * @param  rawEntryDN  The raw, unprocessed entry DN as included in the client
   *                     request.
   */
  void setRawEntryDN(ByteString rawEntryDN);

  /**
   * Retrieves the DN of the entry to delete.  This should not be called by
   * pre-parse plugins because the processed DN will not be available yet.
   * Instead, they should call the <CODE>getRawEntryDN</CODE> method.
   *
   * @return  The DN of the entry to delete, or <CODE>null</CODE> if the raw
   *          entry DN has not yet been processed.
   */
  DN getEntryDN();

  /**
   * Retrieves the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @return  The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  DN getProxiedAuthorizationDN();

  /**
   * Set the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @param proxiedAuthorizationDN
   *          The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  void setProxiedAuthorizationDN(DN proxiedAuthorizationDN);


}
