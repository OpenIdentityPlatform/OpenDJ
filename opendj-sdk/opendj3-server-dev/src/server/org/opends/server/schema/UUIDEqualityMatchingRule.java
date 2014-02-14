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
package org.opends.server.schema;



import java.util.Collection;
import java.util.Collections;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;



/**
 * This class defines the uuidMatch matching rule defined in RFC 4530.  It will
 * be used as the default equality matching rule for the UUID syntax.
 */
class UUIDEqualityMatchingRule
       extends EqualityMatchingRule
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Creates a new instance of this caseExactMatch matching rule.
   */
  public UUIDEqualityMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getNames()
  {
    return Collections.singleton(EMR_UUID_NAME);
  }


  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_UUID_OID;
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
    return SYNTAX_UUID_OID;
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
    if (value.length() != 36)
    {
      return reportInvalidAttrSyntax(value,
          WARN_ATTR_SYNTAX_UUID_INVALID_LENGTH.get(value, value.length()));
    }

    StringBuilder builder = new StringBuilder(36);
    char c;
    for (int i=0; i < 36; i++)
    {
      // The 9th, 14th, 19th, and 24th characters must be dashes.  All others
      // must be hex.  Convert all uppercase hex characters to lowercase.
      c = (char)value.byteAt(i);
      switch (i)
      {
        case 8:
        case 13:
        case 18:
        case 23:
          if (c != '-')
          {
            return reportInvalidAttrSyntax(value,
                WARN_ATTR_SYNTAX_UUID_EXPECTED_DASH.get(value, i, c));
          }
          builder.append(c);
          break;
        default:
          switch (c)
          {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case 'a':
            case 'b':
            case 'c':
            case 'd':
            case 'e':
            case 'f':
              // These are all fine.
              builder.append(c);
              break;
            case 'A':
              builder.append('a');
              break;
            case 'B':
              builder.append('b');
              break;
            case 'C':
              builder.append('c');
              break;
            case 'D':
              builder.append('d');
              break;
            case 'E':
              builder.append('e');
              break;
            case 'F':
              builder.append('f');
              break;
            default:
            return reportInvalidAttrSyntax(value,
                WARN_ATTR_SYNTAX_UUID_EXPECTED_HEX.get(value, i, value.byteAt(i)));
          }
      }
    }

    return ByteString.valueOf(builder.toString());
  }

  private ByteString reportInvalidAttrSyntax(ByteSequence value, LocalizableMessage message)
      throws DirectoryException
  {
    switch (DirectoryServer.getSyntaxEnforcementPolicy())
    {
      case REJECT:
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
      case WARN:
        logger.error(message);
        return value.toByteString();
      default:
        return value.toByteString();
    }
  }
}

