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
package org.opends.server.api;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;

/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements a matching
 * rule used for substring matching.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class SubstringMatchingRule
        extends AbstractMatchingRule
        implements MatchingRule
{
  /**
   * Normalizes the provided value fragment into a form that can be
   * used to efficiently compare values.
   *
   * @param  substring  The value fragment to be normalized.
   *
   * @return  The normalized form of the value fragment.
   *
   * @throws  DecodeException  If the provided value fragment is
   *                              not acceptable according to the
   *                              associated syntax.
   */
  public abstract ByteString normalizeSubstring(
      ByteSequence substring) throws DecodeException;



  /**
   * Determines whether the provided value matches the given substring
   * filter components.  Note that any of the substring filter
   * components may be {@code null} but at least one of them must be
   * non-{@code null}.
   *
   * @param  value           The normalized value against which to
   *                         compare the substring components.
   * @param  subInitial      The normalized substring value fragment
   *                         that should appear at the beginning of
   *                         the target value.
   * @param  subAnyElements  The normalized substring value fragments
   *                         that should appear in the middle of the
   *                         target value.
   * @param  subFinal        The normalized substring value fragment
   *                         that should appear at the end of the
   *                         target value.
   *
   * @return  {@code true} if the provided value does match the given
   *          substring components, or {@code false} if not.
   */
  public boolean valueMatchesSubstring(ByteSequence value,
                                    ByteSequence subInitial,
                                    List<ByteSequence> subAnyElements,
                                    ByteSequence subFinal)
  {
    int valueLength = value.length();

    int pos = 0;
    if (subInitial != null)
    {
      int initialLength = subInitial.length();
      if (initialLength > valueLength)
      {
        return false;
      }

      for (; pos < initialLength; pos++)
      {
        if (subInitial.byteAt(pos) != value.byteAt(pos))
        {
          return false;
        }
      }
    }


    if ((subAnyElements != null) && (! subAnyElements.isEmpty()))
    {
      for (ByteSequence element : subAnyElements)
      {
        int anyLength = element.length();
        if(anyLength == 0)
            continue;
        int end = valueLength - anyLength;
        boolean match = false;
        for (; pos <= end; pos++)
        {
          if (element.byteAt(0) == value.byteAt(pos))
          {
            boolean subMatch = true;
            for (int i=1; i < anyLength; i++)
            {
              if (element.byteAt(i) != value.byteAt(pos+i))
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
      int finalLength = subFinal.length();

      if ((valueLength - finalLength) < pos)
      {
        return false;
      }

      pos = valueLength - finalLength;
      for (int i=0; i < finalLength; i++,pos++)
      {
        if (subFinal.byteAt(i) != value.byteAt(pos))
        {
          return false;
        }
      }
    }


    return true;
  }

  /**
   * Default assertion implementation for substring matching rules.
   * For example, with the assertion value "initial*any1*any2*any3*final",
   * the assertion will be decomposed like this:
   * <ul>
   * <li>normInitial will contain "initial"</li>
   * <li>normAnys will contain [ "any1", "any2", "any3" ]</li>
   * <li>normFinal will contain "final"</li>
   * </ul>
   */
  static final class DefaultSubstringAssertion implements Assertion {
      /** Normalized substring for the text before the first '*' character. */
      private final ByteString normInitial;
      /** Normalized substrings for all text chunks in between '*' characters. */
      private final ByteString[] normAnys;
      /** Normalized substring for the text after the last '*' character. */
      private final ByteString normFinal;

      private DefaultSubstringAssertion(final ByteString normInitial,
              final ByteString[] normAnys, final ByteString normFinal) {
          this.normInitial = normInitial;
          this.normAnys = normAnys;
          this.normFinal = normFinal;
      }

      /** {@inheritDoc} */
      @Override
      public ConditionResult matches(final ByteSequence normalizedAttributeValue) {
          final int valueLength = normalizedAttributeValue.length();

          int pos = 0;
          if (normInitial != null) {
              final int initialLength = normInitial.length();
              if (initialLength > valueLength) {
                  return ConditionResult.FALSE;
              }

              for (; pos < initialLength; pos++) {
                  if (normInitial.byteAt(pos) != normalizedAttributeValue.byteAt(pos)) {
                      return ConditionResult.FALSE;
                  }
              }
          }

          if (normAnys != null) {
          matchEachSubstring:
              for (final ByteSequence element : normAnys) {
                  final int anyLength = element.length();
                  final int end = valueLength - anyLength;
              matchCurrentSubstring:
                  for (; pos <= end; pos++) {
                      // Try to match all characters from the substring
                      for (int i = 0; i < anyLength; i++) {
                          if (element.byteAt(i) != normalizedAttributeValue.byteAt(pos + i)) {
                              // not a match,
                              // try to find a match in the rest of this value
                              continue matchCurrentSubstring;
                          }
                      }
                      // we just matched current substring,
                      // go try to match the next substring
                      pos += anyLength;
                      continue matchEachSubstring;
                  }
                  // Could not match current substring
                  return ConditionResult.FALSE;
              }
          }

          if (normFinal != null) {
              final int finalLength = normFinal.length();

              if (valueLength - finalLength < pos) {
                  return ConditionResult.FALSE;
              }

              pos = valueLength - finalLength;
              for (int i = 0; i < finalLength; i++, pos++) {
                  if (normFinal.byteAt(i) != normalizedAttributeValue.byteAt(pos)) {
                      return ConditionResult.FALSE;
                  }
              }
          }

          return ConditionResult.TRUE;
      }

      /** {@inheritDoc} */
      @Override
      public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
          final Collection<T> subqueries = new LinkedList<T>();
          if (normInitial != null) {
              // relies on the fact that equality indexes are also ordered
              subqueries.add(rangeMatch(factory, "equality", normInitial));
          }
          if (normAnys != null) {
              for (ByteString normAny : normAnys) {
                  substringMatch(factory, normAny, subqueries);
              }
          }
          if (normFinal != null) {
              substringMatch(factory, normFinal, subqueries);
          }
          if (normInitial != null) {
              // Add this one last to minimize the risk to run the same search twice
              // (possible overlapping with the use of equality index at the start of this method)
              substringMatch(factory, normInitial, subqueries);
          }
          return factory.createIntersectionQuery(subqueries);
      }

      private <T> T rangeMatch(IndexQueryFactory<T> factory, String indexID, ByteSequence lower) {
          // Iterate through all the keys that have this value as the prefix.

          // Set the upper bound for a range search.
          // We need a key for the upper bound that is of equal length
          // but slightly greater than the lower bound.
          final ByteStringBuilder upper = new ByteStringBuilder(lower);

          for (int i = upper.length() - 1; i >= 0; i--) {
              if (upper.byteAt(i) == (byte) 0xFF) {
                  // We have to carry the overflow to the more significant byte.
                  upper.setByte(i, (byte) 0);
              } else {
                  // No overflow, we can stop.
                  upper.setByte(i, (byte) (upper.byteAt(i) + 1));
                  break;
              }
          }

          // Read the range: lower <= keys < upper.
          return factory.createRangeMatchQuery(indexID, lower, upper, true, false);
      }

      private <T> void substringMatch(final IndexQueryFactory<T> factory, final ByteString normSubstring,
              final Collection<T> subqueries) {
          int substrLength = factory.getIndexingOptions().substringKeySize();

          // There are two cases, depending on whether the user-provided
          // substring is smaller than the configured index substring length or not.
          if (normSubstring.length() < substrLength) {
              subqueries.add(rangeMatch(factory, "substring", normSubstring));
          } else {
              // Break the value up into fragments of length equal to the
              // index substring length, and read those keys.

              // Eliminate duplicates by putting the keys into a set.
              final TreeSet<ByteSequence> substringKeys = new TreeSet<ByteSequence>();

              // Example: The value is ABCDE and the substring length is 3.
              // We produce the keys ABC BCD CDE.
              for (int first = 0, last = substrLength;
                   last <= normSubstring.length(); first++, last++) {
                  substringKeys.add(normSubstring.subSequence(first, first + substrLength));
              }

              for (ByteSequence key : substringKeys) {
                  subqueries.add(factory.createExactMatchQuery("substring", key));
              }
          }
      }

  }

  /** {@inheritDoc} */
  @Override
  public Assertion getSubstringAssertion(ByteSequence subInitial,
      List<? extends ByteSequence> subAnyElements, ByteSequence subFinal)
      throws DecodeException
  {
    final ByteString normInitial = subInitial == null ? null : normalizeSubstring(subInitial);

    ByteString[] normAnys = null;
    if (subAnyElements != null && !subAnyElements.isEmpty())
    {
      normAnys = new ByteString[subAnyElements.size()];
      for (int i = 0; i < subAnyElements.size(); i++)
      {
        normAnys[i] = normalizeSubstring(subAnyElements.get(i));
      }
    }
    final ByteString normFinal = subFinal == null ? null : normalizeSubstring(subFinal);

    return new DefaultSubstringAssertion(normInitial, normAnys, normFinal);
  }

}

