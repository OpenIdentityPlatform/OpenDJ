/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.messages.AccessControlMessages.*;
import static org.opends.server.util.CollectionUtils.*;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;

/**
 * This class is used to match RDN patterns containing wildcards in either
 * the attribute types or the attribute values.
 * Substring matching on the attribute types is not supported.
 */
public class PatternRDN
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Indicate whether the RDN contains a wildcard in any of its attribute types. */
  private final boolean hasTypeWildcard;
  /** The set of attribute type patterns. */
  private String[] typePatterns;
  /**
   * The set of attribute value patterns.
   * The value pattern is split into a list according to the positions of any
   * wildcards.  For example, the value "A*B*C" is represented as a
   * list of three elements A, B and C.  The value "A" is represented as
   * a list of one element A.  The value "*A*" is represented as a list
   * of three elements "", A and "".
   */
  private final List<List<ByteString>> valuePatterns;

  /**
   * Create a new RDN pattern composed of a single attribute-value pair.
   * @param type The attribute type pattern.
   * @param valuePattern The attribute value pattern.
   * @param dnString The DN pattern containing the attribute-value pair.
   * @throws DirectoryException If the attribute-value pair is not valid.
   */
  public PatternRDN(String type, List<ByteString> valuePattern, String dnString)
       throws DirectoryException
  {
    // Only Whole-Type wildcards permitted.
    if (type.contains("*"))
    {
      if (!type.equals("*"))
      {
        LocalizableMessage message =
            WARN_PATTERN_DN_TYPE_CONTAINS_SUBSTRINGS.get(dnString);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }
      hasTypeWildcard = true;
    }
    else
    {
      hasTypeWildcard = false;
    }

    typePatterns = new String[] { type };
    valuePatterns = newArrayList(valuePattern);
  }


  /**
   * Add another attribute-value pair to the pattern.
   * @param type The attribute type pattern.
   * @param valuePattern The attribute value pattern.
   * @param dnString The DN pattern containing the attribute-value pair.
   * @throws DirectoryException If the attribute-value pair is not valid.
   * @return  <CODE>true</CODE> if the type-value pair was added to
   *          this RDN, or <CODE>false</CODE> if it was not (e.g., it
   *          was already present).
   */
  public boolean addValue(String type, List<ByteString> valuePattern, String dnString) throws DirectoryException
  {
    // No type wildcards permitted in multi-valued patterns.
    if (hasTypeWildcard || type.contains("*"))
    {
      LocalizableMessage message =
          WARN_PATTERN_DN_TYPE_WILDCARD_IN_MULTIVALUED_RDN.get(dnString);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message);
    }

    int oldLength = typePatterns.length;
    typePatterns = Arrays.copyOf(typePatterns, oldLength + 1);
    typePatterns[oldLength] = type;

    valuePatterns.add(valuePattern);

    return true;
  }


  /**
   * Retrieves the number of attribute-value pairs contained in this
   * RDN pattern.
   *
   * @return  The number of attribute-value pairs contained in this
   *          RDN pattern.
   */
  public int getNumValues()
  {
    return typePatterns.length;
  }


  /**
   * Determine whether a given RDN matches the pattern.
   * @param rdn The RDN to be matched.
   * @return true if the RDN matches the pattern.
   */
  public boolean matchesRDN(RDN rdn)
  {
    if (getNumValues() == 1)
    {
      // Check for ",*," matching any RDN.
      if (typePatterns[0].equals("*") && valuePatterns.get(0) == null)
      {
        return true;
      }

      if (rdn.size() != 1)
      {
        return false;
      }

      AVA ava = rdn.getFirstAVA();
      if (!typePatterns[0].equals("*"))
      {
        AttributeType thisType = DirectoryServer.getSchema().getAttributeType(typePatterns[0]);
        if (thisType.isPlaceHolder() || !thisType.equals(ava.getAttributeType()))
        {
          return false;
        }
      }

      return matchValuePattern(valuePatterns.get(0), ava);
    }

    if (hasTypeWildcard || typePatterns.length != rdn.size())
    {
      return false;
    }

    // Sort the attribute-value pairs by attribute type.
    TreeMap<String, List<ByteString>> patternMap = new TreeMap<>();
    for (int i = 0; i < typePatterns.length; i++)
    {
      AttributeType type = DirectoryServer.getSchema().getAttributeType(typePatterns[i]);
      if (type.isPlaceHolder())
      {
        return false;
      }
      patternMap.put(type.getNameOrOID(), valuePatterns.get(i));
    }

    Iterator<String> patternKeyIter = patternMap.keySet().iterator();
    for (AVA ava : rdn)
    {
      String rdnKey = ava.getAttributeType().getNameOrOID();
      if (!rdnKey.equals(patternKeyIter.next())
          || !matchValuePattern(patternMap.get(rdnKey), ava))
      {
        return false;
      }
    }

    return true;
  }

  /**
   * Determine whether a value pattern matches a given attribute-value pair.
   * @param pattern The value pattern where each element of the list is a
   *                substring of the pattern appearing between wildcards.
   * @param type The attribute type of the attribute-value pair.
   * @param value The value of the attribute-value pair.
   * @return true if the value pattern matches the attribute-value pair.
   */
  private boolean matchValuePattern(List<ByteString> pattern, AVA ava)
  {
    if (pattern == null)
    {
      return true;
    }

    final AttributeType type = ava.getAttributeType();
    ByteString value = ava.getAttributeValue();
    try
    {
      if (pattern.size() == 1)
      {
        // Handle this just like an equality filter.
        MatchingRule rule = type.getEqualityMatchingRule();
        ByteString thatNormValue = rule.normalizeAttributeValue(value);
        return rule.getAssertion(pattern.get(0)).matches(thatNormValue).toBoolean();
      }

      // Handle this just like a substring filter.
      ByteString subInitial = pattern.get(0);
      if (subInitial.length() == 0)
      {
        subInitial = null;
      }

      ByteString subFinal = pattern.get(pattern.size() - 1);
      if (subFinal.length() == 0)
      {
        subFinal = null;
      }

      List<ByteString> subAnyElements;
      if (pattern.size() > 2)
      {
        subAnyElements = pattern.subList(1, pattern.size()-1);
      }
      else
      {
        subAnyElements = null;
      }

      Attribute attr = Attributes.create(type, value);
      return attr.matchesSubstring(subInitial, subAnyElements, subFinal).toBoolean();
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return false;
    }
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append("(");
    for (int i = 0; i < typePatterns.length; i++)
    {
      sb.append(typePatterns[i]).append("=");
      List<ByteString> patterns = valuePatterns.get(i);
      if (patterns.size() == 1)
      {
        sb.append(patterns.get(0));
      }
      else
      {
        sb.append(patterns);
      }
    }
    sb.append(")");
    return sb.toString();
  }
}
