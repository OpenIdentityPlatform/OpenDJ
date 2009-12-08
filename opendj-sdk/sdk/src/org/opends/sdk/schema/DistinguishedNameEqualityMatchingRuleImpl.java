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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.sdk.schema;



import java.util.Arrays;
import java.util.Comparator;

import org.opends.sdk.*;
import org.opends.sdk.RDN.AVA;




/**
 * This class defines the distinguishedNameMatch matching rule defined
 * in X.520 and referenced in RFC 2252.
 */
final class DistinguishedNameEqualityMatchingRuleImpl extends
    AbstractMatchingRuleImpl
{
  private static final Comparator<AVA> ATV_COMPARATOR = new Comparator<AVA>()
  {
    public int compare(AVA o1, AVA o2)
    {
      return o1.getAttributeType().compareTo(o2.getAttributeType());
    }
  };



  @Override
  public Assertion getAssertion(final Schema schema, ByteSequence value)
      throws DecodeException
  {
    DN assertion;
    try
    {
      assertion = DN.valueOf(value.toString(), schema);
    }
    catch (final LocalizedIllegalArgumentException e)
    {
      throw DecodeException.error(e.getMessageObject());
    }

    final DN finalAssertion = assertion;
    return new Assertion()
    {
      public ConditionResult matches(ByteSequence attributeValue)
      {
        try
        {
          final DN attribute = DN.valueOf(attributeValue.toString(),
              schema);
          return matchDNs(finalAssertion, attribute);
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          return ConditionResult.UNDEFINED;
        }
      }
    };
  }



  public ByteString normalizeAttributeValue(Schema schema,
      ByteSequence value) throws DecodeException
  {
    try
    {
      return ByteString.valueOf(DN.valueOf(value.toString(), schema)
          .toNormalizedString());
    }
    catch (final LocalizedIllegalArgumentException e)
    {
      throw DecodeException.error(e.getMessageObject());
    }
  }



  private ConditionResult matchAVAs(AVA ava1, AVA ava2)
  {
    final AttributeType type = ava1.getAttributeType();

    if (!type.equals(ava2.getAttributeType()))
    {
      return ConditionResult.FALSE;
    }

    final MatchingRule matchingRule = type.getEqualityMatchingRule();
    if (matchingRule != null)
    {
      try
      {
        final ByteString nv1 = matchingRule
            .normalizeAttributeValue(ava1.getAttributeValue());
        final ByteString nv2 = matchingRule
            .normalizeAttributeValue(ava2.getAttributeValue());
        return nv1.equals(nv2) ? ConditionResult.TRUE
            : ConditionResult.FALSE;
      }
      catch (final DecodeException de)
      {
        return ConditionResult.UNDEFINED;
      }
    }

    return ConditionResult.UNDEFINED;
  }



  private ConditionResult matchDNs(DN dn1, DN dn2)
  {
    final int sz1 = dn1.size();
    final int sz2 = dn2.size();

    if (sz1 != sz2)
    {
      return ConditionResult.FALSE;
    }
    else
    {
      final RDN rdn1 = dn1.rdn();
      final RDN rdn2 = dn2.rdn();
      while (rdn1 != null)
      {
        final ConditionResult result = matchRDNs(rdn1, rdn2);
        if (result != ConditionResult.TRUE)
        {
          return result;
        }
      }
      return ConditionResult.TRUE;
    }
  }



  private ConditionResult matchRDNs(RDN rdn1, RDN rdn2)
  {
    final int sz1 = rdn1.size();
    final int sz2 = rdn2.size();

    if (sz1 != sz2)
    {
      return ConditionResult.FALSE;
    }
    else if (sz1 == 1)
    {
      return matchAVAs(rdn1.getFirstAVA(), rdn2.getFirstAVA());
    }
    else
    {
      // Need to sort the AVAs before comparing.
      final AVA[] a1 = new AVA[sz1];
      int i = 0;
      for (final AVA ava : rdn1)
      {
        a1[i++] = ava;
      }
      Arrays.sort(a1, ATV_COMPARATOR);

      final AVA[] a2 = new AVA[sz1];
      i = 0;
      for (final AVA ava : rdn2)
      {
        a2[i++] = ava;
      }
      Arrays.sort(a2, ATV_COMPARATOR);

      for (i = 0; i < sz1; i++)
      {
        final ConditionResult result = matchAVAs(a1[i], a2[i]);
        if (result != ConditionResult.TRUE)
        {
          return result;
        }
      }

      return ConditionResult.TRUE;
    }
  }
}
