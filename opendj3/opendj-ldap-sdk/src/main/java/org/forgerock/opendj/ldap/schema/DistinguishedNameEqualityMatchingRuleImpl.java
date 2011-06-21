/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.ldap.schema;



import static com.forgerock.opendj.util.StaticUtils.getBytes;

import java.util.Iterator;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.*;

import com.forgerock.opendj.util.StaticUtils;


/**
 * This class defines the distinguishedNameMatch matching rule defined in X.520
 * and referenced in RFC 2252.
 */
final class DistinguishedNameEqualityMatchingRuleImpl extends
    AbstractMatchingRuleImpl
{
  /**
   * {@inheritDoc}
   */
  public ByteString normalizeAttributeValue(final Schema schema,
      final ByteSequence value) throws DecodeException
  {
    try
    {
      DN dn = DN.valueOf(value.toString(), schema.asNonStrictSchema());
      StringBuilder builder = new StringBuilder(value.length());
      return ByteString.valueOf(normalizeDN(builder, dn));
    }
    catch (final LocalizedIllegalArgumentException e)
    {
      throw DecodeException.error(e.getMessageObject());
    }
  }

  /**
   * Returns the normalized string representation of a DN.
   *
   * @param builder The StringBuilder to use to construct the normalized string.
   * @param dn The DN.
   * @return The normalized string representation of the provided DN.
   */
  private static StringBuilder normalizeDN(final StringBuilder builder,
                                          final DN dn)
  {
    if(dn.rdn() == null)
    {
      return builder;
    }

    int i = dn.size() - 1;
    normalizeRDN(builder, dn.parent(i).rdn());
    for (i--; i >= 0; i--)
    {
      builder.append('\u0000');
      normalizeRDN(builder, dn.parent(i).rdn());
    }
    return builder;
  }

  /**
   * Returns the normalized string representation of a RDN.
   *
   * @param builder The StringBuilder to use to construct the normalized string.
   * @param rdn The RDN.
   * @return The normalized string representation of the provided RDN.
   */
  private static StringBuilder normalizeRDN(final StringBuilder builder,
                                           final RDN rdn)
  {
    final int sz = rdn.size();
    if (sz == 1)
    {
      return normalizeAVA(builder, rdn.getFirstAVA());
    }
    else
    {
      // Need to sort the AVAs before comparing.
      TreeSet<AVA> a = new TreeSet<AVA>();
      for(AVA ava : rdn)
      {
        a.add(ava);
      }
      Iterator<AVA> i = a.iterator();
      // Normalize the first AVA.
      normalizeAVA(builder, i.next());
      while(i.hasNext())
      {
        builder.append('\u0001');
        normalizeAVA(builder, i.next());
      }

      return builder;
    }
  }

  /**
   * Returns the normalized string representation of an AVA.
   *
   * @param builder The StringBuilder to use to construct the normalized string.
   * @param ava The AVA.
   * @return The normalized string representation of the provided AVA.
   */
  private static StringBuilder normalizeAVA(final StringBuilder builder,
                                           final AVA ava)
  {
    ByteString value = ava.getAttributeValue();
    final MatchingRule matchingRule =
        ava.getAttributeType().getEqualityMatchingRule();
    if (matchingRule != null)
    {
      try
      {
        value =
            matchingRule.normalizeAttributeValue(ava.getAttributeValue());
      }
      catch (final DecodeException de)
      {
        // Ignore - we'll drop back to the user provided value.
      }
    }

    if (!ava.getAttributeType().getNames().iterator().hasNext())
    {
      builder.append(ava.getAttributeType().getOID());
      builder.append("=#");
      StaticUtils.toHex(value, builder);
    }
    else
    {
      final String name = ava.getAttributeType().getNameOrOID();
      // Normalizing.
      StaticUtils.toLowerCase(name, builder);

      builder.append("=");

      final Syntax syntax = ava.getAttributeType().getSyntax();
      if (!syntax.isHumanReadable())
      {
        builder.append("#");
        StaticUtils.toHex(value, builder);
      }
      else
      {
        final String str = value.toString();
        if (str.length() == 0)
        {
          return builder;
        }
        char c = str.charAt(0);
        int startPos = 0;
        if ((c == ' ') || (c == '#'))
        {
          builder.append('\\');
          builder.append(c);
          startPos = 1;
        }
        final int length = str.length();
        for (int si = startPos; si < length; si++)
        {
          c = str.charAt(si);
          if (c < ' ')
          {
            for (final byte b : getBytes(String.valueOf(c)))
            {
              builder.append('\\');
              builder.append(StaticUtils.byteToLowerHex(b));
            }
          }
          else
          {
            if ((c == ' ' && si == length - 1)
                || (c == '"' || c == '+' || c == ',' || c == ';' || c == '<'
                    || c == '=' || c == '>' || c == '\\' || c == '\u0000'))
            {
              builder.append('\\');
            }
            builder.append(c);
          }
        }
      }
    }
    return builder;
  }
}
