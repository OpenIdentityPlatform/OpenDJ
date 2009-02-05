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



import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.Collections;

import org.opends.messages.Message;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;



/**
 * This class implements the numericStringMatch matching rule defined in X.520
 * and referenced in RFC 2252.  It allows for values with numeric digits and
 * spaces, but ignores spaces when performing matching.
 */
class NumericStringEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * Creates a new instance of this caseExactMatch matching rule.
   */
  public NumericStringEqualityMatchingRule()
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
    return EMR_NUMERIC_STRING_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_NUMERIC_STRING_OID;
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
    return SYNTAX_NUMERIC_STRING_OID;
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
    String        valueString = value.toString();
    int           valueLength = valueString.length();
    StringBuilder valueBuffer = new StringBuilder(valueLength);

    boolean logged = false;
    for (int i=0; i < valueLength; i++)
    {
      char c = valueString.charAt(i);
      if (isDigit(c))
      {
        valueBuffer.append(c);
      }
      else if (c != ' ')
      {
        // This is an illegal character.  Either log it or reject it.

        Message message = WARN_ATTR_SYNTAX_NUMERIC_STRING_ILLEGAL_CHAR.get(
                valueString, String.valueOf(c), i);

        switch (DirectoryServer.getSyntaxEnforcementPolicy())
        {
          case REJECT:
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          case WARN:
            if (! logged)
            {
              ErrorLogger.logError(message);
              logged = true;
            }
        }
      }
    }

    return ByteString.valueOf(valueBuffer.toString());
  }
}

