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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */
package org.opends.server.types;



import static org.opends.messages.SchemaMessages.*;

import java.util.*;
import java.util.regex.Pattern;

import org.opends.messages.Message;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.StaticUtils;



/**
 * An RFC 3672 subtree specification.
 * <p>
 * This implementation extends RFC 3672 by supporting search filters
 * for specification filters. More specifically, the
 * {@code Refinement} product has been extended as follows:
 *
 * <pre>
 *  Refinement = item / and / or / not / Filter
 *
 *  Filter     = dquote *SafeUTF8Character dquote
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc3672">RFC 3672 -
 *      Subentries inthe Lightweight Directory Access Protocol (LDAP)
 *      </a>
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate = false,
    mayExtend = true,
    mayInvoke = false)
public final class SubtreeSpecification
{

  /**
   * RFC 3672 subtree specification AND refinement. This type of
   * refinement filters entries based on all of the underlying
   * refinements being <code>true</code>.
   */
  public static final class AndRefinement extends Refinement
  {
    // The set of refinements which must all be true.
    private final Collection<Refinement> refinementSet;



    /**
     * Create a new AND refinement.
     *
     * @param refinementSet
     *          The set of refinements which must all be
     *          <code>true</code>.
     */
    public AndRefinement(final Collection<Refinement> refinementSet)
    {

      this.refinementSet = refinementSet;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj)
    {

      if (this == obj)
      {
        return true;
      }

      if (obj instanceof AndRefinement)
      {
        final AndRefinement other = (AndRefinement) obj;

        return refinementSet.equals(other.refinementSet);
      }

      return false;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {

      return refinementSet.hashCode();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(final Entry entry)
    {
      for (final Refinement refinement : refinementSet)
      {
        if (refinement.matches(entry) == false)
        {
          return false;
        }
      }

      // All sub-refinements matched.
      return true;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public StringBuilder toString(final StringBuilder builder)
    {
      switch (refinementSet.size())
      {
      case 0:
        // Do nothing.
        break;
      case 1:
        refinementSet.iterator().next().toString(builder);
        break;
      default:
        builder.append("and:{");
        final Iterator<Refinement> iterator = refinementSet
            .iterator();
        iterator.next().toString(builder);
        while (iterator.hasNext())
        {
          builder.append(", ");
          iterator.next().toString(builder);
        }
        builder.append("}");
        break;
      }

      return builder;
    }
  }



  /**
   * A refinement which uses a search filter.
   */
  public static final class FilterRefinement extends Refinement
  {
    // The search filter.
    private final SearchFilter filter;



    /**
     * Create a new filter refinement.
     *
     * @param filter
     *          The search filter.
     */
    public FilterRefinement(final SearchFilter filter)
    {

      this.filter = filter;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj)
    {

      if (this == obj)
      {
        return true;
      }

      if (obj instanceof FilterRefinement)
      {
        final FilterRefinement other = (FilterRefinement) obj;
        return filter.equals(other.filter);
      }

      return false;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {

      return filter.hashCode();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(final Entry entry)
    {
      try
      {
        return filter.matchesEntry(entry);
      }
      catch (final DirectoryException e)
      {
        // TODO: need to decide what to do with the exception here.
        // It's probably safe to ignore, but we could log it perhaps.
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public StringBuilder toString(final StringBuilder builder)
    {
      StaticUtils.toRFC3641StringValue(builder, filter.toString());
      return builder;
    }
  }



  /**
   * RFC 3672 subtree specification Item refinement. This type of
   * refinement filters entries based on the presence of a specified
   * object class.
   */
  public static final class ItemRefinement extends Refinement
  {
    // The item's object class.
    private final String objectClass;

    // The item's normalized object class.
    private final String normalizedObjectClass;



    /**
     * Create a new item refinement.
     *
     * @param objectClass
     *          The item's object class.
     */
    public ItemRefinement(final String objectClass)
    {

      this.objectClass = objectClass;
      this.normalizedObjectClass = StaticUtils
          .toLowerCase(objectClass.trim());
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj)
    {

      if (this == obj)
      {
        return true;
      }

      if (obj instanceof ItemRefinement)
      {
        final ItemRefinement other = (ItemRefinement) obj;

        return normalizedObjectClass
            .equals(other.normalizedObjectClass);
      }

      return false;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {

      return normalizedObjectClass.hashCode();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(final Entry entry)
    {
      final ObjectClass oc = DirectoryServer
          .getObjectClass(normalizedObjectClass);

      if (oc == null)
      {
        return false;
      }
      else
      {
        return entry.hasObjectClass(oc);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public StringBuilder toString(final StringBuilder builder)
    {
      builder.append("item:");
      builder.append(objectClass);
      return builder;
    }
  }



  /**
   * RFC 3672 subtree specification NOT refinement. This type of
   * refinement filters entries based on the underlying refinement
   * being <code>false</code>
   * .
   */
  public static final class NotRefinement extends Refinement
  {
    // The inverted refinement.
    private final Refinement refinement;



    /**
     * Create a new NOT refinement.
     *
     * @param refinement
     *          The refinement which must be <code>false</code>.
     */
    public NotRefinement(final Refinement refinement)
    {

      this.refinement = refinement;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj)
    {

      if (this == obj)
      {
        return true;
      }

      if (obj instanceof NotRefinement)
      {
        final NotRefinement other = (NotRefinement) obj;

        return refinement.equals(other.refinement);
      }

      return false;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {

      return refinement.hashCode();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(final Entry entry)
    {
      return !refinement.matches(entry);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public StringBuilder toString(final StringBuilder builder)
    {
      builder.append("not:");
      return refinement.toString(builder);
    }
  }



  /**
   * RFC 3672 subtree specification OR refinement. This type of
   * refinement filters entries based on at least one of the
   * underlying refinements being <code>true</code>.
   */
  public static final class OrRefinement extends Refinement
  {
    // The set of refinements of which at least one must be true.
    private final Collection<Refinement> refinementSet;



    /**
     * Create a new OR refinement.
     *
     * @param refinementSet
     *          The set of refinements of which at least one must be
     *          <code>true</code>.
     */
    public OrRefinement(final Collection<Refinement> refinementSet)
    {

      this.refinementSet = refinementSet;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj)
    {

      if (this == obj)
      {
        return true;
      }

      if (obj instanceof AndRefinement)
      {
        final AndRefinement other = (AndRefinement) obj;

        return refinementSet.equals(other.refinementSet);
      }

      return false;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {

      return refinementSet.hashCode();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(final Entry entry)
    {
      for (final Refinement refinement : refinementSet)
      {
        if (refinement.matches(entry) == true)
        {
          return true;
        }
      }

      // No sub-refinements matched.
      return false;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public StringBuilder toString(final StringBuilder builder)
    {
      switch (refinementSet.size())
      {
      case 0:
        // Do nothing.
        break;
      case 1:
        refinementSet.iterator().next().toString(builder);
        break;
      default:
        builder.append("or:{");
        final Iterator<Refinement> iterator = refinementSet
            .iterator();
        iterator.next().toString(builder);
        while (iterator.hasNext())
        {
          builder.append(", ");
          iterator.next().toString(builder);
        }
        builder.append("}");
        break;
      }

      return builder;
    }
  }



  /**
   * Abstract interface for RFC3672 specification filter refinements.
   */
  public static abstract class Refinement
  {
    /**
     * Create a new RFC3672 specification filter refinement.
     */
    protected Refinement()
    {
      // No implementation required.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public abstract boolean equals(Object obj);



    /**
     * {@inheritDoc}
     */
    @Override
    public abstract int hashCode();



    /**
     * Check if the refinement matches the given entry.
     *
     * @param entry
     *          The filterable entry.
     * @return Returns <code>true</code> if the entry matches the
     *         refinement, or <code>false</code> otherwise.
     */
    public abstract boolean matches(Entry entry);



    /**
     * {@inheritDoc}
     */
    @Override
    public final String toString()
    {
      final StringBuilder builder = new StringBuilder();

      return toString(builder).toString();
    }



    /**
     * Append the string representation of the refinement to the
     * provided strin builder.
     *
     * @param builder
     *          The string builder.
     * @return The string builder.
     */
    public abstract StringBuilder toString(StringBuilder builder);
  }



  /**
   * Internal utility class which can be used by sub-classes to help
   * parse string representations.
   */
  protected static final class Parser
  {
    // Text scanner used to parse the string value.
    private final Scanner scanner;

    // Pattern used to detect left braces.
    private static Pattern LBRACE = Pattern.compile("\\{.*");

    // Pattern used to parse left braces.
    private static Pattern LBRACE_TOKEN = Pattern.compile("\\{");

    // Pattern used to detect right braces.
    private static Pattern RBRACE = Pattern.compile("\\}.*");

    // Pattern used to parse right braces.
    private static Pattern RBRACE_TOKEN = Pattern.compile("\\}");

    // Pattern used to detect comma separators.
    private static Pattern SEP = Pattern.compile(",.*");

    // Pattern used to parse comma separators.
    private static Pattern SEP_TOKEN = Pattern.compile(",");

    // Pattern used to detect colon separators.
    private static Pattern COLON = Pattern.compile(":.*");

    // Pattern used to parse colon separators.
    private static Pattern COLON_TOKEN = Pattern.compile(":");

    // Pattern used to detect integer values.
    private static Pattern INT = Pattern.compile("\\d.*");

    // Pattern used to parse integer values.
    private static Pattern INT_TOKEN = Pattern.compile("\\d+");

    // Pattern used to detect name values.
    private static Pattern NAME = Pattern.compile("[\\w_;-].*");

    // Pattern used to parse name values.
    private static Pattern NAME_TOKEN = Pattern.compile("[\\w_;-]+");

    // Pattern used to detect RFC3641 string values.
    private static Pattern STRING_VALUE = Pattern.compile("\".*");

    // Pattern used to parse RFC3641 string values.
    private static Pattern STRING_VALUE_TOKEN = Pattern
        .compile("\"([^\"]|(\"\"))*\"");



    /**
     * Create a new parser for a subtree specification string value.
     *
     * @param value
     *          The subtree specification string value.
     */
    public Parser(final String value)
    {
      this.scanner = new Scanner(value);
    }



    /**
     * Determine if there are remaining tokens.
     *
     * @return <code>true</code> if and only if there are remaining
     *         tokens.
     */
    public boolean hasNext()
    {
      return scanner.hasNext();
    }



    /**
     * Determine if the next token is a right-brace character.
     *
     * @return <code>true</code> if and only if the next token is a
     *         valid right brace character.
     */
    public boolean hasNextRightBrace()
    {
      return scanner.hasNext(RBRACE);
    }



    /**
     * Scans the next token of the input as a non-negative
     * <code>int</code> value.
     *
     * @return The name value scanned from the input.
     * @throws InputMismatchException
     *           If the next token is not a valid non-negative integer
     *           string.
     * @throws NoSuchElementException
     *           If input is exhausted.
     */
    public int nextInt() throws InputMismatchException,
        NoSuchElementException
    {
      final String s = nextValue(INT, INT_TOKEN);
      return Integer.parseInt(s);
    }



    /**
     * Scans the next token of the input as a key value.
     *
     * @return The lower-case key value scanned from the input.
     * @throws InputMismatchException
     *           If the next token is not a valid key string.
     * @throws NoSuchElementException
     *           If input is exhausted.
     */
    public String nextKey() throws InputMismatchException,
        NoSuchElementException
    {
      return StaticUtils.toLowerCase(scanner.next());
    }



    /**
     * Scans the next token of the input as a name value.
     * <p>
     * A name is any string containing only alpha-numeric characters
     * or hyphens, semi-colons, or underscores.
     *
     * @return The name value scanned from the input.
     * @throws InputMismatchException
     *           If the next token is not a valid name string.
     * @throws NoSuchElementException
     *           If input is exhausted.
     */
    public String nextName() throws InputMismatchException,
        NoSuchElementException
    {
      return nextValue(NAME, NAME_TOKEN);
    }



    /**
     * Scans the next tokens of the input as a set of specific
     * exclusions encoded according to the SpecificExclusion
     * production in RFC 3672.
     *
     * @param chopBefore
     *          The set of chop before local names.
     * @param chopAfter
     *          The set of chop after local names.
     * @throws InputMismatchException
     *           If the common component did not have a valid syntax.
     * @throws NoSuchElementException
     *           If input is exhausted.
     * @throws DirectoryException
     *           If an error occurred when attempting to parse a
     *           DN value.
     */
    public void nextSpecificExclusions(final Set<DN> chopBefore,
        final Set<DN> chopAfter) throws InputMismatchException,
        NoSuchElementException, DirectoryException
    {

      // Skip leading open-brace.
      skipLeftBrace();

      // Parse each chop DN in the sequence.
      boolean isFirstValue = true;
      while (true)
      {
        // Make sure that there is a closing brace.
        if (hasNextRightBrace())
        {
          skipRightBrace();
          break;
        }

        // Make sure that there is a comma separator if this is not
        // the first element.
        if (!isFirstValue)
        {
          skipSeparator();
        }
        else
        {
          isFirstValue = false;
        }

        // Parse each chop specification which is of the form
        // <type>:<value>.
        final String type = StaticUtils.toLowerCase(nextName());
        skipColon();
        if (type.equals("chopbefore"))
        {
          chopBefore.add(DN.decode(nextStringValue()));
        }
        else if (type.equals("chopafter"))
        {
          chopAfter.add(DN.decode(nextStringValue()));
        }
        else
        {
          throw new java.util.InputMismatchException();
        }
      }
    }



    /**
     * Scans the next token of the input as a string quoted according
     * to the StringValue production in RFC 3641.
     * <p>
     * The return string has its outer double quotes removed and any
     * escape inner double quotes unescaped.
     *
     * @return The string value scanned from the input.
     * @throws InputMismatchException
     *           If the next token is not a valid string.
     * @throws NoSuchElementException
     *           If input is exhausted.
     */
    public String nextStringValue() throws InputMismatchException,
        NoSuchElementException
    {
      final String s = nextValue(STRING_VALUE, STRING_VALUE_TOKEN);
      return s.substring(1, s.length() - 1).replace("\"\"", "\"");
    }



    /**
     * Skip a colon separator.
     *
     * @throws InputMismatchException
     *           If the next token is not a colon separator character.
     * @throws NoSuchElementException
     *           If input is exhausted.
     */
    public void skipColon() throws InputMismatchException,
        NoSuchElementException
    {
      nextValue(COLON, COLON_TOKEN);
    }



    /**
     * Skip a left-brace character.
     *
     * @throws InputMismatchException
     *           If the next token is not a left-brace character.
     * @throws NoSuchElementException
     *           If input is exhausted.
     */
    public void skipLeftBrace() throws InputMismatchException,
        NoSuchElementException
    {
      nextValue(LBRACE, LBRACE_TOKEN);
    }



    /**
     * Skip a right-brace character.
     *
     * @throws InputMismatchException
     *           If the next token is not a right-brace character.
     * @throws NoSuchElementException
     *           If input is exhausted.
     */
    public void skipRightBrace() throws InputMismatchException,
        NoSuchElementException
    {
      nextValue(RBRACE, RBRACE_TOKEN);
    }



    /**
     * Skip a comma separator.
     *
     * @throws InputMismatchException
     *           If the next token is not a comma separator character.
     * @throws NoSuchElementException
     *           If input is exhausted.
     */
    public void skipSeparator() throws InputMismatchException,
        NoSuchElementException
    {
      nextValue(SEP, SEP_TOKEN);
    }



    /**
     * Parse the next token from the string using the specified
     * patterns.
     *
     * @param head
     *          The pattern used to determine if the next token is a
     *          possible match.
     * @param content
     *          The pattern used to parse the token content.
     * @return The next token matching the <code>content</code>
     *         pattern.
     * @throws InputMismatchException
     *           If the next token does not match the
     *           <code>content</code> pattern.
     * @throws NoSuchElementException
     *           If input is exhausted.
     */
    private String nextValue(final Pattern head,
        final Pattern content)
        throws InputMismatchException, NoSuchElementException
    {
      if (!scanner.hasNext())
      {
        throw new java.util.NoSuchElementException();
      }

      if (!scanner.hasNext(head))
      {
        throw new java.util.InputMismatchException();
      }

      final String s = scanner.findInLine(content);
      if (s == null)
      {
        throw new java.util.InputMismatchException();
      }

      return s;
    }
  }



  /**
   * Parses the string argument as an RFC3672 subtree specification.
   *
   * @param rootDN
   *          The DN of the subtree specification's base entry.
   * @param s
   *          The string to be parsed.
   * @return The RFC3672 subtree specification represented by the
   *         string argument.
   * @throws DirectoryException
   *           If the string does not contain a parsable relative
   *           subtree specification.
   */
  public static SubtreeSpecification valueOf(final DN rootDN,
      final String s) throws DirectoryException
  {

    // Default values.
    DN relativeBaseDN = null;

    int minimum = -1;
    int maximum = -1;

    final HashSet<DN> chopBefore = new HashSet<DN>();
    final HashSet<DN> chopAfter = new HashSet<DN>();

    Refinement refinement = null;

    // Value must have an opening left brace.
    final Parser parser = new Parser(s);
    boolean isValid = true;

    try
    {
      parser.skipLeftBrace();

      // Parse each element of the value sequence.
      boolean isFirst = true;

      while (true)
      {
        if (parser.hasNextRightBrace())
        {
          // Make sure that there is a closing brace and no trailing
          // text.
          parser.skipRightBrace();

          if (parser.hasNext())
          {
            throw new java.util.InputMismatchException();
          }
          break;
        }

        // Make sure that there is a comma separator if this is not
        // the first element.
        if (!isFirst)
        {
          parser.skipSeparator();
        }
        else
        {
          isFirst = false;
        }

        final String key = parser.nextKey();
        if (key.equals("base"))
        {
          if (relativeBaseDN != null)
          {
            // Relative base DN specified more than once.
            throw new InputMismatchException();
          }
          relativeBaseDN = DN.decode(parser.nextStringValue());
        }
        else if (key.equals("minimum"))
        {
          if (minimum != -1)
          {
            // Minimum specified more than once.
            throw new InputMismatchException();
          }
          minimum = parser.nextInt();
        }
        else if (key.equals("maximum"))
        {
          if (maximum != -1)
          {
            // Maximum specified more than once.
            throw new InputMismatchException();
          }
          maximum = parser.nextInt();
        }
        else if (key.equals("specificationfilter"))
        {
          if (refinement != null)
          {
            // Refinements specified more than once.
            throw new InputMismatchException();
          }

          // First try normal search filter before RFC3672
          // refinements.
          try
          {
            final SearchFilter filter = SearchFilter
                .createFilterFromString(parser.nextStringValue());
            refinement = new FilterRefinement(filter);
          }
          catch (final InputMismatchException e)
          {
            refinement = parseRefinement(parser);
          }
        }
        else if (key.equals("specificexclusions"))
        {
          if (!chopBefore.isEmpty() || !chopAfter.isEmpty())
          {
            // Specific exclusions specified more than once.
            throw new InputMismatchException();
          }

          parser.nextSpecificExclusions(chopBefore, chopAfter);
        }
        else
        {
          throw new InputMismatchException();
        }
      }

      // Make default minimum value is 0.
      if (minimum < 0)
      {
        minimum = 0;
      }

      // Check that the maximum, if specified, is gte the minimum.
      if (maximum >= 0 && maximum < minimum)
      {
        isValid = false;
      }
    }
    catch (final InputMismatchException e)
    {
      isValid = false;
    }
    catch (final NoSuchElementException e)
    {
      isValid = false;
    }

    if (isValid)
    {
      return new SubtreeSpecification(rootDN, relativeBaseDN,
          minimum, maximum, chopBefore, chopAfter, refinement);
    }
    else
    {
      final Message message =
        ERR_ATTR_SYNTAX_RFC3672_SUBTREE_SPECIFICATION_INVALID.get(s);
      throw new DirectoryException(
          ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }
  }



  /**
   * Parse a single refinement.
   *
   * @param parser
   *          The active subtree specification parser.
   * @return The parsed refinement.
   * @throws InputMismatchException
   *           If the common component did not have a valid syntax.
   * @throws NoSuchElementException
   *           If input is exhausted.
   */
  private static Refinement parseRefinement(final Parser parser)
      throws InputMismatchException, NoSuchElementException
  {
    // Get the type of refinement.
    final String type = StaticUtils.toLowerCase(parser.nextName());

    // Skip the colon separator.
    parser.skipColon();

    if (type.equals("item"))
    {
      return new ItemRefinement(parser.nextName());
    }
    else if (type.equals("not"))
    {
      final Refinement refinement = parseRefinement(parser);
      return new NotRefinement(refinement);
    }
    else if (type.equals("and"))
    {
      final ArrayList<Refinement> refinements =
        parseRefinementSet(parser);
      return new AndRefinement(refinements);
    }
    else if (type.equals("or"))
    {
      final ArrayList<Refinement> refinements =
        parseRefinementSet(parser);
      return new OrRefinement(refinements);
    }
    else
    {
      // Unknown refinement type.
      throw new InputMismatchException();
    }
  }



  /**
   * Parse a list of refinements.
   *
   * @param parser
   *          The active subtree specification parser.
   * @return The parsed refinement list.
   * @throws InputMismatchException
   *           If the common component did not have a valid syntax.
   * @throws NoSuchElementException
   *           If input is exhausted.
   */
  private static ArrayList<Refinement> parseRefinementSet(
      final Parser parser) throws InputMismatchException,
      NoSuchElementException
  {
    final ArrayList<Refinement> refinements =
      new ArrayList<Refinement>();

    // Skip leading open-brace.
    parser.skipLeftBrace();

    // Parse each chop DN in the sequence.
    boolean isFirstValue = true;
    while (true)
    {
      // Make sure that there is a closing brace.
      if (parser.hasNextRightBrace())
      {
        parser.skipRightBrace();
        break;
      }

      // Make sure that there is a comma separator if this is not
      // the first element.
      if (!isFirstValue)
      {
        parser.skipSeparator();
      }
      else
      {
        isFirstValue = false;
      }

      // Parse each sub-refinement.
      final Refinement refinement = parseRefinement(parser);
      refinements.add(refinement);
    }

    return refinements;
  }



  // The absolute base of the subtree.
  private final DN baseDN;

  // Optional minimum depth (<=0 means unlimited).
  private final int minimumDepth;

  // Optional maximum depth (<0 means unlimited).
  private final int maximumDepth;

  // Optional set of chop before absolute DNs (mapping to their
  // local-names).
  private final Map<DN, DN> chopBefore;

  // Optional set of chop after absolute DNs (mapping to their
  // local-names).
  private final Map<DN, DN> chopAfter;

  // The root DN.
  private final DN rootDN;

  // The optional relative base DN.
  private final DN relativeBaseDN;

  // The optional specification filter refinements.
  private final Refinement refinements;



  /**
   * Create a new RFC3672 subtree specification.
   *
   * @param rootDN
   *          The root DN of the subtree.
   * @param relativeBaseDN
   *          The relative base DN (or <code>null</code> if not
   *          specified).
   * @param minimumDepth
   *          The minimum depth (<=0 means unlimited).
   * @param maximumDepth
   *          The maximum depth (<0 means unlimited).
   * @param chopBefore
   *          The set of chop before local names (relative to the
   *          relative base DN), or <code>null</code> if there are
   *          none.
   * @param chopAfter
   *          The set of chop after local names (relative to the
   *          relative base DN), or <code>null</code> if there are
   *          none.
   * @param refinements
   *          The optional specification filter refinements, or
   *          <code>null</code> if there are none.
   */
  public SubtreeSpecification(final DN rootDN,
      final DN relativeBaseDN, final int minimumDepth,
      final int maximumDepth, final Iterable<DN> chopBefore,
      final Iterable<DN> chopAfter, final Refinement refinements)
  {
    this.baseDN = relativeBaseDN == null ? rootDN : rootDN
        .concat(relativeBaseDN);
    this.minimumDepth = minimumDepth;
    this.maximumDepth = maximumDepth;

    if (chopBefore != null && chopBefore.iterator().hasNext())
    {
      // Calculate the absolute DNs.
      final TreeMap<DN, DN> map = new TreeMap<DN, DN>();
      for (final DN localName : chopBefore)
      {
        map.put(baseDN.concat(localName), localName);
      }
      this.chopBefore = Collections.unmodifiableMap(map);
    }
    else
    {
      // No chop before specifications.
      this.chopBefore = Collections.emptyMap();
    }

    if (chopAfter != null && chopAfter.iterator().hasNext())
    {
      // Calculate the absolute DNs.
      final TreeMap<DN, DN> map = new TreeMap<DN, DN>();
      for (final DN localName : chopAfter)
      {
        map.put(baseDN.concat(localName), localName);
      }
      this.chopAfter = Collections.unmodifiableMap(map);
    }
    else
    {
      // No chop after specifications.
      this.chopAfter = Collections.emptyMap();
    }

    this.rootDN = rootDN;
    this.relativeBaseDN = relativeBaseDN;
    this.refinements = refinements;
  }



  /**
   * Indicates whether the provided object is logically equal to this
   * subtree specification object.
   *
   * @param obj
   *          The object for which to make the determination.
   * @return {@code true} if the provided object is logically equal
   *         to this subtree specification object, or {@code false}
   *         if not.
   */
  @Override
  public boolean equals(final Object obj)
  {

    if (this == obj)
    {
      return true;
    }

    if (obj instanceof SubtreeSpecification)
    {
      final SubtreeSpecification other = (SubtreeSpecification) obj;

      if (!commonComponentsEquals(other))
      {
        return false;
      }

      if (!getBaseDN().equals(other.getBaseDN()))
      {
        return false;
      }

      if (refinements != null)
      {
        return refinements.equals(other.refinements);
      }
      else
      {
        return refinements == other.refinements;
      }
    }

    return false;
  }



  /**
   * Get the absolute base DN of the subtree specification.
   *
   * @return Returns the absolute base DN of the subtree
   *         specification.
   */
  public DN getBaseDN()
  {
    return baseDN;
  }



  /**
   * Get the set of chop after relative DNs.
   *
   * @return Returns the set of chop after relative DNs.
   */
  public Iterable<DN> getChopAfter()
  {

    return chopAfter.values();
  }



  /**
   * Get the set of chop before relative DNs.
   *
   * @return Returns the set of chop before relative DNs.
   */
  public Iterable<DN> getChopBefore()
  {

    return chopBefore.values();
  }



  /**
   * Get the maximum depth of the subtree specification.
   *
   * @return Returns the maximum depth (<0 indicates unlimited depth).
   */
  public int getMaximumDepth()
  {
    return maximumDepth;
  }



  /**
   * Get the minimum depth of the subtree specification.
   *
   * @return Returns the minimum depth (<=0 indicates unlimited
   *         depth).
   */
  public int getMinimumDepth()
  {
    return minimumDepth;
  }



  /**
   * Get the specification filter refinements.
   *
   * @return Returns the specification filter refinements, or
   *         <code>null</code> if none were specified.
   */
  public Refinement getRefinements()
  {
    return refinements;
  }



  /**
   * Get the relative base DN.
   *
   * @return Returns the relative base DN or <code>null</code> if
   *         none was specified.
   */
  public DN getRelativeBaseDN()
  {
    return relativeBaseDN;
  }



  /**
   * Get the root DN.
   *
   * @return Returns the root DN.
   */
  public DN getRootDN()
  {
    return rootDN;
  }



  /**
   * Retrieves the hash code for this subtree specification object.
   *
   * @return The hash code for this subtree specification object.
   */
  @Override
  public int hashCode()
  {

    int hash = commonComponentsHashCode();

    hash = hash * 31 + getBaseDN().hashCode();

    if (refinements != null)
    {
      hash = hash * 31 + refinements.hashCode();
    }

    return hash;
  }



  /**
   * Determine if the specified DN is within the scope of the subtree
   * specification.
   *
   * @param dn
   *          The distinguished name.
   * @return Returns <code>true</code> if the DN is within the scope
   *         of the subtree specification, or <code>false</code>
   *         otherwise.
   */
  public boolean isDNWithinScope(final DN dn)
  {

    if (!dn.isDescendantOf(baseDN))
    {
      return false;
    }

    // Check minimum and maximum depths.
    final int baseRDNCount = baseDN.getNumComponents();

    if (minimumDepth > 0)
    {
      final int entryRDNCount = dn.getNumComponents();

      if (entryRDNCount - baseRDNCount < minimumDepth)
      {
        return false;
      }
    }

    if (maximumDepth >= 0)
    {
      final int entryRDNCount = dn.getNumComponents();

      if (entryRDNCount - baseRDNCount > maximumDepth)
      {
        return false;
      }
    }

    // Check exclusions.
    for (final DN chopBeforeDN : chopBefore.keySet())
    {
      if (dn.isDescendantOf(chopBeforeDN))
      {
        return false;
      }
    }

    for (final DN chopAfterDN : chopAfter.keySet())
    {
      if (!dn.equals(chopAfterDN) && dn.isDescendantOf(chopAfterDN))
      {
        return false;
      }
    }

    // Everything seemed to match.
    return true;
  }



  /**
   * Determine if an entry is within the scope of the subtree
   * specification.
   *
   * @param entry
   *          The entry.
   * @return {@code true} if the entry is within the scope of the
   *         subtree specification, or {@code false} if not.
   */
  public boolean isWithinScope(final Entry entry)
  {

    if (isDNWithinScope(entry.getDN()))
    {
      if (refinements != null)
      {
        return refinements.matches(entry);
      }
      else
      {
        return true;
      }
    }
    else
    {
      return false;
    }
  }



  /**
   * Retrieves a string representation of this subtree specification
   * object.
   *
   * @return A string representation of this subtree specification
   *         object.
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    return toString(builder).toString();
  }



  /**
   * Append the string representation of the subtree specification to
   * the provided string builder.
   *
   * @param builder
   *          The string builder.
   * @return The string builder.
   */
  public StringBuilder toString(final StringBuilder builder)
  {

    boolean isFirstElement = true;

    // Output the optional base DN.
    builder.append("{");
    if (relativeBaseDN != null && !relativeBaseDN.isNullDN())
    {
      builder.append(" base ");
      StaticUtils.toRFC3641StringValue(builder,
          relativeBaseDN.toString());
      isFirstElement = false;
    }

    // Output the optional specific exclusions.
    final Iterable<DN> chopBefore = getChopBefore();
    final Iterable<DN> chopAfter = getChopAfter();

    if ((chopBefore.iterator().hasNext())
        || (chopAfter.iterator().hasNext()))
    {

      if (!isFirstElement)
      {
        builder.append(",");
      }
      else
      {
        isFirstElement = false;
      }
      builder.append(" specificExclusions { ");

      boolean isFirst = true;

      for (final DN dn : chopBefore)
      {
        if (!isFirst)
        {
          builder.append(", chopBefore:");
        }
        else
        {
          builder.append("chopBefore:");
          isFirst = false;
        }
        StaticUtils.toRFC3641StringValue(builder, dn.toString());
      }

      for (final DN dn : chopAfter)
      {
        if (!isFirst)
        {
          builder.append(", chopAfter:");
        }
        else
        {
          builder.append("chopAfter:");
          isFirst = false;
        }
        StaticUtils.toRFC3641StringValue(builder, dn.toString());
      }

      builder.append(" }");
    }

    // Output the optional minimum depth.
    if (getMinimumDepth() > 0)
    {
      if (!isFirstElement)
      {
        builder.append(",");
      }
      else
      {
        isFirstElement = false;
      }
      builder.append(" minimum ");
      builder.append(getMinimumDepth());
    }

    // Output the optional maximum depth.
    if (getMaximumDepth() >= 0)
    {
      if (!isFirstElement)
      {
        builder.append(",");
      }
      else
      {
        isFirstElement = false;
      }
      builder.append(" maximum ");
      builder.append(getMaximumDepth());
    }

    // Output the optional refinements.
    if (refinements != null)
    {
      if (!isFirstElement)
      {
        builder.append(",");
      }
      else
      {
        isFirstElement = false;
      }
      builder.append(" specificationFilter ");
      refinements.toString(builder);
    }

    builder.append(" }");

    return builder;
  }



  /**
   * Determine if the common components of this subtree specification
   * are equal to the common components of another subtree
   * specification.
   *
   * @param other
   *          The other subtree specification.
   * @return Returns <code>true</code> if they are equal.
   */
  private boolean commonComponentsEquals(
      final SubtreeSpecification other)
  {

    if (this == other)
    {
      return true;
    }

    if (minimumDepth != other.minimumDepth)
    {
      return false;
    }

    if (maximumDepth != other.maximumDepth)
    {
      return false;
    }

    if (!chopBefore.values().equals(other.chopBefore.values()))
    {
      return false;
    }

    if (!chopAfter.values().equals(other.chopAfter.values()))
    {
      return false;
    }

    return true;
  }



  /**
   * Get a hash code of the subtree specification's common components.
   *
   * @return The computed hash code.
   */
  private int commonComponentsHashCode()
  {
    int hash = minimumDepth * 31 + maximumDepth;
    hash = hash * 31 + chopBefore.values().hashCode();
    hash = hash * 31 + chopAfter.values().hashCode();
    return hash;
  }
}
