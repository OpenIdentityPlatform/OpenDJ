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
package org.opends.server.schema;



import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.Collections;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.NameForm;
import org.opends.server.types.ObjectClass;
import org.opends.server.util.ServerConstants;



/**
 * This class implements the objectIdentifierFirstComponentMatch matching rule
 * defined in X.520 and referenced in RFC 2252.  This rule is intended for use
 * with attributes whose values contain a set of parentheses enclosing a
 * space-delimited set of names and/or name-value pairs (like attribute type or
 * objectclass descriptions) in which the "first component" is the first item
 * after the opening parenthesis.
 */
class ObjectIdentifierFirstComponentEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * Creates a new instance of this integerFirstComponentMatch matching rule.
   */
  public ObjectIdentifierFirstComponentEqualityMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getAllNames()
  {
    return Collections.singleton(getName());
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  @Override
  public String getName()
  {
    return EMR_OID_FIRST_COMPONENT_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_OID_FIRST_COMPONENT_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  @Override
  public String getDescription()
  {
    // There is no standard description for this matching rule.
    return null;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  @Override
  public String getSyntaxOID()
  {
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
  @Override
  public ByteString normalizeValue(ByteSequence value)
         throws DirectoryException
  {
    StringBuilder buffer = new StringBuilder();
    toLowerCase(value, buffer, true);

    int bufferLength = buffer.length();
    if (bufferLength == 0)
    {
      if (value.length() > 0)
      {
        // This should only happen if the value is composed entirely of spaces.
        // In that case, the normalized value is a single space.
        return ServerConstants.SINGLE_SPACE_VALUE;
      }
      else
      {
        // The value is empty, so it is already normalized.
        return ByteString.empty();
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

    return ByteString.valueOf(buffer.toString());
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
  @Override
  public boolean areEqual(ByteSequence value1, ByteSequence value2)
  {
    // For this purpose, the first value will be considered the attribute value,
    // and the second the assertion value.  The attribute value must start with
    // an open parenthesis, followed by one or more spaces.
    String value1String = value1.toString();
    int    value1Length = value1String.length();

    if ((value1Length == 0) || (value1String.charAt(0) != '('))
    {
      // They cannot be equal if the attribute value is empty or doesn't start
      // with an open parenthesis.
      return false;
    }

    int  pos = 1;
    while ((pos < value1Length) && ((value1String.charAt(pos)) == ' '))
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
    while ((pos < value1Length) && ((value1String.charAt(pos)) != ' '))
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
    String value2String = value2.toString();
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
  @Override
  public int generateHashCode(ByteSequence attributeValue)
  {
    // In this case, we'll always return the same value because the matching
    // isn't based on the entire value.
    return 1;
  }
}

