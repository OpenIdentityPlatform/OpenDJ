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
package org.opends.server.schema;



import java.util.Arrays;

import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the distinguishedNameMatch matching rule defined in X.520
 * and referenced in RFC 2252.
 */
public class DistinguishedNameEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  /**
   * Creates a new instance of this caseExactMatch matching rule.
   */
  public DistinguishedNameEqualityMatchingRule()
  {
    super();

  }



  /**
   * Initializes this matching rule based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this matching rule.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem that is not
   *                                   configuration-related occurs during
   *                                   initialization.
   */
  public void initializeMatchingRule(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  public String getName()
  {
    return EMR_DN_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public String getOID()
  {
    return EMR_DN_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
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
  public String getSyntaxOID()
  {
    return SYNTAX_DN_OID;
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
  public ByteString normalizeValue(ByteString value)
         throws DirectoryException
  {
    // Since the normalization for DNs is so complex, it will be handled
    // elsewhere.
    DN dn;
    try
    {
      dn = DN.decode(value.stringValue());
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      // See if we should try to proceed anyway with a bare-bones normalization.
      if (DirectoryServer.getSyntaxEnforcementPolicy() ==
          AcceptRejectWarn.REJECT)
      {
        throw de;
      }

      return bestEffortNormalize(toLowerCase(value.stringValue()));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      if (DirectoryServer.getSyntaxEnforcementPolicy() ==
          AcceptRejectWarn.REJECT)
      {
        int msgID = MSGID_ATTR_SYNTAX_DN_INVALID;
        String message = getMessage(msgID, value.stringValue(),
                                    String.valueOf(e));
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                     message, msgID);
      }
      else
      {
        return bestEffortNormalize(toLowerCase(value.stringValue()));
      }
    }

    return new ASN1OctetString(dn.toNormalizedString());
  }



  /**
   * Performs "best-effort" normalization on the provided string in the event
   * that the real DN normalization code rejected the value.  It will simply
   * attempt to strip out any spaces that it thinks might be unnecessary.
   *
   * @param  lowerString  The all-lowercase version of the string to normalize.
   *
   * @return  A best-effort normalized version of the provided value.
   */
  private ByteString bestEffortNormalize(String lowerString)
  {
    int           length = lowerString.length();
    StringBuilder buffer = new StringBuilder(length);

    for (int i=0; i < length; i++)
    {
      char c = lowerString.charAt(i);
      if (c == ' ')
      {
        if (i == 0)
        {
          // A space at the beginning of the value will be ignored.
          continue;
        }
        else
        {
          // Look at the previous character.  If it was a backslash, then keep
          // the space.  If it was a comma, then skip the space. Otherwise, keep
          // processing.
          char previous = lowerString.charAt(i-1);
          if (previous == '\\')
          {
            buffer.append(' ');
            continue;
          }
          else if (previous == ',')
          {
            continue;
          }
        }


        if (i == (length-1))
        {
          // A space at the end of the value will be ignored.
          break;
        }
        else
        {
          // Look at the next character.  If it is a space or a comma, then skip
          // the space.  Otherwise, include it.
          char next = lowerString.charAt(i+1);
          if ((next == ' ') || (next == ','))
          {
            continue;
          }
          else
          {
            buffer.append(' ');
          }
        }
      }
      else
      {
        // It's not a space, so we'll include it.
        buffer.append(c);
      }
    }

    return new ASN1OctetString(buffer.toString());
  }



  /**
   * Indicates whether the two provided normalized values are equal to each
   * other.
   *
   * @param  value1  The normalized form of the first value to compare.
   * @param  value2  The normalized form of the second value to compare.
   *
   * @return  <CODE>true</CODE> if the provided values are equal, or
   *          <CODE>false</CODE> if not.
   */
  public boolean areEqual(ByteString value1, ByteString value2)
  {
    // Since the values are already normalized, we just need to compare the
    // associated byte arrays.
    return Arrays.equals(value1.value(), value2.value());
  }
}

