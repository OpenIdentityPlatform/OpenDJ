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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;

import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.opends.server.api.SubtreeSpecification;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;

/**
 * A simple subtree specification implementation that has a subtree
 * base, optional minimum and maximum depths, and a set of chop
 * specifications.
 */
public abstract class SimpleSubtreeSpecification extends
    SubtreeSpecification {


  // The absolute base of the subtree.
  private DN baseDN;

  // Optional minimum depth (<=0 means unlimited).
  private int minimumDepth;

  // Optional maximum depth (<0 means unlimited).
  private int maximumDepth;

  // Optional set of chop before absolute DNs (mapping to their
  // local-names).
  private TreeMap<DN, DN> chopBefore;

  // Optional set of chop after absolute DNs (mapping to their
  // local-names).
  private TreeMap<DN, DN> chopAfter;

  /**
   * Internal utility class which can be used by sub-classes to help
   * parse string representations.
   */
  protected static final class Parser {
    // Text scanner used to parse the string value.
    private Scanner scanner;

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
    public Parser(String value) {
      this.scanner = new Scanner(value);
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
        NoSuchElementException {
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
        NoSuchElementException {
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
        NoSuchElementException {
      nextValue(SEP, SEP_TOKEN);
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
        NoSuchElementException {
      nextValue(COLON, COLON_TOKEN);
    }

    /**
     * Determine if the next token is a right-brace character.
     *
     * @return <code>true</code> if and only if the next token is a
     *         valid right brace character.
     */
    public boolean hasNextRightBrace() {
      return scanner.hasNext(RBRACE);
    }

    /**
     * Determine if there are remaining tokens.
     *
     * @return <code>true</code> if and only if there are remaining
     *         tokens.
     */
    public boolean hasNext() {
      return scanner.hasNext();
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
        NoSuchElementException {
      return StaticUtils.toLowerCase(scanner.next());
    }

    /**
     * Scans the next token of the input as a name value.
     * <p>
     * A name is any string containing only alpha-numeric characters or
     * hyphens, semi-colons, or underscores.
     *
     * @return The name value scanned from the input.
     * @throws InputMismatchException
     *           If the next token is not a valid name string.
     * @throws NoSuchElementException
     *           If input is exhausted.
     */
    public String nextName() throws InputMismatchException,
        NoSuchElementException {
      return nextValue(NAME, NAME_TOKEN);
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
        NoSuchElementException {
      String s = nextValue(INT, INT_TOKEN);
      return Integer.parseInt(s);
    }

    /**
     * Scans the next token of the input as a string quoted according to
     * the StringValue production in RFC 3641.
     * <p>
     * The return string has its outer double quotes removed and any
     * escaped inner double quotes unescaped.
     *
     * @return The string value scanned from the input.
     * @throws InputMismatchException
     *           If the next token is not a valid string.
     * @throws NoSuchElementException
     *           If input is exhausted.
     */
    public String nextStringValue() throws InputMismatchException,
        NoSuchElementException {
      String s = nextValue(STRING_VALUE, STRING_VALUE_TOKEN);
      return s.substring(1, s.length() - 1).replace("\"\"", "\"");
    }

    /**
     * Scans the next tokens of the input as a set of specific
     * exclusions encoded according to the SpecificExclusion production
     * in RFC 3672.
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
     *           If an error occurred when attempting to parse a DN
     *           value.
     */
    public void nextSpecificExclusions(Set<DN> chopBefore, Set<DN> chopAfter)
        throws InputMismatchException, NoSuchElementException,
        DirectoryException {

      // Skip leading open-brace.
      skipLeftBrace();

      // Parse each chop DN in the sequence.
      boolean isFirstValue = true;
      while (true) {
        // Make sure that there is a closing brace.
        if (hasNextRightBrace()) {
          skipRightBrace();
          break;
        }

        // Make sure that there is a comma separator if this is not
        // the first element.
        if (!isFirstValue) {
          skipSeparator();
        } else {
          isFirstValue = false;
        }

        // Parse each chop specification which is of the form
        // <type>:<value>.
        String type = StaticUtils.toLowerCase(nextName());
        skipColon();
        if (type.equals("chopbefore")) {
          chopBefore.add(DN.decode(nextStringValue()));
        } else if (type.equals("chopafter")) {
          chopAfter.add(DN.decode(nextStringValue()));
        } else {
          throw new java.util.InputMismatchException();
        }
      }
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
    private String nextValue(Pattern head, Pattern content)
        throws InputMismatchException, NoSuchElementException {
      if (!scanner.hasNext()) {
        throw new java.util.NoSuchElementException();
      }

      if (!scanner.hasNext(head)) {
        throw new java.util.InputMismatchException();
      }

      String s = scanner.findInLine(content);
      if (s == null) {
        throw new java.util.InputMismatchException();
      }

      return s;
    }
  }

  /**
   * Create a new simple subtree specification.
   *
   * @param baseDN
   *          The absolute base DN of the subtree.
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
   */
  protected SimpleSubtreeSpecification(DN baseDN, int minimumDepth,
      int maximumDepth, Iterable<DN> chopBefore, Iterable<DN> chopAfter) {

    this.baseDN = baseDN;
    this.minimumDepth = minimumDepth;
    this.maximumDepth = maximumDepth;

    if (chopBefore != null && chopBefore.iterator().hasNext()) {
      // Calculate the absolute DNs.
      this.chopBefore = new TreeMap<DN, DN>();

      for (DN localName : chopBefore) {
        this.chopBefore.put(baseDN.concat(localName), localName);
      }
    } else {
      // No chop before specifications.
      this.chopBefore = null;
    }

    if (chopAfter != null && chopAfter.iterator().hasNext()) {
      // Calculate the absolute DNs.
      this.chopAfter = new TreeMap<DN, DN>();

      for (DN localName : chopAfter) {
        this.chopAfter.put(baseDN.concat(localName), localName);
      }
    } else {
      // No chop after specifications.
      this.chopAfter = null;
    }
  }

  /**
   * Determine if the specified DN is within the scope of the subtree
   * specification.
   *
   * @param dn
   *          The distringuished name.
   * @return Returns <code>true</code> if the DN is within the scope
   *         of the subtree specification, or <code>false</code>
   *         otherwise.
   */
  protected final boolean isDNWithinScope(DN dn) {

    if (!dn.isDescendantOf(baseDN)) {
      return false;
    }

    // Check minimum and maximum depths.
    int baseRDNCount = baseDN.getNumComponents();

    if (minimumDepth > 0) {
      int entryRDNCount = dn.getNumComponents();

      if (entryRDNCount - baseRDNCount < minimumDepth) {
        return false;
      }
    }

    if (maximumDepth >= 0) {
      int entryRDNCount = dn.getNumComponents();

      if (entryRDNCount - baseRDNCount > maximumDepth) {
        return false;
      }
    }

    // Check exclusions.
    if (chopBefore != null) {
      for (DN chopBeforeDN : chopBefore.keySet()) {
        if (dn.isDescendantOf(chopBeforeDN)) {
          return false;
        }
      }
    }

    if (chopAfter != null) {
      for (DN chopAfterDN : chopAfter.keySet()) {
        if (!dn.equals(chopAfterDN) && dn.isDescendantOf(chopAfterDN)) {
          return false;
        }
      }
    }

    // Everything seemed to match.
    return true;
  }

  /**
   * Get the absolute base DN of the subtree specification.
   *
   * @return Returns the absolute base DN of the subtree specification.
   */
  protected final DN getBaseDN() {
    return baseDN;
  }

  /**
   * Determine if the common components of this subtree specification
   * are equal to the common components of another subtre specification.
   *
   * @param other
   *          The other subtree specification.
   * @return Returns <code>true</code> if they are equal.
   */
  protected final boolean commonComponentsEquals(
      SimpleSubtreeSpecification other) {

    if (this == other) {
      return true;
    }

    if (minimumDepth != other.minimumDepth) {
      return false;
    }

    if (maximumDepth != other.maximumDepth) {
      return false;
    }

    if (chopBefore != null && other.chopBefore != null) {
      if (!chopBefore.values().equals(other.chopBefore.values())) {
        return false;
      }
    } else if (chopBefore != other.chopBefore) {
      return false;
    }

    if (chopAfter != null && other.chopAfter != null) {
      if (!chopAfter.values().equals(other.chopAfter.values())) {
        return false;
      }
    } else if (chopAfter != other.chopAfter) {
      return false;
    }

    return true;
  }

  /**
   * Get a hash code of the subtree specification's common components.
   *
   * @return The computed hash code.
   */
  protected final int commonComponentsHashCode() {

    int hash = minimumDepth * 31 + maximumDepth;

    if (chopBefore != null) {
      hash = hash * 31 + chopBefore.values().hashCode();
    }

    if (chopAfter != null) {
      hash = hash * 31 + chopAfter.values().hashCode();
    }

    return hash;
  }

  /**
   * Get the set of chop after relative DNs.
   *
   * @return Returns the set of chop after relative DNs, or
   *         <code>null</code> if there are not any.
   */
  public final Iterable<DN> getChopAfter() {

    if (chopAfter != null) {
      return chopAfter.values();
    } else {
      return null;
    }
  }

  /**
   * Get the set of chop before relative DNs.
   *
   * @return Returns the set of chop before relative DNs, or
   *         <code>null</code> if there are not any.
   */
  public final Iterable<DN> getChopBefore() {

    if (chopBefore != null) {
      return chopBefore.values();
    } else {
      return null;
    }
  }

  /**
   * Get the maximum depth of the subtree specification.
   *
   * @return Returns the maximum depth (<0 indicates unlimited depth).
   */
  public final int getMaximumDepth() {
    return maximumDepth;
  }

  /**
   * Get the minimum depth of the subtree specification.
   *
   * @return Returns the minimum depth (<=0 indicates unlimited depth).
   */
  public final int getMinimumDepth() {
    return minimumDepth;
  }
}
