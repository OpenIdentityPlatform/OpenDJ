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



import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.Collections;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.NameForm;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ResultCode;



/**
 * This class defines the objectIdentifierMatch matching rule defined in X.520
 * and referenced in RFC 2252.  This expects to work on OIDs and will match
 * either an attribute/objectclass name or a numeric OID.
 */
class ObjectIdentifierEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * Creates a new instance of this objectIdentifierMatch matching rule.
   */
  public ObjectIdentifierEqualityMatchingRule()
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
    return EMR_OID_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_OID_OID;
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
    String lowerValue = buffer.toString();

    // Normalize OIDs into schema names, and secondary schema names into
    // primary schema names.

    String schemaName = null;

    AttributeType attributeType = DirectoryServer.getAttributeType(lowerValue);
    if (attributeType != null)
    {
      schemaName = attributeType.getNameOrOID();
    }

    if (schemaName == null)
    {
      ObjectClass objectClass = DirectoryServer.getObjectClass(lowerValue);
      if (objectClass != null)
      {
        schemaName = objectClass.getNameOrOID();
      }
    }

    if (schemaName == null)
    {
      MatchingRule matchingRule = DirectoryServer.getMatchingRule(lowerValue);
      if (matchingRule != null)
      {
        schemaName = matchingRule.getNameOrOID();
      }
    }

    if (schemaName == null)
    {
      NameForm nameForm = DirectoryServer.getNameForm(lowerValue);
      if (nameForm != null)
      {
        schemaName = nameForm.getNameOrOID();
      }
    }

    if (schemaName != null)
    {
      return ByteString.valueOf(toLowerCase(schemaName));
    }

    // There were no schema matches so we must check the syntax.
    switch (DirectoryServer.getSyntaxEnforcementPolicy())
    {
      case REJECT:
        MessageBuilder invalidReason = new MessageBuilder();
        if (isValidSchemaElement(lowerValue, 0, lowerValue.length(),
                                invalidReason))
        {
          return ByteString.valueOf(lowerValue);
        }
        else
        {
          Message message = ERR_ATTR_SYNTAX_OID_INVALID_VALUE.get(
              lowerValue, invalidReason.toString());
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }

      case WARN:
        invalidReason = new MessageBuilder();
        if (! isValidSchemaElement(lowerValue, 0, lowerValue.length(),
                                   invalidReason))
        {
          Message message = ERR_ATTR_SYNTAX_OID_INVALID_VALUE.get(
              lowerValue, invalidReason.toString());
          ErrorLogger.logError(message);
        }

        return ByteString.valueOf(lowerValue);

      default:
        return ByteString.valueOf(lowerValue);
    }
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
    // First, compare the normalized values to see if they are the same.
    if (value1.equals(value2))
    {
      return true;
    }


    // The following code implies that the normalized values cannot be
    // compared byte-for-byte, which would require that the generateHashCode
    // method of EqualityMatchingRule be overridden to avoid using the
    // normalized value.  Instead, values are now normalized such that they
    // can be compared byte-for-byte.  There are still some rare cases where
    // comparison fails.  For example, say there is an object class with primary
    // name "a" and secondary name "b", and there is also an attribute type with
    // primary name "b".  In this case comparing "a" with "b" returns false even
    // though the two values are equivalent in an object class context.

/*
    // It is possible that they are different names referring to the same
    // schema element.  See if we can find a case where that is true in the
    // server configuration for all of the following schema element types:
    // - Attribute Types
    // - Objectclasses
    // - Attribute syntaxes
    // - Matching Rules
    // - Name Forms
    String valueStr1 = value1.stringValue();
    AttributeType attrType1 = DirectoryServer.getAttributeType(valueStr1);
    if (attrType1 != null)
    {
      String valueStr2 = value2.stringValue();
      AttributeType attrType2 = DirectoryServer.getAttributeType(valueStr2);
      if (attrType2 == null)
      {
        return false;
      }
      else
      {
        return attrType1.equals(attrType2);
      }
    }

    ObjectClass oc1 = DirectoryServer.getObjectClass(valueStr1);
    if (oc1 != null)
    {
      String valueStr2 = value2.stringValue();
      ObjectClass oc2 = DirectoryServer.getObjectClass(valueStr2);
      if (oc2 == null)
      {
        return false;
      }
      else
      {
        return oc1.equals(oc2);
      }
    }

    AttributeSyntax syntax1 = DirectoryServer.getAttributeSyntax(valueStr1,
                                                                 false);
    if (syntax1 != null)
    {
      String valueStr2 = value2.stringValue();
      AttributeSyntax syntax2 = DirectoryServer.getAttributeSyntax(valueStr2,
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


    MatchingRule mr1 = DirectoryServer.getMatchingRule(valueStr1);
    if (mr1 != null)
    {
      String valueStr2 = value2.stringValue();
      MatchingRule mr2 = DirectoryServer.getMatchingRule(valueStr2);
      if (mr2 == null)
      {
        return false;
      }
      else
      {
        return mr1.equals(mr2);
      }
    }


    NameForm nf1 = DirectoryServer.getNameForm(valueStr1);
    if (nf1 != null)
    {
      String valueStr2 = value2.stringValue();
      NameForm nf2 = DirectoryServer.getNameForm(valueStr2);
      if (nf2 == null)
      {
        return false;
      }
      else
      {
        return nf1.equals(nf2);
      }
    }
*/


    // If we've gotten here, then we've exhausted all reasonable checking and
    // we can't consider them equal.
    return false;
  }

}

