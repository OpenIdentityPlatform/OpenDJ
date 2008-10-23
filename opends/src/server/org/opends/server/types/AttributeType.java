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
package org.opends.server.types;
import org.opends.messages.Message;



import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.schema.AttributeTypeSyntax;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.Validator.*;



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
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class AttributeType
       extends CommonSchemaElements
       implements SchemaFileElement
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

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

  // Indicates whether this attribute type is operational.
  private final boolean isOperational;

  // Indicates whether this attribute type is declared "single-value".
  private final boolean isSingleValue;

  // Indicates whether there is a possibility that this attribute type
  // may have one or more subtypes that list this type or one of its
  // subtypes as a superior.  Note that this variable is intentional
  // not declared "final", but if it ever gets set to "true", then it
  // should never be unset back to "false".
  private boolean mayHaveSubordinateTypes;

  // The equality matching rule for this attribute type.
  private final EqualityMatchingRule equalityMatchingRule;

  // The ordering matching rule for this attribute type.
  private final OrderingMatchingRule orderingMatchingRule;

  // The definition string used to create this attribute type.
  private final String definition;

  // The OID for the associated syntax.
  private final String syntaxOID;

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
   * @param definition
   *          The definition string used to create this attribute
   *          type.  It must not be {@code null}.
   * @param primaryName
   *          The primary name for this attribute type, or
   *          <code>null</code> if there is no primary name.
   * @param typeNames
   *          The full set of names for this attribute type, or
   *          <code>null</code> if there are no names.
   * @param oid
   *          The OID for this attribute type.  It must not be
   *          {@code null}.
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
  public AttributeType(String definition, String primaryName,
                       Collection<String> typeNames,
                       String oid, String description,
                       AttributeType superiorType,
                       AttributeSyntax syntax,
                       AttributeUsage attributeUsage,
                       boolean isCollective,
                       boolean isNoUserModification,
                       boolean isObsolete, boolean isSingleValue)
  {
    this(definition, primaryName, typeNames, oid, description,
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
   * @param definition
   *          The definition string used to create this attribute
   *          type.  It must not be {@code null}.
   * @param primaryName
   *          The primary name for this attribute type, or
   *          <code>null</code> if there is no primary name.
   * @param typeNames
   *          The full set of names for this attribute type, or
   *          <code>null</code> if there are no names.
   * @param oid
   *          The OID for this attribute type.  It must not be
   *          {@code null}.
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
   */
  public AttributeType(String definition, String primaryName,
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
  {
    super(primaryName, typeNames, oid, description, isObsolete,
        extraProperties);


    ensureNotNull(definition, oid);

    this.superiorType = superiorType;
    this.isCollective = isCollective;
    this.isNoUserModification = isNoUserModification;
    this.isSingleValue = isSingleValue;

    mayHaveSubordinateTypes = false;

    int schemaFilePos = definition.indexOf(SCHEMA_PROPERTY_FILENAME);
    if (schemaFilePos > 0)
    {
      String defStr;
      try
      {
        int firstQuotePos = definition.indexOf('\'', schemaFilePos);
        int secondQuotePos = definition.indexOf('\'',
                                                firstQuotePos+1);

        defStr = definition.substring(0, schemaFilePos).trim() + " " +
                 definition.substring(secondQuotePos+1).trim();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        defStr = definition;
      }

      this.definition = defStr;
    }
    else
    {
      this.definition = definition;
    }

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
    syntaxOID = this.syntax.getOID();


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

    isOperational = this.attributeUsage.isOperational();
  }



  /**
   * Retrieves the definition string used to create this attribute
   * type.
   *
   * @return  The definition string used to create this attribute
   *          type.
   */
  public String getDefinition()
  {
    return definition;
  }

  /**
   * Retrieves the definition string used to create this attribute
   * type and including the X-SCHEMA-FILE extension.
   *
   * @return  The definition string used to create this attribute
   *          type including the X-SCHEMA-FILE extension.
   */
  public String getDefinitionWithFileName()
  {
    if (getSchemaFile() != null)
    {
      int pos = definition.lastIndexOf(')');
      String defStr = definition.substring(0, pos).trim() + " " +
                      SCHEMA_PROPERTY_FILENAME + " '" +
                      getSchemaFile() + "' )";
      return defStr;
    }
    else
      return definition;
  }

  /**
   * Creates a new instance of this attribute type based on the
   * definition string.  It will also preserve other state information
   * associated with this attribute type that is not included in the
   * definition string (e.g., the name of the schema file with which
   * it is associated).
   *
   * @return  The new instance of this attribute type based on the
   *          definition string.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to create a new attribute type
   *                              instance from the definition string.
   */
  public AttributeType recreateFromDefinition()
         throws DirectoryException
  {
    ByteString value  = ByteStringFactory.create(definition);
    Schema     schema = DirectoryServer.getSchema();

    AttributeType at =
         AttributeTypeSyntax.decodeAttributeType(value, schema,
                                              false);
    at.setSchemaFile(getSchemaFile());
    at.mayHaveSubordinateTypes = mayHaveSubordinateTypes;

    return at;
  }



  /**
   * Retrieves the superior type for this attribute type.
   *
   * @return  The superior type for this attribute type, or
   *          <CODE>null</CODE> if it does not have one.
   */
  public AttributeType getSuperiorType()
  {
    return superiorType;
  }



  /**
   * Indicates whether there is a possibility that this attribute type
   * may have one or more subordinate attribute types defined in the
   * server schema.  This is only intended for use by the
   * {@code org.opends.server.types.Entry} class for the purpose of
   * determining whether to check for subtypes when retrieving
   * attributes.  Note that it is possible for this method to report
   * false positives (if an attribute type that previously had one or
   * more subordinate types no longer has any), but not false
   * negatives.
   *
   * @return  {@code true} if the {@code hasSubordinateTypes} flag has
   *          been set for this attribute type at any time since
   *          startup, or {@code false} if not.
   */
  boolean mayHaveSubordinateTypes()
  {
    return mayHaveSubordinateTypes;
  }



  /**
   * Sets a flag indicating that this attribute type may have one or
   * more subordinate attribute types defined in the server schema.
   * This is only intended for use by the
   * {@code org.opends.server.types.Schema} class.
   */
  void setMayHaveSubordinateTypes()
  {
    mayHaveSubordinateTypes = true;
  }



  /**
   * Retrieves the syntax for this attribute type.
   *
   * @return  The syntax for this attribute type.
   */
  public AttributeSyntax getSyntax()
  {
    return syntax;
  }



  /**
   * Indicates whether this attribute sytax is a binary one.
   * @return  {@code true} if it is a binary syntax rule
   *          , or {@code false} if not.
   */
  public boolean isBinary()
  {
    return syntax.isBinary();
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
    return syntaxOID;
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
    return substringMatchingRule;
  }



  /**
   * Retrieves the usage indicator for this attribute type.
   *
   * @return  The usage indicator for this attribute type.
   */
  public AttributeUsage getUsage()
  {
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
    return isOperational;
  }



  /**
   * Indicates whether this attribute type is declared "collective".
   *
   * @return  <CODE>true</CODE> if this attribute type is declared
   * "collective", or <CODE>false</CODE> if not.
   */
  public boolean isCollective()
  {
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
    if (equalityMatchingRule == null)
    {
      Message message = ERR_ATTR_TYPE_NORMALIZE_NO_MR.get(
          String.valueOf(value), getNameOrOID());
      throw new DirectoryException(ResultCode.INAPPROPRIATE_MATCHING,
                                   message);
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      try
      {
        return value.getValue().hashCode();
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e2);
        }

        return 0;
      }
    }
  }



  /**
   * Appends a string representation of this schema definition's
   * non-generic properties to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  protected void toStringContent(StringBuilder buffer)
  {
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

