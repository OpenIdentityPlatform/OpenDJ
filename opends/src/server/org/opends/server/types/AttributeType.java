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



import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines a data structure for storing and interacting
 * with an attribute type, which contains information about the format
 * of an attribute and the syntax and matching rules that should be
 * used when interacting with it.
 * <p>
 * Any methods which accesses the set of names associated with this
 * attribute type, will retrieve the primary name as the first name,
 * regardless of whether or not it was contained in the original set
 * of <code>names</code> passed to the constructor.
 * <p>
 * Where ordered sets of names, or extra properties are provided, the
 * ordering will be preserved when the associated fields are accessed
 * via their getters or via the {@link #toString()} methods.
 */
public final class AttributeType extends CommonSchemaElements
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.AttributeType";



  // The approximate matching rule for this attribute type.
  private final ApproximateMatchingRule approximateMatchingRule;

  // The syntax for this attribute type.
  private final AttributeSyntax syntax;

  // The superior attribute type from which this attribute type
  // inherits.
  private final AttributeType superiorType;

  // The attribute usage for this attribute type.
  private final AttributeUsage attributeUsage;

  // Indicates whether this attribute type is declared "collective".
  private final boolean isCollective;

  // Indicates whether this attribute type is declared
  // "no-user-modification".
  private final boolean isNoUserModification;

  // Indicates whether this attribute type is the objectclass type.
  private final boolean isObjectClassType;

  // Indicates whether this attribute type is declared "single-value".
  private final boolean isSingleValue;

  // The equality matching rule for this attribute type.
  private final EqualityMatchingRule equalityMatchingRule;

  // The ordering matching rule for this attribute type.
  private final OrderingMatchingRule orderingMatchingRule;

  // The substring matching rule for this attribute type.
  private final SubstringMatchingRule substringMatchingRule;



  /**
   * Creates a new attribute type with the provided information.
   * <p>
   * If no <code>primaryName</code> is specified, but a set of
   * <code>names</code> is specified, then the first name retrieved
   * from the set of <code>names</code> will be used as the primary
   * name.
   *
   * @param primaryName
   *          The primary name for this attribute type, or
   *          <code>null</code> if there is no primary name.
   * @param typeNames
   *          The full set of names for this attribute type, or
   *          <code>null</code> if there are no names.
   * @param oid
   *          The OID for this attribute type (must not be
   *          <code>null</code>).
   * @param description
   *          The description for the attribute type, or
   *          <code>null</code> if there is no description.
   * @param superiorType
   *          The reference to the superior type for this attribute
   *          type, or <code>null</code> if there is no superior
   *          type.
   * @param syntax
   *          The syntax for this attribute type, or <code>null</code>
   *          if there is no syntax.
   * @param attributeUsage
   *          The attribute usage for this attribute type, or
   *          <code>null</code> to default to user applications.
   * @param isCollective
   *          Indicates whether this attribute type is declared
   *          "collective".
   * @param isNoUserModification
   *          Indicates whether this attribute type is declared
   *          "no-user-modification".
   * @param isObsolete
   *          Indicates whether this attribute type is declared
   *          "obsolete".
   * @param isSingleValue
   *          Indicates whether this attribute type is declared
   *          "single-value".
   */
  public AttributeType(String primaryName,
                       Collection<String> typeNames,
                       String oid, String description,
                       AttributeType superiorType,
                       AttributeSyntax syntax,
                       AttributeUsage attributeUsage,
                       boolean isCollective,
                       boolean isNoUserModification,
                       boolean isObsolete, boolean isSingleValue)
  {
    this(primaryName, typeNames, oid, description,
        superiorType, syntax, null, null, null,
        null, attributeUsage, isCollective,
        isNoUserModification, isObsolete, isSingleValue, null);
  }



  /**
   * Creates a new attribute type with the provided information.
   * <p>
   * If no <code>primaryName</code> is specified, but a set of
   * <code>names</code> is specified, then the first name retrieved
   * from the set of <code>names</code> will be used as the primary
   * name.
   *
   * @param primaryName
   *          The primary name for this attribute type, or
   *          <code>null</code> if there is no primary name.
   * @param typeNames
   *          The full set of names for this attribute type, or
   *          <code>null</code> if there are no names.
   * @param oid
   *          The OID for this attribute type (must not be
   *          <code>null</code>).
   * @param description
   *          The description for the attribute type, or
   *          <code>null</code> if there is no description.
   * @param superiorType
   *          The reference to the superior type for this attribute
   *          type, or <code>null</code> if there is no superior
   *          type.
   * @param syntax
   *          The syntax for this attribute type, or <code>null</code>
   *          if there is no syntax.
   * @param approximateMatchingRule
   *          The approximate matching rule for this attribute type,
   *          or <code>null</code> if there is no rule.
   * @param equalityMatchingRule
   *          The equality matching rule for this attribute type, or
   *          <code>null</code> if there is no rule.
   * @param orderingMatchingRule
   *          The ordering matching rule for this attribute type, or
   *          <code>null</code> if there is no rule.
   * @param substringMatchingRule
   *          The substring matching rule for this attribute type, or
   *          <code>null</code> if there is no rule.
   * @param attributeUsage
   *          The attribute usage for this attribute type, or
   *          <code>null</code> to default to user applications.
   * @param isCollective
   *          Indicates whether this attribute type is declared
   *          "collective".
   * @param isNoUserModification
   *          Indicates whether this attribute type is declared
   *          "no-user-modification".
   * @param isObsolete
   *          Indicates whether this attribute type is declared
   *          "obsolete".
   * @param isSingleValue
   *          Indicates whether this attribute type is declared
   *          "single-value".
   * @param extraProperties
   *          A set of extra properties for this attribute type, or
   *          <code>null</code> if there are no extra properties.
   * @throws NullPointerException
   *           If the provided OID was <code>null</code>.
   */
  public AttributeType(String primaryName,
                       Collection<String> typeNames,
                       String oid, String description,
                       AttributeType superiorType,
                       AttributeSyntax syntax,
                       ApproximateMatchingRule
                            approximateMatchingRule,
                       EqualityMatchingRule equalityMatchingRule,
                       OrderingMatchingRule orderingMatchingRule,
                       SubstringMatchingRule substringMatchingRule,
                       AttributeUsage attributeUsage,
                       boolean isCollective,
                       boolean isNoUserModification,
                       boolean isObsolete, boolean isSingleValue,
                       Map<String,List<String>> extraProperties)
                       throws NullPointerException
  {
    super(primaryName, typeNames, oid, description, isObsolete,
        extraProperties);

    assert debugConstructor(CLASS_NAME,String.valueOf(primaryName),
                              String.valueOf(typeNames),
                              String.valueOf(oid),
                              String.valueOf(description),
                              String.valueOf(superiorType),
                              String.valueOf(syntax),
                              String.valueOf(approximateMatchingRule),
                              String.valueOf(equalityMatchingRule),
                              String.valueOf(orderingMatchingRule),
                              String.valueOf(substringMatchingRule),
                              String.valueOf(attributeUsage),
                              String.valueOf(isCollective),
                              String.valueOf(isNoUserModification),
                              String.valueOf(isObsolete),
                              String.valueOf(isSingleValue),
                              String.valueOf(extraProperties));

    this.superiorType = superiorType;
    this.isCollective = isCollective;
    this.isNoUserModification = isNoUserModification;
    this.isSingleValue = isSingleValue;

    if (syntax == null)
    {
      if (superiorType != null)
      {
        this.syntax = superiorType.getSyntax();
      }
      else
      {
        this.syntax = DirectoryServer.getDefaultAttributeSyntax();
      }
    }
    else
    {
      this.syntax = syntax;
    }


    if (approximateMatchingRule == null)
    {
      this.approximateMatchingRule =
             this.syntax.getApproximateMatchingRule();
    }
    else
    {
      this.approximateMatchingRule = approximateMatchingRule;
    }


    if (equalityMatchingRule == null)
    {
      this.equalityMatchingRule =
        this.syntax.getEqualityMatchingRule();
    }
    else
    {
      this.equalityMatchingRule = equalityMatchingRule;
    }


    if (orderingMatchingRule == null)
    {
      this.orderingMatchingRule =
        this.syntax.getOrderingMatchingRule();
    }
    else
    {
      this.orderingMatchingRule = orderingMatchingRule;
    }


    if (substringMatchingRule == null)
    {
      this.substringMatchingRule =
        this.syntax.getSubstringMatchingRule();
    }
    else
    {
      this.substringMatchingRule = substringMatchingRule;
    }

    if (attributeUsage != null)
    {
      this.attributeUsage = attributeUsage;
    }
    else
    {
      this.attributeUsage = AttributeUsage.USER_APPLICATIONS;
    }

    if (oid.equals(OBJECTCLASS_ATTRIBUTE_TYPE_OID))
    {
      isObjectClassType = true;
    }
    else
    {
      isObjectClassType = hasName(OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
    }
  }



  /**
   * Retrieves the superior type for this attribute type.
   *
   * @return  The superior type for this attribute type, or
   *          <CODE>null</CODE> if it does not have one.
   */
  public AttributeType getSuperiorType()
  {
    assert debugEnter(CLASS_NAME, "getSuperiorType");

    return superiorType;
  }



  /**
   * Retrieves the syntax for this attribute type.
   *
   * @return  The syntax for this attribute type.
   */
  public AttributeSyntax getSyntax()
  {
    assert debugEnter(CLASS_NAME, "getSyntax");

    return syntax;
  }



  /**
   * Retrieves the OID for this syntax associated with this attribute
   * type.
   *
   * @return  The OID for this syntax associated with this attribute
   *          type.
   */
  public String getSyntaxOID()
  {
    assert debugEnter(CLASS_NAME, "getSyntaxOID");

    return syntax.getOID();
  }



  /**
   * Retrieves the matching rule that should be used for approximate
   * matching with this attribute type.
   *
   * @return  The matching rule that should be used for approximate
   *          matching with this attribute type.
   */
  public ApproximateMatchingRule getApproximateMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getApproximateMatchingRule");

    return approximateMatchingRule;
  }



  /**
   * Retrieves the matching rule that should be used for equality
   * matching with this attribute type.
   *
   * @return  The matching rule that should be used for equality
   *          matching with this attribute type.
   */
  public EqualityMatchingRule getEqualityMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getEqualityMatchingRule");

    return equalityMatchingRule;
  }



  /**
   * Retrieves the matching rule that should be used for ordering with
   * this attribute type.
   *
   * @return  The matching rule that should be used for ordering with
   *          this attribute type.
   */
  public OrderingMatchingRule getOrderingMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getOrderingMatchingRule");

    return orderingMatchingRule;
  }



  /**
   * Retrieves the matching rule that should be used for substring
   * matching with this attribute type.
   *
   * @return  The matching rule that should be used for substring
   *          matching with this attribute type.
   */
  public SubstringMatchingRule getSubstringMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getSubstringMatchingRule");

    return substringMatchingRule;
  }



  /**
   * Retrieves the usage indicator for this attribute type.
   *
   * @return  The usage indicator for this attribute type.
   */
  public AttributeUsage getUsage()
  {
    assert debugEnter(CLASS_NAME, "getUsage");

    return attributeUsage;
  }



  /**
   * Indicates whether this is an operational attribute.  An
   * operational attribute is one with a usage of
   * "directoryOperation", "distributedOperation", or "dSAOperation"
   * (i.e., only userApplications is not operational).
   *
   * @return  <CODE>true</CODE> if this is an operational attribute,
   *          or <CODE>false</CODE> if not.
   */
  public boolean isOperational()
  {
    assert debugEnter(CLASS_NAME, "isOperational");

    return attributeUsage.isOperational();
  }



  /**
   * Indicates whether this attribute type is declared "collective".
   *
   * @return  <CODE>true</CODE> if this attribute type is declared
   * "collective", or <CODE>false</CODE> if not.
   */
  public boolean isCollective()
  {
    assert debugEnter(CLASS_NAME, "isCollective");

    return isCollective;
  }



  /**
   * Indicates whether this attribute type is declared
   * "no-user-modification".
   *
   * @return  <CODE>true</CODE> if this attribute type is declared
   *          "no-user-modification", or <CODE>false</CODE> if not.
   */
  public boolean isNoUserModification()
  {
    assert debugEnter(CLASS_NAME, "isNoUserModification");

    return isNoUserModification;
  }



  /**
   * Indicates whether this attribute type is declared "single-value".
   *
   * @return  <CODE>true</CODE> if this attribute type is declared
   *          "single-value", or <CODE>false</CODE> if not.
   */
  public boolean isSingleValue()
  {
    assert debugEnter(CLASS_NAME, "isSingleValue");

    return isSingleValue;
  }



  /**
   * Indicates whether this attribute type represents the
   * "objectclass" attribute.  The determination will be made based on
   * the name and/or OID.
   *
   * @return  <CODE>true</CODE> if this attribute type is the
   *          objectclass type, or <CODE>false</CODE> if not.
   */
  public boolean isObjectClassType()
  {
    assert debugEnter(CLASS_NAME, "isObjectClassType");

    return isObjectClassType;
  }



  /**
   * Attempts to normalize the provided value using the equality
   * matching rule associated with this attribute type.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized form of the provided value.
   *
   * @throws  DirectoryException  If this attribute type does not have
   *                              an equality matching rule, or if the
   *                              provided value could not be
   *                              normalized.
   */
  public ByteString normalize(ByteString value)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "normalize", String.valueOf(value));

    if (equalityMatchingRule == null)
    {
      int    msgID   = MSGID_ATTR_TYPE_NORMALIZE_NO_MR;
      String message = getMessage(msgID, String.valueOf(value),
                                  getNameOrOID());
      throw new DirectoryException(ResultCode.INAPPROPRIATE_MATCHING,
                                   message, msgID);
    }

    return equalityMatchingRule.normalizeValue(value);
  }



  /**
   * Generates a hash code for the specified attribute value.  If an
   * equality matching rule is defined for this type, then it will be
   * used to generate the hash code.  If the value does not have an
   * equality matching rule but does have a normalized form, then that
   * will be used to obtain the hash code.  Otherwise, it will simply
   * be the hash code of the provided value.
   *
   * @param  value  The attribute value for which to generate the hash
   *                code.
   *
   * @return  The generated hash code for the provided value.
   */
  public int generateHashCode(AttributeValue value)
  {
    assert debugEnter(CLASS_NAME, "generateHashCode",
                      String.valueOf(value));

    try
    {
      if (equalityMatchingRule == null)
      {
        ByteString normalizedValue = value.getNormalizedValue();
        if (normalizedValue == null)
        {
          return value.getValue().hashCode();
        }
        else
        {
          return normalizedValue.hashCode();
        }
      }
      else
      {
        return equalityMatchingRule.generateHashCode(value);
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "generateHashCode", e);

      try
      {
        return value.getValue().hashCode();
      }
      catch (Exception e2)
      {
        assert debugException(CLASS_NAME, "generateHashCode", e2);

        return 0;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  protected void toStringContent(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toStringContent",
                      "java.lang.StringBuilder");

    if (superiorType != null)
    {
      buffer.append(" SUP ");
      buffer.append(superiorType.getNameOrOID());
    }

    if (equalityMatchingRule != null)
    {
      buffer.append(" EQUALITY ");
      buffer.append(equalityMatchingRule.getNameOrOID());
    }

    if (orderingMatchingRule != null)
    {
      buffer.append(" ORDERING ");
      buffer.append(orderingMatchingRule.getNameOrOID());
    }

    if (substringMatchingRule != null)
    {
      buffer.append(" SUBSTR ");
      buffer.append(substringMatchingRule.getNameOrOID());
    }

    // NOTE -- We will not include any approximate matching rule
    // information here because it would break the standard and
    // anything that depends on it.
    // FIXME -- Should we encode this into one of the "extra"
    // properties?

    if (syntax != null)
    {
      buffer.append(" SYNTAX ");
      buffer.append(syntax.getOID());
    }

    if (isSingleValue)
    {
      buffer.append(" SINGLE-VALUE");
    }

    if (isCollective)
    {
      buffer.append(" COLLECTIVE");
    }

    if (isNoUserModification)
    {
      buffer.append(" NO-USER-MODIFICATION");
    }

    if (attributeUsage != null)
    {
      buffer.append(" USAGE ");
      buffer.append(attributeUsage.toString());
    }
  }
}

