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
package org.opends.server.schema;



import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NameForm;
import org.opends.server.types.ObjectClass;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the objectIdentifierFirstComponentMatch matching rule
 * defined in X.520 and referenced in RFC 2252.  This rule is intended for use
 * with attributes whose values contain a set of parentheses enclosing a
 * space-delimited set of names and/or name-value pairs (like attribute type or
 * objectclass descriptions) in which the "first component" is the first item
 * after the opening parenthesis.
 */
public class ObjectIdentifierFirstComponentEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME = "org.opends.server.schema." +
       "ObjectIdentifierFirstComponentEqualityMatchingRule";



  /**
   * Creates a new instance of this integerFirstComponentMatch matching rule.
   */
  public ObjectIdentifierFirstComponentEqualityMatchingRule()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Initializes this matching rule based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this matching rule.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem that is not
   *                                   configuration-related occurs during
   *                                   initialization.
   */
  public void initializeMatchingRule(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeMatchingRule",
                      String.valueOf(configEntry));

    // No initialization is required.
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  public String getName()
  {
    assert debugEnter(CLASS_NAME, "getName");

    return EMR_OID_FIRST_COMPONENT_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public String getOID()
  {
    assert debugEnter(CLASS_NAME, "getOID");

    return EMR_OID_FIRST_COMPONENT_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    // There is no standard description for this matching rule.
    return null;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  public String getSyntaxOID()
  {
    assert debugEnter(CLASS_NAME, "getSyntaxOID");

    return SYNTAX_OID_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DirectoryException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  public ByteString normalizeValue(ByteString value)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "normalizeValue", String.valueOf(value));

    StringBuilder buffer = new StringBuilder();
    toLowerCase(value.value(), buffer, true);

    int bufferLength = buffer.length();
    if (bufferLength == 0)
    {
      if (value.value().length > 0)
      {
        // This should only happen if the value is composed entirely of spaces.
        // In that case, the normalized value is a single space.
        return new ASN1OctetString(" ");
      }
      else
      {
        // The value is empty, so it is already normalized.
        return new ASN1OctetString();
      }
    }


    // Replace any consecutive spaces with a single space.
    for (int pos = bufferLength-1; pos > 0; pos--)
    {
      if (buffer.charAt(pos) == ' ')
      {
        if (buffer.charAt(pos-1) == ' ')
        {
          buffer.delete(pos, pos+1);
        }
      }
    }

    return new ASN1OctetString(buffer.toString());
  }



  /**
   * Indicates whether the two provided normalized values are equal to each
   * other.
   *
   * @param  value1  The normalized form of the first value to compare.
   * @param  value2  The normalized form of the second value to compare.
   *
   * @return  <CODE>true</CODE> if the provided values are equal, or
   *          <CODE>false</CODE> if not.
   */
  public boolean areEqual(ByteString value1, ByteString value2)
  {
    assert debugEnter(CLASS_NAME, "areEqual", String.valueOf(value1),
                      String.valueOf(value2));


    // For this purpose, the first value will be considered the attribute value,
    // and the second the assertion value.  The attribute value must start with
    // an open parenthesis, followed by one or more spaces.
    String value1String = value1.stringValue();
    int    value1Length = value1String.length();

    if ((value1Length == 0) || (value1String.charAt(0) != '('))
    {
      // They cannot be equal if the attribute value is empty or doesn't start
      // with an open parenthesis.
      return false;
    }

    char c;
    int  pos = 1;
    while ((pos < value1Length) && ((c = value1String.charAt(pos)) == ' '))
    {
      pos++;
    }

    if (pos >= value1Length)
    {
      // We hit the end of the value before finding a non-space character.
      return false;
    }


    // The current position must be the start position for the value.  Keep
    // reading until we find the next space.
    int startPos = pos++;
    while ((pos < value1Length) && ((c = value1String.charAt(pos)) != ' '))
    {
      pos++;
    }

    if (pos >= value1Length)
    {
      // We hit the end of the value before finding the next space.
      return false;
    }


    // Grab the substring between the start pos and the current pos.  If it is
    // equal to the string representation of the second value, then we have a
    // match.
    String oid          = value1String.substring(startPos, pos);
    String value2String = value2.stringValue();
    if (oid.equals(value2String))
    {
      return true;
    }


    // Just because the two values did not match doesn't mean it's a total
    // waste.  See if the OID refers to a known element of any of the following
    // types that can also be referred to by the name or OID of the second
    // value:
    // - Attribute types
    // - Objectclasses
    // - Attribute Syntax
    // - Matching Rule
    // - Name Form

    AttributeType attrType1 = DirectoryServer.getAttributeType(oid);
    if (attrType1 != null)
    {
      AttributeType attrType2 = DirectoryServer.getAttributeType(value2String);
      if (attrType2 == null)
      {
        return false;
      }
      else
      {
        return attrType1.equals(attrType2);
      }
    }

    ObjectClass oc1 = DirectoryServer.getObjectClass(oid);
    if (oc1 != null)
    {
      ObjectClass oc2 = DirectoryServer.getObjectClass(value2String);
      if (oc2 == null)
      {
        return false;
      }
      else
      {
        return oc1.equals(oc2);
      }
    }

    AttributeSyntax syntax1 = DirectoryServer.getAttributeSyntax(oid, false);
    if (syntax1 != null)
    {
      AttributeSyntax syntax2 = DirectoryServer.getAttributeSyntax(value2String,
                                                                   false);
      if (syntax2 == null)
      {
        return false;
      }
      else
      {
        return syntax1.equals(syntax2);
      }
    }

    MatchingRule mr1 = DirectoryServer.getMatchingRule(oid);
    if (mr1 != null)
    {
      MatchingRule mr2 = DirectoryServer.getMatchingRule(value2String);
      if (mr2 == null)
      {
        return false;
      }
      else
      {
        return mr1.equals(mr2);
      }
    }

    NameForm nf1 = DirectoryServer.getNameForm(oid);
    if (nf1 != null)
    {
      NameForm nf2 = DirectoryServer.getNameForm(value2String);
      if (nf2 == null)
      {
        return false;
      }
      else
      {
        return nf1.equals(nf2);
      }
    }


    // At this point, we're out of things to try so it's not a match.
    return false;
  }



  /**
   * Generates a hash code for the provided attribute value.  This version of
   * the method will simply create a hash code from the normalized form of the
   * attribute value.  For matching rules explicitly designed to work in cases
   * where byte-for-byte comparisons of normalized values is not sufficient for
   * determining equality (e.g., if the associated attribute syntax is based on
   * hashed or encrypted values), then this method must be overridden to provide
   * an appropriate implementation for that case.
   *
   * @param  attributeValue  The attribute value for which to generate the hash
   *                         code.
   *
   * @return  The hash code generated for the provided attribute value.*/
  public int generateHashCode(AttributeValue attributeValue)
  {
    assert debugEnter(CLASS_NAME, "generateHashCode",
                      String.valueOf(attributeValue));

    // In this case, we'll always return the same value because the matching
    // isn't based on the entire value.
    return 1;
  }
}

