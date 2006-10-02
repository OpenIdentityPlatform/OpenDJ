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



import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
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
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.RDN";



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
   * @param  attributeType   The attribute type for this RDN.
   * @param  attributeValue  The value for this RDN.
   */
  public RDN(AttributeType attributeType,
             AttributeValue attributeValue)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(attributeType),
                            String.valueOf(attributeValue));

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
   * @param  attributeType   The attribute type for this RDN.
   * @param  attributeName   The user-provided name for this RDN.
   * @param  attributeValue  The value for this RDN.
   */
  public RDN(AttributeType attributeType, String attributeName,
             AttributeValue attributeValue)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(attributeType),
                            String.valueOf(attributeName),
                            String.valueOf(attributeValue));

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
   * @param  attributeNames   The set of user-provided names for this
   *                          RDN.
   * @param  attributeValues  The set of values for this RDN.
   */
  public RDN(List<AttributeType> attributeTypes,
             List<String> attributeNames,
             List<AttributeValue> attributeValues)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(attributeTypes),
                            String.valueOf(attributeNames),
                            String.valueOf(attributeValues));

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
   * @param  attributeNames   The set of user-provided names for this
   *                          RDN.
   * @param  attributeValues  The set of values for this RDN.
   */
  public RDN(AttributeType[] attributeTypes, String[] attributeNames,
             AttributeValue[] attributeValues)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(attributeTypes),
                            String.valueOf(attributeNames),
                            String.valueOf(attributeValues));

    this.numValues       = attributeTypes.length;
    this.attributeTypes  = attributeTypes;
    this.attributeNames  = attributeNames;
    this.attributeValues = attributeValues;

    rdnString     = null;
    normalizedRDN = null;
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
    assert debugEnter(CLASS_NAME, "getNumValues");

    return numValues;
  }



  /**
   * Retrieves the set of attribute types for this RDN.  The returned
   * array must not be modified by the caller.
   *
   * @return  The set of attribute types for this RDN.
   */
  public AttributeType[] getAttributeTypes()
  {
    assert debugEnter(CLASS_NAME, "getAttributeTypes");

    return attributeTypes;
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
    assert debugEnter(CLASS_NAME, "hasAttributeType",
                      String.valueOf(attributeType));

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
    assert debugEnter(CLASS_NAME, "hasAttributeType",
                      String.valueOf(lowerName));

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
   * Retrieves the set of user-provided names for this RDN.  The
   * returned array must not be modified by the caller.
   *
   * @return  The set of user-provided names for this RDN.
   */
  public String[] getAttributeNames()
  {
    assert debugEnter(CLASS_NAME, "getAttributeNames");

    return attributeNames;
  }



  /**
   * Retrieves the set of attribute values for this RDN.  The returned
   * list must not be modified by the caller.
   *
   * @return  The set of attribute values for this RDN.
   */
  public AttributeValue[] getAttributeValues()
  {
    assert debugEnter(CLASS_NAME, "getAttributeValues");

    return attributeValues;
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
    assert debugEnter(CLASS_NAME, "getAttributeValue",
                      String.valueOf(attributeType));

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
   * Indicates whether this RDN is multivalued.
   *
   * @return  <CODE>true</CODE> if this RDN is multivalued, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isMultiValued()
  {
    assert debugEnter(CLASS_NAME, "isMultiValued");

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
    assert debugEnter(CLASS_NAME, "hasValue", String.valueOf(type),
                      String.valueOf(value));

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
   * Adds the provided type-value pair from this RDN.
   *
   * @param  type   The attribute type of the pair to add.
   * @param  name   The user-provided name of the pair to add.
   * @param  value  The attribute value of the pair to add.
   *
   * @return  <CODE>true</CODE> if the type-value pair was added to
   *          this RDN, or <CODE>false</CODE> if it was not (e.g., it
   *          was already present).
   */
  public boolean addValue(AttributeType type, String name,
                          AttributeValue value)
  {
    assert debugEnter(CLASS_NAME, "addValue", String.valueOf(type),
                      String.valueOf(name), String.valueOf(value));

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
   * Removes the provided type-value pair from this RDN.
   *
   * @param  type   The attribute type of the pair to remove.
   * @param  value  The attribute value of the pair to remove.
   *
   * @return  <CODE>true</CODE> if the type-value pair was found and
   *          removed from this RDN, or <CODE>false</CODE> if it was
   *          not.
   */
  public boolean removeValue(AttributeType type, AttributeValue value)
  {
    assert debugEnter(CLASS_NAME, "removeValue", String.valueOf(type),
                      String.valueOf(value));

    for (int i=0; i < numValues; i++)
    {
      if (attributeTypes[i].equals(type) &&
          attributeValues[i].equals(value))
      {
        numValues--;

        if (numValues == 0)
        {
          attributeTypes  = new AttributeType[0];
          attributeNames  = new String[0];
          attributeValues = new AttributeValue[0];
        }
        else if (i == 0)
        {
          AttributeType[] newTypes = new AttributeType[numValues];
          System.arraycopy(attributeTypes, 1, newTypes, 0, numValues);
          attributeTypes = newTypes;

          String[] newNames = new String[numValues];
          System.arraycopy(attributeNames, 1, newNames, 0, numValues);
          attributeNames = newNames;

          AttributeValue[] newValues = new AttributeValue[numValues];
          System.arraycopy(attributeValues, 1, newValues, 0,
                           numValues);
          attributeValues = newValues;
        }
        else if (i == numValues)
        {
          AttributeType[] newTypes = new AttributeType[numValues];
          System.arraycopy(attributeTypes, 0, newTypes, 0, numValues);
          attributeTypes = newTypes;

          String[] newNames = new String[numValues];
          System.arraycopy(attributeNames, 0, newNames, 0, numValues);
          attributeNames = newNames;

          AttributeValue[] newValues = new AttributeValue[numValues];
          System.arraycopy(attributeValues, 0, newValues, 0,
                           numValues);
          attributeValues = newValues;
        }
        else
        {
          int remaining = numValues - i;

          AttributeType[] newTypes = new AttributeType[numValues];
          System.arraycopy(attributeTypes, 0, newTypes, 0, i);
          System.arraycopy(attributeTypes, i+1, newTypes, i,
                           remaining);
          attributeTypes = newTypes;

          String[] newNames = new String[numValues];
          System.arraycopy(attributeNames, 0, newNames, 0, i);
          System.arraycopy(attributeNames, i+1, newNames, i,
                           remaining);
          attributeNames = newNames;

          AttributeValue[] newValues = new AttributeValue[numValues];
          System.arraycopy(attributeValues, 0, newValues, 0, i);
          System.arraycopy(attributeValues, i+1, newValues, i,
                           remaining);
          attributeValues = newValues;
        }

        rdnString     = null;
        normalizedRDN = null;

        return true;
      }
    }

    return false;
  }



  /**
   * Replaces the set of values for this RDN with the provided
   * name-value pair.
   *
   * @param  type   The attribute type for this RDN.
   * @param  name   The user-provided name for this RDN.
   * @param  value  The attribute value for this RDN.
   */
  public void replaceValues(AttributeType type, String name,
                            AttributeValue value)
  {
    assert debugEnter(CLASS_NAME, "replaceValues",
                      String.valueOf(type), String.valueOf(name),
                      String.valueOf(value));

    attributeTypes  = new AttributeType[] { type };
    attributeNames  = new String[] { name };
    attributeValues = new AttributeValue[] { value };

    numValues     = 1;
    rdnString     = null;
    normalizedRDN = null;
  }



  /**
   * Replaces the set of values for this RDN with the provided set of
   * name-value pairs.  The number of elements in each list must be
   * equal and greater than one.
   *
   * @param  attributeTypes   The set of attribute types for this RDN.
   * @param  attributeNames   The set of user-provided names for this
   *                          RDN.
   * @param  attributeValues  The set of values for this RDN.
   */
  public void replaceValues(ArrayList<AttributeType> attributeTypes,
                            ArrayList<String> attributeNames,
                            ArrayList<AttributeValue> attributeValues)
  {
    assert debugEnter(CLASS_NAME, "replaceValues",
                      String.valueOf(attributeTypes),
                      String.valueOf(attributeNames),
                      String.valueOf(attributeValues));

    this.attributeTypes = new AttributeType[attributeTypes.size()];
    attributeTypes.toArray(this.attributeTypes);

    this.attributeNames = new String[attributeNames.size()];
    attributeNames.toArray(this.attributeNames);

    this.attributeValues = new AttributeValue[attributeValues.size()];
    attributeValues.toArray(this.attributeValues);

    numValues     = attributeTypes.size();
    rdnString     = null;
    normalizedRDN = null;
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
    assert debugEnter(CLASS_NAME, "decode",
                      String.valueOf(rdnString));


    // A null or empty RDN is not acceptable.
    if (rdnString == null)
    {
      int    msgID   = MSGID_RDN_DECODE_NULL;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message, msgID);
    }

    int length = rdnString.length();
    if (length == 0)
    {
      int    msgID   = MSGID_RDN_DECODE_NULL;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message, msgID);
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
        int    msgID   = MSGID_RDN_DECODE_NULL;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message, msgID);
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
      int    msgID   = MSGID_RDN_END_WITH_ATTR_NAME;
      String message = getMessage(msgID,
                            rdnString, attributeName.toString());
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message, msgID);
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
        int    msgID   = MSGID_RDN_END_WITH_ATTR_NAME;
        String message = getMessage(msgID,
                              rdnString, attributeName.toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message, msgID);
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
      int    msgID   = MSGID_RDN_NO_EQUAL;
      String message = getMessage(msgID, rdnString,
                                  attributeName.toString(), c, pos);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message, msgID);
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
      int    msgID   = MSGID_RDN_UNEXPECTED_COMMA;
      String message = getMessage(msgID, rdnString, pos);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message, msgID);
    }


    // If the next character is anything but a plus sign, then it is
    // illegal.
    if (c != '+')
    {
      int    msgID   = MSGID_RDN_ILLEGAL_CHARACTER;
      String message = getMessage(msgID, rdnString, c, pos);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message, msgID);
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
        int    msgID   = MSGID_RDN_END_WITH_ATTR_NAME;
        String message = getMessage(msgID, rdnString,
                                    attributeName.toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message, msgID);
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
          int    msgID   = MSGID_RDN_END_WITH_ATTR_NAME;
          String message = getMessage(msgID, rdnString,
                                      attributeName.toString());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);
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
        int    msgID   = MSGID_RDN_NO_EQUAL;
        String message = getMessage(msgID, rdnString,
                                    attributeName.toString(), c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message, msgID);
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
        int    msgID   = MSGID_RDN_UNEXPECTED_COMMA;
        String message = getMessage(msgID, rdnString, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message, msgID);
      }


      // If the next character is anything but a plus sign, then it is
      // illegal.
      if (c != '+')
      {
        int    msgID   = MSGID_RDN_ILLEGAL_CHARACTER;
        String message = getMessage(msgID, rdnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message, msgID);
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
    assert debugEnter(CLASS_NAME, "duplicate");

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
    assert debugEnter(CLASS_NAME, "equals", String.valueOf(o));

    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof RDN)))
    {
      return false;
    }

    RDN rdn = (RDN) o;
    if (numValues != rdn.numValues)
    {
      return false;
    }

    for (int i=0; i < numValues; i++)
    {
      if ((! attributeTypes[i].equals(rdn.attributeTypes[i])) ||
          (! attributeValues[i].equals(rdn.attributeValues[i])))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Retrieves the hash code for this RDN.  It will be calculated as
   * the sum of the hash codes of the types and values.
   *
   * @return  The hash code for this RDN.
   */
  public int hashCode()
  {
    assert debugEnter(CLASS_NAME, "hashCode");

    int hashCode = 0;

    for (int i=0; i < numValues; i++)
    {
      hashCode += attributeTypes[i].hashCode() +
                  attributeValues[i].hashCode();
    }

    return hashCode;
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
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder");

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
    assert debugEnter(CLASS_NAME, "toNormalizedString",
                      "java.lang.StringBuilder");

    if (normalizedRDN != null)
    {
      buffer.append(normalizedRDN);
      return;
    }

    boolean bufferEmpty = (buffer.length() == 0);

    if (attributeNames.length == 1)
    {
      toLowerCase(attributeNames[0], buffer);
      buffer.append('=');

      try
      {
        buffer.append(
             attributeValues[0].getNormalizedDNStringValue());
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "toNormalizedString", e);

        buffer.append(attributeValues[0].getStringValue());
      }
    }
    else
    {
      TreeSet<String> rdnElementStrings = new TreeSet<String>();

      for (int i=0; i < attributeNames.length; i++)
      {
        StringBuilder b2 = new StringBuilder();
        toLowerCase(attributeNames[i], b2);
        b2.append('=');

        try
        {
          b2.append(attributeValues[i].getNormalizedStringValue());
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "toNormalizedString", e);

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
    assert debugEnter(CLASS_NAME, "compareTo", String.valueOf(rdn));

    if (equals(rdn))
    {
      return 0;
    }

    int minValues = Math.min(numValues, rdn.numValues);
    for (int i=0; i < minValues; i++)
    {
      String n1 = attributeNames[i].toLowerCase();
      String n2 = rdn.attributeNames[i].toLowerCase();

      int result = n1.compareTo(n2);
      if (result != 0)
      {
        return result;
      }

      try
      {
        String v1 = attributeValues[i].getNormalizedStringValue();
        String v2 = rdn.attributeValues[i].getNormalizedStringValue();

        result = v1.compareTo(v2);
        if (result != 0)
        {
          return result;
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "compareTo", e);

        return 0;
      }
    }

    if (numValues > minValues)
    {
      return 1;
    }
    else if (rdn.numValues > minValues)
    {
      return -1;
    }
    else
    {
      return 0;
    }
  }
}

