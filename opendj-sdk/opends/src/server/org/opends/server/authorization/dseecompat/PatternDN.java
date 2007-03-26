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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;

import java.util.LinkedHashSet;
import java.util.ArrayList;

/**
 * This class is used to encapsulate DN pattern matching using wildcards.
 *
 * The current implementation builds a fake entry containing the DN
 * in an attribute and then matches against a substring filter representing
 * the pattern.
 *
 * TODO Evaluate making this more efficient.
 *
 * Creating a dummy entry and attempting to do substring
 * matching on a DN is a pretty expensive and error-prone approach.
 * Using a regular expression would likely be much more efficient and
 * should be simpler.

 * TODO Evaluate re-writing pattern (substring) determination code.
 * The current code is similar to current DS6 implementation.
 *
 * I'm confused by the part of the constructor that generates a search
 * filter. First, there is no substring matching rule defined for the
 * DN syntax in the official standard, so technically trying to perform
 * substring matching against DNs is illegal.  Although we do try to use
 * the caseIgnoreSubstringsMatch rule, it is extremely unreliable for DNs
 * because it's just not possible to do substring matching correctly in all
 * cases for them.
 */
public class PatternDN
{
  private static final String PATTERN_DN_FAKE_TYPE_NAME = "patterndn";
  private AttributeType fakeType;
  private SearchFilter filter;

  private PatternDN(AttributeType fakeType, SearchFilter filter)
  {
    this.fakeType = fakeType;
    this.filter = filter;
  }

  /**
   * Create a new DN pattern matcher from a pattern string.
   * @param pattern The DN pattern string.
   * @throws org.opends.server.types.DirectoryException If the pattern string
   * is not valid.
   * @return A new DN pattern matcher.
   */
  public static PatternDN decode(String pattern) throws DirectoryException
  {
    AttributeType fakeType =
         DirectoryServer.getAttributeType(PATTERN_DN_FAKE_TYPE_NAME);
    if (fakeType == null)
    {
       fakeType =
            DirectoryServer.getDefaultAttributeType(PATTERN_DN_FAKE_TYPE_NAME);
    }

    SearchFilter filter;
    DN patternDN = DN.decode(pattern);
    String filterStr = PATTERN_DN_FAKE_TYPE_NAME + "=" +
         patternDN.toNormalizedString();
    filter=SearchFilter.createFilterFromString(filterStr);

    return new PatternDN(fakeType, filter);
  }

  /**
   * Determine whether a given DN matches this pattern.
   * @param dn The DN to be matched.
   * @return true if the DN matches the pattern.
   */
  public boolean matchesDN(DN dn)
  {
    String normalizedStr = dn.toNormalizedString();

    LinkedHashSet<AttributeValue> vals =
            new LinkedHashSet<AttributeValue>();
    vals.add(new AttributeValue(fakeType, normalizedStr));
    Attribute attr = new Attribute(fakeType, PATTERN_DN_FAKE_TYPE_NAME, vals);
    Entry e = new Entry(DN.nullDN(), null, null, null);
    e.addAttribute(attr,new ArrayList<AttributeValue>());

    try
    {
      return filter.matchesEntry(e);
    }
    catch (DirectoryException ex)
    {
      return false;
    }
  }
}
