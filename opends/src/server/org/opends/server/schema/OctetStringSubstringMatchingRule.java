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



import java.util.List;

import org.opends.server.admin.std.server.SubstringMatchingRuleCfg;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the octetStringSubstringsMatch matching rule defined in
 * X.520.  It will be used as the default substring matching rule for the binary
 * and octet string syntaxes.
 */
public class OctetStringSubstringMatchingRule
       extends SubstringMatchingRule
{
  /**
   * Creates a new instance of this octetStringSubstringsMatch matching rule.
   */
  public OctetStringSubstringMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeMatchingRule(SubstringMatchingRuleCfg configuration)
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
    return SMR_OCTET_STRING_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public String getOID()
  {
    return SMR_OCTET_STRING_OID;
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
    return SYNTAX_SUBSTRING_ASSERTION_OID;
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
    return new ASN1OctetString(value.value());
  }



  /**
   * Normalizes the provided value fragment into a form that can be used to
   * efficiently compare values.
   *
   * @param  substring  The value fragment to be normalized.
   *
   * @return  The normalized form of the value fragment.
   *
   * @throws  DirectoryException  If the provided value fragment is not
   *                              acceptable according to the associated syntax.
   */
  public ByteString normalizeSubstring(ByteString substring)
         throws DirectoryException
  {
    // This is exactly the same as normalizing a full value.
    return new ASN1OctetString(substring.value());
  }



  /**
   * Determines whether the provided value matches the given substring filter
   * components.  Note that any of the substring filter components may be
   * <CODE>null</CODE> but at least one of them must be non-<CODE>null</CODE>.
   *
   * @param  value           The normalized value against which to compare the
   *                         substring components.
   * @param  subInitial      The normalized substring value fragment that should
   *                         appear at the beginning of the target value.
   * @param  subAnyElements  The normalized substring value fragments that
   *                         should appear in the middle of the target value.
   * @param  subFinal        The normalized substring value fragment that should
   *                         appear at the end of the target value.
   *
   * @return  <CODE>true</CODE> if the provided value does match the given
   *          substring components, or <CODE>false</CODE> if not.
   */
  public boolean valueMatchesSubstring(ByteString value, ByteString subInitial,
                                       List<ByteString> subAnyElements,
                                       ByteString subFinal)
  {
    byte[] valueBytes = value.value();
    int valueLength = valueBytes.length;

    int pos = 0;
    if (subInitial != null)
    {
      byte[] initialBytes = subInitial.value();
      int initialLength = initialBytes.length;
      if (initialLength > valueLength)
      {
        return false;
      }

      for (; pos < initialLength; pos++)
      {
        if (initialBytes[pos] != valueBytes[pos])
        {
          return false;
        }
      }
    }


    if ((subAnyElements != null) && (! subAnyElements.isEmpty()))
    {
      for (ByteString element : subAnyElements)
      {
        byte[] anyBytes = element.value();
        int anyLength = anyBytes.length;

        int end = valueLength - anyLength;
        boolean match = false;
        for (; pos <= end; pos++)
        {
          if (anyBytes[0] == valueBytes[pos])
          {
            boolean subMatch = true;
            for (int i=1; i < anyLength; i++)
            {
              if (anyBytes[i] != valueBytes[pos+i])
              {
                subMatch = false;
                break;
              }
            }

            if (subMatch)
            {
              match = subMatch;
              break;
            }
          }
        }

        if (match)
        {
          pos += anyLength;
        }
        else
        {
          return false;
        }
      }
    }


    if (subFinal != null)
    {
      byte[] finalBytes = subFinal.value();
      int finalLength = finalBytes.length;

      if ((valueLength - finalLength) < pos)
      {
        return false;
      }

      pos = valueLength - finalLength;
      for (int i=0; i < finalLength; i++,pos++)
      {
        if (finalBytes[i] != valueBytes[pos])
        {
          return false;
        }
      }
    }


    return true;
  }
}

