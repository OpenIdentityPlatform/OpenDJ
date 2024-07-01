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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.List;

import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;

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
  @Override
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
  @Override
  void setProxiedAuthorizationDN(DN proxiedAuthorizationDN);

}
