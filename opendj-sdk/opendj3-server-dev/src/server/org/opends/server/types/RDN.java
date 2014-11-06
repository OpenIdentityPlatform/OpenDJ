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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.types;

import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.core.DirectoryServer;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a data structure for storing and interacting
 * with the relative distinguished names associated with entries in
 * the Directory Server.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class RDN
       implements Comparable<RDN>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The set of attribute types for the elements in this RDN. */
  private AttributeType[] attributeTypes;

  /** The set of values for the elements in this RDN. */
  private ByteString[] attributeValues;

  /** The number of values for this RDN. */
  private int numValues;

  /** The string representation of the normalized form of this RDN. */
  private String normalizedRDN;

  /** The string representation of this RDN. */
  private String rdnString;

  /** The set of user-provided names for the attributes in this RDN. */
  private String[] attributeNames;



  /**
   * Creates a new RDN with the provided information.
   *
   * @param  attributeType   The attribute type for this RDN.  It must
   *                         not be {@code null}.
   * @param  attributeValue  The value for this RDN.  It must not be
   *                         {@code null}.
   */
  public RDN(AttributeType attributeType,
             ByteString attributeValue)
  {
    attributeTypes  = new AttributeType[] { attributeType };
    attributeNames  = new String[] { attributeType.getPrimaryName() };
    attributeValues = new ByteString[] { attributeValue };

    numValues     = 1;
    rdnString     = null;
    normalizedRDN = null;
  }



  /**
   * Creates a new RDN with the provided information.
   *
   * @param  attributeType   The attribute type for this RDN.  It must
   *                         not be {@code null}.
   * @param  attributeName   The user-provided name for this RDN.  It
   *                         must not be {@code null}.
   * @param  attributeValue  The value for this RDN.  It must not be
   *                         {@code null}.
   */
  public RDN(AttributeType attributeType, String attributeName,
             ByteString attributeValue)
  {
    attributeTypes  = new AttributeType[] { attributeType };
    attributeNames  = new String[] { attributeName };
    attributeValues = new ByteString[] { attributeValue };

    numValues     = 1;
    rdnString     = null;
    normalizedRDN = null;
  }



  /**
   * Creates a new RDN with the provided information.  The number of
   * type, name, and value elements must be nonzero and equal.
   *
   * @param  attributeTypes   The set of attribute types for this RDN.
   *                          It must not be empty or {@code null}.
   * @param  attributeNames   The set of user-provided names for this
   *                          RDN.  It must have the same number of
   *                          elements as the {@code attributeTypes}
   *                          argument.
   * @param  attributeValues  The set of values for this RDN.  It must
   *                          have the same number of elements as the
   *                          {@code attributeTypes} argument.
   */
  public RDN(List<AttributeType> attributeTypes,
             List<String> attributeNames,
             List<ByteString> attributeValues)
  {
    this.attributeTypes  = new AttributeType[attributeTypes.size()];
    this.attributeNames  = new String[attributeNames.size()];
    this.attributeValues = new ByteString[attributeValues.size()];

    attributeTypes.toArray(this.attributeTypes);
    attributeNames.toArray(this.attributeNames);
    attributeValues.toArray(this.attributeValues);

    numValues     = attributeTypes.size();
    rdnString     = null;
    normalizedRDN = null;
  }



  /**
   * Creates a new RDN with the provided information.  The number of
   * type, name, and value elements must be nonzero and equal.
   *
   * @param  attributeTypes   The set of attribute types for this RDN.
   *                          It must not be empty or {@code null}.
   * @param  attributeNames   The set of user-provided names for this
   *                          RDN.  It must have the same number of
   *                          elements as the {@code attributeTypes}
   *                          argument.
   * @param  attributeValues  The set of values for this RDN.  It must
   *                          have the same number of elements as the
   *                          {@code attributeTypes} argument.
   */
  public RDN(AttributeType[] attributeTypes, String[] attributeNames,
             ByteString[] attributeValues)
  {
    this.numValues       = attributeTypes.length;
    this.attributeTypes  = attributeTypes;
    this.attributeNames  = attributeNames;
    this.attributeValues = attributeValues;

    rdnString     = null;
    normalizedRDN = null;
  }



  /**
   * Creates a new RDN with the provided information.
   *
   * @param  attributeType   The attribute type for this RDN.  It must
   *                         not be {@code null}.
   * @param  attributeValue  The value for this RDN.  It must not be
   *                         {@code null}.
   *
   * @return  The RDN created with the provided information.
   */
  public static RDN create(AttributeType attributeType, ByteString attributeValue)
  {
    return new RDN(attributeType, attributeValue);
  }



  /**
   * Retrieves the number of attribute-value pairs contained in this
   * RDN.
   *
   * @return  The number of attribute-value pairs contained in this
   *          RDN.
   */
  public int getNumValues()
  {
    return numValues;
  }



  /**
   * Indicates whether this RDN includes the specified attribute type.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the RDN includes the specified
   *          attribute type, or <CODE>false</CODE> if not.
   */
  public boolean hasAttributeType(AttributeType attributeType)
  {
    for (AttributeType t : attributeTypes)
    {
      if (t.equals(attributeType))
      {
        return true;
      }
    }

    return false;
  }



  /**
   * Indicates whether this RDN includes the specified attribute type.
   *
   * @param  lowerName  The name or OID for the attribute type for
   *                    which to make the determination, formatted in
   *                    all lowercase characters.
   *
   * @return  <CODE>true</CODE> if the RDN includes the specified
   *          attribute type, or <CODE>false</CODE> if not.
   */
  public boolean hasAttributeType(String lowerName)
  {
    for (AttributeType t : attributeTypes)
    {
      if (t.hasNameOrOID(lowerName))
      {
        return true;
      }
    }

    for (String s : attributeNames)
    {
      if (s.equalsIgnoreCase(lowerName))
      {
        return true;
      }
    }

    return false;
  }



  /**
   * Retrieves the attribute type at the specified position in the set
   * of attribute types for this RDN.
   *
   * @param  pos  The position of the attribute type to retrieve.
   *
   * @return  The attribute type at the specified position in the set
   *          of attribute types for this RDN.
   */
  public AttributeType getAttributeType(int pos)
  {
    return attributeTypes[pos];
  }



  /**
   * Retrieves the name for the attribute type at the specified
   * position in the set of attribute types for this RDN.
   *
   * @param  pos  The position of the attribute type for which to
   *              retrieve the name.
   *
   * @return  The name for the attribute type at the specified
   *          position in the set of attribute types for this RDN.
   */
  public String getAttributeName(int pos)
  {
    return attributeNames[pos];
  }



  /**
   * Retrieves the attribute value that is associated with the
   * specified attribute type.
   *
   * @param  attributeType  The attribute type for which to retrieve
   *                        the corresponding value.
   *
   * @return  The value for the requested attribute type, or
   *          <CODE>null</CODE> if the specified attribute type is not
   *          present in the RDN.
   */
  public ByteString getAttributeValue(AttributeType attributeType)
  {
    for (int i=0; i < numValues; i++)
    {
      if (attributeTypes[i].equals(attributeType))
      {
        return attributeValues[i];
      }
    }

    return null;
  }



  /**
   * Retrieves the value for the attribute type at the specified
   * position in the set of attribute types for this RDN.
   *
   * @param  pos  The position of the attribute type for which to
   *              retrieve the value.
   *
   * @return  The value for the attribute type at the specified
   *          position in the set of attribute types for this RDN.
   */
  public ByteString getAttributeValue(int pos)
  {
    return attributeValues[pos];
  }



  /**
   * Indicates whether this RDN is multivalued.
   *
   * @return  <CODE>true</CODE> if this RDN is multivalued, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isMultiValued()
  {
    return numValues > 1;
  }



  /**
   * Indicates whether this RDN contains the specified type-value
   * pair.
   *
   * @param  type   The attribute type for which to make the
   *                determination.
   * @param  value  The value for which to make the determination.
   *
   * @return  <CODE>true</CODE> if this RDN contains the specified
   *          attribute value, or <CODE>false</CODE> if not.
   */
  public boolean hasValue(AttributeType type, ByteString value)
  {
    for (int i=0; i < numValues; i++)
    {
      if (attributeTypes[i].equals(type) &&
          attributeValues[i].equals(value))
      {
        return true;
      }
    }

    return false;
  }



  /**
   * Adds the provided type-value pair from this RDN.  Note that this
   * is intended only for internal use when constructing DN values.
   *
   * @param  type   The attribute type of the pair to add.
   * @param  name   The user-provided name of the pair to add.
   * @param  value  The attribute value of the pair to add.
   *
   * @return  <CODE>true</CODE> if the type-value pair was added to
   *          this RDN, or <CODE>false</CODE> if it was not (e.g., it
   *          was already present).
   */
  boolean addValue(AttributeType type, String name, ByteString value)
  {
    for (int i=0; i < numValues; i++)
    {
      if (attributeTypes[i].equals(type) &&
          attributeValues[i].equals(value))
      {
        return false;
      }
    }

    numValues++;

    AttributeType[] newTypes = new AttributeType[numValues];
    System.arraycopy(attributeTypes, 0, newTypes, 0, attributeTypes.length);
    newTypes[attributeTypes.length] = type;
    attributeTypes = newTypes;

    String[] newNames = new String[numValues];
    System.arraycopy(attributeNames, 0, newNames, 0, attributeNames.length);
    newNames[attributeNames.length] = name;
    attributeNames = newNames;

    ByteString[] newValues = new ByteString[numValues];
    System.arraycopy(attributeValues, 0, newValues, 0, attributeValues.length);
    newValues[attributeValues.length] = value;
    attributeValues = newValues;

    rdnString     = null;
    normalizedRDN = null;

    return true;
  }



  /**
   * Retrieves a version of the provided value in a form that is
   * properly escaped for use in a DN or RDN.
   *
   * @param  valueBS  The value to be represented in a DN-safe form.
   *
   * @return  A version of the provided value in a form that is
   *          properly escaped for use in a DN or RDN.
   */
  private static String getDNValue(ByteString valueBS) {
    final String value = valueBS.toString();
    if (value == null || value.length() == 0) {
      return "";
    }

    // Only copy the string value if required.
    boolean needsEscaping = false;
    int length = value.length();

    needsEscaping: {
      char c = value.charAt(0);
      if ((c == ' ') || (c == '#')) {
        needsEscaping = true;
        break needsEscaping;
      }

      if (value.charAt(length - 1) == ' ') {
        needsEscaping = true;
        break needsEscaping;
      }

      for (int i = 0; i < length; i++) {
        c = value.charAt(i);
        if (c < ' ') {
          needsEscaping = true;
          break needsEscaping;
        } else {
          switch (c) {
          case ',':
          case '+':
          case '"':
          case '\\':
          case '<':
          case '>':
          case ';':
            needsEscaping = true;
            break needsEscaping;
          }
        }
      }
    }

    if (!needsEscaping) {
      return value;
    }

    // We need to copy and escape the string (allow for at least one
    // escaped character).
    StringBuilder buffer = new StringBuilder(length + 3);

    // If the lead character is a space or a # it must be escaped.
    int start = 0;
    char c = value.charAt(0);
    if ((c == ' ') || (c == '#')) {
      buffer.append('\\');
      buffer.append(c);
      start = 1;
    }

    // Escape remaining characters as necessary.
    for (int i = start; i < length; i++) {
      c = value.charAt(i);
      if (c < ' ') {
        for (byte b : getBytes(String.valueOf(c))) {
          buffer.append('\\');
          buffer.append(byteToLowerHex(b));
        }
      } else {
        switch (value.charAt(i)) {
        case ',':
        case '+':
        case '"':
        case '\\':
        case '<':
        case '>':
        case ';':
          buffer.append('\\');
          buffer.append(c);
          break;
        default:
          buffer.append(c);
          break;
        }
      }
    }

    // If the last character is a space it must be escaped.
    if (value.charAt(length - 1) == ' ') {
      length = buffer.length();
      buffer.insert(length - 1, '\\');
    }

    return buffer.toString();
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
  public static RDN decode(String rdnString) throws DirectoryException
  {
    // A null or empty RDN is not acceptable.
    if (rdnString == null)
    {
      LocalizableMessage message = ERR_RDN_DECODE_NULL.get();
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message);
    }

    int length = rdnString.length();
    if (length == 0)
    {
      LocalizableMessage message = ERR_RDN_DECODE_NULL.get();
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message);
    }


    // Iterate through the RDN string.  The first thing to do is to
    // get rid of any leading spaces.
    int pos = 0;
    char c = rdnString.charAt(pos);
    while (c == ' ')
    {
      pos++;
      if (pos == length)
      {
        // This means that the RDN was completely comprised of spaces,
        // which is not valid.
        LocalizableMessage message = ERR_RDN_DECODE_NULL.get();
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message);
      }
      else
      {
        c = rdnString.charAt(pos);
      }
    }


    // We know that it's not an empty RDN, so we can do the real processing.
    // First, parse the attribute name. We can borrow the DN code for this.
    boolean allowExceptions = DirectoryServer.allowAttributeNameExceptions();
    StringBuilder attributeName = new StringBuilder();
    pos = DN.parseAttributeName(rdnString, pos, attributeName, allowExceptions);


    // Make sure that we're not at the end of the RDN string because
    // that would be invalid.
    if (pos >= length)
    {
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
          ERR_RDN_END_WITH_ATTR_NAME.get(rdnString, attributeName));
    }


    // Skip over any spaces between the attribute name and its value.
    c = rdnString.charAt(pos);
    while (c == ' ')
    {
      pos++;
      if (pos >= length)
      {
        // This means that we hit the end of the string before finding a '='.
        // This is illegal because there is no attribute-value separator.
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            ERR_RDN_END_WITH_ATTR_NAME.get(rdnString, attributeName));
      }
      else
      {
        c = rdnString.charAt(pos);
      }
    }


    // The next character must be an equal sign.  If it is not, then
    // that's an error.
    if (c == '=')
    {
      pos++;
    }
    else
    {
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
          ERR_RDN_NO_EQUAL.get(rdnString, attributeName, c));
    }


    // Skip over any spaces between the equal sign and the value.
    while ((pos < length) && ((c = rdnString.charAt(pos)) == ' '))
    {
      pos++;
    }


    // If we are at the end of the RDN string, then that must mean
    // that the attribute value was empty.
    if (pos >= length)
    {
      String        name      = attributeName.toString();
      String        lowerName = toLowerCase(name);
      LocalizableMessage message = ERR_RDN_MISSING_ATTRIBUTE_VALUE.get(rdnString,
             lowerName);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message);
    }


    // Parse the value for this RDN component.  This can be done using
    // the DN code.
    ByteStringBuilder parsedValue = new ByteStringBuilder(0);
    pos = DN.parseAttributeValue(rdnString, pos, parsedValue);


    // Create the new RDN with the provided information.  However,
    // don't return it yet because this could be a multi-valued RDN.
    String name            = attributeName.toString();
    String lowerName       = toLowerCase(name);
    AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
    if (attrType == null)
    {
      // This must be an attribute type that we don't know about.
      // In that case, we'll create a new attribute using the default
      // syntax.  If this is a problem, it will be caught later either
      // by not finding the target entry or by not allowing the entry
      // to be added.
      attrType = DirectoryServer.getDefaultAttributeType(name);
    }

    RDN rdn = new RDN(attrType, name, parsedValue.toByteString());


    // Skip over any spaces that might be after the attribute value.
    while ((pos < length) && ((c = rdnString.charAt(pos)) == ' '))
    {
      pos++;
    }


    // Most likely, this is the end of the RDN.  If so, then return it.
    if (pos >= length)
    {
      return rdn;
    }


    // If the next character is a comma or semicolon, then that is not
    // allowed.  It would be legal for a DN but not an RDN.
    if ((c == ',') || (c == ';'))
    {
      LocalizableMessage message = ERR_RDN_UNEXPECTED_COMMA.get(rdnString, pos);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message);
    }


    // If the next character is anything but a plus sign, then it is illegal.
    if (c != '+')
    {
      LocalizableMessage message = ERR_RDN_ILLEGAL_CHARACTER.get(rdnString, c, pos);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message);
    }


    // If we have gotten here, then it is a multi-valued RDN.  Parse
    // the remaining attribute/value pairs and add them to the RDN
    // that we've already created.
    while (true)
    {
      // Skip over the plus sign and any spaces that may follow it
      // before the next attribute name.
      pos++;
      while ((pos < length) && ((c = rdnString.charAt(pos)) == ' '))
      {
        pos++;
      }


      // Parse the attribute name.
      attributeName = new StringBuilder();
      pos = DN.parseAttributeName(rdnString, pos, attributeName,
                                  allowExceptions);


      // Make sure we're not at the end of the RDN.
      if (pos >= length)
      {
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            ERR_RDN_END_WITH_ATTR_NAME.get(rdnString, attributeName));
      }


      // Skip over any spaces between the attribute name and the equal sign.
      c = rdnString.charAt(pos);
      while (c == ' ')
      {
        pos++;
        if (pos >= length)
        {
          // This means that we hit the end of the string before finding a '='.
          // This is illegal because there is no attribute-value separator.
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
              ERR_RDN_END_WITH_ATTR_NAME.get(rdnString, attributeName));
        }
        else
        {
          c = rdnString.charAt(pos);
        }
      }


      // The next character must be an equal sign.
      if (c == '=')
      {
        pos++;
      }
      else
      {
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
            ERR_RDN_NO_EQUAL.get(rdnString, attributeName, c));
      }


      // Skip over any spaces after the equal sign.
      while ((pos < length) && ((c = rdnString.charAt(pos)) == ' '))
      {
        pos++;
      }


      // If we are at the end of the RDN string, then that must mean
      // that the attribute value was empty.  This will probably never
      // happen in a real-world environment, but technically isn't
      // illegal.  If it does happen, then go ahead and return the RDN.
      if (pos >= length)
      {
        name      = attributeName.toString();
        lowerName = toLowerCase(name);
        attrType  = DirectoryServer.getAttributeType(lowerName);

        if (attrType == null)
        {
          // This must be an attribute type that we don't know about.
          // In that case, we'll create a new attribute using the
          // default syntax.  If this is a problem, it will be caught
          // later either by not finding the target entry or by not
          // allowing the entry to be added.
          attrType = DirectoryServer.getDefaultAttributeType(name);
        }

        rdn.addValue(attrType, name, ByteString.empty());
        return rdn;
      }


      // Parse the value for this RDN component.
      parsedValue.clear();
      pos = DN.parseAttributeValue(rdnString, pos, parsedValue);


      // Update the RDN to include the new attribute/value.
      name            = attributeName.toString();
      lowerName       = toLowerCase(name);
      attrType = DirectoryServer.getAttributeType(lowerName);
      if (attrType == null)
      {
        // This must be an attribute type that we don't know about.
        // In that case, we'll create a new attribute using the
        // default syntax.  If this is a problem, it will be caught
        // later either by not finding the target entry or by not
        // allowing the entry to be added.
        attrType = DirectoryServer.getDefaultAttributeType(name);
      }

      rdn.addValue(attrType, name, parsedValue.toByteString());


      // Skip over any spaces that might be after the attribute value.
      while ((pos < length) && ((c = rdnString.charAt(pos)) == ' '))
      {
        pos++;
      }


      // If we're at the end of the string, then return the RDN.
      if (pos >= length)
      {
        return rdn;
      }


      // If the next character is a comma or semicolon, then that is
      // not allowed.  It would be legal for a DN but not an RDN.
      if ((c == ',') || (c == ';'))
      {
        LocalizableMessage message = ERR_RDN_UNEXPECTED_COMMA.get(rdnString, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message);
      }


      // If the next character is anything but a plus sign, then it is illegal.
      if (c != '+')
      {
        LocalizableMessage message = ERR_RDN_ILLEGAL_CHARACTER.get(rdnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, message);
      }
    }
  }



  /**
   * Creates a duplicate of this RDN that can be modified without
   * impacting this RDN.
   *
   * @return  A duplicate of this RDN that can be modified without
   *          impacting this RDN.
   */
  public RDN duplicate()
  {
    AttributeType[] newTypes = new AttributeType[numValues];
    System.arraycopy(attributeTypes, 0, newTypes, 0, numValues);

    String[] newNames = new String[numValues];
    System.arraycopy(attributeNames, 0, newNames, 0, numValues);

    ByteString[] newValues = new ByteString[numValues];
    System.arraycopy(attributeValues, 0, newValues, 0, numValues);

    return new RDN(newTypes, newNames, newValues);
  }



  /**
   * Indicates whether the provided object is equal to this RDN.  It
   * will only be considered equal if it is an RDN object that
   * contains the same number of elements in the same order with the
   * same types and normalized values.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if it is determined that the provided
   *          object is equal to this RDN, or <CODE>false</CODE> if
   *          not.
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (o instanceof RDN)
    {
      return compareTo((RDN) o) == 0;
    }
    return false;
  }

  /**
   * Retrieves the hash code for this RDN.  It will be calculated as
   * the sum of the hash codes of the types and values.
   *
   * @return  The hash code for this RDN.
   */
  @Override
  public int hashCode()
  {
    // Avoid an algorithm that requires the AVAs to be sorted.
    int hash = 0;

    for (int i = 0; i < attributeNames.length; i++)
    {
      hash += attributeTypes[i].hashCode() * 31 + getEqualityNormalizedValue(i).hashCode();
    }
    return hash;
  }

  /** Returns normalized value for attribute at provided position. */
  private ByteString getEqualityNormalizedValue(int position)
  {
    final MatchingRule matchingRule = attributeTypes[position].getEqualityMatchingRule();
    ByteString attributeValue = attributeValues[position];
    if (matchingRule != null)
    {
      try
      {
        attributeValue = matchingRule.normalizeAttributeValue(attributeValue);
      }
      catch (final DecodeException de)
      {
        // Unable to normalize, use default
        attributeValue = attributeValues[position];
      }
    }
    return attributeValue;
  }



  /**
   * Retrieves a string representation of this RDN.
   *
   * @return  A string representation of this RDN.
   */
  @Override
  public String toString()
  {
    if (rdnString == null)
    {
      StringBuilder buffer = new StringBuilder();

      buffer.append(attributeNames[0]);
      buffer.append("=");
      buffer.append(getDNValue(attributeValues[0]));

      for (int i=1; i < numValues; i++)
      {
        buffer.append("+");
        buffer.append(attributeNames[i]);
        buffer.append("=");
        buffer.append(getDNValue(attributeValues[i]));
      }

      rdnString = buffer.toString();
    }
    return rdnString;
  }



  /**
   * Appends a string representation of this RDN to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string representation
   *                 should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append(toString());
  }



  /**
   * Retrieves a normalized string representation of this RDN.
   *
   * @return  A normalized string representation of this RDN.
   */
  public String toNormalizedString()
  {
    if (normalizedRDN == null)
    {
      toNormalizedString(new StringBuilder());
    }
    return normalizedRDN;
  }



  /**
   * Appends a normalized string representation of this RDN to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which to append the information.
   */
  public void toNormalizedString(StringBuilder buffer)
  {
    if (normalizedRDN != null)
    {
      buffer.append(normalizedRDN);
      return;
    }

    boolean bufferEmpty = (buffer.length() == 0);

    if (attributeNames.length == 1)
    {
      getNormalizedAVAString(0, buffer);
    }
    else
    {
      // normalization sorts RDNs alphabetically
      SortedSet<String> rdnElementStrings = new TreeSet<String>();

      for (int i=0; i < attributeNames.length; i++)
      {
        StringBuilder b2 = new StringBuilder();
        getNormalizedAVAString(i, b2);
        rdnElementStrings.add(b2.toString());
      }

      Iterator<String> iterator = rdnElementStrings.iterator();
      buffer.append(iterator.next());
      while (iterator.hasNext())
      {
        buffer.append('+');
        buffer.append(iterator.next());
      }
    }

    if (bufferEmpty)
    {
      normalizedRDN = buffer.toString();
    }
  }



  /**
   * Appends a normalized string representation of this RDN to the
   * provided buffer.
   *
   * @param  pos  The position of the attribute type and value to
   *              retrieve.
   * @param  buffer  The buffer to which to append the information.
   */
  public void getNormalizedAVAString(int pos, StringBuilder buffer)
  {
      AttributeType type = attributeTypes[pos];
      buffer.append(type.getNormalizedPrimaryNameOrOID());
      buffer.append('=');

      ByteString value = attributeValues[pos];
      try
      {
        MatchingRule rule = type.getEqualityMatchingRule();
        ByteString normValue = rule.normalizeAttributeValue(value);
        buffer.append(getDNValue(normValue));
      }
      catch (Exception e)
      {
        logger.traceException(e);
        buffer.append(getDNValue(value));
      }
  }

  /**
   * Compares this RDN with the provided RDN based on an alphabetic
   * comparison of the attribute names and values.
   *
   * @param  rdn  The RDN against which to compare this RDN.
   *
   * @return  A negative integer if this RDN should come before the
   *          provided RDN, a positive integer if this RDN should come
   *          after the provided RDN, or zero if there is no
   *          difference with regard to ordering.
   */
  @Override
  public int compareTo(RDN rdn)
  {
    if (attributeTypes.length == 1 && rdn.attributeTypes.length == 1)
    {
      // fast path
      AttributeType type = attributeTypes[0];
      if (type.equals(rdn.attributeTypes[0]))
      {
        return compare(attributeValues[0], rdn.attributeValues[0], type);
      }
      else
      {
        String name1 = type.getNormalizedPrimaryNameOrOID();
        String name2 = rdn.attributeTypes[0].getNormalizedPrimaryNameOrOID();
        return name1.compareTo(name2);
      }
    }

    TreeMap<String,AttributeType> typeMap1 =
         new TreeMap<String,AttributeType>();
    TreeMap<String, ByteString> valueMap1 = new TreeMap<String, ByteString>();
    for (int i=0; i < attributeTypes.length; i++)
    {
      String lowerName = attributeTypes[i].getNormalizedPrimaryNameOrOID();
      typeMap1.put(lowerName, attributeTypes[i]);
      valueMap1.put(lowerName, attributeValues[i]);
    }

    TreeMap<String,AttributeType> typeMap2 =
         new TreeMap<String,AttributeType>();
    TreeMap<String, ByteString> valueMap2 = new TreeMap<String, ByteString>();
    for (int i=0; i < rdn.attributeTypes.length; i++)
    {
      String lowerName = rdn.attributeTypes[i].getNormalizedPrimaryNameOrOID();
      typeMap2.put(lowerName, rdn.attributeTypes[i]);
      valueMap2.put(lowerName, rdn.attributeValues[i]);
    }

    Iterator<String> iterator1 = valueMap1.keySet().iterator();
    Iterator<String> iterator2 = valueMap2.keySet().iterator();
    String           name1     = iterator1.next();
    String           name2     = iterator2.next();
    AttributeType    type1     = typeMap1.get(name1);
    AttributeType    type2     = typeMap2.get(name2);
    ByteString       value1    = valueMap1.get(name1);
    ByteString       value2    = valueMap2.get(name2);

    while (true)
    {
      if (!type1.equals(type2))
      {
        // there is a difference => return result
        return name1.compareTo(name2);
      }

      final int valueComparison = compare(value1, value2, type1);
      if (valueComparison != 0)
      {
        // we found a difference => return result
        return valueComparison;
      }

      if (!iterator1.hasNext())
      {
        if (iterator2.hasNext())
        {
          return -1;
        }
        return 0;
      }
      if (!iterator2.hasNext())
      {
        return 1;
      }

      name1  = iterator1.next();
      name2  = iterator2.next();
      type1  = typeMap1.get(name1);
      type2  = typeMap2.get(name2);
      value1 = valueMap1.get(name1);
      value2 = valueMap2.get(name2);
    }
  }

  /**
   * Compares two attribute values by using the provided MatchingRule if
   * it is not null, or relying on alphabetical ordering otherwise.
   *
   * @param value1
   *          the first attribute value to compare
   * @param value2
   *          the second attribute value to compare
   * @param type
   *          the type whose MatchingRule is to be used for comparison
   * @return A negative integer if this value1 should come before the value2, a
   *         positive integer if value1 should come after value2, or zero if
   *         there is no difference with regard to ordering.
   */
  private int compare(ByteString value1, ByteString value2, AttributeType type)
  {
    final MatchingRule orderingRule = type.getOrderingMatchingRule();
    final MatchingRule rule = orderingRule != null ? orderingRule : type.getEqualityMatchingRule();

    ByteString val1 = value1;
    ByteString val2 = value2;
    if (rule != null)
    {
      try
      {
        val1 = rule.normalizeAttributeValue(val1);
        val2 = rule.normalizeAttributeValue(val2);
        return rule.comparator().compare(val1, val2);
      }
      catch (DecodeException e)
      {
        logger.traceException(e);
      }
    }
    return val1.toString().compareTo(val2.toString());
  }
}
