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
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class implements the uniqueMemberMatch matching rule defined in X.520
 * and referenced in RFC 2252.  It is based on the name and optional UID syntax,
 * and will compare values with a distinguished name and optional bit string
 * suffix.
 */
class UniqueMemberEqualityMatchingRule
       extends EqualityMatchingRule
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /**
   * Creates a new instance of this uniqueMemberMatch matching rule.
   */
  public UniqueMemberEqualityMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getNames()
  {
    return Collections.singleton(EMR_UNIQUE_MEMBER_NAME);
  }


  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_UNIQUE_MEMBER_OID;
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
    return SYNTAX_NAME_AND_OPTIONAL_UID_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DecodeException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeAttributeValue(ByteSequence value)
         throws DecodeException
  {
    String valueString = value.toString().trim();
    int    valueLength = valueString.length();


    // See if the value contains the "optional uid" portion.  If we think it
    // does, then mark its location.
    int dnEndPos = valueLength;
    int sharpPos = -1;
    if (valueString.endsWith("'B") || valueString.endsWith("'b"))
    {
      sharpPos = valueString.lastIndexOf("#'");
      if (sharpPos > 0)
      {
        dnEndPos = sharpPos;
      }
    }


    // Take the DN portion of the string and try to normalize it.  If it fails,
    // then this will throw an exception.
    StringBuilder valueBuffer = new StringBuilder(valueLength);
    try
    {
      DN dn = DN.valueOf(valueString.substring(0, dnEndPos));
      dn.toNormalizedString(valueBuffer);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      // We couldn't normalize the DN for some reason.  If we're supposed to use
      // strict syntax enforcement, then throw an exception.  Otherwise, log a
      // message and just try our best.
      LocalizableMessage message = ERR_ATTR_SYNTAX_NAMEANDUID_INVALID_DN.get(
              valueString, getExceptionMessage(e));

      switch (DirectoryServer.getSyntaxEnforcementPolicy())
      {
        case REJECT:
          throw DecodeException.error(message);
        case WARN:
          logger.error(message);
          break;
      }
      valueBuffer.append(toLowerCase(valueString).substring(0, dnEndPos));
    }



    // If there is an "optional uid", then normalize it and make sure it only
    // contains valid binary digits.
    if (sharpPos > 0)
    {
      valueBuffer.append("#'");

      int     endPos = valueLength - 2;
      boolean logged = false;
      for (int i=sharpPos+2; i < endPos; i++)
      {
        char c = valueString.charAt(i);
        if ((c == '0') || (c == '1'))
        {
          valueBuffer.append(c);
        }
        else
        {
          // There was an invalid binary digit.  We'll either throw an exception
          // or log a message and continue, based on the server's configuration.
          LocalizableMessage message = ERR_ATTR_SYNTAX_NAMEANDUID_ILLEGAL_BINARY_DIGIT.get(valueString, c, i);

          switch (DirectoryServer.getSyntaxEnforcementPolicy())
          {
            case REJECT:
              throw DecodeException.error(message);
            case WARN:
              if (! logged)
              {
                logger.error(message);
                logged = true;
              }
              break;
          }
        }
      }

      valueBuffer.append("'B");
    }

    return ByteString.valueOf(valueBuffer.toString());
  }
}

