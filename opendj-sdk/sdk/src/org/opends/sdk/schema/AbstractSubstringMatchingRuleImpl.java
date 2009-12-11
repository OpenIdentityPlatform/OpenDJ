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



import static com.sun.opends.sdk.messages.Messages.*;

import java.util.LinkedList;
import java.util.List;

import org.opends.sdk.*;

import com.sun.opends.sdk.util.StaticUtils;
import com.sun.opends.sdk.util.SubstringReader;



/**
 * This class implements a default substring matching rule that matches
 * normalized substring assertion values in byte order.
 */
abstract class AbstractSubstringMatchingRuleImpl extends
    AbstractMatchingRuleImpl
{
  static class DefaultSubstringAssertion implements Assertion
  {
    private final ByteString normInitial;
    private final ByteString[] normAnys;
    private final ByteString normFinal;



    protected DefaultSubstringAssertion(ByteString normInitial,
        ByteString[] normAnys, ByteString normFinal)
    {
      this.normInitial = normInitial;
      this.normAnys = normAnys;
      this.normFinal = normFinal;
    }



    public ConditionResult matches(ByteSequence attributeValue)
    {
      final int valueLength = attributeValue.length();

      int pos = 0;
      if (normInitial != null)
      {
        final int initialLength = normInitial.length();
        if (initialLength > valueLength)
        {
          return ConditionResult.FALSE;
        }

        for (; pos < initialLength; pos++)
        {
          if (normInitial.byteAt(pos) != attributeValue.byteAt(pos))
          {
            return ConditionResult.FALSE;
          }
        }
      }

      if (normAnys != null && normAnys.length != 0)
      {
        for (final ByteSequence element : normAnys)
        {
          final int anyLength = element.length();
          if (anyLength == 0)
          {
            continue;
          }
          final int end = valueLength - anyLength;
          boolean match = false;
          for (; pos <= end; pos++)
          {
            if (element.byteAt(0) == attributeValue.byteAt(pos))
            {
              boolean subMatch = true;
              for (int i = 1; i < anyLength; i++)
              {
                if (element.byteAt(i) != attributeValue.byteAt(pos + i))
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
            return ConditionResult.FALSE;
          }
        }
      }

      if (normFinal != null)
      {
        final int finalLength = normFinal.length();

        if (valueLength - finalLength < pos)
        {
          return ConditionResult.FALSE;
        }

        pos = valueLength - finalLength;
        for (int i = 0; i < finalLength; i++, pos++)
        {
          if (normFinal.byteAt(i) != attributeValue.byteAt(pos))
          {
            return ConditionResult.FALSE;
          }
        }
      }

      return ConditionResult.TRUE;
    }
  }



  AbstractSubstringMatchingRuleImpl()
  {
    // Nothing to do.
  }



  @Override
  public Assertion getAssertion(Schema schema, ByteSequence value)
      throws DecodeException
  {
    if (value.length() == 0)
    {
      throw DecodeException.error(
          WARN_ATTR_SYNTAX_SUBSTRING_EMPTY.get());
    }

    ByteSequence initialString = null;
    ByteSequence finalString = null;
    List<ByteSequence> anyStrings = null;

    final String valueString = value.toString();

    if (valueString.length() == 1 && valueString.charAt(0) == '*')
    {
      return getAssertion(schema, initialString, anyStrings,
          finalString);
    }

    final char[] escapeChars = new char[] { '*' };
    final SubstringReader reader = new SubstringReader(valueString);

    ByteString bytes =
        StaticUtils.evaluateEscapes(reader, escapeChars, false);
    if (bytes.length() > 0)
    {
      initialString = normalizeSubString(schema, bytes);
    }
    if (reader.remaining() == 0)
    {
      throw DecodeException.error(
          WARN_ATTR_SYNTAX_SUBSTRING_NO_WILDCARDS
              .get(value.toString()));
    }
    while (true)
    {
      reader.read();
      bytes = StaticUtils.evaluateEscapes(reader, escapeChars, false);
      if (reader.remaining() > 0)
      {
        if (bytes.length() == 0)
        {
          throw DecodeException.error(
              WARN_ATTR_SYNTAX_SUBSTRING_CONSECUTIVE_WILDCARDS
                  .get(value.toString(), reader.pos()));
        }
        if (anyStrings == null)
        {
          anyStrings = new LinkedList<ByteSequence>();
        }
        anyStrings.add(normalizeSubString(schema, bytes));
      }
      else
      {
        if (bytes.length() > 0)
        {
          finalString = normalizeSubString(schema, bytes);
        }
        break;
      }
    }

    return getAssertion(schema, initialString, anyStrings, finalString);
  }



  @Override
  public Assertion getAssertion(Schema schema, ByteSequence subInitial,
      List<? extends ByteSequence> subAnyElements, ByteSequence subFinal)
      throws DecodeException
  {
    final ByteString normInitial =
        subInitial == null ? null : normalizeSubString(schema,
            subInitial);

    ByteString[] normAnys = null;
    if (subAnyElements != null && !subAnyElements.isEmpty())
    {
      normAnys = new ByteString[subAnyElements.size()];
      for (int i = 0; i < subAnyElements.size(); i++)
      {
        normAnys[i] = normalizeSubString(schema, subAnyElements.get(i));
      }
    }
    final ByteString normFinal =
        subFinal == null ? null : normalizeSubString(schema, subFinal);

    return new DefaultSubstringAssertion(normInitial, normAnys,
        normFinal);
  }



  ByteString normalizeSubString(Schema schema, ByteSequence value)
      throws DecodeException
  {
    return normalizeAttributeValue(schema, value);
  }
}
