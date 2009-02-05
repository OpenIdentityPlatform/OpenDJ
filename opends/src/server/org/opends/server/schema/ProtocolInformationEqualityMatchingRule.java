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
package org.opends.server.schema;



import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.Collections;

import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.util.ServerConstants;



/**
 * This class implements the protocolInformationMatch matching rule defined in
 * X.520 and referenced in RFC 2252.  However, since this matching rule and the
 * associated syntax have been deprecated, this matching rule behaves exactly
 * like the caseIgnoreMatch rule.
 */
class ProtocolInformationEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * Creates a new instance of this protocolInformationMatch matching rule.
   */
  public ProtocolInformationEqualityMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getAllNames()
  {
    return Collections.singleton(getName());
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  @Override
  public String getName()
  {
    return EMR_PROTOCOL_INFORMATION_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_PROTOCOL_INFORMATION_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  @Override
  public String getDescription()
  {
    // There is no standard description for this matching rule.
    return null;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  @Override
  public String getSyntaxOID()
  {
    return SYNTAX_PROTOCOL_INFORMATION_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DirectoryException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeValue(ByteSequence value)
         throws DirectoryException
  {
    StringBuilder buffer = new StringBuilder();
    toLowerCase(value, buffer, true);

    int bufferLength = buffer.length();
    if (bufferLength == 0)
    {
      if (value.length() > 0)
      {
        // This should only happen if the value is composed entirely of spaces.
        // In that case, the normalized value is a single space.
        return ServerConstants.SINGLE_SPACE_VALUE;
      }
      else
      {
        // The value is empty, so it is already normalized.
        return ByteString.empty();
      }
    }


    // Replace any consecutive spaces with a single space.
    for (int pos = bufferLength-1; pos > 0; pos--)
    {
      if (buffer.charAt(pos) == ' ')
      {
        if (buffer.charAt(pos-1) == ' ')
        {
          buffer.delete(pos, pos+1);
        }
      }
    }

    return ByteString.valueOf(buffer.toString());
  }
}

