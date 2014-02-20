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

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines the distinguishedNameMatch matching rule defined in X.520
 * and referenced in RFC 2252.
 */
class DistinguishedNameEqualityMatchingRule
       extends EqualityMatchingRule
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();




  /**
   * Creates a new instance of this caseExactMatch matching rule.
   */
  public DistinguishedNameEqualityMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getNames()
  {
    return Collections.singleton(EMR_DN_NAME);
  }


  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
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
   * @throws  DecodeException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeAttributeValue(ByteSequence value)
         throws DecodeException
  {
    // Since the normalization for DNs is so complex, it will be handled
    // elsewhere.
    DN dn;
    try
    {
      dn = DN.valueOf(value.toString());
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      // See if we should try to proceed anyway with a bare-bones normalization.
      if (DirectoryServer.getSyntaxEnforcementPolicy() ==
          AcceptRejectWarn.REJECT)
      {
        throw DecodeException.error(de.getMessageObject(), de);
      }

      return bestEffortNormalize(toLowerCase(value.toString()));
    }
    catch (Exception e)
    {
      logger.traceException(e);

      if (DirectoryServer.getSyntaxEnforcementPolicy() ==
          AcceptRejectWarn.REJECT)
      {
        throw DecodeException.error(ERR_ATTR_SYNTAX_DN_INVALID.get(value, e));
      }
      else
      {
        return bestEffortNormalize(toLowerCase(value.toString()));
      }
    }

    return ByteString.valueOf(dn.toNormalizedString());
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

    return ByteString.valueOf(buffer.toString());
  }
}

