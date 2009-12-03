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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import static com.sun.opends.sdk.util.Messages.*;
import static org.opends.sdk.util.StaticUtils.*;

import java.util.*;

import com.sun.opends.sdk.util.Message;
import org.opends.sdk.schema.*;
import org.opends.sdk.util.*;



/**
 * A relative distinguished name (RDN) as defined in RFC 4512 section
 * 2.3 is the name of an entry relative to its immediate superior. An
 * RDN is composed of an unordered set of one or more attribute value
 * assertions (AVA) consisting of an attribute description with zero
 * options and an attribute value. These AVAs are chosen to match
 * attribute values (each a distinguished value) of the entry.
 * <p>
 * An entry's relative distinguished name must be unique among all
 * immediate subordinates of the entry's immediate superior (i.e. all
 * siblings).
 * <p>
 * The following are examples of string representations of RDNs:
 *
 * <pre>
 * uid=12345
 * ou=Engineering
 * cn=Kurt Zeilenga+L=Redwood Shores
 * </pre>
 *
 * The last is an example of a multi-valued RDN; that is, an RDN
 * composed of multiple AVAs.
 * <p>
 * TODO: need more constructors.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4512#section-2.3">RFC
 *      4512 - Lightweight Directory Access Protocol (LDAP): Directory
 *      Information Models </a>
 */
public final class RDN implements Iterable<RDN.AVA>, Comparable<RDN>
{
  /**
   * An attribute value assertion (AVA) as defined in RFC 4512 section
   * 2.3 consists of an attribute description with zero options and an
   * attribute value.
   */
  public static final class AVA implements Comparable<AVA>
  {
    private final AttributeType attributeType;

    private final ByteString attributeValue;



    /**
     * Creates a new attribute value assertion (AVA) using the provided
     * attribute type and value.
     *
     * @param attributeType
     *          The attribute type.
     * @param attributeValue
     *          The attribute value.
     * @throws NullPointerException
     *           If {@code attributeType} or {@code attributeValue} was
     *           {@code null}.
     */
    public AVA(AttributeType attributeType, ByteString attributeValue)
        throws NullPointerException
    {
      Validator.ensureNotNull(attributeType, attributeValue);

      this.attributeType = attributeType;
      this.attributeValue = attributeValue;
    }



    /**
     * {@inheritDoc}
     */
    public int compareTo(AVA ava)
    {
      int result = attributeType.compareTo(ava.attributeType);

      if (result == 0)
      {
        final ByteString nv1 = getNormalizeValue();
        final ByteString nv2 = ava.getNormalizeValue();
        result = nv1.compareTo(nv2);
      }

      return result;
    }



    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj)
    {
      if (this == obj)
      {
        return true;
      }
      else if (obj instanceof AVA)
      {
        return compareTo((AVA) obj) == 0;
      }
      else
      {
        return false;
      }
    }



    /**
     * Returns the attribute type associated with this AVA.
     *
     * @return The attribute type associated with this AVA.
     */
    public AttributeType getAttributeType()
    {
      return attributeType;
    }



    /**
     * Returns the attribute value associated with this AVA.
     *
     * @return The attribute value associated with this AVA.
     */
    public ByteString getAttributeValue()
    {
      return attributeValue;
    }



    /**
     * {@inheritDoc}
     */
    public int hashCode()
    {
      return attributeType.hashCode() * 31
          + getNormalizeValue().hashCode();
    }



    /**
     * {@inheritDoc}
     */
    public String toString()
    {
      final StringBuilder builder = new StringBuilder();
      return toString(builder).toString();
    }



    private ByteString getNormalizeValue()
    {
      final MatchingRule matchingRule = attributeType
          .getEqualityMatchingRule();
      if (matchingRule != null)
      {
        try
        {
          return matchingRule.normalizeAttributeValue(attributeValue);
        }
        catch (final DecodeException de)
        {
          // Ignore - we'll drop back to the user provided value.
        }
      }
      return attributeValue;
    }



    private StringBuilder toNormalizedString(StringBuilder builder)
    {
      return toString(builder, true);
    }



    private StringBuilder toString(StringBuilder builder)
    {
      return toString(builder, false);
    }



    private StringBuilder toString(StringBuilder builder,
        boolean normalize)
    {
      final ByteString value = normalize ? getNormalizeValue()
          : attributeValue;

      if (!attributeType.getNames().iterator().hasNext())
      {
        builder.append(attributeType.getOID());
        builder.append("=#");
        StaticUtils.toHex(value, builder);
      }
      else
      {
        final String name = attributeType.getNameOrOID();
        if (normalize)
        {
          // Normalizing.
          StaticUtils.toLowerCase(name, builder);
        }
        else
        {
          builder.append(name);
        }

        builder.append("=");

        final Syntax syntax = attributeType.getSyntax();
        if (!syntax.isHumanReadable())
        {
          builder.append("#");
          StaticUtils.toHex(value, builder);
        }
        else
        {
          final String str = value.toString();
          char c;
          for (int si = 0; si < str.length(); si++)
          {
            c = str.charAt(si);
            if (c == ' ' || c == '#' || c == '"' || c == '+'
                || c == ',' || c == ';' || c == '<' || c == '='
                || c == '>' || c == '\\' || c == '\u0000')
            {
              builder.append('\\');
            }
            builder.append(c);
          }
        }
      }
      return builder;
    }
  }



  private static final char[] SPECIAL_CHARS = new char[] { '\"', '+',
      ',', ';', '<', '>', ' ', '#', '=', '\\' };

  private static final char[] DELIMITER_CHARS = new char[] { '+', ',',
      ';' };

  private static final char[] DQUOTE_CHAR = new char[] { '\"' };

  private static final Comparator<AVA> ATV_COMPARATOR = new Comparator<AVA>()
  {
    public int compare(AVA o1, AVA o2)
    {
      return o1.getAttributeType().compareTo(o2.getAttributeType());
    }
  };



  /**
   * Parses the provided LDAP string representation of an RDN using the
   * default schema.
   *
   * @param rdn
   *          The LDAP string representation of a RDN.
   * @return The parsed RDN.
   * @throws LocalizedIllegalArgumentException
   *           If {@code rdn} is not a valid LDAP string representation
   *           of a RDN.
   * @throws NullPointerException
   *           If {@code rdn} was {@code null}.
   */
  public static RDN valueOf(String rdn)
      throws LocalizedIllegalArgumentException
  {
    return valueOf(rdn, Schema.getDefaultSchema());
  }



  /**
   * Parses the provided LDAP string representation of a RDN using the
   * provided schema.
   *
   * @param rdn
   *          The LDAP string representation of a RDN.
   * @param schema
   *          The schema to use when parsing the RDN.
   * @return The parsed RDN.
   * @throws LocalizedIllegalArgumentException
   *           If {@code rdn} is not a valid LDAP string representation
   *           of a RDN.
   * @throws NullPointerException
   *           If {@code rdn} or {@code schema} was {@code null}.
   */
  public static RDN valueOf(String rdn, Schema schema)
      throws LocalizedIllegalArgumentException
  {
    final SubstringReader reader = new SubstringReader(rdn);
    try
    {
      return decode(rdn, reader, schema);
    }
    catch (final UnknownSchemaElementException e)
    {
      final Message message = ERR_RDN_TYPE_NOT_FOUND.get(rdn, e
          .getMessageObject());
      throw new LocalizedIllegalArgumentException(message);
    }
  }



  private static AVA readAttributeTypeAndValue(SubstringReader reader,
      Schema schema) throws LocalizedIllegalArgumentException,
      UnknownSchemaElementException
  {
    // Skip over any spaces at the beginning.
    reader.skipWhitespaces();

    final AttributeType attribute = readDNAttributeName(reader, schema);

    // Make sure that we're not at the end of the DN string because
    // that would be invalid.
    if (reader.remaining() == 0)
    {
      final Message message = ERR_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME
          .get(reader.getString(), attribute.getNameOrOID());
      throw new LocalizedIllegalArgumentException(message);
    }

    // The next character must be an equal sign. If it is not, then
    // that's an error.
    char c;
    if ((c = reader.read()) != '=')
    {
      final Message message = ERR_ATTR_SYNTAX_DN_NO_EQUAL.get(reader
          .getString(), attribute.getNameOrOID(), c);
      throw new LocalizedIllegalArgumentException(message);
    }

    // Skip over any spaces after the equal sign.
    reader.skipWhitespaces();

    // Parse the value for this RDN component.
    final ByteString value = readDNAttributeValue(reader);

    return new AVA(attribute, value);
  }



  private static AttributeType readDNAttributeName(
      SubstringReader reader, Schema schema)
      throws LocalizedIllegalArgumentException,
      UnknownSchemaElementException
  {
    int length = 1;
    reader.mark();

    // The next character must be either numeric (for an OID) or
    // alphabetic (for
    // an attribute description).
    char c = reader.read();
    if (isDigit(c))
    {
      boolean lastWasPeriod = false;
      do
      {
        if (c == '.')
        {
          if (lastWasPeriod)
          {
            final Message message = ERR_ATTR_SYNTAX_OID_CONSECUTIVE_PERIODS
                .get(reader.getString(), reader.pos() - 1);
            throw new LocalizedIllegalArgumentException(message);
          }
          else
          {
            lastWasPeriod = true;
          }
        }
        else if (!isDigit(c))
        {
          // This must have been an illegal character.
          final Message message = ERR_ATTR_SYNTAX_OID_ILLEGAL_CHARACTER
              .get(reader.getString(), reader.pos() - 1);
          throw new LocalizedIllegalArgumentException(message);
        }
        else
        {
          lastWasPeriod = false;
        }
        length++;
      } while ((c = reader.read()) != '=');
    }
    if (isAlpha(c))
    {
      // This must be an attribute description. In this case, we will
      // only
      // accept alphabetic characters, numeric digits, and the hyphen.
      while ((c = reader.read()) != '=')
      {
        if (length == 0 && !isAlpha(c))
        {
          // This is an illegal character.
          final Message message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR
              .get(reader.getString(), c, reader.pos() - 1);
          throw new LocalizedIllegalArgumentException(message);
        }

        if (!isAlpha(c) && !isDigit(c) && c != '-')
        {
          // This is an illegal character.
          final Message message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR
              .get(reader.getString(), c, reader.pos() - 1);
          throw new LocalizedIllegalArgumentException(message);
        }

        length++;
      }
    }
    else
    {
      final Message message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR.get(
          reader.getString(), c, reader.pos() - 1);
      throw new LocalizedIllegalArgumentException(message);
    }

    reader.reset();

    // Return the position of the first non-space character after the
    // token.

    return schema.getAttributeType(reader.read(length));
  }



  private static ByteString readDNAttributeValue(SubstringReader reader)
      throws LocalizedIllegalArgumentException
  {
    // All leading spaces have already been stripped so we can start
    // reading the value. However, it may be empty so check for that.
    if (reader.remaining() == 0)
    {
      return ByteString.empty();
    }

    reader.mark();

    // Look at the first character. If it is an octothorpe (#), then
    // that means that the value should be a hex string.
    char c = reader.read();
    int length = 0;
    if (c == '#')
    {
      // The first two characters must be hex characters.
      reader.mark();
      if (reader.remaining() < 2)
      {
        final Message message = ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT
            .get(reader.getString());
        throw new LocalizedIllegalArgumentException(message);
      }

      for (int i = 0; i < 2; i++)
      {
        c = reader.read();
        if (isHexDigit(c))
        {
          length++;
        }
        else
        {
          final Message message = ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT
              .get(reader.getString(), c);
          throw new LocalizedIllegalArgumentException(message);
        }
      }

      // The rest of the value must be a multiple of two hex
      // characters. The end of the value may be designated by the
      // end of the DN, a comma or semicolon, or a space.
      while (reader.remaining() > 0)
      {
        c = reader.read();
        if (isHexDigit(c))
        {
          length++;

          if (reader.remaining() > 0)
          {
            c = reader.read();
            if (isHexDigit(c))
            {
              length++;
            }
            else
            {
              final Message message = ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT
                  .get(reader.getString(), c);
              throw new LocalizedIllegalArgumentException(message);
            }
          }
          else
          {
            final Message message = ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT
                .get(reader.getString());
            throw new LocalizedIllegalArgumentException(message);
          }
        }
        else if ((c == ' ') || (c == ',') || (c == ';'))
        {
          // This denotes the end of the value.
          break;
        }
        else
        {
          final Message message = ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT
              .get(reader.getString(), c);
          throw new LocalizedIllegalArgumentException(message);
        }
      }

      // At this point, we should have a valid hex string. Convert it
      // to a byte array and set that as the value of the provided
      // octet string.
      try
      {
        reader.reset();
        return ByteString
            .wrap(hexStringToByteArray(reader.read(length)));
      }
      catch (final Exception e)
      {
        final Message message = ERR_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE
            .get(reader.getString(), String.valueOf(e));
        throw new LocalizedIllegalArgumentException(message);
      }
    }

    // If the first character is a quotation mark, then the value
    // should continue until the corresponding closing quotation mark.
    else if (c == '"')
    {
      try
      {
        return StaticUtils.evaluateEscapes(reader, DQUOTE_CHAR, false);
      }
      catch (final DecodeException e)
      {
        throw new LocalizedIllegalArgumentException(e
            .getMessageObject());
      }
    }

    // Otherwise, use general parsing to find the end of the value.
    else
    {
      reader.reset();
      ByteString bytes;
      try
      {
        bytes = StaticUtils.evaluateEscapes(reader, SPECIAL_CHARS,
            DELIMITER_CHARS, true);
      }
      catch (final DecodeException e)
      {
        throw new LocalizedIllegalArgumentException(e
            .getMessageObject());
      }
      if (bytes.length() == 0)
      {
        // We don't allow an empty attribute value.
        final Message message = ERR_ATTR_SYNTAX_DN_INVALID_REQUIRES_ESCAPE_CHAR
            .get(reader.getString(), reader.pos());
        throw new LocalizedIllegalArgumentException(message);
      }
      return bytes;
    }
  }



  // FIXME: ensure that the decoded RDN does not contain multiple AVAs
  // with the same type.
  static RDN decode(String rdnString, SubstringReader reader,
      Schema schema) throws LocalizedIllegalArgumentException,
      UnknownSchemaElementException
  {
    final AVA firstAVA = readAttributeTypeAndValue(reader, schema);

    // Skip over any spaces that might be after the attribute value.
    reader.skipWhitespaces();

    reader.mark();
    if (reader.remaining() > 0 && reader.read() == '+')
    {
      final List<AVA> avas = new ArrayList<AVA>();
      avas.add(firstAVA);

      do
      {
        avas.add(readAttributeTypeAndValue(reader, schema));

        // Skip over any spaces that might be after the attribute value.
        reader.skipWhitespaces();

        reader.mark();
      } while (reader.read() == '+');

      reader.reset();
      return new RDN(avas.toArray(new AVA[avas.size()]), rdnString);
    }
    else
    {
      reader.reset();
      return new RDN(new AVA[] { firstAVA }, rdnString);
    }
  }



  // In original order.
  private final AVA[] avas;

  // We need to store the original string value if provided in order to
  // preserve the original whitespace.
  private String stringValue;



  private RDN(AVA[] avas, String stringValue)
  {
    this.avas = avas;
    this.stringValue = stringValue;
  }



  /**
   * {@inheritDoc}
   */
  public int compareTo(RDN rdn)
  {
    final int sz1 = avas.length;
    final int sz2 = rdn.avas.length;

    if (sz1 != sz2)
    {
      return sz1 - sz2;
    }

    if (sz1 == 1)
    {
      return avas[0].compareTo(rdn.avas[0]);
    }

    // Need to sort the AVAs before comparing.
    final AVA[] a1 = new AVA[sz1];
    System.arraycopy(avas, 0, a1, 0, sz1);
    Arrays.sort(a1, ATV_COMPARATOR);

    final AVA[] a2 = new AVA[sz1];
    System.arraycopy(rdn.avas, 0, a2, 0, sz1);
    Arrays.sort(a2, ATV_COMPARATOR);

    for (int i = 0; i < sz1; i++)
    {
      final int result = a1[i].compareTo(a2[i]);
      if (result != 0)
      {
        return result;
      }
    }

    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public boolean equals(Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    else if (obj instanceof RDN)
    {
      return compareTo((RDN) obj) == 0;
    }
    else
    {
      return false;
    }
  }



  /**
   * Returns the attribute value contained in this RDN which is
   * associated with the provided attribute type, or {@code null} if
   * this RDN does not include such an attribute value.
   *
   * @param attributeType
   *          The attribute type.
   * @return The attribute value.
   */
  public ByteString getAttributeValue(AttributeType attributeType)
  {
    for (final AVA ava : avas)
    {
      if (ava.getAttributeType().equals(attributeType))
      {
        return ava.getAttributeValue();
      }
    }
    return null;
  }



  /**
   * Returns the first AVA contained in this RDN.
   *
   * @return The first AVA contained in this RDN.
   */
  public AVA getFirstAVA()
  {
    return avas[0];
  }



  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    // Avoid an algorithm that requires the AVAs to be sorted.
    int hash = 0;
    for (int i = 0; i < avas.length; i++)
    {
      hash += avas[i].hashCode();
    }
    return hash;
  }



  /**
   * Returns {@code true} if this RDN contains more than one AVA.
   *
   * @return {@code true} if this RDN contains more than one AVA,
   *         otherwise {@code false}.
   */
  public boolean isMultiValued()
  {
    return avas.length > 1;
  }



  /**
   * Returns an iterator of the AVAs contained in this RDN. The AVAs
   * will be returned in the user provided order.
   * <p>
   * Attempts to remove AVAs using an iterator's {@code remove()} method
   * are not permitted and will result in an {@code
   * UnsupportedOperationException} being thrown.
   *
   * @return An iterator of the AVAs contained in this RDN.
   */
  public Iterator<AVA> iterator()
  {
    return Iterators.arrayIterator(avas);
  }



  /**
   * Returns the number of AVAs in this RDN.
   *
   * @return The number of AVAs in this RDN.
   */
  public int size()
  {
    return avas.length;
  }



  /**
   * Returns the RFC 4514 string representation of this RDN.
   *
   * @return The RFC 4514 string representation of this RDN.
   * @see <a href="http://tools.ietf.org/html/rfc4514">RFC 4514 -
   *      Lightweight Directory Access Protocol (LDAP): String
   *      Representation of Distinguished Names </a>
   */
  public String toString()
  {
    // We don't care about potential race conditions here.
    if (stringValue == null)
    {
      final StringBuilder builder = new StringBuilder();
      avas[0].toString(builder);
      for (int i = 1; i < avas.length; i++)
      {
        builder.append(',');
        avas[i].toString(builder);
      }
      stringValue = builder.toString();
    }
    return stringValue;
  }



  StringBuilder toNormalizedString(StringBuilder builder)
  {
    final int sz = avas.length;
    if (sz == 1)
    {
      return avas[0].toNormalizedString(builder);
    }
    else
    {
      // Need to sort the AVAs before comparing.
      final AVA[] a = new AVA[sz];
      System.arraycopy(avas, 0, a, 0, sz);
      Arrays.sort(a, ATV_COMPARATOR);

      a[0].toString(builder);
      for (int i = 1; i < sz; i++)
      {
        builder.append(',');
        a[i].toNormalizedString(builder);
      }

      return builder;
    }
  }



  StringBuilder toString(StringBuilder builder)
  {
    return builder.append(toString());
  }
}
