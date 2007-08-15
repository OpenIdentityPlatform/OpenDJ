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
package org.opends.server.types;
import org.opends.messages.Message;



import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure for storing and interacting
 * with the relative distinguished names associated with entries in
 * the Directory Server.
 */
public class RDN
       implements Comparable<RDN>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The set of attribute types for the elements in this RDN.
  private AttributeType[] attributeTypes;

  // The set of values for the elements in this RDN.
  private AttributeValue[] attributeValues;

  // The number of values for this RDN.
  private int numValues;

  // The string representation of the normalized form of this RDN.
  private String normalizedRDN;

  // The string representation of this RDN.
  private String rdnString;

  // The set of user-provided names for the attributes in this RDN.
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
             AttributeValue attributeValue)
  {
    attributeTypes  = new AttributeType[] { attributeType };
    attributeNames  = new String[] { attributeType.getPrimaryName() };
    attributeValues = new AttributeValue[] { attributeValue };

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
             AttributeValue attributeValue)
  {
    attributeTypes  = new AttributeType[] { attributeType };
    attributeNames  = new String[] { attributeName };
    attributeValues = new AttributeValue[] { attributeValue };

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
             List<AttributeValue> attributeValues)
  {
    this.attributeTypes  = new AttributeType[attributeTypes.size()];
    this.attributeNames  = new String[attributeNames.size()];
    this.attributeValues = new AttributeValue[attributeValues.size()];

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
             AttributeValue[] attributeValues)
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
  public static RDN create(AttributeType attributeType,
                           AttributeValue attributeValue)
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
  public AttributeValue getAttributeValue(AttributeType attributeType)
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
  public AttributeValue getAttributeValue(int pos)
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
    return (numValues > 1);
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
  public boolean hasValue(AttributeType type, AttributeValue value)
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
  boolean addValue(AttributeType type, String name,
                   AttributeValue value)
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
    System.arraycopy(attributeTypes, 0, newTypes, 0,
                     attributeTypes.length);
    newTypes[attributeTypes.length] = type;
    attributeTypes = newTypes;

    String[] newNames = new String[numValues];
    System.arraycopy(attributeNames, 0, newNames, 0,
                     attributeNames.length);
    newNames[attributeNames.length] = name;
    attributeNames = newNames;

    AttributeValue[] newValues = new AttributeValue[numValues];
    System.arraycopy(attributeValues, 0, newValues, 0,
                     attributeValues.length);
    newValues[attributeValues.length] = value;
    attributeValues = newValues;

    rdnString     = null;
    normalizedRDN = null;

    return true;
  }



  /**
   * Decodes the provided string as an RDN.
   *
   * @param  rdnString  The string to decode as an RDN.
   *
   * @return  The decoded RDN.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              decode the provided string as a RDN.
   */
  public static RDN decode(String rdnString)
         throws DirectoryException
  {
    // A null or empty RDN is not acceptable.
    if (rdnString == null)
    {
      Message message = ERR_RDN_DECODE_NULL.get();
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message);
    }

    int length = rdnString.length();
    if (length == 0)
    {
      Message message = ERR_RDN_DECODE_NULL.get();
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message);
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
        Message message = ERR_RDN_DECODE_NULL.get();
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }
      else
      {
        c = rdnString.charAt(pos);
      }
    }


    // We know that it's not an empty RDN, so we can do the real
    // processing.  First, parse the attribute name.  We can borrow
    // the DN code for this.
    boolean allowExceptions =
         DirectoryServer.allowAttributeNameExceptions();
    StringBuilder attributeName = new StringBuilder();
    pos = DN.parseAttributeName(rdnString, pos, attributeName,
                                allowExceptions);


    // Make sure that we're not at the end of the RDN string because
    // that would be invalid.
    if (pos >= length)
    {
      Message message = ERR_RDN_END_WITH_ATTR_NAME.get(
          rdnString, attributeName.toString());
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message);
    }


    // Skip over any spaces between the attribute name and its value.
    c = rdnString.charAt(pos);
    while (c == ' ')
    {
      pos++;
      if (pos >= length)
      {
        // This means that we hit the end of the string before
        // finding a '='.  This is illegal because there is no
        // attribute-value separator.
        Message message = ERR_RDN_END_WITH_ATTR_NAME.get(
            rdnString, attributeName.toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
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
      Message message = ERR_RDN_NO_EQUAL.get(
          rdnString, attributeName.toString(), c);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message);
    }


    // Skip over any spaces between the equal sign and the value.
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
      String        name      = attributeName.toString();
      String        lowerName = toLowerCase(name);
      AttributeType attrType  =
           DirectoryServer.getAttributeType(lowerName);

      if (attrType == null)
      {
        // This must be an attribute type that we don't know about.
        // In that case, we'll create a new attribute using the
        // default syntax.  If this is a problem, it will be caught
        // later either by not finding the target entry or by not
        // allowing the entry to be added.
        attrType = DirectoryServer.getDefaultAttributeType(name);
      }

      AttributeValue value = new AttributeValue(new ASN1OctetString(),
                                     new ASN1OctetString());
      return new RDN(attrType, name, value);
    }


    // Parse the value for this RDN component.  This can be done using
    // the DN code.
    ByteString parsedValue = new ASN1OctetString();
    pos = DN.parseAttributeValue(rdnString, pos, parsedValue);


    // Create the new RDN with the provided information.  However,
    // don't return it yet because this could be a multi-valued RDN.
    String name            = attributeName.toString();
    String lowerName       = toLowerCase(name);
    AttributeType attrType =
         DirectoryServer.getAttributeType(lowerName);
    if (attrType == null)
    {
      // This must be an attribute type that we don't know about.
      // In that case, we'll create a new attribute using the default
      // syntax.  If this is a problem, it will be caught later either
      // by not finding the target entry or by not allowing the entry
      // to be added.
      attrType = DirectoryServer.getDefaultAttributeType(name);
    }

    AttributeValue value = new AttributeValue(attrType, parsedValue);
    RDN rdn = new RDN(attrType, name, value);


    // Skip over any spaces that might be after the attribute value.
    while ((pos < length) && ((c = rdnString.charAt(pos)) == ' '))
    {
      pos++;
    }


    // Most likely, this is the end of the RDN.  If so, then return
    // it.
    if (pos >= length)
    {
      return rdn;
    }


    // If the next character is a comma or semicolon, then that is not
    // allowed.  It would be legal for a DN but not an RDN.
    if ((c == ',') || (c == ';'))
    {
      Message message = ERR_RDN_UNEXPECTED_COMMA.get(rdnString, pos);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message);
    }


    // If the next character is anything but a plus sign, then it is
    // illegal.
    if (c != '+')
    {
      Message message =
          ERR_RDN_ILLEGAL_CHARACTER.get(rdnString, c, pos);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message);
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
        Message message = ERR_RDN_END_WITH_ATTR_NAME.get(
            rdnString, attributeName.toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }


      // Skip over any spaces between the attribute name and the equal
      // sign.
      c = rdnString.charAt(pos);
      while (c == ' ')
      {
        pos++;
        if (pos >= length)
        {
          // This means that we hit the end of the string before
          // finding a '='.  This is illegal because there is no
          // attribute-value separator.
          Message message = ERR_RDN_END_WITH_ATTR_NAME.get(
              rdnString, attributeName.toString());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message);
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
        Message message = ERR_RDN_NO_EQUAL.get(
            rdnString, attributeName.toString(), c);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }


      // Skip over any spaces after the equal sign.
      while ((pos < length) && ((c = rdnString.charAt(pos)) == ' '))
      {
        pos++;
      }


      // If we are at the end of the RDN string, then that must mean
      // that the attribute value was empty.  This will probably never
      // happen in a real-world environment, but technically isn't
      // illegal.  If it does happen, then go ahead and return the
      // RDN.
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

        value = new AttributeValue(new ASN1OctetString(),
                                   new ASN1OctetString());
        rdn.addValue(attrType, name, value);
        return rdn;
      }


      // Parse the value for this RDN component.
      parsedValue = new ASN1OctetString();
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

      value = new AttributeValue(attrType, parsedValue);
      rdn.addValue(attrType, name, value);


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
        Message message =
            ERR_RDN_UNEXPECTED_COMMA.get(rdnString, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
      }


      // If the next character is anything but a plus sign, then it is
      // illegal.
      if (c != '+')
      {
        Message message =
            ERR_RDN_ILLEGAL_CHARACTER.get(rdnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message);
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

    AttributeValue[] newValues = new AttributeValue[numValues];
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
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof RDN)))
    {
      return false;
    }

    RDN rdn = (RDN) o;
    return toNormalizedString().equals(rdn.toNormalizedString());
  }



  /**
   * Retrieves the hash code for this RDN.  It will be calculated as
   * the sum of the hash codes of the types and values.
   *
   * @return  The hash code for this RDN.
   */
  public int hashCode()
  {
    return toNormalizedString().hashCode();
  }



  /**
   * Retrieves a string representation of this RDN.
   *
   * @return  A string representation of this RDN.
   */
  public String toString()
  {
    if (rdnString == null)
    {
      StringBuilder buffer = new StringBuilder();

      buffer.append(attributeNames[0]);
      buffer.append("=");
      buffer.append(attributeValues[0].getDNStringValue());

      for (int i=1; i < numValues; i++)
      {
        buffer.append("+");
        buffer.append(attributeNames[i]);
        buffer.append("=");
        buffer.append(attributeValues[i].getDNStringValue());
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
      StringBuilder buffer = new StringBuilder();
      toNormalizedString(buffer);
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
      toLowerCase(attributeTypes[0].getNameOrOID(), buffer);
      buffer.append('=');

      try
      {
        buffer.append(
             attributeValues[0].getNormalizedDNStringValue());
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        buffer.append(attributeValues[0].getStringValue());
      }
    }
    else
    {
      TreeSet<String> rdnElementStrings = new TreeSet<String>();

      for (int i=0; i < attributeNames.length; i++)
      {
        StringBuilder b2 = new StringBuilder();
        toLowerCase(attributeTypes[i].getNameOrOID(), b2);
        b2.append('=');

        try
        {
          b2.append(attributeValues[i].getNormalizedStringValue());
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          b2.append(attributeValues[i].getStringValue());
        }

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
  public int compareTo(RDN rdn)
  {
    if ((attributeTypes.length == 1) &&
        (rdn.attributeTypes.length == 1))
    {
      if (attributeTypes[0].equals(rdn.attributeTypes[0]))
      {
        int valueComparison;
        OrderingMatchingRule omr =
             attributeTypes[0].getOrderingMatchingRule();
        if (omr == null)
        {
          try
          {
            return attributeValues[0].getNormalizedStringValue().
                        compareTo(rdn.attributeValues[0].
                             getNormalizedStringValue());
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            return attributeValues[0].getStringValue().
                compareTo(rdn.attributeValues[0].
                    getStringValue());
          }
        }
        else
        {
          try
          {
            return omr.compareValues(
                        attributeValues[0].getNormalizedValue(),
                        rdn.attributeValues[0].getNormalizedValue());
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            return omr.compareValues(
                attributeValues[0].getValue(),
                rdn.attributeValues[0].getValue());
          }
        }
      }
      else
      {
        String name1 = toLowerCase(attributeTypes[0].getNameOrOID());
        String name2 =
             toLowerCase(rdn.attributeTypes[0].getNameOrOID());
        return name1.compareTo(name2);
      }
    }

    if (equals(rdn))
    {
      return 0;
    }

    TreeMap<String,AttributeType> typeMap1 =
         new TreeMap<String,AttributeType>();
    TreeMap<String,AttributeValue> valueMap1 =
         new TreeMap<String,AttributeValue>();
    for (int i=0; i < attributeTypes.length; i++)
    {
      String lowerName =
           toLowerCase(attributeTypes[i].getNameOrOID());
      typeMap1.put(lowerName, attributeTypes[i]);
      valueMap1.put(lowerName, attributeValues[i]);
    }

    TreeMap<String,AttributeType> typeMap2 =
         new TreeMap<String,AttributeType>();
    TreeMap<String,AttributeValue> valueMap2 =
         new TreeMap<String,AttributeValue>();
    for (int i=0; i < rdn.attributeTypes.length; i++)
    {
      String lowerName =
           toLowerCase(rdn.attributeTypes[i].getNameOrOID());
      typeMap2.put(lowerName, rdn.attributeTypes[i]);
      valueMap2.put(lowerName, rdn.attributeValues[i]);
    }

    Iterator<String> iterator1 = valueMap1.keySet().iterator();
    Iterator<String> iterator2 = valueMap2.keySet().iterator();
    String           name1     = iterator1.next();
    String           name2     = iterator2.next();
    AttributeType    type1     = typeMap1.get(name1);
    AttributeType    type2     = typeMap2.get(name2);
    AttributeValue   value1    = valueMap1.get(name1);
    AttributeValue   value2    = valueMap2.get(name2);

    while (true)
    {
      if (type1.equals(type2))
      {
        int valueComparison;
        OrderingMatchingRule omr = type1.getOrderingMatchingRule();
        if (omr == null)
        {
          try
          {
            valueComparison =
                 value1.getNormalizedStringValue().compareTo(
                      value2.getNormalizedStringValue());
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            valueComparison =
                value1.getStringValue().compareTo(
                    value2.getStringValue());
          }
        }
        else
        {
          try
          {
            valueComparison =
                 omr.compareValues(value1.getNormalizedValue(),
                                   value2.getNormalizedValue());
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            valueComparison =
                omr.compareValues(value1.getValue(),
                                  value2.getValue());
          }
        }

        if (valueComparison == 0)
        {
          if (! iterator1.hasNext())
          {
            if (iterator2.hasNext())
            {
              return -1;
            }
            else
            {
              return 0;
            }
          }

          if (! iterator2.hasNext())
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
        else
        {
          return valueComparison;
        }
      }
      else
      {
        return name1.compareTo(name2);
      }
    }
  }
}

