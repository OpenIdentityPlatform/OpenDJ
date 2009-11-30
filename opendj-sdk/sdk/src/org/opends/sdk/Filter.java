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

package org.opends.sdk;



import static org.opends.messages.ProtocolMessages.*;
import static org.opends.sdk.util.StaticUtils.byteToHex;
import static org.opends.sdk.util.StaticUtils.getBytes;
import static org.opends.sdk.util.StaticUtils.toLowerCase;

import java.util.*;

import org.opends.messages.Message;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.*;



/**
 * A search filter as defined in RFC 4511. In addition this class also
 * provides support for the absolute true and absolute false filters as
 * defined in RFC 4526.
 * <p>
 * This class provides many factory methods for creating common types of
 * filter. Applications interact with a filter using
 * {@link FilterVisitor} which is applied to a filter using the
 * {@link #accept(FilterVisitor, Object)} method.
 * <p>
 * The RFC 4515 string representation of a filter can be generated using
 * the {@link #toString} methods and parsed using the
 * {@link #valueOf(String)} factory method.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4511">RFC 4511 -
 *      Lightweight Directory Access Protocol (LDAP): The Protocol </a>
 * @see <a href="http://tools.ietf.org/html/rfc4515">RFC 4515 - String
 *      Representation of Search Filters </a>
 * @see <a href="http://tools.ietf.org/html/rfc4526">RFC 4526 - Absolute
 *      True and False Filters </a>
 */
public final class Filter
{
  private static final class AndImpl extends Impl
  {
    private final List<Filter> subFilters;



    public AndImpl(List<Filter> subFilters)
    {
      this.subFilters = subFilters;
    }



    @Override
    public <R, P> R accept(FilterVisitor<R, P> v, P p)
    {
      return v.visitAndFilter(p, subFilters);
    }

  }



  private static final class ApproxMatchImpl extends Impl
  {

    private final ByteSequence assertionValue;

    private final String attributeDescription;



    public ApproxMatchImpl(String attributeDescription,
        ByteSequence assertionValue)
    {
      this.attributeDescription = attributeDescription;
      this.assertionValue = assertionValue;
    }



    @Override
    public <R, P> R accept(FilterVisitor<R, P> v, P p)
    {
      return v.visitApproxMatchFilter(p, attributeDescription,
          assertionValue);
    }

  }



  private static final class EqualityMatchImpl extends Impl
  {

    private final ByteSequence assertionValue;

    private final String attributeDescription;



    public EqualityMatchImpl(String attributeDescription,
        ByteSequence assertionValue)
    {
      this.attributeDescription = attributeDescription;
      this.assertionValue = assertionValue;
    }



    @Override
    public <R, P> R accept(FilterVisitor<R, P> v, P p)
    {
      return v.visitEqualityMatchFilter(p, attributeDescription,
          assertionValue);
    }

  }



  private static final class ExtensibleMatchImpl extends Impl
  {
    private final String attributeDescription;

    private final boolean dnAttributes;

    private final String matchingRule;

    private final ByteSequence matchValue;



    public ExtensibleMatchImpl(String matchingRule,
        String attributeDescription, ByteSequence matchValue,
        boolean dnAttributes)
    {
      this.matchingRule = matchingRule;
      this.attributeDescription = attributeDescription;
      this.matchValue = matchValue;
      this.dnAttributes = dnAttributes;
    }



    @Override
    public <R, P> R accept(FilterVisitor<R, P> v, P p)
    {
      return v.visitExtensibleMatchFilter(p, matchingRule,
          attributeDescription, matchValue, dnAttributes);
    }

  }



  private static final class GreaterOrEqualImpl extends Impl
  {

    private final ByteSequence assertionValue;

    private final String attributeDescription;



    public GreaterOrEqualImpl(String attributeDescription,
        ByteSequence assertionValue)
    {
      this.attributeDescription = attributeDescription;
      this.assertionValue = assertionValue;
    }



    @Override
    public <R, P> R accept(FilterVisitor<R, P> v, P p)
    {
      return v.visitGreaterOrEqualFilter(p, attributeDescription,
          assertionValue);
    }

  }



  private static abstract class Impl
  {
    protected Impl()
    {
      // Nothing to do.
    }



    public abstract <R, P> R accept(FilterVisitor<R, P> v, P p);
  }



  private static final class LessOrEqualImpl extends Impl
  {

    private final ByteSequence assertionValue;

    private final String attributeDescription;



    public LessOrEqualImpl(String attributeDescription,
        ByteSequence assertionValue)
    {
      this.attributeDescription = attributeDescription;
      this.assertionValue = assertionValue;
    }



    @Override
    public <R, P> R accept(FilterVisitor<R, P> v, P p)
    {
      return v.visitLessOrEqualFilter(p, attributeDescription,
          assertionValue);
    }

  }



  private static final class NotImpl extends Impl
  {
    private final Filter subFilter;



    public NotImpl(Filter subFilter)
    {
      this.subFilter = subFilter;
    }



    @Override
    public <R, P> R accept(FilterVisitor<R, P> v, P p)
    {
      return v.visitNotFilter(p, subFilter);
    }

  }



  private static final class OrImpl extends Impl
  {
    private final List<Filter> subFilters;



    public OrImpl(List<Filter> subFilters)
    {
      this.subFilters = subFilters;
    }



    @Override
    public <R, P> R accept(FilterVisitor<R, P> v, P p)
    {
      return v.visitOrFilter(p, subFilters);
    }

  }



  private static final class PresentImpl extends Impl
  {

    private final String attributeDescription;



    public PresentImpl(String attributeDescription)
    {
      this.attributeDescription = attributeDescription;
    }



    @Override
    public <R, P> R accept(FilterVisitor<R, P> v, P p)
    {
      return v.visitPresentFilter(p, attributeDescription);
    }

  }



  private static final class SubstringsImpl extends Impl
  {

    private final List<ByteSequence> anyStrings;

    private final String attributeDescription;

    private final ByteSequence finalString;

    private final ByteSequence initialString;



    public SubstringsImpl(String attributeDescription,
        ByteSequence initialString, List<ByteSequence> anyStrings,
        ByteSequence finalString)
    {
      this.attributeDescription = attributeDescription;
      this.initialString = initialString;
      this.anyStrings = anyStrings;
      this.finalString = finalString;

    }



    @Override
    public <R, P> R accept(FilterVisitor<R, P> v, P p)
    {
      return v.visitSubstringsFilter(p, attributeDescription,
          initialString, anyStrings, finalString);
    }

  }



  private static final class UnrecognizedImpl extends Impl
  {

    private final ByteSequence filterBytes;

    private final byte filterTag;



    public UnrecognizedImpl(byte filterTag, ByteSequence filterBytes)
    {
      this.filterTag = filterTag;
      this.filterBytes = filterBytes;
    }



    @Override
    public <R, P> R accept(FilterVisitor<R, P> v, P p)
    {
      return v.visitUnrecognizedFilter(p, filterTag, filterBytes);
    }

  }



  // RFC 4526 - FALSE filter.
  private static final Filter FALSE = new Filter(new OrImpl(Collections
      .<Filter> emptyList()));

  // Heavily used (objectClass=*) filter.
  private static final Filter OBJECT_CLASS_PRESENT = new Filter(
      new PresentImpl("objectClass"));

  private static final FilterVisitor<StringBuilder, StringBuilder> TO_STRING_VISITOR = new FilterVisitor<StringBuilder, StringBuilder>()
  {

    public StringBuilder visitAndFilter(StringBuilder builder,
        List<Filter> subFilters)
    {
      builder.append("(&");
      for (Filter subFilter : subFilters)
      {
        subFilter.accept(this, builder);
      }
      builder.append(')');
      return builder;
    }



    public StringBuilder visitApproxMatchFilter(StringBuilder builder,
        String attributeDescription, ByteSequence assertionValue)
    {
      builder.append('(');
      builder.append(attributeDescription);
      builder.append("~=");
      valueToFilterString(builder, assertionValue);
      builder.append(')');
      return builder;
    }



    public StringBuilder visitEqualityMatchFilter(
        StringBuilder builder, String attributeDescription,
        ByteSequence assertionValue)
    {
      builder.append('(');
      builder.append(attributeDescription);
      builder.append("=");
      valueToFilterString(builder, assertionValue);
      builder.append(')');
      return builder;
    }



    public StringBuilder visitExtensibleMatchFilter(
        StringBuilder builder, String matchingRule,
        String attributeDescription, ByteSequence assertionValue,
        boolean dnAttributes)
    {
      builder.append('(');

      if (attributeDescription != null)
      {
        builder.append(attributeDescription);
      }

      if (dnAttributes)
      {
        builder.append(":dn");
      }

      if (matchingRule != null)
      {
        builder.append(':');
        builder.append(matchingRule);
      }

      builder.append(":=");
      valueToFilterString(builder, assertionValue);
      builder.append(')');
      return builder;
    }



    public StringBuilder visitGreaterOrEqualFilter(
        StringBuilder builder, String attributeDescription,
        ByteSequence assertionValue)
    {
      builder.append('(');
      builder.append(attributeDescription);
      builder.append(">=");
      valueToFilterString(builder, assertionValue);
      builder.append(')');
      return builder;
    }



    public StringBuilder visitLessOrEqualFilter(StringBuilder builder,
        String attributeDescription, ByteSequence assertionValue)
    {
      builder.append('(');
      builder.append(attributeDescription);
      builder.append("<=");
      valueToFilterString(builder, assertionValue);
      builder.append(')');
      return builder;
    }



    public StringBuilder visitNotFilter(StringBuilder builder,
        Filter subFilter)
    {
      builder.append("(|");
      subFilter.accept(this, builder);
      builder.append(')');
      return builder;
    }



    public StringBuilder visitOrFilter(StringBuilder builder,
        List<Filter> subFilters)
    {
      builder.append("(|");
      for (Filter subFilter : subFilters)
      {
        subFilter.accept(this, builder);
      }
      builder.append(')');
      return builder;
    }



    public StringBuilder visitPresentFilter(StringBuilder builder,
        String attributeDescription)
    {
      builder.append('(');
      builder.append(attributeDescription);
      builder.append("=*)");
      return builder;
    }



    public StringBuilder visitSubstringsFilter(StringBuilder builder,
        String attributeDescription, ByteSequence initialSubstring,
        List<ByteSequence> anySubstrings, ByteSequence finalSubstring)
    {
      builder.append('(');
      builder.append(attributeDescription);
      builder.append("=");
      if (initialSubstring != null)
      {
        valueToFilterString(builder, initialSubstring);
      }
      for (ByteSequence anySubstring : anySubstrings)
      {
        builder.append('*');
        valueToFilterString(builder, anySubstring);
      }
      builder.append('*');
      if (finalSubstring != null)
      {
        valueToFilterString(builder, finalSubstring);
      }
      builder.append(')');
      return builder;
    }



    public StringBuilder visitUnrecognizedFilter(StringBuilder builder,
        byte filterTag, ByteSequence filterBytes)
    {
      // Fake up a representation.
      builder.append('(');
      builder.append(byteToHex(filterTag));
      builder.append(':');
      StaticUtils.toHex(filterBytes, builder);
      builder.append(')');
      return builder;
    }
  };

  // RFC 4526 - TRUE filter.
  private static final Filter TRUE = new Filter(new AndImpl(Collections
      .<Filter> emptyList()));



  /**
   * Returns the {@code absolute false} filter as defined in RFC 4526
   * which is comprised of an {@code or} filter containing zero
   * components.
   *
   * @return The absolute false filter.
   * @see <a href="http://tools.ietf.org/html/rfc4526">RFC 4526</a>
   */
  public static Filter getAbsoluteFalseFilter()
  {
    return FALSE;
  }



  /**
   * Returns the {@code absolute true} filter as defined in RFC 4526
   * which is comprised of an {@code and} filter containing zero
   * components.
   *
   * @return The absolute true filter.
   * @see <a href="http://tools.ietf.org/html/rfc4526">RFC 4526</a>
   */
  public static Filter getAbsoluteTrueFilter()
  {
    return TRUE;
  }



  /**
   * Returns the {@code objectClass} presence filter {@code
   * (objectClass=*)}.
   * <p>
   * A call to this method is equivalent to but more efficient than the
   * following code:
   *
   * <pre>
   * Filter.present(&quot;objectClass&quot;);
   * </pre>
   *
   * @return The {@code objectClass} presence filter {@code
   *         (objectClass=*)}.
   */
  public static Filter getObjectClassPresentFilter()
  {
    return OBJECT_CLASS_PRESENT;
  }



  /**
   * Creates a new {@code and} filter using the provided list of
   * sub-filters.
   * <p>
   * Creating a new {@code and} filter with a {@code null} or empty list
   * of sub-filters is equivalent to calling
   * {@link #getAbsoluteTrueFilter()}.
   *
   * @param subFilters
   *          The list of sub-filters, may be empty or {@code null}.
   * @return The newly created {@code and} filter.
   */
  public static Filter newAndFilter(Collection<Filter> subFilters)
  {
    if (subFilters == null || subFilters.isEmpty())
    {
      // RFC 4526 - TRUE filter.
      return getAbsoluteTrueFilter();
    }
    else if (subFilters.size() == 1)
    {
      Filter subFilter = subFilters.iterator().next();
      Validator.ensureNotNull(subFilter);
      return new Filter(new AndImpl(Collections
          .singletonList(subFilter)));
    }
    else
    {
      List<Filter> subFiltersList = new ArrayList<Filter>(subFilters
          .size());
      for (Filter subFilter : subFilters)
      {
        Validator.ensureNotNull(subFilter);
        subFiltersList.add(subFilter);
      }
      return new Filter(new AndImpl(Collections
          .unmodifiableList(subFiltersList)));
    }
  }



  /**
   * Creates a new {@code and} filter using the provided list of
   * sub-filters.
   * <p>
   * Creating a new {@code and} filter with a {@code null} or empty list
   * of sub-filters is equivalent to calling
   * {@link #getAbsoluteTrueFilter()}.
   *
   * @param subFilters
   *          The list of sub-filters, may be empty or {@code null}.
   * @return The newly created {@code and} filter.
   */
  public static Filter newAndFilter(Filter... subFilters)
  {
    if ((subFilters == null) || (subFilters.length == 0))
    {
      // RFC 4526 - TRUE filter.
      return getAbsoluteTrueFilter();
    }
    else if (subFilters.length == 1)
    {
      Validator.ensureNotNull(subFilters[0]);
      return new Filter(new AndImpl(Collections
          .singletonList(subFilters[0])));
    }
    else
    {
      List<Filter> subFiltersList = new ArrayList<Filter>(
          subFilters.length);
      for (Filter subFilter : subFilters)
      {
        Validator.ensureNotNull(subFilter);
        subFiltersList.add(subFilter);
      }
      return new Filter(new AndImpl(Collections
          .unmodifiableList(subFiltersList)));
    }
  }



  /**
   * Creates a new {@code approximate match} filter using the provided
   * attribute description and assertion value.
   *
   * @param attributeDescription
   *          The attribute description.
   * @param assertionValue
   *          The assertion value.
   * @return The newly created {@code approximate match} filter.
   */
  public static Filter newApproxMatchFilter(
      String attributeDescription, ByteSequence assertionValue)
  {
    Validator.ensureNotNull(attributeDescription);
    Validator.ensureNotNull(assertionValue);
    return new Filter(new ApproxMatchImpl(attributeDescription,
        assertionValue));
  }



  /**
   * Creates a new {@code equality match} filter using the provided
   * attribute description and assertion value.
   *
   * @param attributeDescription
   *          The attribute description.
   * @param assertionValue
   *          The assertion value.
   * @return The newly created {@code equality match} filter.
   */
  public static Filter newEqualityMatchFilter(
      String attributeDescription, ByteSequence assertionValue)
  {
    Validator.ensureNotNull(attributeDescription);
    Validator.ensureNotNull(assertionValue);
    return new Filter(new EqualityMatchImpl(attributeDescription,
        assertionValue));
  }



  /**
   * Creates a new {@code extensible match} filter.
   *
   * @param matchingRule
   *          The matching rule name, may be {@code null} if {@code
   *          attributeDescription} is specified.
   * @param attributeDescription
   *          The attribute description, may be {@code null} if {@code
   *          matchingRule} is specified.
   * @param assertionValue
   *          The assertion value.
   * @param dnAttributes
   *          Indicates whether DN matching should be performed.
   * @return The newly created {@code extensible match} filter.
   */
  public static Filter newExtensibleMatchFilter(String matchingRule,
      String attributeDescription, ByteSequence assertionValue,
      boolean dnAttributes)
  {
    Validator.ensureTrue((matchingRule != null)
        || (attributeDescription != null), "matchingRule and/or "
        + "attributeDescription must not be null");
    Validator.ensureNotNull(assertionValue);
    return new Filter(new ExtensibleMatchImpl(matchingRule,
        attributeDescription, assertionValue, dnAttributes));
  }



  /**
   * Creates a new {@code greater or equal} filter using the provided
   * attribute description and assertion value.
   *
   * @param attributeDescription
   *          The attribute description.
   * @param assertionValue
   *          The assertion value.
   * @return The newly created {@code greater or equal} filter.
   */
  public static Filter newGreaterOrEqualFilter(
      String attributeDescription, ByteSequence assertionValue)
  {
    Validator.ensureNotNull(attributeDescription);
    Validator.ensureNotNull(assertionValue);
    return new Filter(new GreaterOrEqualImpl(attributeDescription,
        assertionValue));
  }



  /**
   * Creates a new {@code less or equal} filter using the provided
   * attribute description and assertion value.
   *
   * @param attributeDescription
   *          The attribute description.
   * @param assertionValue
   *          The assertion value.
   * @return The newly created {@code less or equal} filter.
   */
  public static Filter newLessOrEqualFilter(
      String attributeDescription, ByteSequence assertionValue)
  {
    Validator.ensureNotNull(attributeDescription);
    Validator.ensureNotNull(assertionValue);
    return new Filter(new LessOrEqualImpl(attributeDescription,
        assertionValue));
  }



  /**
   * Creates a new {@code not} filter using the provided sub-filter.
   *
   * @param subFilter
   *          The sub-filter.
   * @return The newly created {@code not} filter.
   */
  public static Filter newNotFilter(Filter subFilter)
  {
    Validator.ensureNotNull(subFilter);
    return new Filter(new NotImpl(subFilter));
  }



  /**
   * Creates a new {@code or} filter using the provided list of
   * sub-filters.
   * <p>
   * Creating a new {@code or} filter with a {@code null} or empty list
   * of sub-filters is equivalent to calling
   * {@link #getAbsoluteFalseFilter()}.
   *
   * @param subFilters
   *          The list of sub-filters, may be empty or {@code null}.
   * @return The newly created {@code or} filter.
   */
  public static Filter newOrFilter(Collection<Filter> subFilters)
  {
    if (subFilters == null || subFilters.isEmpty())
    {
      // RFC 4526 - FALSE filter.
      return getAbsoluteFalseFilter();
    }
    else if (subFilters.size() == 1)
    {
      Filter subFilter = subFilters.iterator().next();
      Validator.ensureNotNull(subFilter);
      return new Filter(
          new OrImpl(Collections.singletonList(subFilter)));
    }
    else
    {
      List<Filter> subFiltersList = new ArrayList<Filter>(subFilters
          .size());
      for (Filter subFilter : subFilters)
      {
        Validator.ensureNotNull(subFilter);
        subFiltersList.add(subFilter);
      }
      return new Filter(new OrImpl(Collections
          .unmodifiableList(subFiltersList)));
    }
  }



  /**
   * Creates a new {@code or} filter using the provided list of
   * sub-filters.
   * <p>
   * Creating a new {@code or} filter with a {@code null} or empty list
   * of sub-filters is equivalent to calling
   * {@link #getAbsoluteFalseFilter()}.
   *
   * @param subFilters
   *          The list of sub-filters, may be empty or {@code null}.
   * @return The newly created {@code or} filter.
   */
  public static Filter newOrFilter(Filter... subFilters)
  {
    if ((subFilters == null) || (subFilters.length == 0))
    {
      // RFC 4526 - FALSE filter.
      return getAbsoluteFalseFilter();
    }
    else if (subFilters.length == 1)
    {
      Validator.ensureNotNull(subFilters[0]);
      return new Filter(new OrImpl(Collections
          .singletonList(subFilters[0])));
    }
    else
    {
      List<Filter> subFiltersList = new ArrayList<Filter>(
          subFilters.length);
      for (Filter subFilter : subFilters)
      {
        Validator.ensureNotNull(subFilter);
        subFiltersList.add(subFilter);
      }
      return new Filter(new OrImpl(Collections
          .unmodifiableList(subFiltersList)));
    }
  }



  /**
   * Creates a new {@code present} filter using the provided attribute
   * description.
   *
   * @param attributeDescription
   *          The attribute description.
   * @return The newly created {@code present} filter.
   */
  public static Filter newPresentFilter(String attributeDescription)
  {
    Validator.ensureNotNull(attributeDescription);
    if (toLowerCase(attributeDescription).equals("objectclass"))
    {
      return OBJECT_CLASS_PRESENT;
    }
    return new Filter(new PresentImpl(attributeDescription));
  }



  /**
   * Creates a new {@code substrings} filter using the provided
   * attribute description, {@code initial}, {@code final}, and {@code
   * any} sub-strings.
   *
   * @param attributeDescription
   *          The attribute description.
   * @param initialSubstring
   *          The initial sub-string, may be {@code null} if either
   *          {@code finalSubstring} or {@code anySubstrings} are
   *          specified.
   * @param anySubstrings
   *          The final sub-string, may be {@code null} or empty if
   *          either {@code finalSubstring} or {@code initialSubstring}
   *          are specified.
   * @param finalSubstring
   *          The final sub-string, may be {@code null}, may be {@code
   *          null} if either {@code initialSubstring} or {@code
   *          anySubstrings} are specified.
   * @return The newly created {@code substrings} filter.
   */
  public static Filter newSubstringsFilter(String attributeDescription,
      ByteSequence initialSubstring, ByteSequence[] anySubstrings,
      ByteSequence finalSubstring)
  {
    Validator.ensureNotNull(attributeDescription);
    Validator.ensureTrue((initialSubstring != null)
        || (finalSubstring != null)
        || ((anySubstrings != null) && (anySubstrings.length > 0)),
        "at least one substring (initial, any or final)"
            + " must be specified");

    List<ByteSequence> anySubstringList;
    if ((anySubstrings == null) || (anySubstrings.length == 0))
    {
      anySubstringList = Collections.emptyList();
    }
    else if (anySubstrings.length == 1)
    {
      Validator.ensureNotNull(anySubstrings[0]);
      anySubstringList = Collections.singletonList(anySubstrings[0]);
    }
    else
    {
      anySubstringList = new ArrayList<ByteSequence>(
          anySubstrings.length);
      for (ByteSequence anySubstring : anySubstrings)
      {
        Validator.ensureNotNull(anySubstring);

        anySubstringList.add(anySubstring);
      }
      anySubstringList = Collections.unmodifiableList(anySubstringList);
    }

    return new Filter(new SubstringsImpl(attributeDescription,
        initialSubstring, anySubstringList, finalSubstring));
  }



  /**
   * Creates a new {@code substrings} filter using the provided
   * attribute description, {@code initial}, {@code final}, and {@code
   * any} sub-strings.
   *
   * @param attributeDescription
   *          The attribute description.
   * @param initialSubstring
   *          The initial sub-string, may be {@code null} if either
   *          {@code finalSubstring} or {@code anySubstrings} are
   *          specified.
   * @param anySubstrings
   *          The final sub-string, may be {@code null} or empty if
   *          either {@code finalSubstring} or {@code initialSubstring}
   *          are specified.
   * @param finalSubstring
   *          The final sub-string, may be {@code null}, may be {@code
   *          null} if either {@code initialSubstring} or {@code
   *          anySubstrings} are specified.
   * @return The newly created {@code substrings} filter.
   */
  public static Filter newSubstringsFilter(String attributeDescription,
      ByteSequence initialSubstring,
      Collection<ByteSequence> anySubstrings,
      ByteSequence finalSubstring)
  {
    Validator.ensureNotNull(attributeDescription);
    Validator.ensureTrue((initialSubstring != null)
        || (finalSubstring != null)
        || ((anySubstrings != null) && (anySubstrings.size() > 0)),
        "at least one substring (initial, any or final)"
            + " must be specified");

    List<ByteSequence> anySubstringList;
    if ((anySubstrings == null) || (anySubstrings.size() == 0))
    {
      anySubstringList = Collections.emptyList();
    }
    else if (anySubstrings.size() == 1)
    {
      ByteSequence anySubstring = anySubstrings.iterator().next();
      Validator.ensureNotNull(anySubstring);
      anySubstringList = Collections.singletonList(anySubstring);
    }
    else
    {
      anySubstringList = new ArrayList<ByteSequence>(anySubstrings
          .size());
      for (ByteSequence anySubstring : anySubstrings)
      {
        Validator.ensureNotNull(anySubstring);

        anySubstringList.add(anySubstring);
      }
      anySubstringList = Collections.unmodifiableList(anySubstringList);
    }

    return new Filter(new SubstringsImpl(attributeDescription,
        initialSubstring, anySubstringList, finalSubstring));
  }



  /**
   * Creates a new {@code unrecognized} filter using the provided ASN1
   * filter tag and content. This type of filter should be used for
   * filters which are not part of the standard filter definition.
   *
   * @param filterTag
   *          The ASN.1 tag.
   * @param filterBytes
   *          The filter content.
   * @return The newly created {@code unrecognized} filter.
   */
  public static Filter newUnrecognizedFilter(byte filterTag,
      ByteSequence filterBytes)
  {
    Validator.ensureNotNull(filterBytes);
    return new Filter(new UnrecognizedImpl(filterTag, filterBytes));
  }



  /**
   * Parses the provided LDAP string representation of a filter as a
   * {@code Filter}.
   *
   * @param string
   *          The LDAP string representation of a filter.
   * @return The parsed {@code Filter}.
   * @throws LocalizedIllegalArgumentException
   *           If {@code string} is not a valid LDAP string
   *           representation of a filter.
   */
  public static Filter valueOf(String string)
      throws LocalizedIllegalArgumentException
  {
    Validator.ensureNotNull(string);

    // If the filter is enclosed in a pair of single quotes it
    // is invalid (issue #1024).
    if ((string.length() > 1) && string.startsWith("'")
        && string.endsWith("'"))
    {
      Message message = ERR_LDAP_FILTER_ENCLOSED_IN_APOSTROPHES
          .get(string);
      throw new LocalizedIllegalArgumentException(message);
    }

    if (string.startsWith("("))
    {
      if (string.endsWith(")"))
      {
        return valueOf0(string, 1, string.length() - 1);
      }
      else
      {
        Message message = ERR_LDAP_FILTER_MISMATCHED_PARENTHESES.get(
            string, 1, string.length());
        throw new LocalizedIllegalArgumentException(message);
      }
    }
    else
    {
      // We tolerate the top level filter component not being surrounded
      // by parentheses.
      return valueOf0(string, 0, string.length());
    }
  }



  private static Filter valueOf0(String string,
      int beginIndex /* inclusive */, int endIndex /* exclusive */)
      throws LocalizedIllegalArgumentException
  {
    if (beginIndex >= endIndex)
    {
      Message message = ERR_LDAP_FILTER_STRING_NULL.get();
      throw new LocalizedIllegalArgumentException(message);
    }

    int index = beginIndex;
    char c = string.charAt(index);

    if (c == '&')
    {
      List<Filter> subFilters = valueOfFilterList(string, index + 1,
          endIndex);
      if (subFilters.isEmpty())
      {
        return getAbsoluteTrueFilter();
      }
      else
      {
        return new Filter(new AndImpl(subFilters));
      }
    }
    else if (c == '|')
    {
      List<Filter> subFilters = valueOfFilterList(string, index + 1,
          endIndex);
      if (subFilters.isEmpty())
      {
        return getAbsoluteFalseFilter();
      }
      else
      {
        return new Filter(new OrImpl(subFilters));
      }
    }
    else if (c == '!')
    {
      if ((string.charAt(index + 1) != '(')
          || (string.charAt(endIndex - 1) != ')'))
      {
        Message message = ERR_LDAP_FILTER_COMPOUND_MISSING_PARENTHESES
            .get(string, index, endIndex - 1);
        throw new LocalizedIllegalArgumentException(message);
      }

      Filter subFilter = valueOf0(string, index + 2, endIndex - 1);
      return new Filter(new NotImpl(subFilter));
    }
    else
    {
      // It must be a simple filter. It must have an equal sign at some
      // point, so find it.
      int equalPos = -1;
      for (int i = index; i < endIndex; i++)
      {
        if (string.charAt(i) == '=')
        {
          equalPos = i;
          break;
        }
      }

      // Look at the character immediately before the equal sign,
      // because it may help determine the filter type.
      String attributeDescription;
      ByteSequence assertionValue;

      switch (string.charAt(equalPos - 1))
      {
      case '~':
        attributeDescription = valueOfAttributeDescription(string,
            index, equalPos - 1);
        assertionValue = valueOfAssertionValue(string, equalPos + 1,
            endIndex);
        return new Filter(new ApproxMatchImpl(attributeDescription,
            assertionValue));
      case '>':
        attributeDescription = valueOfAttributeDescription(string,
            index, equalPos - 1);
        assertionValue = valueOfAssertionValue(string, equalPos + 1,
            endIndex);
        return new Filter(new GreaterOrEqualImpl(attributeDescription,
            assertionValue));
      case '<':
        attributeDescription = valueOfAttributeDescription(string,
            index, equalPos - 1);
        assertionValue = valueOfAssertionValue(string, equalPos + 1,
            endIndex);
        return new Filter(new LessOrEqualImpl(attributeDescription,
            assertionValue));
      case ':':
        return valueOfExtensibleFilter(string, index, equalPos,
            endIndex);
      default:
        attributeDescription = valueOfAttributeDescription(string,
            index, equalPos);
        return valueOfGenericFilter(string, attributeDescription,
            equalPos + 1, endIndex);
      }
    }
  }



  private static ByteSequence valueOfAssertionValue(String string,
      int startIndex, int endIndex)
      throws LocalizedIllegalArgumentException
  {
    boolean hasEscape = false;
    byte[] valueBytes = getBytes(string.substring(startIndex, endIndex));
    for (byte valueByte : valueBytes)
    {
      if (valueByte == 0x5C) // The backslash character
      {
        hasEscape = true;
        break;
      }
    }

    if (hasEscape)
    {
      ByteStringBuilder valueBuffer = new ByteStringBuilder(
          valueBytes.length);
      for (int i = 0; i < valueBytes.length; i++)
      {
        if (valueBytes[i] == 0x5C) // The backslash character
        {
          // The next two bytes must be the hex characters that comprise
          // the binary value.
          if ((i + 2) >= valueBytes.length)
          {
            Message message = ERR_LDAP_FILTER_INVALID_ESCAPED_BYTE.get(
                string, startIndex + i + 1);
            throw new LocalizedIllegalArgumentException(message);
          }

          byte byteValue = 0;
          switch (valueBytes[++i])
          {
          case 0x30: // '0'
            break;
          case 0x31: // '1'
            byteValue = (byte) 0x10;
            break;
          case 0x32: // '2'
            byteValue = (byte) 0x20;
            break;
          case 0x33: // '3'
            byteValue = (byte) 0x30;
            break;
          case 0x34: // '4'
            byteValue = (byte) 0x40;
            break;
          case 0x35: // '5'
            byteValue = (byte) 0x50;
            break;
          case 0x36: // '6'
            byteValue = (byte) 0x60;
            break;
          case 0x37: // '7'
            byteValue = (byte) 0x70;
            break;
          case 0x38: // '8'
            byteValue = (byte) 0x80;
            break;
          case 0x39: // '9'
            byteValue = (byte) 0x90;
            break;
          case 0x41: // 'A'
          case 0x61: // 'a'
            byteValue = (byte) 0xA0;
            break;
          case 0x42: // 'B'
          case 0x62: // 'b'
            byteValue = (byte) 0xB0;
            break;
          case 0x43: // 'C'
          case 0x63: // 'c'
            byteValue = (byte) 0xC0;
            break;
          case 0x44: // 'D'
          case 0x64: // 'd'
            byteValue = (byte) 0xD0;
            break;
          case 0x45: // 'E'
          case 0x65: // 'e'
            byteValue = (byte) 0xE0;
            break;
          case 0x46: // 'F'
          case 0x66: // 'f'
            byteValue = (byte) 0xF0;
            break;
          default:
            Message message = ERR_LDAP_FILTER_INVALID_ESCAPED_BYTE.get(
                string, startIndex + i + 1);
            throw new LocalizedIllegalArgumentException(message);
          }

          switch (valueBytes[++i])
          {
          case 0x30: // '0'
            break;
          case 0x31: // '1'
            byteValue |= (byte) 0x01;
            break;
          case 0x32: // '2'
            byteValue |= (byte) 0x02;
            break;
          case 0x33: // '3'
            byteValue |= (byte) 0x03;
            break;
          case 0x34: // '4'
            byteValue |= (byte) 0x04;
            break;
          case 0x35: // '5'
            byteValue |= (byte) 0x05;
            break;
          case 0x36: // '6'
            byteValue |= (byte) 0x06;
            break;
          case 0x37: // '7'
            byteValue |= (byte) 0x07;
            break;
          case 0x38: // '8'
            byteValue |= (byte) 0x08;
            break;
          case 0x39: // '9'
            byteValue |= (byte) 0x09;
            break;
          case 0x41: // 'A'
          case 0x61: // 'a'
            byteValue |= (byte) 0x0A;
            break;
          case 0x42: // 'B'
          case 0x62: // 'b'
            byteValue |= (byte) 0x0B;
            break;
          case 0x43: // 'C'
          case 0x63: // 'c'
            byteValue |= (byte) 0x0C;
            break;
          case 0x44: // 'D'
          case 0x64: // 'd'
            byteValue |= (byte) 0x0D;
            break;
          case 0x45: // 'E'
          case 0x65: // 'e'
            byteValue |= (byte) 0x0E;
            break;
          case 0x46: // 'F'
          case 0x66: // 'f'
            byteValue |= (byte) 0x0F;
            break;
          default:
            Message message = ERR_LDAP_FILTER_INVALID_ESCAPED_BYTE.get(
                string, startIndex + i + 1);
            throw new LocalizedIllegalArgumentException(message);
          }

          valueBuffer.append(byteValue);
        }
        else
        {
          valueBuffer.append(valueBytes[i]);
        }
      }

      return valueBuffer.toByteString();
    }
    else
    {
      return ByteString.wrap(valueBytes);
    }
  }



  private static String valueOfAttributeDescription(String string,
      int startIndex, int endIndex)
      throws LocalizedIllegalArgumentException
  {
    // The part of the filter string before the equal sign should be the
    // attribute type. Make sure that the characters it contains are
    // acceptable for attribute types, including those allowed by
    // attribute name exceptions (ASCII letters and digits, the dash,
    // and the underscore). We also need to allow attribute options,
    // which includes the semicolon and the equal sign.
    String attrType = string.substring(startIndex, endIndex);
    for (int i = 0; i < attrType.length(); i++)
    {
      switch (attrType.charAt(i))
      {
      case '-':
      case '0':
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
      case '8':
      case '9':
      case ';':
      case '=':
      case 'A':
      case 'B':
      case 'C':
      case 'D':
      case 'E':
      case 'F':
      case 'G':
      case 'H':
      case 'I':
      case 'J':
      case 'K':
      case 'L':
      case 'M':
      case 'N':
      case 'O':
      case 'P':
      case 'Q':
      case 'R':
      case 'S':
      case 'T':
      case 'U':
      case 'V':
      case 'W':
      case 'X':
      case 'Y':
      case 'Z':
      case '_':
      case 'a':
      case 'b':
      case 'c':
      case 'd':
      case 'e':
      case 'f':
      case 'g':
      case 'h':
      case 'i':
      case 'j':
      case 'k':
      case 'l':
      case 'm':
      case 'n':
      case 'o':
      case 'p':
      case 'q':
      case 'r':
      case 's':
      case 't':
      case 'u':
      case 'v':
      case 'w':
      case 'x':
      case 'y':
      case 'z':
        // These are all OK.
        break;

      case '.':
      case '/':
      case ':':
      case '<':
      case '>':
      case '?':
      case '@':
      case '[':
      case '\\':
      case ']':
      case '^':
      case '`':
        // These are not allowed, but they are explicitly called out
        // because they are included in the range of values between '-'
        // and 'z', and making sure all possible characters are included
        // can help make the switch statement more efficient. We'll fall
        // through to the default clause to reject them.
      default:
        Message message = ERR_LDAP_FILTER_INVALID_CHAR_IN_ATTR_TYPE
            .get(attrType, String.valueOf(attrType.charAt(i)), i);
        throw new LocalizedIllegalArgumentException(message);
      }
    }

    return attrType;
  }



  private static Filter valueOfExtensibleFilter(String string,
      int startIndex, int equalIndex, int endIndex)
      throws LocalizedIllegalArgumentException
  {
    String attributeDescription = null;
    boolean dnAttributes = false;
    String matchingRule = null;

    // Look at the first character. If it is a colon, then it must be
    // followed by either the string "dn" or the matching rule ID. If it
    // is not, then must be the attribute type.
    String lowerLeftStr = toLowerCase(string.substring(startIndex,
        equalIndex));
    if (string.charAt(startIndex) == ':')
    {
      // See if it starts with ":dn". Otherwise, it much be the matching
      // rule ID.
      if (lowerLeftStr.startsWith(":dn:"))
      {
        dnAttributes = true;

        if ((startIndex + 4) < (equalIndex - 1))
        {
          matchingRule = string.substring(startIndex + 4,
              equalIndex - 1);
        }
      }
      else
      {
        matchingRule = string.substring(startIndex + 1, equalIndex - 1);
      }
    }
    else
    {
      int colonPos = string.indexOf(':', startIndex);
      if (colonPos < 0)
      {
        Message message = ERR_LDAP_FILTER_EXTENSIBLE_MATCH_NO_COLON
            .get(string, startIndex);
        throw new LocalizedIllegalArgumentException(message);
      }

      attributeDescription = string.substring(startIndex, colonPos);

      // If there is anything left, then it should be ":dn" and/or ":"
      // followed by the matching rule ID.
      if (colonPos < (equalIndex - 1))
      {
        if (lowerLeftStr.startsWith(":dn:", colonPos - startIndex))
        {
          dnAttributes = true;

          if ((colonPos + 4) < (equalIndex - 1))
          {
            matchingRule = string.substring(colonPos + 4,
                equalIndex - 1);
          }
        }
        else
        {
          matchingRule = string.substring(colonPos + 1, equalIndex - 1);
        }
      }
    }

    // Parse out the attribute value.
    ByteSequence matchValue = valueOfAssertionValue(string,
        equalIndex + 1, endIndex);

    // Make sure that the filter has at least one of an attribute
    // description and/or a matching rule ID.
    if ((attributeDescription == null) && (matchingRule == null))
    {
      Message message = ERR_LDAP_FILTER_EXTENSIBLE_MATCH_NO_AD_OR_MR
          .get(string, startIndex);
      throw new LocalizedIllegalArgumentException(message);
    }

    return new Filter(new ExtensibleMatchImpl(matchingRule,
        attributeDescription, matchValue, dnAttributes));
  }



  private static List<Filter> valueOfFilterList(String string,
      int startIndex, int endIndex)
      throws LocalizedIllegalArgumentException
  {
    // If the end index is equal to the start index, then there are no
    // components.
    if (startIndex >= endIndex)
    {
      return Collections.emptyList();
    }

    // At least one sub-filter.
    Filter firstFilter = null;
    List<Filter> subFilters = null;

    // The first and last characters must be parentheses. If not, then
    // that's an error.
    if ((string.charAt(startIndex) != '(')
        || (string.charAt(endIndex - 1) != ')'))
    {
      Message message = ERR_LDAP_FILTER_COMPOUND_MISSING_PARENTHESES
          .get(string, startIndex, endIndex);
      throw new LocalizedIllegalArgumentException(message);
    }

    // Iterate through the characters in the value. Whenever an open
    // parenthesis is found, locate the corresponding close parenthesis
    // by counting the number of intermediate open/close parentheses.
    int pendingOpens = 0;
    int openIndex = -1;
    for (int i = startIndex; i < endIndex; i++)
    {
      char c = string.charAt(i);
      if (c == '(')
      {
        if (openIndex < 0)
        {
          openIndex = i;
        }
        pendingOpens++;
      }
      else if (c == ')')
      {
        pendingOpens--;
        if (pendingOpens == 0)
        {
          Filter subFilter = valueOf0(string, openIndex + 1, i);
          if (subFilters != null)
          {
            subFilters.add(subFilter);
          }
          else if (firstFilter != null)
          {
            subFilters = new LinkedList<Filter>();
            subFilters.add(firstFilter);
            subFilters.add(subFilter);
            firstFilter = null;
          }
          else
          {
            firstFilter = subFilter;
          }
          openIndex = -1;
        }
        else if (pendingOpens < 0)
        {
          Message message = ERR_LDAP_FILTER_NO_CORRESPONDING_OPEN_PARENTHESIS
              .get(string, i);
          throw new LocalizedIllegalArgumentException(message);
        }
      }
      else if (pendingOpens <= 0)
      {
        Message message = ERR_LDAP_FILTER_COMPOUND_MISSING_PARENTHESES
            .get(string, startIndex, endIndex);
        throw new LocalizedIllegalArgumentException(message);
      }
    }

    // At this point, we have parsed the entire set of filter
    // components. The list of open parenthesis positions must be empty.
    if (pendingOpens != 0)
    {
      Message message = ERR_LDAP_FILTER_NO_CORRESPONDING_CLOSE_PARENTHESIS
          .get(string, openIndex);
      throw new LocalizedIllegalArgumentException(message);
    }

    if (subFilters != null)
    {
      return Collections.unmodifiableList(subFilters);
    }
    else
    {
      return Collections.singletonList(firstFilter);
    }
  }



  private static Filter valueOfGenericFilter(String string,
      String attributeDescription, int startIndex, int endIndex)
      throws LocalizedIllegalArgumentException
  {
    if (startIndex >= endIndex)
    {
      // Equality filter with empty assertion value.
      return new Filter(new EqualityMatchImpl(attributeDescription,
          ByteString.empty()));
    }
    else if ((endIndex - startIndex == 1)
        && (string.charAt(startIndex) == '*'))
    {
      // Single asterisk is a present filter.
      return newPresentFilter(attributeDescription);
    }
    else
    {
      // Either an equality or substring filter.
      ByteSequence assertionValue = valueOfAssertionValue(string,
          startIndex, endIndex);

      ByteSequence initialString = null;
      ByteSequence finalString = null;
      LinkedList<ByteSequence> anyStrings = null;

      int lastAsteriskIndex = -1;
      int length = assertionValue.length();
      for (int i = 0; i < length; i++)
      {
        if (assertionValue.byteAt(i) == '*')
        {
          if (lastAsteriskIndex == -1)
          {
            if (i > 0)
            {
              // Got an initial substring.
              initialString = assertionValue.subSequence(0, i);
            }
            lastAsteriskIndex = i;
          }
          else
          {
            // Got an any substring.
            if (anyStrings == null)
            {
              anyStrings = new LinkedList<ByteSequence>();
            }

            int s = lastAsteriskIndex + 1;
            if (s == i)
            {
              // A zero length substring.
              Message message = ERR_LDAP_FILTER_BAD_SUBSTRING.get(
                  string, string.subSequence(startIndex, endIndex));
              throw new LocalizedIllegalArgumentException(message);
            }

            anyStrings.add(assertionValue.subSequence(s, i));
            lastAsteriskIndex = i;
          }
        }
      }

      if (lastAsteriskIndex >= 0 && lastAsteriskIndex < length - 1)
      {
        // Got a final substring.
        finalString = assertionValue.subSequence(lastAsteriskIndex + 1,
            length);
      }

      if ((initialString == null) && (anyStrings == null)
          && (finalString == null))
      {
        return new Filter(new EqualityMatchImpl(attributeDescription,
            assertionValue));
      }
      else
      {
        List<ByteSequence> tmp;

        if (anyStrings == null)
        {
          tmp = Collections.emptyList();
        }
        else if (anyStrings.size() == 1)
        {
          tmp = Collections.singletonList(anyStrings.getFirst());
        }
        else
        {
          tmp = Collections.unmodifiableList(anyStrings);
        }

        return new Filter(new SubstringsImpl(attributeDescription,
            initialString, tmp, finalString));
      }
    }
  }



  /**
   * Appends a properly-cleaned version of the provided value to the
   * given builder so that it can be safely used in string
   * representations of this search filter. The formatting changes that
   * may be performed will be in compliance with the specification in
   * RFC 2254.
   *
   * @param builder
   *          The builder to which the "safe" version of the value will
   *          be appended.
   * @param value
   *          The value to be appended to the builder.
   */
  private static void valueToFilterString(StringBuilder builder,
      ByteSequence value)
  {
    // Get the binary representation of the value and iterate through
    // it to see if there are any unsafe characters. If there are,
    // then escape them and replace them with a two-digit hex
    // equivalent.
    builder.ensureCapacity(builder.length() + value.length());
    for (int i = 0; i < value.length(); i++)
    {
      // TODO: this is a bit overkill - it will escape all non-ascii
      // chars!
      byte b = value.byteAt(i);
      if (((b & 0x7F) != b) || // Not 7-bit clean
          (b <= 0x1F) || // Below the printable character range
          (b == 0x28) || // Open parenthesis
          (b == 0x29) || // Close parenthesis
          (b == 0x2A) || // Asterisk
          (b == 0x5C) || // Backslash
          (b == 0x7F)) // Delete character
      {
        builder.append('\\');
        builder.append(byteToHex(b));
      }
      else
      {
        builder.append((char) b);
      }
    }
  }



  private final Impl pimpl;



  private Filter(Impl pimpl)
  {
    this.pimpl = pimpl;
  }



  /**
   * Applies a {@code FilterVisitor} to this {@code Filter}.
   *
   * @param <R>
   *          The return type of the visitor's methods.
   * @param <P>
   *          The type of the additional parameters to the visitor's
   *          methods.
   * @param v
   *          The filter visitor.
   * @param p
   *          Optional additional visitor parameter.
   * @return A result as specified by the visitor.
   */
  public <R, P> R accept(FilterVisitor<R, P> v, P p)
  {
    return pimpl.accept(v, p);
  }



  /**
   * Returns a {@code Matcher} which can be used to compare this {@code
   * Filter} against entries using the provided {@code Schema}.
   *
   * @param schema
   *          The schema which the {@code Matcher} should use for
   *          comparisons.
   * @return The {@code Matcher}.
   */
  public Matcher matcher(Schema schema)
  {
    return new Matcher(this, schema);
  }



  /**
   * Returns a {@code Matcher} which can be used to compare this {@code
   * Filter} against entries using the default schema.
   *
   * @return The {@code Matcher}.
   */
  public Matcher matcher()
  {
    return new Matcher(this, Schema.getDefaultSchema());
  }



  /**
   * Indicates whether this {@code Filter} matches the provided {@code
   * Entry} using the schema associated with the entry.
   * <p>
   * Calling this method is equivalent to the following:
   *
   * <pre>
   * boolean b = matcher(entry.getSchema()).matches(entry);
   * </pre>
   *
   * @param entry
   *          The entry to be matched.
   * @return {@code true} if this {@code Filter} matches the provided
   *         {@code Entry}.
   */
  public ConditionResult matches(Entry entry)
  {
    return matcher(Schema.getDefaultSchema()).matches(entry);
  }



  /**
   * Returns a {@code String} whose contents is the LDAP string
   * representation of this {@code Filter}.
   *
   * @return The LDAP string representation of this {@code Filter}.
   */
  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    return toString(builder).toString();
  }



  /**
   * Appends the LDAP string representation of this {@code Filter} to
   * the provided {@code StringBuilder}.
   *
   * @param builder
   *          The {@code StringBuilder} to which the LDAP string
   *          representation of this {@code Filter} should be appended.
   * @return The updated {@code StringBuilder}.
   */
  public StringBuilder toString(StringBuilder builder)
  {
    return pimpl.accept(TO_STRING_VISITOR, builder);
  }

}
