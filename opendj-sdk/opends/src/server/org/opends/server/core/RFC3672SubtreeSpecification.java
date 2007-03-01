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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;

import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.SchemaMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ResultCode;
import org.opends.server.util.StaticUtils;

/**
 * An RFC 3672 subtree specification.
 * <p>
 * Refer to RFC 3672 for a detailed definition of the subtree
 * specification string representation.
 */
public final class RFC3672SubtreeSpecification extends
    SimpleSubtreeSpecification {

  // The root DN.
  private DN rootDN;

  // The optional relative base DN.
  private DN relativeBaseDN;

  // The optional specification filter refinements.
  private Refinement refinements;

  /**
   * Abstract interface for RFC3672 specification filter refinements.
   */
  public static abstract class Refinement {
    /**
     * Create a new RFC3672 specification filter refinement.
     */
    protected Refinement() {
      // No implementation required.
    }

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
    public final String toString() {
      StringBuilder builder = new StringBuilder();

      return toString(builder).toString();
    }

    /**
     * Append the string representation of the refinement to the
     * provided string builder.
     *
     * @param builder
     *          The string builder.
     * @return The string builder.
     */
    public abstract StringBuilder toString(StringBuilder builder);

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
  }

  /**
   * RFC 3672 subtree specification Item refinement. This type of
   * refinement filters entries based on the presence of a specified
   * object class.
   */
  public static final class ItemRefinement extends Refinement {
    // The item's object class.
    private String objectClass;

    // The item's normalized object class.
    private String normalizedObjectClass;

    /**
     * Create a new item refinement.
     *
     * @param objectClass
     *          The item's object class.
     */
    public ItemRefinement(String objectClass) {

      this.objectClass = objectClass;
      this.normalizedObjectClass = StaticUtils.toLowerCase(objectClass
          .trim());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(Entry entry) {
      ObjectClass oc = DirectoryServer
          .getObjectClass(normalizedObjectClass);

      if (oc == null) {
        return false;
      } else {
        return entry.hasObjectClass(oc);
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StringBuilder toString(StringBuilder builder) {
      builder.append("item:");
      builder.append(objectClass);
      return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {

      if (this == obj) {
        return true;
      }

      if (obj instanceof ItemRefinement) {
        ItemRefinement other = (ItemRefinement) obj;

        return normalizedObjectClass.equals(other.normalizedObjectClass);
      }

      return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {

      return normalizedObjectClass.hashCode();
    }
  }

  /**
   * RFC 3672 subtree specification NOT refinement. This type of
   * refinement filters entries based on the underlying refinement being
   * <code>false</code>.
   */
  public static final class NotRefinement extends Refinement {
    // The inverted refinement.
    private Refinement refinement;

    /**
     * Create a new NOT refinement.
     *
     * @param refinement
     *          The refinement which must be <code>false</code>.
     */
    public NotRefinement(Refinement refinement) {

      this.refinement = refinement;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(Entry entry) {
      return !refinement.matches(entry);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StringBuilder toString(StringBuilder builder) {
      builder.append("not:");
      return refinement.toString(builder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {

      if (this == obj) {
        return true;
      }

      if (obj instanceof NotRefinement) {
        NotRefinement other = (NotRefinement) obj;

        return refinement.equals(other.refinement);
      }

      return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {

      return refinement.hashCode();
    }
  }

  /**
   * RFC 3672 subtree specification AND refinement. This type of
   * refinement filters entries based on all of the underlying
   * refinements being <code>true</code>.
   */
  public static final class AndRefinement extends Refinement {
    // The set of refinements which must all be true.
    private Collection<Refinement> refinementSet;

    /**
     * Create a new AND refinement.
     *
     * @param refinementSet
     *          The set of refinements which must all be
     *          <code>true</code>.
     */
    public AndRefinement(Collection<Refinement> refinementSet) {

      this.refinementSet = refinementSet;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(Entry entry) {
      for (Refinement refinement : refinementSet) {
        if (refinement.matches(entry) == false) {
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
    public StringBuilder toString(StringBuilder builder) {
      switch (refinementSet.size()) {
      case 0:
        // Do nothing.
        break;
      case 1:
        refinementSet.iterator().next().toString(builder);
        break;
      default:
        builder.append("and:{");
        Iterator<Refinement> iterator = refinementSet.iterator();
        iterator.next().toString(builder);
        while (iterator.hasNext()) {
          builder.append(", ");
          iterator.next().toString(builder);
        }
        builder.append("}");
        break;
      }

      return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {

      if (this == obj) {
        return true;
      }

      if (obj instanceof AndRefinement) {
        AndRefinement other = (AndRefinement) obj;

        return refinementSet.equals(other.refinementSet);
      }

      return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {

      return refinementSet.hashCode();
    }
  }

  /**
   * RFC 3672 subtree specification OR refinement. This type of
   * refinement filters entries based on at least one of the underlying
   * refinements being <code>true</code>.
   */
  public static final class OrRefinement extends Refinement {
    // The set of refinements of which at least one must be true.
    private Collection<Refinement> refinementSet;

    /**
     * Create a new OR refinement.
     *
     * @param refinementSet
     *          The set of refinements of which at least one must be
     *          <code>true</code>.
     */
    public OrRefinement(Collection<Refinement> refinementSet) {

      this.refinementSet = refinementSet;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(Entry entry) {
      for (Refinement refinement : refinementSet) {
        if (refinement.matches(entry) == true) {
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
    public StringBuilder toString(StringBuilder builder) {
      switch (refinementSet.size()) {
      case 0:
        // Do nothing.
        break;
      case 1:
        refinementSet.iterator().next().toString(builder);
        break;
      default:
        builder.append("or:{");
        Iterator<Refinement> iterator = refinementSet.iterator();
        iterator.next().toString(builder);
        while (iterator.hasNext()) {
          builder.append(", ");
          iterator.next().toString(builder);
        }
        builder.append("}");
        break;
      }

      return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {

      if (this == obj) {
        return true;
      }

      if (obj instanceof AndRefinement) {
        AndRefinement other = (AndRefinement) obj;

        return refinementSet.equals(other.refinementSet);
      }

      return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {

      return refinementSet.hashCode();
    }
  }

  /**
   * Parses the string argument as an RFC3672 subtree specification.
   *
   * @param rootDN
   *          The DN of the subtree specification's base entry.
   * @param s
   *          The string to be parsed.
   * @return The RFC3672 subtree specification represented by the string
   *         argument.
   * @throws DirectoryException
   *           If the string does not contain a parsable relative
   *           subtree specification.
   */
  public static RFC3672SubtreeSpecification valueOf(DN rootDN, String s)
      throws DirectoryException {

    // Default values.
    DN relativeBaseDN = null;

    int minimum = -1;
    int maximum = -1;

    HashSet<DN> chopBefore = new HashSet<DN>();
    HashSet<DN> chopAfter = new HashSet<DN>();

    Refinement refinement = null;

    // Value must have an opening left brace.
    Parser parser = new Parser(s);
    boolean isValid = true;

    try {
      parser.skipLeftBrace();

      // Parse each element of the value sequence.
      boolean isFirst = true;

      while (true) {
        if (parser.hasNextRightBrace()) {
          // Make sure that there is a closing brace and no trailing
          // text.
          parser.skipRightBrace();

          if (parser.hasNext()) {
            throw new java.util.InputMismatchException();
          }
          break;
        }

        // Make sure that there is a comma separator if this is not the
        // first element.
        if (!isFirst) {
          parser.skipSeparator();
        } else {
          isFirst = false;
        }

        String key = parser.nextKey();
        if (key.equals("base")) {
          if (relativeBaseDN != null) {
            // Relative base DN specified more than once.
            throw new InputMismatchException();
          }
          relativeBaseDN = DN.decode(parser.nextStringValue());
        } else if (key.equals("minimum")) {
          if (minimum != -1) {
            // Minimum specified more than once.
            throw new InputMismatchException();
          }
          minimum = parser.nextInt();
        } else if (key.equals("maximum")) {
          if (maximum != -1) {
            // Maximum specified more than once.
            throw new InputMismatchException();
          }
          maximum = parser.nextInt();
        } else if (key.equals("specificationfilter")) {
          if (refinement != null) {
            // Refinements specified more than once.
            throw new InputMismatchException();
          }

          refinement = parseRefinement(parser);
        } else if (key.equals("specificexclusions")) {
          if (!chopBefore.isEmpty() || !chopAfter.isEmpty()) {
            // Specific exclusions specified more than once.
            throw new InputMismatchException();
          }

          parser.nextSpecificExclusions(chopBefore, chopAfter);
        } else {
          throw new InputMismatchException();
        }
      }

      // Make default minimum value is 0.
      if (minimum < 0) {
        minimum = 0;
      }

      // Check that the maximum, if specified, is gte the minimum.
      if (maximum >= 0 && maximum < minimum) {
        isValid = false;
      }
    } catch (InputMismatchException e) {
      isValid = false;
    } catch (NoSuchElementException e) {
      isValid = false;
    }

    if (isValid) {
      return new RFC3672SubtreeSpecification(rootDN, relativeBaseDN,
          minimum, maximum, chopBefore, chopAfter, refinement);
    } else {
      int msgID = MSGID_ATTR_SYNTAX_RFC3672_SUBTREE_SPECIFICATION_INVALID;
      String message = getMessage(msgID, s);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
          message, msgID);
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
  private static Refinement parseRefinement(Parser parser)
      throws InputMismatchException, NoSuchElementException {
    // Get the type of refinement.
    String type = StaticUtils.toLowerCase(parser.nextName());

    // Skip the colon separator.
    parser.skipColon();

    if (type.equals("item")) {
      return new ItemRefinement(parser.nextName());
    } else if (type.equals("not")) {
      Refinement refinement = parseRefinement(parser);
      return new NotRefinement(refinement);
    } else if (type.equals("and")) {
      ArrayList<Refinement> refinements = parseRefinementSet(parser);
      return new AndRefinement(refinements);
    } else if (type.equals("or")) {
      ArrayList<Refinement> refinements = parseRefinementSet(parser);
      return new OrRefinement(refinements);
    } else {
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
  private static ArrayList<Refinement> parseRefinementSet(Parser parser)
      throws InputMismatchException, NoSuchElementException {
    ArrayList<Refinement> refinements = new ArrayList<Refinement>();

    // Skip leading open-brace.
    parser.skipLeftBrace();

    // Parse each chop DN in the sequence.
    boolean isFirstValue = true;
    while (true) {
      // Make sure that there is a closing brace.
      if (parser.hasNextRightBrace()) {
        parser.skipRightBrace();
        break;
      }

      // Make sure that there is a comma separator if this is not
      // the first element.
      if (!isFirstValue) {
        parser.skipSeparator();
      } else {
        isFirstValue = false;
      }

      // Parse each sub-refinement.
      Refinement refinement = parseRefinement(parser);
      refinements.add(refinement);
    }

    return refinements;
  }

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
  public RFC3672SubtreeSpecification(DN rootDN, DN relativeBaseDN,
      int minimumDepth, int maximumDepth, Iterable<DN> chopBefore,
      Iterable<DN> chopAfter, Refinement refinements) {
    super(relativeBaseDN == null ? rootDN : rootDN.concat(relativeBaseDN),
        minimumDepth, maximumDepth, chopBefore, chopAfter);


    this.rootDN = rootDN;
    this.relativeBaseDN = relativeBaseDN;
    this.refinements = refinements;
  }

  /**
   * Get the root DN.
   *
   * @return Returns the root DN.
   */
  public DN getRootDN() {
    return rootDN;
  }

  /**
   * Get the relative base DN.
   *
   * @return Returns the relative base DN or <code>null</code> if none
   *         was specified.
   */
  public DN getRelativeBaseDN() {
    return relativeBaseDN;
  }

  /**
   * Get the specification filter refinements.
   *
   * @return Returns the specification filter refinements, or
   *         <code>null</code> if none were specified.
   */
  public Refinement getRefinements() {
    return refinements;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isWithinScope(Entry entry) {

    if (isDNWithinScope(entry.getDN())) {
      if (refinements != null) {
        return refinements.matches(entry);
      } else {
        return true;
      }
    } else {
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public StringBuilder toString(StringBuilder builder) {

    boolean isFirstElement = true;

    // Output the optional base DN.
    builder.append("{");
    if (relativeBaseDN != null && !relativeBaseDN.isNullDN()) {
      builder.append(" base ");
      StaticUtils.toRFC3641StringValue(builder, relativeBaseDN.toString());
      isFirstElement = false;
    }

    // Output the optional specific exclusions.
    Iterable<DN> chopBefore = getChopBefore();
    Iterable<DN> chopAfter = getChopAfter();

    if ((chopBefore != null && chopBefore.iterator().hasNext())
        || (chopAfter != null && chopAfter.iterator().hasNext())) {

      if (!isFirstElement) {
        builder.append(",");
      } else {
        isFirstElement = false;
      }
      builder.append(" specificExclusions { ");

      boolean isFirst = true;

      if (chopBefore != null) {
        for (DN dn : chopBefore) {
          if (!isFirst) {
            builder.append(", chopBefore:");
          } else {
            builder.append("chopBefore:");
            isFirst = false;
          }
          StaticUtils.toRFC3641StringValue(builder, dn.toString());
        }
      }

      if (chopAfter != null) {
        for (DN dn : chopAfter) {
          if (!isFirst) {
            builder.append(", chopAfter:");
          } else {
            builder.append("chopAfter:");
            isFirst = false;
          }
          StaticUtils.toRFC3641StringValue(builder, dn.toString());
        }
      }

      builder.append(" }");
    }

    // Output the optional minimum depth.
    if (getMinimumDepth() > 0) {
      if (!isFirstElement) {
        builder.append(",");
      } else {
        isFirstElement = false;
      }
      builder.append(" minimum ");
      builder.append(getMinimumDepth());
    }

    // Output the optional maximum depth.
    if (getMaximumDepth() >= 0) {
      if (!isFirstElement) {
        builder.append(",");
      } else {
        isFirstElement = false;
      }
      builder.append(" maximum ");
      builder.append(getMaximumDepth());
    }

    // Output the optional refinements.
    if (refinements != null) {
      if (!isFirstElement) {
        builder.append(",");
      } else {
        isFirstElement = false;
      }
      builder.append(" specificationFilter ");
      refinements.toString(builder);
    }

    builder.append(" }");

    return builder;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {

    if (this == obj) {
      return true;
    }

    if (obj instanceof RFC3672SubtreeSpecification) {
      RFC3672SubtreeSpecification other = (RFC3672SubtreeSpecification) obj;

      if (!commonComponentsEquals(other)) {
        return false;
      }

      if (!getBaseDN().equals(other.getBaseDN())) {
        return false;
      }

      if (refinements != null) {
        return refinements.equals(other.refinements);
      } else {
        return refinements == other.refinements;
      }
    }

    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {

    int hash = commonComponentsHashCode();

    hash = hash * 31 + getBaseDN().hashCode();

    if (refinements != null) {
      hash = hash * 31 + refinements.hashCode();
    }

    return hash;
  }
}
