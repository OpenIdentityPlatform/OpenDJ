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
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.types.operation;



import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.RawModification;


/**
 * This class defines a set of methods that are available for use by
 * pre-parse plugins for modify operations.  Note that this interface
 * is intended only to define an API for use by plugins and is not
 * intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PreParseModifyOperation
       extends PreParseOperation
{
  /**
   * Retrieves the raw, unprocessed entry DN as included in the client
   * request.  The DN that is returned may or may not be a valid DN,
   * since no validation will have been performed upon it.
   *
   * @return  The raw, unprocessed entry DN as included in the client
   *          request.
   */
  ByteString getRawEntryDN();



  /**
   * Specifies the raw, unprocessed entry DN as included in the client
   * request.
   *
   * @param  rawEntryDN  The raw, unprocessed entry DN as included in
   *                     the client request.
   */
  void setRawEntryDN(ByteString rawEntryDN);



  /**
   * Retrieves the set of raw, unprocessed modifications as included
   * in the client request.  Note that this may contain one or more
   * invalid modifications, as no validation will have been performed
   * on this information.  The list returned must not be altered by
   * the caller.
   *
   * @return  The set of raw, unprocessed modifications as included
   *          in the client request.
   */
  List<RawModification> getRawModifications();



  /**
   * Adds the provided modification to the set of raw modifications
   * for this modify operation.
   *
   * @param  rawModification  The modification to add to the set of
   *                          raw modifications for this modify
   *                          operation.
   */
  void addRawModification(RawModification rawModification);



  /**
   * Specifies the set of raw modifications for this modify operation.
   *
   * @param  rawModifications  The raw modifications for this modify
   *                           operation.
   */
  void setRawModifications(List<RawModification> rawModifications);
}
