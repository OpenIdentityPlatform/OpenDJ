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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.opends.server.loggers.Debug.*;
import static org.opends.server.util.Validator.ensureNotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.core.DirectoryServer;



/**
 * This class defines a data structure for storing and interacting
 * with the distinguished names associated with entries in the
 * Directory Server.
 * <p>
 * All the methods in this class will throw a
 * <code>NullPointerException</code> when provided with
 * <code>null</code> reference parameters unless otherwise stated.
 */
public final class DN implements Comparable<DN>, Serializable {
  // FIXME: Don't store the normalized form and define equals(),
  // hashCode(), and compareTo() in terms of RDN components (which
  // cache their normalized form). This could potentially lead to less
  // memory utilization for very little performance loss.

  // FIXME: Optimize normalization for common use-cases. E.g.
  // concat(DN) can simply join the two normalized forms together.
  // Similarly, getParent() and getLocalName() can avoid
  // recalculating the normalized form and take a substring from the
  // source DN's normalized form (this requires parsing commas, but
  // could save memory).

  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
    "org.opends.server.types.DN";

  /**
   * The serial version identifier required to satisfy the compiler
   * because this class implements the
   * <code>java.io.Serializable</code> interface. This value was
   * generated using the <code>serialver</code> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = 1184263456768819888L;

  // The number of RDN components that comprise this DN.
  private final int numComponents;

  // The index of the first RDN component (furthest from the root) in
  // this DN.
  private final int offset;

  // The set of RDN components that comprise this DN, arranged with
  // the suffix as the last element.
  private final RDN[] rdnComponents;

  // The cached normalized string representation of this DN.
  private final String normalizedDN;

  // A DN comprising of zero RDN components.
  private static final DN EMPTY_DN = new DN(new RDN[0], 0, 0);



  /**
   * Creates a new DN comprising of zero RDN components (a null DN or
   * root DSE).
   *
   * @return Returns a new DN comprising of zero RDN components.
   */
  public static DN nullDN() {
    return EMPTY_DN;
  }



  /**
   * Creates a new <code>DN</code> containing the specified RDN
   * sequence.
   * <p>
   * If the argument RDN sequence is empty, then the effect is the
   * same as if {@link #nullDN()} had been called.
   *
   * @param rdns
   *          The RDN sequence that the new DN will represent.
   * @return Returns a DN that represents the specified RDN sequence
   *         onto the end of this DN.
   */
  public static DN create(RDN... rdns) {
    ensureNotNull(rdns);

    if (rdns.length == 0) {
      return nullDN();
    }

    RDN[] allRDNs = new RDN[rdns.length];
    System.arraycopy(rdns, 0, allRDNs, 0, rdns.length);
    return new DN(allRDNs, 0, rdns.length);
  }



  /**
   * Returns a <code>DN</code> object holding the value of the
   * specified <code>String</code>. The argument is interpreted as
   * representing the LDAP string representation of a DN.
   * <p>
   * This method is identical to {@link #decode(String)}.
   *
   * @param s
   *          The string to be parsed, or <code>null</code> in
   *          which case a <code>nullDN</code> will be returned.
   * @return Returns a <code>DN</code> holding the value represented
   *         by the <code>string</code> argument.
   * @throws DirectoryException
   *           If a problem occurs while trying to decode the provided
   *           string as a DN.
   */
  public static DN valueOf(String s) throws DirectoryException {
    return decode(s);
  }



  /**
   * Decodes the provided ASN.1 octet string as a DN.
   *
   * @param dnString
   *          The ASN.1 octet string to decode as a DN, or
   *          <code>null</code> in which case a <code>nullDN</code>
   *          will be returned.
   * @return The decoded DN.
   * @throws DirectoryException
   *           If a problem occurs while trying to decode the provided
   *           ASN.1 octet string as a DN.
   */
  public static DN decode(ByteString dnString)
      throws DirectoryException {
    assert debugEnter(CLASS_NAME, "decode", String.valueOf(dnString));

    // A null or empty DN is acceptable.
    if (dnString == null) {
      return nullDN();
    }

    byte[] dnBytes = dnString.value();
    int length = dnBytes.length;
    if (length == 0) {
      return nullDN();
    }

    // Use string-based decoder.
    return decode(dnString.stringValue());
  }



  /**
   * Decodes the provided string as a DN.
   *
   * @param dnString
   *          The string to decode as a DN, or <code>null</code> in
   *          which case a <code>nullDN</code> will be returned.
   * @return The decoded DN.
   * @throws DirectoryException
   *           If a problem occurs while trying to decode the provided
   *           string as a DN.
   */
  public static DN decode(String dnString) throws DirectoryException {
    assert debugEnter(CLASS_NAME, "decode", String.valueOf(dnString));

    // A null or empty DN is acceptable.
    if (dnString == null || dnString.length() == 0) {
      return nullDN();
    }

    // Parse the first RDN.
    int pos = 0;
    RDN.Builder builder = RDN.createBuilder();
    pos = builder.parse(dnString, pos, true);

    if (builder.isEmpty()) {
      return nullDN();
    } else {
      // Parse the remaining RDNs.
      List<RDN> rdns = new ArrayList<RDN>(10);
      rdns.add(builder.getInstance());

      while (pos >= 0) {
        // Skip the RDN separator.
        pos++;

        // Parse the next RDN.
        builder.clear();
        pos = builder.parse(dnString, pos, false);
        rdns.add(builder.getInstance());
      }

      // Parse successful - create the DN.
      int sz = rdns.size();
      return new DN(rdns.toArray(new RDN[sz]), 0, sz);
    }
  }



  /**
   * Creates a new DN with the provided set of RDNs, arranged with the
   * suffix as the last element.
   *
   * @param rdnComponents
   *          The set of RDN components that make up this DN.
   * @param offset
   *          The index of the first RDN component (furthest from the
   *          root) in this DN.
   * @param count
   *          The number of RDNs to include in this DN.
   */
  private DN(RDN[] rdnComponents, int offset, int count) {
    assert debugConstructor(CLASS_NAME,
        String.valueOf(rdnComponents), String.valueOf(offset));

    this.rdnComponents = rdnComponents;
    this.offset = offset;
    this.numComponents = count;
    this.normalizedDN = normalize();
  }



  /**
   * Concatenates the specified DN to the end of this DN.
   * <p>
   * If the argument DN is the null DN, then this DN is returned.
   * Conversely, if this DN is the null DN then the argument DN will
   * be returned. Otherwise, the returned DN will be a descendent of
   * this DN.
   *
   * @param localName
   *          The DN that will be concatenated to the end of this DN.
   * @return Returns a DN that represents the concatenation of the
   *         specified DN onto the end of this DN.
   */
  public DN concat(DN localName) {
    ensureNotNull(localName);

    if (localName.isNullDN()) {
      return this;
    }

    if (isNullDN()) {
      return localName;
    }

    RDN[] allRDNs = new RDN[numComponents + localName.numComponents];
    System.arraycopy(localName.rdnComponents, localName.offset,
        allRDNs, 0, localName.numComponents);
    System.arraycopy(rdnComponents, offset, allRDNs,
        localName.numComponents, numComponents);

    return new DN(allRDNs, 0, allRDNs.length);
  }



  /**
   * Concatenates the specified RDN sequence to the end of this DN.
   * <p>
   * If the argument RDN sequence is empty, then this DN is returned.
   * Otherwise, the returned DN will be a descendent of this DN.
   *
   * @param rdns
   *          The RDN sequence that will be concatenated to the end of
   *          this DN.
   * @return Returns a DN that represents the concatenation of the
   *         specified RDN sequence onto the end of this DN.
   */
  public DN concat(RDN... rdns) {
    ensureNotNull(rdns);

    if (rdns.length == 0) {
      return this;
    }

    // Don't check if this is a nullDN, because were going to copy the
    // RDN sequence anyway.

    RDN[] allRDNs = new RDN[rdns.length + numComponents];

    System.arraycopy(rdns, 0, allRDNs, 0, rdns.length);
    System.arraycopy(rdnComponents, offset, allRDNs,
        rdns.length, numComponents);

    return new DN(allRDNs, 0, allRDNs.length);
  }



  /**
   * Get the parent DN of this DN.
   *
   * @return Returns the parent DN of this DN, or <code>null</code>
   *         if this DN does not have a parent (i.e. it is a DN having
   *         a single RDN component, or the null DN).
   */
  public DN getParent() {
    if (numComponents <= 1) {
      return null;
    } else {
      return new DN(rdnComponents, offset + 1, numComponents - 1);
    }
  }



  /**
   * Create a local name (a relative DN) from this DN.
   * <p>
   * Examples: <blockquote>
   *
   * <pre>
   * DN dn = DN.decode(&quot;cn=john,o=example,c=us&quot;);
   *
   * dn.getLocalName(0) returns &quot;cn=john,o=example,c=us&quot;
   * dn.getLocalName(1) returns &quot;cn=john,o=example&quot;
   * dn.getLocalName(3) returns &quot;&quot; (null DN).
   * </pre>
   *
   * </blockquote>
   *
   * @param beginIndex
   *          The index of the first RDN component (nearest the root),
   *          inclusive.
   * @return Returns the specified local name.
   * @throws IndexOutOfBoundsException
   *           If <code>beginIndex</code> is negative, or greater
   *           than the number of RDN components in this DN.
   */
  public DN getLocalName(int beginIndex)
      throws IndexOutOfBoundsException {
    return getLocalName(beginIndex, numComponents);
  }



  /**
   * Create a local name (a relative DN) from this DN.
   * <p>
   * Examples: <blockquote>
   *
   * <pre>
   * DN dn = DN.decode(&quot;cn=john,o=example,c=us&quot;);
   *
   * dn.getLocalName(0, 3) returns &quot;cn=john,o=example,c=us&quot;
   * dn.getLocalName(1, 2) returns &quot;o=example&quot;
   * dn.getLocalName(2, 2) returns &quot;&quot; (null DN).
   * </pre>
   *
   * </blockquote>
   *
   * @param beginIndex
   *          The index of the first RDN component (nearest the root),
   *          inclusive.
   * @param endIndex
   *          The index of the last RDN component (furthest from the
   *          root), exclusive.
   * @return Returns the specified local name.
   * @throws IndexOutOfBoundsException
   *           If <code>beginIndex</code> is negative, or
   *           <code>endIndex</code> is larger than the number of
   *           RDN components in this DN, or <code>beginIndex</code>
   *           is larger than <code>endIndex</code>.
   */
  public DN getLocalName(int beginIndex, int endIndex)
      throws IndexOutOfBoundsException {
    if (beginIndex < 0) {
      throw new IndexOutOfBoundsException("beginIndex out of range: "
          + beginIndex);
    }

    if (endIndex > numComponents) {
      throw new IndexOutOfBoundsException("endIndex out of range: "
          + endIndex);
    }

    if (beginIndex > endIndex) {
      throw new IndexOutOfBoundsException(
          "beginIndex greater than endIndex");
    }

    if (beginIndex == 0 && endIndex == numComponents) {
      return this;
    } else {
      int i = offset + numComponents - endIndex;
      return new DN(rdnComponents, i, endIndex - beginIndex);
    }
  }



  /**
   * Get the number of RDN components that make up this DN.
   *
   * @return Returns the number of RDN components that make up this
   *         DN.
   */
  public int getNumComponents() {
    assert debugEnter(CLASS_NAME, "getNumComponents");

    return numComponents;
  }



  /**
   * Retrieves the outermost RDN component for this DN (i.e., the one
   * that is furthest from the suffix). This method is equivalent to
   * calling <code>getRDN(0)</code> for non-null DNs.
   *
   * @return The outermost RDN component for this DN, or
   *         <code>null</code> if there are no RDN components in the
   *         DN.
   */
  public RDN getRDN() {
    assert debugEnter(CLASS_NAME, "getRDN");

    if (numComponents == 0) {
      return null;
    } else {
      return getRDN(0);
    }
  }



  /**
   * Get the RDN at the specified index.
   *
   * @param index
   *          The index of the RDN to retrieve, where <code>0</code>
   *          indicates the outermost RDN component (i.e. the one that
   *          is furthest from the suffix).
   * @return Returns the RDN at the specified index.
   * @throws IndexOutOfBoundsException
   *           If <code>index</code> is negative, or greater than or
   *           equal to the number of RDN components in this DN.
   */
  public RDN getRDN(int index) throws IndexOutOfBoundsException {
    if (index < 0) {
      throw new IndexOutOfBoundsException("index out of range: "
          + index);
    }

    if (index >= numComponents) {
      throw new IndexOutOfBoundsException("index out of range: "
          + index);
    }

    return rdnComponents[offset + index];
  }



  /**
   * Retrieves the DN of the entry that is the immediate parent for
   * this entry.
   *
   * @return The DN of the entry that is the immediate parent for this
   *         entry, or <code>null</code> if the entry with this DN
   *         does not have a parent (either because there is only a
   *         single RDN component or because this DN is a suffix
   *         defined in the server).
   */
  public DN getParentDNInSuffix() {
    assert debugEnter(CLASS_NAME, "getParentDNInSuffix");

    if ((numComponents <= 1) ||
        DirectoryServer.isNamingContext(this))
    {
      return null;
    }

    return getParent();
  }



  /**
   * Indicates whether this represents a null DN. This could target
   * the root DSE for the Directory Server, or the authorization DN
   * for an anonymous or unauthenticated client.
   *
   * @return <code>true</code> if this does represent a null DN, or
   *         <code>false</code> if it does not.
   */
  public boolean isNullDN() {
    assert debugEnter(CLASS_NAME, "isNullDN");

    return (numComponents == 0);
  }



  /**
   * Indicates whether this DN is a descendant of the provided DN
   * (i.e., that the RDN components of the provided DN are the same as
   * the last RDN components for this DN).
   *
   * @param dn
   *          The DN for which to make the determination.
   * @return <code>true</code> if this DN is a descendant of the
   *         provided DN, or <code>false</code> if not.
   */
  public boolean isDescendantOf(DN dn) {
    assert debugEnter(CLASS_NAME, "isDescendantOf", String
        .valueOf(dn));

    ensureNotNull(dn);

    int diff = numComponents - dn.numComponents;
    if (diff < 0) {
      return false;
    }

    for (int i = 0; i < dn.numComponents; i++) {
      if (!getRDN(i + diff).equals(dn.getRDN(i))) {
        return false;
      }
    }

    return true;
  }



  /**
   * Indicates whether this DN is an ancestor of the provided DN
   * (i.e., that the RDN components of this DN are the same as the
   * last RDN components for the provided DN).
   *
   * @param dn
   *          The DN for which to make the determination.
   * @return <code>true</code> if this DN is an ancestor of the
   *         provided DN, or <code>false</code> if not.
   */
  public boolean isAncestorOf(DN dn) {
    assert debugEnter(CLASS_NAME, "isAncestorOf", String.valueOf(dn));

    ensureNotNull(dn);

    int diff = dn.numComponents - numComponents;
    if (diff < 0) {
      return false;
    }

    for (int i = 0; i < numComponents; i++) {
      if (!getRDN(i).equals(dn.getRDN(i + diff))) {
        return false;
      }
    }

    return true;
  }



  /**
   * Indicates whether the provided object is equal to this DN. In
   * order for the object to be considered equal, it must be a DN with
   * the same number of RDN components and each corresponding RDN
   * component must be equal.
   *
   * @param o
   *          The object for which to make the determination.
   * @return <code>true</code> if the provided object is a DN that
   *         is equal to this DN, or <code>false</code> if it is
   *         not.
   */
  public boolean equals(Object o) {
    assert debugEnter(CLASS_NAME, "equals", String.valueOf(o));

    if (this == o) {
      return true;
    } else if (o instanceof DN) {
      DN other = (DN) o;
      return normalizedDN.equals(other.normalizedDN);
    } else {
      return false;
    }
  }



  /**
   * Retrieves the hash code for this DN. The hash code will be the
   * sum of the hash codes for all the RDN components.
   *
   * @return The hash code for this DN.
   */
  public int hashCode() {
    assert debugEnter(CLASS_NAME, "hashCode");

    return normalizedDN.hashCode();
  }



  /**
   * Retrieves a string representation of this DN.
   *
   * @return A string representation of this DN.
   */
  public String toString() {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder builder = new StringBuilder();
    toString(builder);
    return builder.toString();
  }



  /**
   * Appends a string representation of this DN to the provided
   * buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer) {
    assert debugEnter(CLASS_NAME, "toString",
        "java.lang.StringBuilder");

    ensureNotNull(buffer);

    if (numComponents != 0) {
      getRDN(0).toString(buffer);

      for (int i = 1; i < numComponents; i++) {
        buffer.append(",");
        getRDN(i).toString(buffer);
      }
    }
  }



  /**
   * Retrieves a normalized string representation of this DN.
   *
   * @return A normalized string representation of this DN.
   */
  public String toNormalizedString() {
    assert debugEnter(CLASS_NAME, "toNormalizedString");

    return normalizedDN;
  }



  /**
   * Appends a normalized string representation of this DN to the
   * provided buffer.
   *
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  public void toNormalizedString(StringBuilder buffer) {
    assert debugEnter(CLASS_NAME, "toNormalizedString",
        "java.lang.StringBuilder");

    ensureNotNull(buffer);

    buffer.append(normalizedDN);
  }



  /**
   * Compares this DN with the provided DN based on a natural order.
   * This order will be first hierarchical (ancestors will come before
   * descendants) and then alphabetical by attribute name(s) and
   * value(s).
   * <p>
   * NOTE: the implementation of this method does not perform a
   * lexicographic comparison of the DN's normalized form. Instead,
   * each individual RDN is compared using ordering matching rules
   * where possible.
   *
   * @param dn
   *          The DN against which to compare this DN.
   * @return A negative integer if this DN should come before the
   *         provided DN, a positive integer if this DN should come
   *         after the provided DN, or zero if there is no difference
   *         with regard to ordering.
   */
  public int compareTo(DN dn) {
    assert debugEnter(CLASS_NAME, "compareTo", String.valueOf(this),
        String.valueOf(dn));

    ensureNotNull(dn);

    int index1 = numComponents - 1;
    int index2 = dn.numComponents - 1;

    while (true) {
      if (index1 >= 0) {
        if (index2 >= 0) {
          int value = getRDN(index1).compareTo(dn.getRDN(index2));
          if (value != 0) {
            return value;
          }
        } else {
          return 1;
        }
      } else if (index2 >= 0) {
        return -1;
      } else {
        return 0;
      }

      index1--;
      index2--;
    }
  }



  /**
   * Construct the normalized form of this DN.
   *
   * @return Returns the normalized string representation of this DN.
   */
  private String normalize() {
    if (numComponents == 0) {
      return "";
    } else {
      StringBuilder buffer = new StringBuilder();
      getRDN(0).toNormalizedString(buffer);

      for (int i = 1; i < numComponents; i++) {
        buffer.append(',');
        getRDN(i).toNormalizedString(buffer);
      }

      return buffer.toString();
    }
  }
}
