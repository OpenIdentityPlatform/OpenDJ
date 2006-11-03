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



import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.ensureNotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.util.StaticUtils;



/**
 * This class defines a data structure for storing and interacting
 * with the relative distinguished names associated with entries in
 * the Directory Server.
 * <p>
 * All the methods in this class will throw a
 * <code>NullPointerException</code> when provided with
 * <code>null</code> reference parameters unless otherwise stated.
 */
public final class RDN implements Comparable<RDN> {
  // TODO: per-thread cache of common RDNs?

  // TODO: implement pimpl idiom and provide a "singleton"
  // implementation for common case where the RDN only has a single
  // type and value. This would result in less memory being used.

  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
    "org.opends.server.types.RDN";

  // The set of attribute types for the elements in this RDN.
  private final AttributeType[] attributeTypes;

  // The set of user-provided names for the attributes in this RDN.
  private final String[] attributeNames;

  // The set of values for the elements in this RDN.
  private final AttributeValue[] attributeValues;

  /**
   * The cached normalized string representation of this RDN.
   *
   * This non-final field will default to null. The Java memory model
   * guarantees that it will be initialized to null before being
   * visible to other threads.
   */
  private String normalizedRDN;



  /**
   * Creates a new RDN with the provided information.
   *
   * @param type
   *          The attribute type for this RDN.
   * @param value
   *          The value for this RDN.
   * @return Returns the new RDN.
   */
  public static RDN create(AttributeType type, AttributeValue value) {
    return create(type, type.getNameOrOID(), value);
  }



  /**
   * Creates a new RDN with the provided information.
   *
   * @param type
   *          The attribute type for this RDN.
   * @param name
   *          The user-provided name for this RDN.
   * @param value
   *          The value for this RDN.
   * @return Returns the new RDN.
   */
  public static RDN create(AttributeType type, String name,
      AttributeValue value) {
    ensureNotNull(type, name, value);

    AttributeType[] types = new AttributeType[] { type };
    String[] names = new String[] { name };
    AttributeValue[] values = new AttributeValue[] { value };

    return new RDN(types, names, values);
  }



  /**
   * Create a new RDN builder which can be used to incrementally build
   * a new RDN.
   *
   * @return Returns the new RDN builder.
   */
  public static Builder createBuilder() {
    return new Builder();
  }



  /**
   * This class provides an interface for constructing RDNs
   * incrementally.
   * <p>
   * Typically, an application will construct a new
   * <code>Builder</code> and append attribute value assertions
   * (AVAs) using the <code>append</code> method. When the RDN is
   * fully constructed, it can be retrieved using the
   * <code>getInstance</code> method.
   */
  public static final class Builder {
    // The list of attribute types.
    private List<AttributeType> attributeTypes;

    // The list of user-provided attribute names.
    private List<String> attributeNames;

    // The list of attribute values.
    private List<AttributeValue> attributeValues;



    /**
     * Create the new empty RDN builder.
     */
    private Builder() {
      clear();
    }



    /**
     * Appends the provided attribute value assertion to the RDN.
     *
     * @param type
     *          The attribute type.
     * @param value
     *          The attribute value.
     * @throws IllegalArgumentException
     *           If the RDN being constructed already contains an
     *           attribute value assertion for this attribute type.
     */
    public void append(AttributeType type, AttributeValue value)
        throws IllegalArgumentException {
      append(type, type.getNameOrOID(), value);
    }



    /**
     * Appends the provided attribute value assertion to the RDN.
     *
     * @param type
     *          The attribute type.
     * @param name
     *          The user-provided attribute name.
     * @param value
     *          The attribute value.
     * @throws IllegalArgumentException
     *           If the RDN being constructed already contains an
     *           attribute value assertion for this attribute type.
     */
    public void append(AttributeType type, String name,
        AttributeValue value) throws IllegalArgumentException {
      ensureNotNull(type, name, value);

      if (attributeTypes.contains(type)) {
        throw new IllegalArgumentException(
            "Builder already contains the attribute type "
                + type.getNameOrOID());
      }

      attributeTypes.add(type);
      attributeNames.add(name);
      attributeValues.add(value);
    }



    /**
     * Parses an RDN from the provided string starting at the
     * specified location, appending any AVAs to this RDN builder.
     * <p>
     * This method is package visible so that it can be used by
     * the DN decoder. It is not intended for use elsewhere.
     *
     * @param s
     *          The string to be parsed.
     * @param pos
     *          The position of the first character in the string to
     *          parse.
     * @param allowEmpty
     *          Flag indicating whether or not the parsed RDN can be
     *          empty or not.
     * @return Returns <code>-1</code> if decoding was successful
     *         and parsing consumed the remainder of the string
     *         (including trailing space), or the position of the next
     *         RDN separator character (i.e. a ',' or ';').
     * @throws DirectoryException
     *           If it was not possible to parse a valid RDN from the
     *           provided string.
     */
    int parse(String s, int pos, boolean allowEmpty)
        throws DirectoryException {
      assert debugEnter(CLASS_NAME, "parse", String.valueOf(s),
          String.valueOf(pos));

      // There must be at least one AVA.
      int count = attributeTypes.size();
      pos = parseAVA(s, pos);

      if (pos == -1 && !allowEmpty) {
        if (count == attributeTypes.size()) {
          // Nothing was parsed.
          int msgID = MSGID_RDN_DECODE_NULL;
          String message = getMessage(msgID);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message, msgID);
        }
      }

      // Parse any remaining AVAs.
      while (pos != -1 && s.charAt(pos) == '+') {
        count = attributeTypes.size();
        pos = parseAVA(s, pos + 1);

        if (pos == -1 && count == attributeTypes.size()) {
          // Nothing was parsed.
          int msgID = MSGID_RDN_UNEXPECTED_COMMA;
          String message = getMessage(msgID, s, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message, msgID);
        }
      }

      return pos;
    }



    /**
     * Parse a single AVA.
     *
     * @param s
     *          The string to be parsed.
     * @param pos
     *          The position of the first character in the string to
     *          parse.
     * @return Returns <code>-1</code> if decoding was successful
     *         and parsing consumed the remainder of the possibly
     *         empty string (including trailing space), or the
     *         position of the next AVA separator character (i.e. a
     *         '+', ',' or ';').
     * @throws DirectoryException
     *           If it was not possible to parse a valid AVA from the
     *           provided string.
     */
    private int parseAVA(String s, int pos)
        throws DirectoryException {
      int length = s.length();

      // Skip over any spaces that may follow it
      // before the next attribute name.
      char c;
      while ((pos < length) && ((c = s.charAt(pos)) == ' ')) {
        pos++;
      }

      // Reached the end of the string - let the caller handle this.
      if (pos >= length) {
        return -1;
      }

      // Parse the attribute name.
      StringBuilder attributeName = new StringBuilder();
      pos = parseAttributeName(s, pos, attributeName);

      // Make sure we're not at the end of the RDN.
      if (pos >= length) {
        int msgID = MSGID_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME;
        String message = getMessage(msgID, s, attributeName
            .toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);
      }

      // Skip over any spaces between the attribute name and the
      // equal sign.
      c = s.charAt(pos);
      while (c == ' ') {
        pos++;
        if (pos >= length) {
          // This means that we hit the end of the string before
          // finding a '='. This is illegal because there is no
          // attribute-value separator.
          int msgID = MSGID_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME;
          String message = getMessage(msgID, s, attributeName
              .toString());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message, msgID);
        } else {
          c = s.charAt(pos);
        }
      }

      // The next character must be an equal sign.
      if (c == '=') {
        pos++;
      } else {
        int msgID = MSGID_ATTR_SYNTAX_DN_NO_EQUAL;
        String message = getMessage(msgID, s, attributeName
            .toString(), c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);
      }

      // Skip over any spaces after the equal sign.
      while ((pos < length) && ((c = s.charAt(pos)) == ' ')) {
        pos++;
      }

      // If we are at the end of the RDN string, then that must mean
      // that the attribute value was empty. This will probably
      // never happen in a real-world environment, but technically
      // isn't illegal. If it does happen, then go ahead and return
      // the RDN.
      if (pos >= length) {
        String name = attributeName.toString();
        String lowerName = toLowerCase(name);
        AttributeType attrType = DirectoryServer
            .getAttributeType(lowerName);

        if (attrType == null) {
          // This must be an attribute type that we don't know
          // about.
          // In that case, we'll create a new attribute using the
          // default syntax. If this is a problem, it will be caught
          // later either by not finding the target entry or by not
          // allowing the entry to be added.
          attrType = DirectoryServer.getDefaultAttributeType(name);
        }

        AttributeValue value = new AttributeValue(
            new ASN1OctetString(), new ASN1OctetString());
        append(attrType, name, value);
        return -1;
      }

      // Parse the value for this RDN component.
      ByteString parsedValue = new ASN1OctetString();
      pos = parseAttributeValue(s, pos, parsedValue);

      // Update the RDN to include the new attribute/value.
      String name = attributeName.toString();
      String lowerName = toLowerCase(name);
      AttributeType attrType = DirectoryServer
          .getAttributeType(lowerName);
      if (attrType == null) {
        // This must be an attribute type that we don't know about.
        // In that case, we'll create a new attribute using the
        // default syntax. If this is a problem, it will be caught
        // later either by not finding the target entry or by not
        // allowing the entry to be added.
        attrType = DirectoryServer.getDefaultAttributeType(name);
      }

      AttributeValue value = new AttributeValue(attrType,
          parsedValue);
      append(attrType, name, value);

      // Skip over any spaces that might be after the attribute
      // value.
      while ((pos < length) && ((c = s.charAt(pos)) == ' ')) {
        pos++;
      }

      // If we're at the end of the string, then return the RDN.
      if (pos >= length) {
        return -1;
      }

      // If the next character is a comma or semicolon, then that is
      // not allowed. It would be legal for a DN but not an RDN.
      if ((c == ',') || (c == ';')) {
        return pos;
      }

      // If the next character is anything but a plus sign, then it
      // is illegal.
      if (c != '+') {
        int msgID = MSGID_ATTR_SYNTAX_DN_INVALID_CHAR;
        String message = getMessage(msgID, s, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);
      }

      return pos;
    }



    /**
     * Removes all the attribute value assertions from this RDN
     * builder.
     */
    public void clear() {
      attributeTypes = new ArrayList<AttributeType>(3);
      attributeValues = new ArrayList<AttributeValue>(3);
      attributeNames = new ArrayList<String>(3);
    }



    /**
     * Returns <code>true</code> if this RDN builder is empty.
     *
     * @return Returns <code>true</code> if this RDN builder is
     *         empty.
     */
    public boolean isEmpty() {
      return attributeTypes.isEmpty();
    }



    /**
     * Creates a new RDN instance based on the current contents of
     * this RDN builder. Subsequent changes to this RDN builder do not
     * affect the contents of the returned <code>RDN</code>.
     *
     * @return Returns a new RDN instance based on the current
     *         contents of this RDN builder.
     * @throws IllegalStateException
     *           If a new RDN could not be created because this RDN
     *           builder is emtpy.
     */
    public RDN getInstance() throws IllegalStateException {
      int sz = attributeTypes.size();

      if (sz == 0) {
        throw new IllegalStateException("RDN builder is empty");
      }

      AttributeType[] types = new AttributeType[sz];
      String[] names = new String[sz];
      AttributeValue[] values = new AttributeValue[sz];

      attributeTypes.toArray(types);
      attributeNames.toArray(names);
      attributeValues.toArray(values);

      return new RDN(types, names, values);
    }
  }



  /**
   * Creates a new RDN with the provided information.
   *
   * @param types
   *          The attribute types for this RDN.
   * @param names
   *          The user-provided names for this RDN.
   * @param values
   *          The values for this RDN.
   */
  private RDN(AttributeType[] types, String[] names,
      AttributeValue[] values) {
    assert debugConstructor(CLASS_NAME, String.valueOf(types), String
        .valueOf(names), String.valueOf(values));

    this.attributeTypes = types;
    this.attributeNames = names;
    this.attributeValues = values;
  }



  /**
   * Retrieves the number of attribute-value pairs contained in this
   * RDN.
   *
   * @return The number of attribute-value pairs contained in this
   *         RDN.
   */
  public int getNumValues() {
    assert debugEnter(CLASS_NAME, "getNumValues");

    return attributeTypes.length;
  }



  /**
   * Retrieves the attribute type at the specified AVA in this RDN.
   *
   * @param index
   *          The index of the AVA in this RDN.
   * @return Returns the attribute type at the specified AVA in this
   *         RDN.
   * @throws IndexOutOfBoundsException
   *           If <code>index</code> is out of range
   *           <code>(index < 0 || index >= getNumValues()</code>.
   */
  public AttributeType getAttributeType(int index)
      throws IndexOutOfBoundsException {
    assert debugEnter(CLASS_NAME, "getAttributeType");

    if (index < 0 || index >= attributeTypes.length) {
      throw new IndexOutOfBoundsException("Index: " + index
          + ", Size: " + attributeTypes.length);
    }
    return attributeTypes[index];
  }



  /**
   * Retrieves the user-defined attribute name at the specified AVA in
   * this RDN.
   *
   * @param index
   *          The index of the AVA in this RDN.
   * @return Returns the user-defined attribute name at the specified
   *         AVA in this RDN.
   * @throws IndexOutOfBoundsException
   *           If <code>index</code> is out of range
   *           <code>(index < 0 || index >= getNumValues()</code>.
   */
  public String getAttributeName(int index)
      throws IndexOutOfBoundsException {
    assert debugEnter(CLASS_NAME, "getAttributeName");

    if (index < 0 || index >= attributeTypes.length) {
      throw new IndexOutOfBoundsException("Index: " + index
          + ", Size: " + attributeTypes.length);
    }
    return attributeNames[index];
  }



  /**
   * Retrieves the attribute value at the specified AVA in this RDN.
   * <p>
   * Applications <b>must not</b> modify the contents of the returned
   * attribute value.
   *
   * @param index
   *          The index of the AVA in this RDN.
   * @return Returns the attribute value at the specified AVA in this
   *         RDN.
   * @throws IndexOutOfBoundsException
   *           If <code>index</code> is out of range
   *           <code>(index < 0 || index >= getNumValues()</code>.
   */
  public AttributeValue getAttributeValue(int index)
      throws IndexOutOfBoundsException {
    assert debugEnter(CLASS_NAME, "getAttributeValue");

    if (index < 0 || index >= attributeTypes.length) {
      throw new IndexOutOfBoundsException("Index: " + index
          + ", Size: " + attributeTypes.length);
    }
    return attributeValues[index];
  }



  /**
   * Indicates whether this RDN includes the specified attribute type.
   *
   * @param attributeType
   *          The attribute type for which to make the determination.
   * @return <code>true</code> if the RDN includes the specified
   *         attribute type, or <code>false</code> if not.
   */
  public boolean hasAttributeType(AttributeType attributeType) {
    assert debugEnter(CLASS_NAME, "hasAttributeType", String
        .valueOf(attributeType));

    ensureNotNull(attributeType);

    for (AttributeType t : attributeTypes) {
      if (t.equals(attributeType)) {
        return true;
      }
    }

    return false;
  }



  /**
   * Retrieves the attribute value that is associated with the
   * specified attribute type.
   * <p>
   * Applications <b>must not</b> modify the contents of the returned
   * attribute value.
   *
   * @param attributeType
   *          The attribute type for which to retrieve the
   *          corresponding value.
   * @return The value for the requested attribute type, or
   *         <code>null</code> if the specified attribute type is
   *         not present in the RDN.
   */
  public AttributeValue getAttributeValue(
      AttributeType attributeType) {
    assert debugEnter(CLASS_NAME, "getAttributeValue", String
        .valueOf(attributeType));

    ensureNotNull(attributeType);

    for (int i = 0; i < attributeTypes.length; i++) {
      if (attributeTypes[i].equals(attributeType)) {
        return attributeValues[i];
      }
    }

    return null;
  }



  /**
   * Indicates whether this RDN is multivalued.
   *
   * @return <code>true</code> if this RDN is multivalued, or
   *         <code>false</code> if not.
   */
  public boolean isMultiValued() {
    assert debugEnter(CLASS_NAME, "isMultiValued");

    return (attributeTypes.length > 1);
  }



  /**
   * Returns an <code>RDN</code> object holding the value of the
   * specified <code>String</code>. The argument is interpreted as
   * representing the LDAP string representation of an RDN.
   * <p>
   * This method is identical to {@link #decode(String)}.
   *
   * @param s
   *          The string to be parsed.
   * @return Returns a <code>RDN</code> holding the value
   *         represented by the <code>string</code> argument.
   * @throws DirectoryException
   *           If a problem occurs while trying to decode the provided
   *           string as a RDN.
   */
  public static RDN valueOf(String s) throws DirectoryException {
    return decode(s);
  }



  /**
   * Decodes the provided ASN.1 octet string as a RDN.
   *
   * @param rdnString
   *          The ASN.1 octet string to decode as a RDN.
   * @return The decoded RDN.
   * @throws DirectoryException
   *           If a problem occurs while trying to decode the provided
   *           ASN.1 octet string as a RDN.
   */
  public static RDN decode(ByteString rdnString)
      throws DirectoryException {
    assert debugEnter(CLASS_NAME, "decode",
        String.valueOf(rdnString));

    ensureNotNull(rdnString);

    // Use string-based decoder.
    return decode(rdnString.stringValue());
  }



  /**
   * Decodes the provided string as an RDN.
   *
   * @param rdnString
   *          The string to decode as an RDN.
   * @return The decoded RDN.
   * @throws DirectoryException
   *           If a problem occurs while trying to decode the provided
   *           string as a RDN.
   */
  public static RDN decode(String rdnString)
      throws DirectoryException {
    assert debugEnter(CLASS_NAME, "decode",
        String.valueOf(rdnString));

    ensureNotNull(rdnString);

    // Use an RDN builder to parse the string.
    Builder builder = createBuilder();
    int pos = builder.parse(rdnString, 0, false);

    // Make sure that the string did not contain any trailing RDNs.
    if (pos != -1) {
      int msgID = MSGID_RDN_UNEXPECTED_COMMA;
      String message = getMessage(msgID, rdnString, pos);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
          message, msgID);
    }

    // Return the parsed RDN instance.
    return builder.getInstance();
  }



  /**
   * Indicates whether the provided object is equal to this RDN. It
   * will only be considered equal if it is an RDN object containing
   * the same attribute value assertions as this RDN (the order does
   * not matter).
   *
   * @param o
   *          The object for which to make the determination.
   * @return <code>true</code> if it is determined that the provided
   *         object is equal to this RDN, or <code>false</code> if
   *         not.
   */
  public boolean equals(Object o) {
    assert debugEnter(CLASS_NAME, "equals", String.valueOf(o));

    if (this == o) {
      return true;
    } else if (o instanceof RDN) {
      RDN other = (RDN) o;

      String nvalue1 = toNormalizedString();
      String nvalue2 = other.toNormalizedString();
      return nvalue1.equals(nvalue2);
    } else {
      return false;
    }
  }



  /**
   * Retrieves the hash code for this RDN. It will be calculated as
   * the hash code of the RDN's normalized string representation.
   *
   * @return The hash code for this RDN.
   */
  public int hashCode() {
    assert debugEnter(CLASS_NAME, "hashCode");

    return toNormalizedString().hashCode();
  }



  /**
   * Retrieves a string representation of this RDN.
   *
   * @return A string representation of this RDN.
   */
  public String toString() {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this RDN to the provided
   * buffer.
   *
   * @param buffer
   *          The buffer to which the string representation should be
   *          appended.
   */
  public void toString(StringBuilder buffer) {
    assert debugEnter(CLASS_NAME, "toString",
        "java.lang.StringBuilder");

    ensureNotNull(buffer);

    buffer.append(attributeNames[0]);
    buffer.append("=");
    String value = attributeValues[0].getStringValue();
    quoteAttributeValue(buffer, value);

    for (int i = 1; i < attributeTypes.length; i++) {
      buffer.append("+");
      buffer.append(attributeNames[i]);
      buffer.append("=");

      value = attributeValues[i].getStringValue();
      quoteAttributeValue(buffer, value);
    }
  }



  /**
   * Retrieves a normalized string representation of this RDN.
   *
   * @return A normalized string representation of this RDN.
   */
  public String toNormalizedString() {
    if (normalizedRDN == null) {
      StringBuilder builder = new StringBuilder();

      if (attributeNames.length == 1) {
        // Optimize for the common case of a single AVA.
        appendNormalizedAVA(builder, attributeTypes[0],
            attributeValues[0]);
      } else {
        // Multiple AVAs require sorting.
        TreeMap<String, Integer> map;

        map = new TreeMap<String, Integer>();
        for (int i = 0; i < attributeTypes.length; i++) {
          map.put(attributeTypes[i].getNameOrOID(), i);
        }

        boolean isFirst = true;
        for (Integer i : map.values()) {
          if (!isFirst) {
            builder.append('+');
          } else {
            isFirst = false;
          }
          appendNormalizedAVA(builder, attributeTypes[i],
              attributeValues[i]);
        }
      }

      normalizedRDN = builder.toString();
    }

    return normalizedRDN;
  }



  /**
   * Appends a normalized string representation of this RDN to the
   * provided buffer.
   *
   * @param buffer
   *          The buffer to which to append the information.
   */
  public void toNormalizedString(StringBuilder buffer) {
    assert debugEnter(CLASS_NAME, "toNormalizedString",
        "java.lang.StringBuilder");

    ensureNotNull(buffer);

    buffer.append(toNormalizedString());
  }



  /**
   * Compares this RDN with the provided RDN.
   * <p>
   * The comparison will be done in order of the sorted RDN
   * components. It will attempt to use an ordering matching rule for
   * the associated attributes (if one is provided), but will fall
   * back on a bytewise comparison of the normalized values if
   * necessary.
   *
   * @param rdn
   *          The RDN against which to compare this RDN.
   * @return A negative integer if this RDN should come before the
   *         provided RDN, a positive integer if this RDN should come
   *         after the provided RDN, or zero if there is no difference
   *         with regard to ordering.
   */
  public int compareTo(RDN rdn) {
    assert debugEnter(CLASS_NAME, "compareTo", String.valueOf(rdn));

    ensureNotNull(rdn);

    // Handle the common case efficiently.
    if (attributeTypes.length == 1
        && rdn.attributeTypes.length == 1) {
      AttributeType type1 = attributeTypes[0];
      AttributeType type2 = rdn.attributeTypes[0];

      AttributeValue value1 = attributeValues[0];
      AttributeValue value2 = rdn.attributeValues[0];

      return compareAVA(type1, value1, type2, value2);
    }

    // We have at least one multi-valued RDNs, so we need to sort.
    TreeMap<String, Integer> map1;
    TreeMap<String, Integer> map2;

    map1 = new TreeMap<String, Integer>();
    map2 = new TreeMap<String, Integer>();

    for (int i = 0; i < attributeTypes.length; i++) {
      map1.put(attributeTypes[i].getNameOrOID(), i);
    }

    for (int i = 0; i < rdn.attributeTypes.length; i++) {
      map2.put(rdn.attributeTypes[i].getNameOrOID(), i);
    }

    // Now compare the sorted AVAs.
    Iterator<Integer> i1= map1.values().iterator();
    Iterator<Integer> i2 = map2.values().iterator();

    while (i1.hasNext() && i2.hasNext()) {
      int int1 = i1.next();
      int int2 = i2.next();

      AttributeType type1 = attributeTypes[int1];
      AttributeType type2 = rdn.attributeTypes[int2];

      AttributeValue value1 = attributeValues[int1];
      AttributeValue value2 = rdn.attributeValues[int2];

      int rc = compareAVA(type1, value1, type2, value2);
      if (rc != 0) {
        return rc;
      }
    }

    // At least one of the iterators has finished.
    if (i1.hasNext() == false && i2.hasNext() == false) {
      return 0;
    } else if (i1.hasNext() == false) {
      return -1;
    } else {
      return 1;
    }
  }



  /**
   * Compare two AVAs for order.
   *
   * @param type1
   *          The attribute type of the first AVA.
   * @param value1
   *          The attribute value of the first AVA.
   * @param type2
   *          The attribute type of the second AVA.
   * @param value2
   *          The attribute value of the second AVA.
   * @return Returns a negative integer, zero, or a positive integer
   *         if the first AVA is less than, equal to, or greater than
   *         the second.
   */
  private int compareAVA(AttributeType type1, AttributeValue value1,
      AttributeType type2, AttributeValue value2) {
    if (type1.equals(type2)) {
      OrderingMatchingRule rule = type1.getOrderingMatchingRule();

      try {
        if (rule != null) {
          byte[] b1 = value1.getNormalizedValueBytes();
          byte[] b2 = value2.getNormalizedValueBytes();

          return rule.compare(b1, b2);
        } else {
          byte[] b1 = value1.getNormalizedValue().value();
          byte[] b2 = value2.getNormalizedValue().value();

          return StaticUtils.compare(b1, b2);
        }
      } catch (Exception e) {
        assert debugException(CLASS_NAME, "compareAVA", e);

        // Just get the raw values and do a comparison between them.
        byte[] b1 = value1.getValue().value();
        byte[] b2 = value2.getValue().value();

        return StaticUtils.compare(b1, b2);
      }
    } else {
      String name1 = toLowerCase(type1.getNameOrOID());
      String name2 = toLowerCase(type2.getNameOrOID());

      return name1.compareTo(name2);
    }
  }



  /**
   * Normalize and append the provided attribute type and value to the
   * provided buffer.
   *
   * @param buffer
   *          The string buffer.
   * @param type
   *          The attribute type.
   * @param value
   *          The attribute value.
   */
  private void appendNormalizedAVA(StringBuilder buffer,
      AttributeType type, AttributeValue value) {
    toLowerCase(type.getNameOrOID(), buffer);
    buffer.append('=');

    try {
      quoteAttributeValue(buffer, value.getNormalizedStringValue());
    } catch (Exception e) {
      assert debugException(CLASS_NAME, "toNormalizedString", e);
      quoteAttributeValue(buffer, value.getStringValue());
    }
  }



  /**
   * Encode an attribute value according to the DN string encoding
   * rules, and append it to the provided buffer.
   *
   * @param buffer
   *          Append the attribtue value to this buffer.
   * @param value
   *          The value to be represented in a DN-safe form.
   */
  private void quoteAttributeValue(StringBuilder buffer,
      String value) {
    assert debugEnter(CLASS_NAME, "quoteAttributeValue", String
        .valueOf(value));

    // Do nothing if the value is empty.
    int length = value.length();
    if (length == 0) {
      return;
    }

    // Assume 1-byte UTF8 and that no quoting will be required.
    buffer.ensureCapacity(buffer.length() + length);

    // Quote leading space or #.
    char c = value.charAt(0);
    if (c == ' ' || c == '#') {
      buffer.append('\\');
      buffer.append(c);
    } else {
      quoteChar(buffer, c);
    }

    // Process the remainder of the string.
    for (int i = 1; i < (length - 1); i++) {
      quoteChar(buffer, value.charAt(i));
    }

    // Quote trailing space.
    if (length > 1) {
      c = value.charAt(length - 1);
      if (c == ' ') {
        buffer.append('\\');
        buffer.append(c);
      } else {
        quoteChar(buffer, c);
      }
    }
  }



  /**
   * Encode a single attribute value from an RDN according to the DN
   * string encoding rules.
   *
   * @param buffer
   *          Append the character to this buffer.
   * @param c
   *          The character to be encoded.
   */
  private void quoteChar(StringBuilder buffer, char c) {
    if ((c < ' ') || (c > '~')) {
      for (byte b : getBytes(String.valueOf(c))) {
        buffer.append('\\');
        buffer.append(byteToLowerHex(b));
      }
    } else {
      switch (c) {
      case ',':
      case '+':
      case '"':
      case '\\':
      case '<':
      case '>':
      case ';':
        buffer.append('\\');
      }

      buffer.append(c);
    }
  }



  /**
   * Parses an attribute name from the provided DN string starting at
   * the specified location.
   *
   * @param dnString
   *          The DN string to be parsed.
   * @param pos
   *          The position at which to start parsing the attribute
   *          name.
   * @param attributeName
   *          The buffer to which to append the parsed attribute name.
   * @return The position of the first character that is not part of
   *         the attribute name.
   * @throws DirectoryException
   *           If it was not possible to parse a valid attribute name
   *           from the provided DN string.
   */
  private static int parseAttributeName(String dnString, int pos,
      StringBuilder attributeName) throws DirectoryException {
    assert debugEnter(CLASS_NAME, "parseAttributeName", String
        .valueOf(dnString), String.valueOf(pos),
        "java.lang.StringBuilder");
    boolean allowExceptions = DirectoryServer
        .allowAttributeNameExceptions();

    int length = dnString.length();

    // Skip over any leading spaces.
    if (pos < length) {
      while (dnString.charAt(pos) == ' ') {
        pos++;
        if (pos == length) {
          // This means that the remainder of the DN was completely
          // comprised of spaces. If we have gotten here, then we
          // know that there is at least one RDN component, and
          // therefore the last non-space character of the DN must
          // have been a comma. This is not acceptable.
          int msgID = MSGID_ATTR_SYNTAX_DN_END_WITH_COMMA;
          String message = getMessage(msgID, dnString);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message, msgID);
        }
      }
    }

    // Next, we should find the attribute name for this RDN component.
    // It may either be a name (with only letters, digits, and dashes
    // and starting with a letter) or an OID (with only digits and
    // periods, optionally prefixed with "oid."), and there is also a
    // special case in which we will allow underscores. Because of
    // the complexity involved, read the entire name first with
    // minimal validation and then do more thorough validation later.
    boolean checkForOID = false;
    boolean endOfName = false;
    while (pos < length) {
      // To make the switch more efficient, we'll include all ASCII
      // characters in the range of allowed values and then reject the
      // ones that aren't allowed.
      char c = dnString.charAt(pos);
      switch (c) {
      case ' ':
        // This should denote the end of the attribute name.
        endOfName = true;
        break;

      case '!':
      case '"':
      case '#':
      case '$':
      case '%':
      case '&':
      case '\'':
      case '(':
      case ')':
      case '*':
      case '+':
      case ',':
        // None of these are allowed in an attribute name or any
        // character immediately following it.
        int msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
        String message = getMessage(msgID, dnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);

      case '-':
        // This will be allowed as long as it isn't the first
        // character in the attribute name.
        if (attributeName.length() > 0) {
          attributeName.append(c);
        } else {
          msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_DASH;
          message = getMessage(msgID, dnString, c);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message, msgID);
        }
        break;

      case '.':
        // The period could be allowed if the attribute name is
        // actually expressed as an OID. We'll accept it for now,
        // but make sure to check it later.
        attributeName.append(c);
        checkForOID = true;
        break;

      case '/':
        // This is not allowed in an attribute name or any character
        // immediately following it.
        msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
        message = getMessage(msgID, dnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);

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
        // Digits are always allowed if they are not the first
        // character. However, they may be allowed if they are the
        // first character if the valid is an OID or if the
        // attribute name exceptions option is enabled. Therefore,
        // we'll accept it now and check it later.
        attributeName.append(c);
        break;

      case ':':
      case ';': // NOTE: attribute options are not allowed in a DN.
      case '<':
        // None of these are allowed in an attribute name or any
        // character immediately following it.
        msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
        message = getMessage(msgID, dnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);

      case '=':
        // This should denote the end of the attribute name.
        endOfName = true;
        break;

      case '>':
      case '?':
      case '@':
        // None of these are allowed in an attribute name or any
        // character immediately following it.
        msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
        message = getMessage(msgID, dnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);

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
        // These will always be allowed.
        attributeName.append(c);
        break;

      case '[':
      case '\\':
      case ']':
      case '^':
        // None of these are allowed in an attribute name or any
        // character immediately following it.
        msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
        message = getMessage(msgID, dnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);

      case '_':
        // This will never be allowed as the first character. It
        // may be allowed for subsequent characters if the attribute
        // name exceptions option is enabled.
        if (attributeName.length() == 0) {
          msgID =
            MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_UNDERSCORE;
          message = getMessage(msgID, dnString,
              ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message, msgID);
        } else if (allowExceptions) {
          attributeName.append(c);
        } else {
          msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_UNDERSCORE_CHAR;
          message = getMessage(msgID, dnString,
              ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message, msgID);
        }
        break;

      case '`':
        // This is not allowed in an attribute name or any character
        // immediately following it.
        msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
        message = getMessage(msgID, dnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);

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
        // These will always be allowed.
        attributeName.append(c);
        break;

      default:
        // This is not allowed in an attribute name or any character
        // immediately following it.
        msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
        message = getMessage(msgID, dnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);
      }

      if (endOfName) {
        break;
      }

      pos++;
    }

    // We should now have the full attribute name. However, we may
    // still need to perform some validation, particularly if the
    // name contains a period or starts with a digit. It must also
    // have at least one character.
    if (attributeName.length() == 0) {
      int msgID = MSGID_ATTR_SYNTAX_DN_ATTR_NO_NAME;
      String message = getMessage(msgID, dnString);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
          message, msgID);
    } else if (checkForOID) {
      boolean validOID = true;

      int namePos = 0;
      int nameLength = attributeName.length();
      char ch = attributeName.charAt(0);
      if ((ch == 'o') || (ch == 'O')) {
        if (nameLength <= 4) {
          validOID = false;
        } else {
          if ((((ch = attributeName.charAt(1)) == 'i') || (ch == 'I'))
              && (((ch = attributeName.charAt(2)) == 'd')
                  || (ch == 'D'))
              && (attributeName.charAt(3) == '.')) {
            attributeName.delete(0, 4);
            nameLength -= 4;
          } else {
            validOID = false;
          }
        }
      }

      while (validOID && (namePos < nameLength)) {
        ch = attributeName.charAt(namePos++);
        if (isDigit(ch)) {
          while (validOID && (namePos < nameLength)
              && isDigit(attributeName.charAt(namePos))) {
            namePos++;
          }

          if ((namePos < nameLength)
              && (attributeName.charAt(namePos) != '.')) {
            validOID = false;
          }
        } else if (ch == '.') {
          if ((namePos == 1)
              || (attributeName.charAt(namePos - 2) == '.')) {
            validOID = false;
          }
        } else {
          validOID = false;
        }
      }

      if (validOID && (attributeName.charAt(nameLength - 1) == '.')) {
        validOID = false;
      }

      if (!validOID) {
        int msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_PERIOD;
        String message = getMessage(msgID, dnString, attributeName
            .toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);
      }
    } else if (isDigit(attributeName.charAt(0))
        && (!allowExceptions)) {
      int msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_DIGIT;
      String message = getMessage(msgID, dnString, attributeName
          .charAt(0), ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
          message, msgID);
    }

    return pos;
  }



  /**
   * Parses the attribute value from the provided DN string starting
   * at the specified location. When the value has been parsed, it
   * will be assigned to the provided ASN.1 octet string.
   *
   * @param dnString
   *          The DN string to be parsed.
   * @param pos
   *          The position of the first character in the attribute
   *          value to parse.
   * @param attributeValue
   *          The ASN.1 octet string whose value should be set to the
   *          parsed attribute value when this method completes
   *          successfully.
   * @return The position of the first character that is not part of
   *         the attribute value.
   * @throws DirectoryException
   *           If it was not possible to parse a valid attribute value
   *           from the provided DN string.
   */
  private static int parseAttributeValue(String dnString, int pos,
      ByteString attributeValue) throws DirectoryException {
    assert debugEnter(CLASS_NAME, "parseAttributeValue", String
        .valueOf(dnString), String.valueOf(pos),
        "java.lang.StringBuilder");

    // All leading spaces have already been stripped so we can start
    // reading the value. However, it may be empty so check for that.
    int length = dnString.length();
    if (pos >= length) {
      attributeValue.setValue("");
      return pos;
    }

    // Look at the first character. If it is an octothorpe (#), then
    // that means that the value should be a hex string.
    char c = dnString.charAt(pos++);
    if (c == '#') {
      // The first two characters must be hex characters.
      StringBuilder hexString = new StringBuilder();
      if ((pos + 2) > length) {
        int msgID = MSGID_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT;
        String message = getMessage(msgID, dnString);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);
      }

      for (int i = 0; i < 2; i++) {
        c = dnString.charAt(pos++);
        if (isHexDigit(c)) {
          hexString.append(c);
        } else {
          int msgID = MSGID_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT;
          String message = getMessage(msgID, dnString, c);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message, msgID);
        }
      }

      // The rest of the value must be a multiple of two hex
      // characters. The end of the value may be designated by the
      // end of the DN, a comma or semicolon, or a space.
      while (pos < length) {
        c = dnString.charAt(pos++);
        if (isHexDigit(c)) {
          hexString.append(c);

          if (pos < length) {
            c = dnString.charAt(pos++);
            if (isHexDigit(c)) {
              hexString.append(c);
            } else {
              int msgID = MSGID_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT;
              String message = getMessage(msgID, dnString, c);
              throw new DirectoryException(
                  ResultCode.INVALID_DN_SYNTAX, message, msgID);
            }
          } else {
            int msgID = MSGID_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT;
            String message = getMessage(msgID, dnString);
            throw new DirectoryException(
                ResultCode.INVALID_DN_SYNTAX, message, msgID);
          }
        } else if ((c == ' ') || (c == ',') || (c == ';')
            || (c == '+')) {
          // This denotes the end of the value.
          pos--;
          break;
        } else {
          int msgID = MSGID_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT;
          String message = getMessage(msgID, dnString, c);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message, msgID);
        }
      }

      // At this point, we should have a valid hex string. Convert it
      // to a byte array and set that as the value of the provided
      // octet string.
      try {
        attributeValue.setValue(hexStringToByteArray(hexString
            .toString()));
        return pos;
      } catch (Exception e) {
        assert debugException(CLASS_NAME, "parseAttributeValue", e);

        int msgID = MSGID_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE;
        String message = getMessage(msgID, dnString, String
            .valueOf(e));
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            message, msgID);
      }
    }

    // If the first character is a quotation mark, then the value
    // should continue until the corresponding closing quotation mark.
    else if (c == '"') {
      // Keep reading until we find an unescaped closing quotation
      // mark.
      boolean escaped = false;
      StringBuilder valueString = new StringBuilder();
      while (true) {
        if (pos >= length) {
          // We hit the end of the DN before the closing quote.
          // That's an error.
          int msgID = MSGID_ATTR_SYNTAX_DN_UNMATCHED_QUOTE;
          String message = getMessage(msgID, dnString);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              message, msgID);
        }

        c = dnString.charAt(pos++);
        if (escaped) {
          // The previous character was an escape, so we'll take this
          // one no matter what.
          valueString.append(c);
          escaped = false;
        } else if (c == '\\') {
          // The next character is escaped. Set a flag to denote
          // this, but don't include the backslash.
          escaped = true;
        } else if (c == '"') {
          // This is the end of the value.
          break;
        } else {
          // This is just a regular character that should be in the
          // value.
          valueString.append(c);
        }
      }

      attributeValue.setValue(valueString.toString());
      return pos;
    }

    // Otherwise, use general parsing to find the end of the value.
    else {
      boolean escaped;
      StringBuilder valueString = new StringBuilder();
      StringBuilder hexChars = new StringBuilder();

      if (c == '\\') {
        escaped = true;
      } else {
        escaped = false;
        valueString.append(c);
      }

      // Keep reading until we find an unescaped comma or plus sign or
      // the end of the DN.
      while (true) {
        if (pos >= length) {
          // This is the end of the DN and therefore the end of the
          // value. If there are any hex characters, then we need to
          // deal with them accordingly.
          appendHexChars(dnString, valueString, hexChars);
          break;
        }

        c = dnString.charAt(pos++);
        if (escaped) {
          // The previous character was an escape, so we'll take this
          // one. However, this could be a hex digit, and if that's
          // the case then the escape would actually be in front of
          // two hex digits that should be treated as a special
          // character.
          if (isHexDigit(c)) {
            // It is a hexadecimal digit, so the next digit must be
            // one too. However, this could be just one in a series
            // of escaped hex pairs that is used in a string
            // containing one or more multi-byte UTF-8 characters so
            // we can't just treat this byte in isolation. Collect
            // all the bytes together and make sure to take care of
            // these hex bytes before appending anything else to the
            // value.
            if (pos >= length) {
              int msgID =
                MSGID_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID;
              String message = getMessage(msgID, dnString);
              throw new DirectoryException(
                  ResultCode.INVALID_DN_SYNTAX, message, msgID);
            } else {
              char c2 = dnString.charAt(pos++);
              if (isHexDigit(c2)) {
                hexChars.append(c);
                hexChars.append(c2);
              } else {
                int msgID =
                  MSGID_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID;
                String message = getMessage(msgID, dnString);
                throw new DirectoryException(
                    ResultCode.INVALID_DN_SYNTAX, message, msgID);
              }
            }
          } else {
            appendHexChars(dnString, valueString, hexChars);
            valueString.append(c);
          }

          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if ((c == ',') || (c == ';') || (c == '+')) {
          appendHexChars(dnString, valueString, hexChars);
          pos--;
          break;
        } else {
          appendHexChars(dnString, valueString, hexChars);
          valueString.append(c);
        }
      }

      // Strip off any unescaped spaces that may be at the end of the
      // value.
      if (pos > 2 && dnString.charAt(pos - 1) == ' '
          && dnString.charAt(pos - 2) != '\\') {
        int lastPos = valueString.length() - 1;
        while (lastPos > 0) {
          if (valueString.charAt(lastPos) == ' ') {
            valueString.delete(lastPos, lastPos + 1);
            lastPos--;
          } else {
            break;
          }
        }
      }

      attributeValue.setValue(valueString.toString());
      return pos;
    }
  }



  /**
   * Decodes a hexadecimal string from the provided
   * <code>hexChars</code> buffer, converts it to a byte array, and
   * then converts that to a UTF-8 string. The resulting UTF-8 string
   * will be appended to the provided <code>valueString</code>
   * buffer, and the <code>hexChars</code> buffer will be cleared.
   *
   * @param dnString
   *          The DN string that is being decoded.
   * @param valueString
   *          The buffer containing the value to which the decoded
   *          string should be appended.
   * @param hexChars
   *          The buffer containing the hexadecimal characters to
   *          decode to a UTF-8 string.
   * @throws DirectoryException
   *           If any problem occurs during the decoding process.
   */
  private static void appendHexChars(String dnString,
      StringBuilder valueString, StringBuilder hexChars)
      throws DirectoryException {
    try {
      byte[] hexBytes = hexStringToByteArray(hexChars.toString());
      valueString.append(new String(hexBytes, "UTF-8"));
      hexChars.delete(0, hexChars.length());
    } catch (Exception e) {
      assert debugException(CLASS_NAME, "appendHexChars", e);

      int msgID = MSGID_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE;
      String message = getMessage(msgID, dnString, String.valueOf(e));
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
          message, msgID);
    }
  }

}
