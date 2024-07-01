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
import org.opends.server.types.RawAttribute;


/**
 * This class defines a set of methods that are available for use by
 * pre-parse plugins for add operations.  Note that this interface is
 * intended only to define an API for use by plugins and is not
 * intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PreParseAddOperation
       extends PreParseOperation
{
  /**
   * Retrieves the DN of the entry to add in a raw, unparsed form as
   * it was included in the request.  This may or may not actually
   * contain a valid DN, since no validation will have been performed
   * on it.
   *
   * @return  The DN of the entry in a raw, unparsed form.
   */
  ByteString getRawEntryDN();



  /**
   * Specifies the raw entry DN for the entry to add.
   *
   * @param  rawEntryDN  The raw entry DN for the entry to add.
   */
  void setRawEntryDN(ByteString rawEntryDN);



  /**
   * Retrieves the set of attributes in their raw, unparsed form as
   * read from the client request.  Some of these attributes may be
   * invalid as no validation will have been performed on them.  The
   * returned list must not be altered by the caller.
   *
   * @return  The set of attributes in their raw, unparsed form as
   *          read from the client request.
   */
  List<RawAttribute> getRawAttributes();



  /**
   * Adds the provided attribute to the set of raw attributes for this
   * add operation.
   *
   * @param  rawAttribute  The attribute to add to the set of raw
   *                       attributes for this add operation.
   */
  void addRawAttribute(RawAttribute rawAttribute);



  /**
   * Replaces the set of raw attributes for this add operation.
   *
   * @param  rawAttributes  The set of raw attributes for this add
   *                        operation.
   */
  void setRawAttributes(List<RawAttribute> rawAttributes);
}

