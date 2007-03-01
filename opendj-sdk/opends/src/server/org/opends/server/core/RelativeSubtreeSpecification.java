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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;

import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.SchemaMessages.*;

import java.util.HashSet;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;

import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.StaticUtils;

/**
 * A relative subtree specification.
 * <p>
 * Relative subtree specifications are very similar toRFC 3672 subtree
 * specifications with the only difference being that the specification
 * filter is not a set of refinements, but an LDAP search filter.
 * <p>
 * The string representation of a relative subtree specification is
 * defined by the following grammar:
 *
 * <pre>
 *  SubtreeSpecification  = &quot;{&quot;
 *                              [     sp ss-relative-base ]
 *                              [ sep sp ss-specificExclusions ]
 *                              [ sep sp ss-minimum ]
 *                              [ sep sp ss-maximum ]
 *                              [ sep sp ss-specificationFilter ]
 *                       sp &quot;}&quot;
 *
 *  ss-relative-base      = &quot;relativeBase&amp;quot msp DistinguishedName
 *
 *  ss-specificExclusions = &quot;specificExclusions&amp;quot
 *                                             msp SpecificExclusions
 *
 *  ss-minimum            = &quot;minimum&amp;quot msp BaseDistance
 *
 *  ss-maximum            = &quot;maximum&amp;quot msp BaseDistance
 *
 *  ss-specificationFilter = &quot;specificationFilter&amp;quot msp Filter
 *
 *  SpecificExclusions    = &quot;{&quot;
 *                              [ sp SpecificExclusion
 *                                  ( &quot;,&quot; sp SpecificExclusion ) ]
 *                       sp &quot;}&quot;
 *
 *  SpecificExclusion     = chopBefore / chopAfter
 *
 *  chopBefore            = &quot;chopBefore&amp;quot &quot;:&quot; LocalName
 *
 *  chopAfter             = &quot;chopAfter&amp;quot &quot;:&quot; LocalName
 *
 *  Filter                = dquote *SafeUTF8Character dquote
 * </pre>
 */
public final class RelativeSubtreeSpecification extends
    SimpleSubtreeSpecification {

  // The root DN.
  private DN rootDN;

  // The optional relative base DN.
  private DN relativeBaseDN;

  // The optional search filter.
  private SearchFilter filter;

  /**
   * Parses the string argument as a relative subtree specification.
   *
   * @param rootDN
   *          The DN of the subtree specification's base entry.
   * @param s
   *          The string to be parsed.
   * @return The relative subtree specification represented by the
   *         string argument.
   * @throws DirectoryException
   *           If the string does not contain a parsable relative
   *           subtree specification.
   */
  public static RelativeSubtreeSpecification valueOf(DN rootDN, String s)
      throws DirectoryException {

    // Default values.
    DN relativeBaseDN = null;

    int minimum = -1;
    int maximum = -1;

    HashSet<DN> chopBefore = new HashSet<DN>();
    HashSet<DN> chopAfter = new HashSet<DN>();

    SearchFilter filter = null;

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
        if (key.equals("relativebase")) {
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
          if (filter != null) {
            // Filter specified more than once.
            throw new InputMismatchException();
          }
          filter = SearchFilter.createFilterFromString(parser
              .nextStringValue());
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
      return new RelativeSubtreeSpecification(rootDN, relativeBaseDN,
          minimum, maximum, chopBefore, chopAfter, filter);
    } else {
      int msgID = MSGID_ATTR_SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_INVALID;
      String message = getMessage(msgID, s);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
          message, msgID);
    }
  }

  /**
   * Create a new relative subtree specification.
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
   *          The set of chop before local names (relative to the base
   *          DN), or <code>null</code> if there are none.
   * @param chopAfter
   *          The set of chop after local names (relative to the base
   *          DN), or <code>null</code> if there are none.
   * @param filter
   *          The optional search filter (<code>null</code> if there
   *          is no filter).
   */
  public RelativeSubtreeSpecification(DN rootDN, DN relativeBaseDN,
      int minimumDepth, int maximumDepth, Iterable<DN> chopBefore,
      Iterable<DN> chopAfter, SearchFilter filter) {
    super(relativeBaseDN == null ? rootDN : rootDN.concat(relativeBaseDN),
        minimumDepth, maximumDepth, chopBefore, chopAfter);


    this.rootDN = rootDN;
    this.relativeBaseDN = relativeBaseDN;
    this.filter = filter;
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
   * Get the specification filter.
   *
   * @return Returns the search filter, or <code>null</code> if there
   *         is no filter.
   */
  public SearchFilter getFilter() {
    return filter;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isWithinScope(Entry entry) {

    if (isDNWithinScope(entry.getDN())) {
      try {
        return filter.matchesEntry(entry);
      } catch (DirectoryException e) {
        // TODO: need to decide what to do with the exception here. It's
        // probably safe to ignore, but we could log it perhaps.
        return false;
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
      builder.append(" relativeBase ");
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

    // Output the optional filter.
    if (filter != null) {
      if (!isFirstElement) {
        builder.append(",");
      } else {
        isFirstElement = false;
      }
      builder.append(" specificationFilter ");
      StaticUtils.toRFC3641StringValue(builder, filter.toString());
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

    if (obj instanceof RelativeSubtreeSpecification) {
      RelativeSubtreeSpecification other = (RelativeSubtreeSpecification) obj;

      if (!commonComponentsEquals(other)) {
        return false;
      }

      if (!getBaseDN().equals(other.getBaseDN())) {
        return false;
      }

      if (filter != null) {
        return filter.equals(other.filter);
      } else {
        return filter == other.filter;
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

    if (filter != null) {
      hash = hash * 31 + filter.hashCode();
    }

    return hash;
  }
}
