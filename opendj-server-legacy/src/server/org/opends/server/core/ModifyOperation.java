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

import java.util.List;

import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;

/**
 * This interface defines an operation used to modify an entry in
 * the Directory Server.
 */
public interface ModifyOperation extends Operation
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
   * Retrieves the DN of the entry to modify.  This should not be called by
   * pre-parse plugins because the processed DN will not be available yet.
   * Instead, they should call the <CODE>getRawEntryDN</CODE> method.
   *
   * @return  The DN of the entry to modify, or <CODE>null</CODE> if the raw
   *          entry DN has not yet been processed.
   */
  DN getEntryDN();

  /**
   * Retrieves the set of raw, unprocessed modifications as included in the
   * client request.  Note that this may contain one or more invalid
   * modifications, as no validation will have been performed on this
   * information.  The list returned must not be altered by the caller.
   *
   * @return  The set of raw, unprocessed modifications as included in the
   *          client request.
   */
  List<RawModification> getRawModifications();

  /**
   * Adds the provided modification to the set of raw modifications for this
   * modify operation.  This must only be called by pre-parse plugins.
   *
   * @param  rawModification  The modification to add to the set of raw
   *                          modifications for this modify operation.
   */
  void addRawModification(RawModification rawModification);

  /**
   * Specifies the raw modifications for this modify operation.
   *
   * @param  rawModifications  The raw modifications for this modify operation.
   */
  void setRawModifications(List<RawModification> rawModifications);

  /**
   * Retrieves the set of modifications for this modify operation.  Its contents
   * should not be altered.  It will not be available to pre-parse plugins.
   *
   * @return  The set of modifications for this modify operation, or
   *          <CODE>null</CODE> if the modifications have not yet been
   *          processed.
   */
  List<Modification> getModifications();

  /**
   * Adds the provided modification to the set of modifications to this modify
   * operation.  This may only be called by pre-operation plugins.
   *
   * @param  modification  The modification to add to the set of changes for
   *                       this modify operation.
   *
   * @throws  DirectoryException  If an unexpected problem occurs while applying
   *                              the modification to the entry.
   */
  void addModification(Modification modification) throws DirectoryException;

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
