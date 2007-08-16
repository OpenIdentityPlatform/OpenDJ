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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types.operation;



import java.util.List;

import org.opends.server.types.ByteString;
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
  public ByteString getRawEntryDN();



  /**
   * Specifies the raw entry DN for the entry to add.
   *
   * @param  rawEntryDN  The raw entry DN for the entry to add.
   */
  public void setRawEntryDN(ByteString rawEntryDN);



  /**
   * Retrieves the set of attributes in their raw, unparsed form as
   * read from the client request.  Some of these attributes may be
   * invalid as no validation will have been performed on them.  The
   * returned list must not be altered by the caller.
   *
   * @return  The set of attributes in their raw, unparsed form as
   *          read from the client request.
   */
  public List<RawAttribute> getRawAttributes();



  /**
   * Adds the provided attribute to the set of raw attributes for this
   * add operation.
   *
   * @param  rawAttribute  The attribute to add to the set of raw
   *                       attributes for this add operation.
   */
  public void addRawAttribute(RawAttribute rawAttribute);



  /**
   * Replaces the set of raw attributes for this add operation.
   *
   * @param  rawAttributes  The set of raw attributes for this add
   *                        operation.
   */
  public void setRawAttributes(List<RawAttribute> rawAttributes);
}

