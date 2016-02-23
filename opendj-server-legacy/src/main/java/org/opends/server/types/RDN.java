/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.types;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.util.Reject;
import org.opends.server.core.DirectoryServer;

import static org.opends.messages.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

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
       implements Comparable<RDN>, Iterable<AVA>
{
  /** The collection of AVAs for the elements in this RDN. */
  private final List<AVA> avas;
  /** Representation of the normalized form of this RDN. */
  private ByteString normalizedRDN;


  /**
   * Creates a new RDN with the provided information.
   *
   * @param  attributeType   The attribute type for this RDN.  It must
   *                         not be {@code null}.
   * @param  attributeValue  The value for this RDN.  It must not be
   *                         {@code null}.
   */
  public RDN(AttributeType attributeType, ByteString attributeValue)
  {
    Reject.ifNull(attributeType, attributeValue);
    avas = new ArrayList<>(1);
    avas.add(new AVA(attributeType, attributeValue));
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
  public RDN(AttributeType attributeType, String attributeName, ByteString attributeValue)
  {
    Reject.ifNull(attributeType, attributeName, attributeValue);
    avas = new ArrayList<>(1);
    avas.add(new AVA(attributeType, attributeName, attributeValue));
  }

  /**
   * Creates a new RDN with the provided {@link AVA}s.
   *
   * @param avas
   *          The AVAs that will define the new RDN
   */
  public RDN(AVA... avas)
  {
    this(Arrays.asList(Reject.checkNotNull(avas, "avas must not be null")));
  }

  /**
   * Creates a new RDN with the provided collection of {@link AVA}.
   *
   * @param avas
   *          The collection of AVA that will define the new RDN
   */
  public RDN(Collection<AVA> avas)
  {
    Reject.ifNull(avas, "avas must not be null");
    Reject.ifTrue(avas.isEmpty(), "avas must not be empty");
    this.avas = new ArrayList<>(avas);
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
   * Retrieves the number of attribute-value pairs contained in this RDN.
   *
   * @return The number of attribute-value pairs contained in this RDN.
   */
  public int size()
  {
    return avas.size();
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
    for (AVA ava : avas)
    {
      if (ava.getAttributeType().equals(attributeType))
      {
        return true;
      }
    }
    return false;
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
    for (AVA ava : avas)
    {
      if (ava.getAttributeType().equals(attributeType))
      {
        return ava.getAttributeValue();
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
    return avas.size() > 1;
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
    for (AVA ava : avas)
    {
      if (ava.getAttributeType().equals(type) && ava.getAttributeValue().equals(value))
      {
        return false;
      }
    }
    avas.add(new AVA(type, name, value));
    return true;
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
    while (pos < length && ((c = rdnString.charAt(pos)) == ' '))
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

    // If using default is a problem, it will be caught later either
    // by not finding the target entry or by not allowing the entry
    // to be added.
    AttributeType attrType = DirectoryServer.getAttributeType(name);

    RDN rdn = new RDN(attrType, name, parsedValue.toByteString());


    // Skip over any spaces that might be after the attribute value.
    while (pos < length && ((c = rdnString.charAt(pos)) == ' '))
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
    if (c == ',' || c == ';')
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
      while (pos < length && ((c = rdnString.charAt(pos)) == ' '))
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
      while (pos < length && ((c = rdnString.charAt(pos)) == ' '))
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
        attrType = DirectoryServer.getAttributeType(name);

        rdn.addValue(attrType, name, ByteString.empty());
        return rdn;
      }


      // Parse the value for this RDN component.
      parsedValue.clear();
      pos = DN.parseAttributeValue(rdnString, pos, parsedValue);


      // Update the RDN to include the new attribute/value.
      name            = attributeName.toString();
      // If using default is a problem, it will be caught later either
      // by not finding the target entry or by not allowing the entry
      // to be added.
      attrType = DirectoryServer.getAttributeType(name);

      rdn.addValue(attrType, name, parsedValue.toByteString());


      // Skip over any spaces that might be after the attribute value.
      while (pos < length && ((c = rdnString.charAt(pos)) == ' '))
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
      if (c == ',' || c == ';')
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
      RDN otherRDN = (RDN) o;
      return toNormalizedByteString().equals(otherRDN.toNormalizedByteString());
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
    return toNormalizedByteString().hashCode();
  }

  /** Returns normalized value for attribute at provided position. */
  private ByteString getEqualityNormalizedValue(AVA ava)
  {
    final MatchingRule matchingRule = ava.getAttributeType().getEqualityMatchingRule();
    ByteString attributeValue = ava.getAttributeValue();
    if (matchingRule != null)
    {
      try
      {
        attributeValue = matchingRule.normalizeAttributeValue(attributeValue);
      }
      catch (final DecodeException de)
      {
        // Unable to normalize, use default
        attributeValue = ava.getAttributeValue();
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
    StringBuilder buffer = new StringBuilder();
    Iterator<AVA> it = avas.iterator();
    buffer.append(it.next());
    while (it.hasNext())
    {
      buffer.append("+");
      buffer.append(it.next());
    }
    return buffer.toString();
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
    buffer.append(this);
  }

 /**
  * Retrieves a normalized string representation of this RDN.
  * <p>
  *
  * This representation is safe to use in an URL or in a file name.
  * However, it is not a valid RDN and can't be reverted to a valid RDN.
  *
  * @return  The normalized string representation of this RDN.
  */
  String toNormalizedUrlSafeString()
  {
    final StringBuilder buffer = new StringBuilder();
    if (avas.size() == 1)
    {
      normalizeAVAToUrlSafeString(getFirstAVA(), buffer);
    }
    else
    {
      // Normalization sorts RDNs alphabetically
      SortedSet<String> avaStrings = new TreeSet<>();
      for (AVA ava : avas)
      {
        StringBuilder builder = new StringBuilder();
        normalizeAVAToUrlSafeString(ava, builder);
        avaStrings.add(builder.toString());
      }

      Iterator<String> iterator = avaStrings.iterator();
      buffer.append(iterator.next());
      while (iterator.hasNext())
      {
        buffer.append('+');
        buffer.append(iterator.next());
      }
    }
    return buffer.toString();
  }

  private ByteString toNormalizedByteString()
  {
    if (normalizedRDN == null)
    {
      computeNormalizedByteString(new ByteStringBuilder());
    }
    return normalizedRDN;
  }

  /**
   * Adds a normalized byte string representation of this RDN to the provided builder.
   * <p>
   * The representation is suitable for equality and comparisons, and for providing a
   * natural hierarchical ordering.
   * However, it is not a valid RDN and can't be reverted to a valid RDN.
   *
   * @param builder
   *           Builder to add this representation to.
   * @return the builder
   */
  public ByteStringBuilder toNormalizedByteString(ByteStringBuilder builder)
  {
    if (normalizedRDN != null)
    {
      return builder.appendBytes(normalizedRDN);
    }
    return computeNormalizedByteString(builder);
  }

  private ByteStringBuilder computeNormalizedByteString(ByteStringBuilder builder)
  {
    final int startPos = builder.length();

    if (avas.size() == 1)
    {
      normalizeAVAToByteString(getFirstAVA(), builder);
    }
    else
    {
      // Normalization sorts RDNs
      SortedSet<ByteString> avaStrings = new TreeSet<>();
      for (AVA ava : avas)
      {
        ByteStringBuilder b = new ByteStringBuilder();
        normalizeAVAToByteString(ava, b);
        avaStrings.add(b.toByteString());
      }

      Iterator<ByteString> iterator = avaStrings.iterator();
      builder.appendBytes(iterator.next());
      while (iterator.hasNext())
      {
        builder.appendByte(DN.NORMALIZED_AVA_SEPARATOR);
        builder.appendBytes(iterator.next());
      }
    }

    if (normalizedRDN == null)
    {
      normalizedRDN = builder.subSequence(startPos, builder.length()).toByteString();
    }

    return builder;
  }

  /**
   * Adds a normalized byte string representation of the AVA corresponding
   * to provided position in this RDN to the provided builder.
   *
   * @param position
   *           Position of AVA in this RDN.
   * @param builder
   *           Builder to add the representation to.
   * @return the builder
   */
  private ByteStringBuilder normalizeAVAToByteString(AVA ava, final ByteStringBuilder builder)
  {
    builder.appendUtf8(ava.getAttributeType().getNormalizedNameOrOID());
    builder.appendUtf8("=");
    final ByteString value = getEqualityNormalizedValue(ava);
    if (value.length() > 0)
    {
      builder.appendBytes(escapeBytes(value));
    }
    return builder;
  }

  /**
   * Return a new byte string with bytes 0x00, 0x01 and 0x02 escaped.
   * <p>
   * These bytes are reserved to represent respectively the RDN separator, the
   * AVA separator and the escape byte in a normalized byte string.
   */
  private ByteString escapeBytes(final ByteString value)
  {
    if (!needEscaping(value))
    {
      return value;
    }

    final ByteStringBuilder builder = new ByteStringBuilder();
    for (int i = 0; i < value.length(); i++)
    {
      final byte b = value.byteAt(i);
      if (isByteToEscape(b))
      {
        builder.appendByte(DN.NORMALIZED_ESC_BYTE);
      }
      builder.appendByte(b);
    }
    return builder.toByteString();
  }

  private boolean needEscaping(final ByteString value)
  {
    for (int i = 0; i < value.length(); i++)
    {
      if (isByteToEscape(value.byteAt(i)))
      {
        return true;
      }
    }
    return false;
  }

  private boolean isByteToEscape(final byte b)
  {
    return b == DN.NORMALIZED_RDN_SEPARATOR || b == DN.NORMALIZED_AVA_SEPARATOR || b == DN.NORMALIZED_ESC_BYTE;
  }


  /**
   * Appends a normalized string representation of this RDN to the
   * provided buffer.
   *
   * @param  position  The position of the attribute type and value to
   *              retrieve.
   * @param  builder  The buffer to which to append the information.
   * @return the builder
   */
  private StringBuilder normalizeAVAToUrlSafeString(AVA ava, StringBuilder builder)
  {
    AttributeType attrType = ava.getAttributeType();
    builder.append(attrType.getNormalizedNameOrOID());
      builder.append('=');

    ByteString value = getEqualityNormalizedValue(ava);
      if (value.length() == 0)
      {
        return builder;
      }
    final boolean hasAttributeName = !attrType.getNames().isEmpty();
    final boolean isHumanReadable = attrType.getSyntax().isHumanReadable();
      if (!hasAttributeName || !isHumanReadable)
      {
        builder.append(value.toPercentHexString());
      }
      else
      {
        // try to decode value as UTF-8 string
        final CharBuffer buffer = CharBuffer.allocate(value.length());
        final CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        if (value.copyTo(buffer, decoder))
        {
          buffer.flip();
          try
          {
            // URL encoding encodes space char as '+' instead of using hex code
            final String val = URLEncoder.encode(buffer.toString(), "UTF-8").replaceAll("\\+", "%20");
            builder.append(val);
          }
          catch (UnsupportedEncodingException e)
          {
            // should never happen
            builder.append(value.toPercentHexString());
          }
        }
        else
        {
          builder.append(value.toPercentHexString());
        }
      }
      return builder;
  }

  /**
   * Compares this RDN with the provided RDN based on natural ordering defined
   * by the toNormalizedByteString() method.
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
    return toNormalizedByteString().compareTo(rdn.toNormalizedByteString());
  }

  /**
   * Returns the first AVA of this {@link RDN}.
   *
   * @return the first AVA of this {@link RDN}.
   */
  public AVA getFirstAVA()
  {
    return avas.get(0);
  }

  @Override
  public Iterator<AVA> iterator()
  {
    return avas.iterator();
  }
}
