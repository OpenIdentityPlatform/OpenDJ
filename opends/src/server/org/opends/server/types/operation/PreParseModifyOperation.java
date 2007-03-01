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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types.operation;



import java.util.List;

import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.ByteString;



/**
 * This class defines a set of methods that are available for use by
 * pre-parse plugins for modify operations.  Note that this interface
 * is intended only to define an API for use by plugins and is not
 * intended to be implemented by any custom classes.
 */
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
  public ByteString getRawEntryDN();



  /**
   * Specifies the raw, unprocessed entry DN as included in the client
   * request.
   *
   * @param  rawEntryDN  The raw, unprocessed entry DN as included in
   *                     the client request.
   */
  public void setRawEntryDN(ByteString rawEntryDN);



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
  public List<LDAPModification> getRawModifications();



  /**
   * Adds the provided modification to the set of raw modifications
   * for this modify operation.
   *
   * @param  rawModification  The modification to add to the set of
   *                          raw modifications for this modify
   *                          operation.
   */
  public void addRawModification(LDAPModification rawModification);



  /**
   * Specifies the set of raw modifications for this modify operation.
   *
   * @param  rawModifications  The raw modifications for this modify
   *                           operation.
   */
  public void setRawModifications(
                   List<LDAPModification> rawModifications);
}

