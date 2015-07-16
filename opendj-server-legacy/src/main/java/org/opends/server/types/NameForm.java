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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;

import static org.forgerock.util.Reject.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class defines a data structure for storing and interacting
 * with a name form, which defines the attribute type(s) that must
 * and/or may be used in the RDN of an entry with a given structural
 * objectclass.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class NameForm
       implements SchemaFileElement
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Indicates whether this name form is declared "obsolete". */
  private final boolean isObsolete;

  /**
   * The set of additional name-value pairs associated with this name
   * form definition.
   */
  private final Map<String,List<String>> extraProperties;

  /**
   * The mapping between the lowercase names and the user-provided
   * names for this name form.
   */
  private final Map<String,String> names;

  /** The reference to the structural objectclass for this name form. */
  private final ObjectClass structuralClass;

  /** The set of optional attribute types for this name form. */
  private final Set<AttributeType> optionalAttributes;

  /** The set of required attribute types for this name form. */
  private final Set<AttributeType> requiredAttributes;

  /** The definition string used to create this name form. */
  private final String definition;

  /** The description for this name form. */
  private final String description;

  /** The OID for this name form. */
  private final String oid;



  /**
   * Creates a new name form definition with the provided information.
   *
   * @param  definition          The definition string used to create
   *                             this name form.  It must not be
   *                             {@code null}.
   * @param  names               The set of names that may be used to
   *                             reference this name form.
   * @param  oid                 The OID for this name form.  It must
   *                             not be {@code null}.
   * @param  description         The description for this name form.
   * @param  isObsolete          Indicates whether this name form is
   *                             declared "obsolete".
   * @param  structuralClass     The structural objectclass with which
   *                             this name form is associated.  It
   *                             must not be {@code null}.
   * @param  requiredAttributes  The set of required attribute types
   *                             for this name form.
   * @param  optionalAttributes  The set of optional attribute types
   *                             for this name form.
   * @param  extraProperties     A set of extra properties for this
   *                             name form.
   */
  public NameForm(String definition, Map<String,String> names,
                  String oid, String description, boolean isObsolete,
                  ObjectClass structuralClass,
                  Set<AttributeType> requiredAttributes,
                  Set<AttributeType> optionalAttributes,
                  Map<String,List<String>> extraProperties)
  {
    ifNull(definition, oid, structuralClass);

    this.oid             = oid;
    this.description     = description;
    this.isObsolete      = isObsolete;
    this.structuralClass = structuralClass;

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
   * Retrieves the set of names that may be used to reference this
   * name form.  The returned object will be a mapping between each
   * name in all lowercase characters and that name in a user-defined
   * form (which may include mixed capitalization).
   *
   * @return  The set of names that may be used to reference this
   *          name form.
   */
  public Map<String,String> getNames()
  {
    return names;
  }



  /**
   * Indicates whether the provided lowercase name may be used to
   * reference this name form.
   *
   * @param  lowerName  The name for which to make the determination,
   *                    in all lowercase characters.
   *
   * @return  {@code true} if the provided lowercase name may be used
   *          to reference this name form, or {@code false} if not.
   */
  public boolean hasName(String lowerName)
  {
    return names.containsKey(lowerName);
  }



  /**
   * Retrieves the OID for this name form.
   *
   * @return  The OID for this name form.
   */
  public String getOID()
  {
    return oid;
  }



  /**
   * Retrieves the name or OID that should be used to reference this
   * name form.  If at least one name is defined, then the first will
   * be returned.  Otherwise, the OID will be returned.
   *
   * @return  The name or OID that should be used to reference this
   *          name form.
   */
  public String getNameOrOID()
  {
    if (names.isEmpty())
    {
      return oid;
    }
    else
    {
      return names.values().iterator().next();
    }
  }



  /**
   * Indicates whether the provided lowercase value is equal to the
   * OID or any of the names that may be used to reference this name
   * form.
   *
   * @param  lowerValue  The value, in all lowercase characters, that
   *                     may be used to make the determination.
   *
   * @return  {@code true} if the provided lowercase value is one of
   *          the names or the OID of this name form, or {@code false}
   *          if it is not.
   */
  public boolean hasNameOrOID(String lowerValue)
  {
    return names.containsKey(lowerValue) || lowerValue.equals(oid);
  }



  /**
   * Retrieves the description for this name form.
   *
   * @return  The description for this name form, or {@code true} if
   *          there is none.
   */
  public String getDescription()
  {
    return description;
  }



  /**
   * Retrieves the reference to the structural objectclass for this
   * name form.
   *
   * @return  The reference to the structural objectclass for this
   *          name form.
   */
  public ObjectClass getStructuralClass()
  {
    return structuralClass;
  }



  /**
   * Retrieves the set of required attributes for this name form.
   *
   * @return  The set of required attributes for this name form.
   */
  public Set<AttributeType> getRequiredAttributes()
  {
    return requiredAttributes;
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * required attribute list for this name form.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  {@code true} if the provided attribute type is required
   *          by this name form, or {@code false} if not.
   */
  public boolean isRequired(AttributeType attributeType)
  {
    return requiredAttributes.contains(attributeType);
  }



  /**
   * Retrieves the set of optional attributes for this name form.
   *
   * @return  The set of optional attributes for this name form.
   */
  public Set<AttributeType> getOptionalAttributes()
  {
    return optionalAttributes;
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * optional attribute list for this name form.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  {@code true} if the provided attribute type is optional
   *          for this name form, or {@code false} if not.
   */
  public boolean isOptional(AttributeType attributeType)
  {
    return optionalAttributes.contains(attributeType);
  }



  /**
   * Indicates whether the provided attribute type is in the list of
   * required or optional attributes for this name form.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  {@code true} if the provided attribute type is required
   *          or optional for this name form, or {@code false} if it
   *          is not.
   */
  public boolean isRequiredOrOptional(AttributeType attributeType)
  {
    return requiredAttributes.contains(attributeType) ||
            optionalAttributes.contains(attributeType);
  }



  /**
   * Indicates whether this name form is declared "obsolete".
   *
   * @return  {@code true} if this name form is declared
   *          "obsolete", or {@code false} if it is not.
   */
  public boolean isObsolete()
  {
    return isObsolete;
  }



  /**
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this name form and the
   * value for that property.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this name form
   *          and the value for that property.
   */
  @Override
  public Map<String,List<String>> getExtraProperties()
  {
    return extraProperties;
  }



  /**
   * Indicates whether the provided object is equal to this name form.
   * The object will be considered equal if it is a name form with the
   * same OID as the current name form.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if the provided object is equal to this
   *          name form, or {@code true} if not.
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (!(o instanceof NameForm))
    {
      return false;
    }
    return oid.equals(((NameForm) o).oid);
  }



  /**
   * Retrieves the hash code for this name form.  It will be based on
   * the sum of the bytes of the OID.
   *
   * @return  The hash code for this name form.
   */
  @Override
  public int hashCode()
  {
    int oidLength = oid.length();
    int hashCode  = 0;
    for (int i=0; i < oidLength; i++)
    {
      hashCode += oid.charAt(i);
    }

    return hashCode;
  }



  /**
   * Retrieves the string representation of this name form in the form
   * specified in RFC 2252.
   *
   * @return  The string representation of this name form in the form
   *          specified in RFC 2252.
   */
  @Override
  public String toString()
  {
    return definition;
  }

}
