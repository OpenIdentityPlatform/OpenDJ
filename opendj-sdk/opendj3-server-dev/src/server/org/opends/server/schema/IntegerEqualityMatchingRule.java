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
 * This class defines the integerMatch matching rule defined in X.520 and
 * referenced in RFC 2252.
 */
class IntegerEqualityMatchingRule
       extends EqualityMatchingRule
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Creates a new instance of this integerMatch matching rule.
   */
  public IntegerEqualityMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getNames()
  {
    return Collections.singleton(EMR_INTEGER_NAME);
  }


  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_INTEGER_OID;
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
    return SYNTAX_INTEGER_OID;
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
  public ByteString normalizeAttributeValue(ByteSequence value)
         throws DirectoryException
  {
    int length = value.length();
    StringBuilder buffer = new StringBuilder(length);

    boolean logged = false;
    for (int i=0; i < length; i++)
    {
      switch (value.byteAt(i))
      {
        case '0':
          switch (buffer.length())
          {
            case 0:
              // This is only OK if the value is zero
              if (i == (length-1))
              {
                buffer.append("0");
              }
              else
              {
                LocalizableMessage message = WARN_ATTR_SYNTAX_INTEGER_INITIAL_ZERO.get(value);

                switch (DirectoryServer.getSyntaxEnforcementPolicy())
                {
                  case REJECT:
                    throw new DirectoryException(
                                  ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
                  case WARN:
                    if (! logged)
                    {
                      logged = true;
                      logger.error(message);
                    }
                    break;
                }
              }
              break;
            case 1:
              // This is OK as long as the first character isn't a dash.
              if (buffer.charAt(0) == '-')
              {
                LocalizableMessage message = WARN_ATTR_SYNTAX_INTEGER_INITIAL_ZERO.get(value);

                switch (DirectoryServer.getSyntaxEnforcementPolicy())
                {
                  case REJECT:
                    throw new DirectoryException(
                                  ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
                  case WARN:
                    if (! logged)
                    {
                      logged = true;
                      logger.error(message);
                    }
                    break;
                }
              }
              else
              {
                buffer.append("0");
              }
              break;
            default:
              // This is always fine.
              buffer.append("0");
              break;
          }
          break;
        case '1':
          buffer.append('1');
          break;
        case '2':
          buffer.append('2');
          break;
        case '3':
          buffer.append('3');
          break;
        case '4':
          buffer.append('4');
          break;
        case '5':
          buffer.append('5');
          break;
        case '6':
          buffer.append('6');
          break;
        case '7':
          buffer.append('7');
          break;
        case '8':
          buffer.append('8');
          break;
        case '9':
          buffer.append('9');
          break;
        case '-':
          // This is only OK if the buffer is empty.
          if (buffer.length() == 0)
          {
            buffer.append("-");
          }
          else
          {
            LocalizableMessage message = WARN_ATTR_SYNTAX_INTEGER_MISPLACED_DASH.get(value);

            switch (DirectoryServer.getSyntaxEnforcementPolicy())
            {
              case REJECT:
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
              case WARN:
                if (! logged)
                {
                  logged = true;
                  logger.error(message);
                }
                break;
            }
          }
          break;
        default:
          LocalizableMessage message = WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER.get(
              value, ((char) value.byteAt(i)), i);
          switch (DirectoryServer.getSyntaxEnforcementPolicy())
          {
            case REJECT:
              throw new DirectoryException(
                             ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
            case WARN:
              if (! logged)
              {
                logged = true;
                logger.error(message);
              }
              break;
          }
      }
    }

    if (buffer.length() == 0)
    {
      LocalizableMessage message = WARN_ATTR_SYNTAX_INTEGER_EMPTY_VALUE.get(value);

      switch (DirectoryServer.getSyntaxEnforcementPolicy())
      {
        case REJECT:
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);

        case WARN:
          if (! logged)
          {
            logged = true;
            logger.error(message);
          }

          buffer.append("0");
          break;

        default:
          buffer.append("0");
          break;
      }
    }
    else if ((buffer.length() == 1) && (buffer.charAt(0) == '-'))
    {
      LocalizableMessage message = WARN_ATTR_SYNTAX_INTEGER_DASH_NEEDS_VALUE.get(value);
      switch (DirectoryServer.getSyntaxEnforcementPolicy())
      {
        case REJECT:
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);

        case WARN:
          if (! logged)
          {
            logged = true;
            logger.error(message);
          }

          buffer.setCharAt(0, '0');
          break;

        default:
          buffer.setCharAt(0, '0');
          break;
      }
    }

    return ByteString.valueOf(buffer.toString());
  }
}

