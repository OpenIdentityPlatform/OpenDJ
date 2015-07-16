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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;

import static org.forgerock.util.Reject.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class defines a DIT content rule, which defines the set of
 * allowed, required, and prohibited attributes for entries with a
 * given structural objectclass, and also indicates which auxiliary
 * classes that may be included in the entry.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class DITContentRule
       implements SchemaFileElement
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Indicates whether this content rule is declared "obsolete". */
  private final boolean isObsolete;

  /**
   * The set of additional name-value pairs associated with this
   * content rule definition.
   */
  private final Map<String,List<String>> extraProperties;

  /**
   * The set of names for this DIT content rule, in a mapping between
   * the all-lowercase form and the user-defined form.
   */
  private final Map<String,String> names;

  /** The structural objectclass for this DIT content rule. */
  private final ObjectClass structuralClass;

  /**
   * The set of auxiliary objectclasses that entries with this content
   * rule may contain, in a mapping between the objectclass and the
   * user-defined name for that class.
   */
  private final Set<ObjectClass> auxiliaryClasses;

  /** The set of optional attribute types for this DIT content rule. */
  private final Set<AttributeType> optionalAttributes;

  /** The set of prohibited attribute types for this DIT content rule. */
  private final Set<AttributeType> prohibitedAttributes;

  /** The set of required attribute types for this DIT content rule. */
  private final Set<AttributeType> requiredAttributes;

  /** The definition string used to create this DIT content rule. */
  private final String definition;

  /** The description for this DIT content rule. */
  private final String description;



  /**
   * Creates a new DIT content rule definition with the provided
   * information.
   *
   * @param  definition            The definition string used to
   *                               create this DIT content rule.  It
   *                               must not be {@code null}.
   * @param  structuralClass       The structural objectclass for this
   *                               DIT content rule.  It must not be
   *                               {@code null}.
   * @param  names                 The set of names that may be used
   *                               to reference this DIT content rule.
   * @param  description           The description for this DIT
   *                               content rule.
   * @param  auxiliaryClasses      The set of auxiliary classes for
   *                               this DIT content rule
   * @param  requiredAttributes    The set of required attribute types
   *                               for this DIT content rule.
   * @param  optionalAttributes    The set of optional attribute types
   *                               for this DIT content rule.
   * @param  prohibitedAttributes  The set of prohibited attribute
   *                               types for this DIT content rule.
   * @param  isObsolete            Indicates whether this DIT content
   *                               rule is declared "obsolete".
   * @param  extraProperties       A set of extra properties for this
   *                               DIT content rule.
   */
  public DITContentRule(String definition,
                        ObjectClass structuralClass,
                        Map<String,String> names, String description,
                        Set<ObjectClass> auxiliaryClasses,
                        Set<AttributeType> requiredAttributes,
                        Set<AttributeType> optionalAttributes,
                        Set<AttributeType> prohibitedAttributes,
                        boolean isObsolete,
                        Map<String,List<String>> extraProperties)
  {
    ifNull(definition, structuralClass);

    this.structuralClass = structuralClass;
    this.description     = description;
    this.isObsolete      = isObsolete;

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
        logger.traceException(e);

        defStr = definition;
      }

      this.definition = defStr;
    }
    else
    {
      this.definition = definition;
    }

    if (names == null || names.isEmpty())
    {
      this.names = new LinkedHashMap<>(0);
    }
    else
    {
      this.names = new LinkedHashMap<>(names);
    }

    if (auxiliaryClasses == null || auxiliaryClasses.isEmpty())
    {
      this.auxiliaryClasses = new LinkedHashSet<>(0);
    }
    else
    {
      this.auxiliaryClasses = new LinkedHashSet<>(auxiliaryClasses);
    }

    if (requiredAttributes == null || requiredAttributes.isEmpty())
    {
      this.requiredAttributes = new LinkedHashSet<>(0);
    }
    else
    {
      this.requiredAttributes = new LinkedHashSet<>(requiredAttributes);
    }

    if (optionalAttributes == null || optionalAttributes.isEmpty())
    {
      this.optionalAttributes = new LinkedHashSet<>(0);
    }
    else
    {
      this.optionalAttributes = new LinkedHashSet<>(optionalAttributes);
    }

    if (prohibitedAttributes == null || prohibitedAttributes.isEmpty())
    {
      this.prohibitedAttributes = new LinkedHashSet<>(0);
    }
    else
    {
      this.prohibitedAttributes = new LinkedHashSet<>(prohibitedAttributes);
    }

    if (extraProperties == null || extraProperties.isEmpty())
    {
      this.extraProperties = new LinkedHashMap<>(0);
    }
    else
    {
      this.extraProperties = new LinkedHashMap<>(extraProperties);
    }
  }



  /**
   * Retrieves the structural objectclass for this DIT content rule.
   *
   * @return  The structural objectclass for this DIT content rule.
   */
  public ObjectClass getStructuralClass()
  {
    return structuralClass;
  }



  /**
   * Retrieves the set of names that may be used to reference this DIT
   * content rule.  The returned object will be a mapping between each
   * name in all lowercase characters and that name in a user-defined
   * form (which may include mixed capitalization).
   *
   * @return  The set of names that may be used to reference this DIT
   *          content rule.
   */
  public Map<String,String> getNames()
  {
    return names;
  }



  /**
   * Retrieves the primary name to use to reference this DIT content
   * rule.
   *
   * @return  The primary name to use to reference this DIT content
   *          rule, or {@code null} if there is none.
   */
  public String getNameOrOID()
  {
    if (names.isEmpty())
    {
      return null;
    }
    else
    {
      return names.values().iterator().next();
    }
  }



  /**
   * Indicates whether the provided lowercase name may be used to
   * reference this DIT content rule.
   *
   * @param  lowerName  The name for which to make the determination,
   *                    in all lowercase characters.
   *
   * @return  {@code true} if the provided lowercase name may be used
   *          to reference this DIT content rule, or {@code false} if
   *          not.
   */
  public boolean hasName(String lowerName)
  {
    return names.containsKey(lowerName);
  }



  /**
   * Retrieves the set of auxiliary objectclasses that may be used for
   * entries associated with this DIT content rule.
   *
   * @return  The set of auxiliary objectclasses that may be used for
   *          entries associated with this DIT content rule.
   */
  public Set<ObjectClass> getAuxiliaryClasses()
  {
    return auxiliaryClasses;
  }



  /**
   * Retrieves the set of required attributes for this DIT content
   * rule.
   *
   * @return  The set of required attributes for this DIT content
   *          rule.
   */
  public Set<AttributeType> getRequiredAttributes()
  {
    return requiredAttributes;
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * required attribute list for this DIT content rule.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  {@code true} if the provided attribute type is required
   *          by this DIT content rule, or {@code false} if not.
   */
  public boolean isRequired(AttributeType attributeType)
  {
    return requiredAttributes.contains(attributeType);
  }



  /**
   * Retrieves the set of optional attributes for this DIT content
   * rule.
   *
   * @return  The set of optional attributes for this DIT content
   *          rule.
   */
  public Set<AttributeType> getOptionalAttributes()
  {
    return optionalAttributes;
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * optional attribute list for this DIT content rule.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  {@code true} if the provided attribute type is optional
   *          for this DIT content rule, or {@code false} if not.
   */
  public boolean isOptional(AttributeType attributeType)
  {
    return optionalAttributes.contains(attributeType);
  }



  /**
   * Indicates whether the provided attribute type is in the list of
   * required or optional attributes for this DIT content rule.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  {@code true} if the provided attribute type is required
   *          or allowed for this DIT content rule, or {@code false}
   *          if it is not.
   */
  public boolean isRequiredOrOptional(AttributeType attributeType)
  {
    return requiredAttributes.contains(attributeType) ||
            optionalAttributes.contains(attributeType);
  }



  /**
   * Retrieves the set of prohibited attributes for this DIT content
   * rule.
   *
   * @return  The set of prohibited attributes for this DIT content
   *          rule.
   */
  public Set<AttributeType> getProhibitedAttributes()
  {
    return prohibitedAttributes;
  }


  /**
   * Indicates whether this DIT content rule is declared "obsolete".
   *
   * @return  {@code true} if this DIT content rule is declared
   *          "obsolete", or {@code false} if it is not.
   */
  public boolean isObsolete()
  {
    return isObsolete;
  }



  /**
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this DIT content rule and
   * the value for that property.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this DIT content
   *          rule and the value for that property.
   */
  @Override
  public Map<String,List<String>> getExtraProperties()
  {
    return extraProperties;
  }



  /**
   * Indicates whether the provided object is equal to this DIT
   * content rule.  The object will be considered equal if it is a DIT
   * content rule for the same structural objectclass and the same
   * sets of names.  For performance reasons, the set of auxiliary
   * classes, and the sets of required, optional, and prohibited
   * attribute types will not be checked, so that should be done
   * manually if a more thorough equality comparison is required.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if the provided object is equal to
   *          this DIT content rule, or {@code false} if not.
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (!(o instanceof DITContentRule))
    {
      return false;
    }

    DITContentRule dcr = (DITContentRule) o;
    if (!structuralClass.equals(dcr.structuralClass))
    {
      return false;
    }

    if (names.size() != dcr.names.size())
    {
      return false;
    }

    Iterator<String> iterator = names.keySet().iterator();
    while (iterator.hasNext())
    {
      if (! dcr.names.containsKey(iterator.next()))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Retrieves the hash code for this DIT content rule.  It will be
   * equal to the hash code for the associated structural objectclass.
   *
   * @return  The hash code for this DIT content rule.
   */
  @Override
  public int hashCode()
  {
    return structuralClass.hashCode();
  }



  /**
   * Retrieves the string representation of this DIT content rule in
   * the form specified in RFC 2252.
   *
   * @return  The string representation of this DIT content rule in
   *          the form specified in RFC 2252.
   */
  @Override
  public String toString()
  {
    return definition;
  }

}
