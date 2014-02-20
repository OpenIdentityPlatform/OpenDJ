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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
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
import org.opends.server.core.DirectoryServer;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;



/**
 * This class defines the integerOrderingMatch matching rule defined in X.520
 * and referenced in RFC 4519.
 */
public class IntegerOrderingMatchingRule extends AbstractOrderingMatchingRule
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = 6654300545706161754L;



  /**
   * Creates a new instance of this integerOrderingMatch matching rule.
   */
  public IntegerOrderingMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getNames()
  {
    return Collections.singleton(OMR_INTEGER_NAME);
  }


  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return OMR_INTEGER_OID;
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
   * @throws  DecodeException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeAttributeValue(ByteSequence value)
         throws DecodeException
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
                logged = reportInvalidSyntax(logged, message);
              }
              break;
            case 1:
              // This is OK as long as the first character isn't a dash.
              if (buffer.charAt(0) == '-')
              {
                LocalizableMessage message = WARN_ATTR_SYNTAX_INTEGER_INITIAL_ZERO.get(value);
                logged = reportInvalidSyntax(logged, message);
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
            logged = reportInvalidSyntax(logged, message);
          }
          break;
        default:
          LocalizableMessage message = WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER.get(
              value, ((char) value.byteAt(i)), i);
          logged = reportInvalidSyntax(logged, message);
      }
    }

    if (buffer.length() == 0)
    {
      LocalizableMessage message = WARN_ATTR_SYNTAX_INTEGER_EMPTY_VALUE.get(value);
      logged = reportInvalidSyntax(logged, message);
      buffer.append("0");
    }
    else if ((buffer.length() == 1) && (buffer.charAt(0) == '-'))
    {
      LocalizableMessage message = WARN_ATTR_SYNTAX_INTEGER_DASH_NEEDS_VALUE.get(value);
      logged = reportInvalidSyntax(logged, message);
      buffer.setCharAt(0, '0');
    }

    return ByteString.valueOf(buffer.toString());
  }

  private boolean reportInvalidSyntax(boolean logged, LocalizableMessage message)
      throws DecodeException
  {
    switch (DirectoryServer.getSyntaxEnforcementPolicy())
    {
      case REJECT:
        throw DecodeException.error(message);
      case WARN:
        if (! logged)
        {
          logged = true;
          logger.error(message);
        }
        break;
    }
    return logged;
  }



  /**
   * Compares the first value to the second and returns a value that indicates
   * their relative order.
   *
   * @param  value1  The normalized form of the first value to compare.
   * @param  value2  The normalized form of the second value to compare.
   *
   * @return  A negative integer if <CODE>value1</CODE> should come before
   *          <CODE>value2</CODE> in ascending order, a positive integer if
   *          <CODE>value1</CODE> should come after <CODE>value2</CODE> in
   *          ascending order, or zero if there is no difference between the
   *          values with regard to ordering.
   */
  @Override
  public int compareValues(ByteSequence value1, ByteSequence value2)
  {
    int b1Length = value1.length();
    int b2Length = value2.length();


    // A length of zero should be considered a value of zero.
    if (b1Length == 0)
    {
      if (b2Length == 0)
      {
        return 0;
      }
      else if (value2.byteAt(0) == '-')
      {
        return 1;
      }
      else
      {
        return -1;
      }
    }
    else if (b2Length == 0)
    {
      if (value1.byteAt(0) == '-')
      {
        return -1;
      }
      else
      {
        return 1;
      }
    }


    // Starting with a dash should be an indicator of a negative value.
    if (value1.byteAt(0) == '-')
    {
      if (value2.byteAt(0) == '-')
      {
        if (b1Length > b2Length)
        {
          return -1;
        }
        else if (b2Length > b1Length)
        {
          return 1;
        }
        else
        {
          for (int i=1; i < b1Length; i++)
          {
            if (value1.byteAt(i) > value2.byteAt(i))
            {
              return -1;
            }
            else if (value1.byteAt(i) < value2.byteAt(i))
            {
              return 1;
            }
          }

          return 0;
        }
      }
      else
      {
        return -1;
      }
    }
    else if (value2.byteAt(0) == '-')
    {
      return 1;
    }


    // They are both positive, so see which one's bigger.
    if (b1Length > b2Length)
    {
      return 1;
    }
    else if (b2Length > b1Length)
    {
      return -1;
    }
    else
    {
      for (int i=0; i < b1Length; i++)
      {
        if (value1.byteAt(i) > value2.byteAt(i))
        {
          return 1;
        }
        else if (value1.byteAt(i) < value2.byteAt(i))
        {
          return -1;
        }
      }

      return 0;
    }
  }



  /**
   * Compares the contents of the provided byte arrays to determine their
   * relative order.
   *
   * @param  b1  The first byte array to use in the comparison.
   * @param  b2  The second byte array to use in the comparison.
   *
   * @return  A negative integer if <CODE>b1</CODE> should come before
   *          <CODE>b2</CODE> in ascending order, a positive integer if
   *          <CODE>b1</CODE> should come after <CODE>b2</CODE> in ascending
   *          order, or zero if there is no difference between the values with
   *          regard to ordering.
   */
  @Override
  public int compare(byte[] b1, byte[] b2)
  {
    int b1Length = b1.length;
    int b2Length = b2.length;


    // A length of zero should be considered a value of zero.
    if (b1Length == 0)
    {
      if (b2Length == 0)
      {
        return 0;
      }
      else if (b2[0] == '-')
      {
        return 1;
      }
      else
      {
        return -1;
      }
    }
    else if (b2Length == 0)
    {
      if (b1[0] == '-')
      {
        return -1;
      }
      else
      {
        return 1;
      }
    }


    // Starting with a dash should be an indicator of a negative value.
    if (b1[0] == '-')
    {
      if (b2[0] == '-')
      {
        if (b1Length > b2Length)
        {
          return -1;
        }
        else if (b2Length > b1Length)
        {
          return 1;
        }
        else
        {
          for (int i=1; i < b1Length; i++)
          {
            if (b1[i] > b2[i])
            {
              return -1;
            }
            else if (b1[i] < b2[i])
            {
              return 1;
            }
          }

          return 0;
        }
      }
      else
      {
        return -1;
      }
    }
    else if (b2[0] == '-')
    {
      return 1;
    }


    // They are both positive, so see which one's bigger.
    if (b1Length > b2Length)
    {
      return 1;
    }
    else if (b2Length > b1Length)
    {
      return -1;
    }
    else
    {
      for (int i=0; i < b1Length; i++)
      {
        if (b1[i] > b2[i])
        {
          return 1;
        }
        else if (b1[i] < b2[i])
        {
          return -1;
        }
      }

      return 0;
    }
  }
}

