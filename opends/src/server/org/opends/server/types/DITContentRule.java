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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.server.schema.DITContentRuleSyntax;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.*;



/**
 * This class defines a DIT content rule, which defines the set of
 * allowed, required, and prohibited attributes for entries with a
 * given structural objectclass, and also indicates which auxiliary
 * classes that may be included in the entry.
 */
public final class DITContentRule
       implements SchemaFileElement
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.DITContentRule";



  // Indicates whether this content rule is declared "obsolete".
  private final boolean isObsolete;

  // The set of additional name-value pairs associated with this
  // content rule definition.
  private final Map<String,List<String>> extraProperties;

  // The set of names for this DIT content rule, in a mapping between
  // the all-lowercase form and the user-defined form.
  private final Map<String,String> names;

  // The structural objectclass for this DIT content rule.
  private final ObjectClass structuralClass;

  // The set of auxiliary objectclasses that entries with this content
  // rule may contain, in a mapping between the objectclass and the
  // user-defined name for that class.
  private final Set<ObjectClass> auxiliaryClasses;

  // The set of optional attribute types for this DIT content rule.
  private final Set<AttributeType> optionalAttributes;

  // The set of prohibited attribute types for this DIT content rule.
  private final Set<AttributeType> prohibitedAttributes;

  // The set of required attribute types for this DIT content rule.
  private final Set<AttributeType> requiredAttributes;

  // The definition string used to create this DIT content rule.
  private final String definition;

  // The description for this DIT content rule.
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
    assert debugConstructor(CLASS_NAME, String.valueOf(definition),
                            String.valueOf(structuralClass),
                            String.valueOf(names),
                            String.valueOf(description),
                            String.valueOf(auxiliaryClasses),
                            String.valueOf(requiredAttributes),
                            String.valueOf(optionalAttributes),
                            String.valueOf(prohibitedAttributes),
                            String.valueOf(isObsolete),
                            String.valueOf(extraProperties));

    ensureNotNull(definition, structuralClass);

    this.definition      = definition;
    this.structuralClass = structuralClass;
    this.description     = description;
    this.isObsolete      = isObsolete;

    if ((names == null) || names.isEmpty())
    {
      this.names = new LinkedHashMap<String,String>(0);
    }
    else
    {
      this.names = new LinkedHashMap<String,String>(names);
    }

    if ((auxiliaryClasses == null) || auxiliaryClasses.isEmpty())
    {
      this.auxiliaryClasses = new LinkedHashSet<ObjectClass>(0);
    }
    else
    {
      this.auxiliaryClasses =
           new LinkedHashSet<ObjectClass>(auxiliaryClasses);
    }

    if ((requiredAttributes == null) || requiredAttributes.isEmpty())
    {
      this.requiredAttributes = new LinkedHashSet<AttributeType>(0);
    }
    else
    {
      this.requiredAttributes =
           new LinkedHashSet<AttributeType>(requiredAttributes);
    }

    if ((optionalAttributes == null) || optionalAttributes.isEmpty())
    {
      this.optionalAttributes = new LinkedHashSet<AttributeType>(0);
    }
    else
    {
      this.optionalAttributes =
           new LinkedHashSet<AttributeType>(optionalAttributes);
    }

    if ((prohibitedAttributes == null) ||
        prohibitedAttributes.isEmpty())
    {
      this.prohibitedAttributes = new LinkedHashSet<AttributeType>(0);
    }
    else
    {
      this.prohibitedAttributes =
           new LinkedHashSet<AttributeType>(prohibitedAttributes);
    }

    if ((extraProperties == null) || extraProperties.isEmpty())
    {
      this.extraProperties =
           new LinkedHashMap<String,List<String>>(0);
    }
    else
    {
      this.extraProperties =
           new LinkedHashMap<String,List<String>>(extraProperties);
    }
  }



  /**
   * Retrieves the definition string used to create this DIT content
   * rule.
   *
   * @return  The definition string used to create this DIT content
   *          rule.
   */
  public String getDefinition()
  {
    assert debugEnter(CLASS_NAME, "getDefinition");

    return definition;
  }



  /**
   * Creates a new instance of this DIT content rule based on the
   * definition string.  It will also preserve other state information
   * associated with this DIT content rule that is not included in the
   * definition string (e.g., the name of the schema file with which
   * it is associated).
   *
   * @return  The new instance of this DIT content rule based on the
   *          definition string.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to create a new DIT content rule
   *                              instance from the definition string.
   */
  public DITContentRule recreateFromDefinition()
         throws DirectoryException
  {
    ByteString value  = ByteStringFactory.create(definition);
    Schema     schema = DirectoryConfig.getSchema();

    DITContentRule dcr =
         DITContentRuleSyntax.decodeDITContentRule(value, schema);
    dcr.setSchemaFile(getSchemaFile());

    return dcr;
  }



  /**
   * Retrieves the structural objectclass for this DIT content rule.
   *
   * @return  The structural objectclass for this DIT content rule.
   */
  public ObjectClass getStructuralClass()
  {
    assert debugEnter(CLASS_NAME, "getStructuralClass");

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
    assert debugEnter(CLASS_NAME, "getNames");

    return names;
  }



  /**
   * Retrieves the primary name to use to reference this DIT content
   * rule.
   *
   * @return  The primary name to use to reference this DIT content
   *          rule, or {@code null} if there is none.
   */
  public String getName()
  {
    assert debugEnter(CLASS_NAME, "getName");

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
    assert debugEnter(CLASS_NAME, "hasName",
                      String.valueOf(lowerName));

    return names.containsKey(lowerName);
  }



  /**
   * Retrieves the name of the schema file that contains the
   * definition for this DIT content rule.
   *
   * @return  The name of the schema file that contains the definition
   *          for this DIT content rule, or {@code null} if it is not
   *          known or if it is not stored in any schema file.
   */
  public String getSchemaFile()
  {
    assert debugEnter(CLASS_NAME, "getSchemaFile");

    List<String> values =
         extraProperties.get(SCHEMA_PROPERTY_FILENAME);
    if ((values == null) || values.isEmpty())
    {
      return null;
    }

    return values.get(0);
  }



  /**
   * Specifies the name of the schema file that contains the
   * definition for this DIT content rule.
   *
   * @param  schemaFile  The name of the schema file that contains the
   *                     definition for this DIT content rule.
   */
  public void setSchemaFile(String schemaFile)
  {
    assert debugEnter(CLASS_NAME, "setSchemaFile",
                      String.valueOf(schemaFile));

    setExtraProperty(SCHEMA_PROPERTY_FILENAME, schemaFile);
  }



  /**
   * Retrieves the description for this DIT content rule.
   *
   * @return  The description for this DIT content rule, or
   *          {@code null} if there is none.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    return description;
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
    assert debugEnter(CLASS_NAME, "getAuxiliaryClasses");

    return auxiliaryClasses;
  }



  /**
   * Indicates whether the provided auxiliary objectclass is allowed
   * for use by this DIT content rule.
   *
   * @param  auxiliaryClass  The auxiliary objectclass for which to
   *                         make the determination.
   *
   * @return  {@code true} if the provided auxiliary objectclass is
   *          allowed for use by this DIT content rule, or
   *          {@code false} if not.
   */
  public boolean isAllowedAuxiliaryClass(ObjectClass auxiliaryClass)
  {
    assert debugEnter(CLASS_NAME, "isAllowedAuxiliaryClass");

    return auxiliaryClasses.contains(auxiliaryClass);
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
    assert debugEnter(CLASS_NAME, "getRequiredAttributes");

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
    assert debugEnter(CLASS_NAME, "isRequired",
                      String.valueOf(attributeType));

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
    assert debugEnter(CLASS_NAME, "getOptionalAttributes");

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
    assert debugEnter(CLASS_NAME, "isOptional",
                      String.valueOf(attributeType));

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
    assert debugEnter(CLASS_NAME, "isRequiredOrOptional",
                      String.valueOf(attributeType));

    return (requiredAttributes.contains(attributeType) ||
            optionalAttributes.contains(attributeType));
  }



  /**
   * Indicates whether the provided attribute type is in the list of
   * required or optional attributes for this DIT content rule.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   * @param  acceptEmpty    Indicates whether an empty list of
   *                        required or optional attributes should be
   *                        taken to indicate that all attributes
   *                        allowed for an objectclass will be
   *                        acceptable.
   *
   * @return  {@code true} if the provided attribute type is required
   *          or allowed for this DIT content rule, or {@code false}
   *          if it is not.
   */
  public boolean isRequiredOrOptional(AttributeType attributeType,
                                      boolean acceptEmpty)
  {
    assert debugEnter(CLASS_NAME, "isRequiredOrOptional",
                      String.valueOf(attributeType));

    if (acceptEmpty &&
        (requiredAttributes.isEmpty() ||
         optionalAttributes.isEmpty()))
    {
      return true;
    }

    return (requiredAttributes.contains(attributeType) ||
            optionalAttributes.contains(attributeType));
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
    assert debugEnter(CLASS_NAME, "getProhibitedAttributes");

    return prohibitedAttributes;
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * prohibited attribute list for this DIT content rule.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  {@code true} if the provided attribute type is
   *          prohibited for this DIT content rule, or {@code false}
   *          if not.
   */
  public boolean isProhibited(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "isProhibited",
                      String.valueOf(attributeType));

    return prohibitedAttributes.contains(attributeType);
  }



  /**
   * Indicates whether this DIT content rule is declared "obsolete".
   *
   * @return  {@code true} if this DIT content rule is declared
   *          "obsolete", or {@code false} if it is not.
   */
  public boolean isObsolete()
  {
    assert debugEnter(CLASS_NAME, "isObsolete");

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
  public Map<String,List<String>> getExtraProperties()
  {
    assert debugEnter(CLASS_NAME, "getExtraProperties");

    return extraProperties;
  }



  /**
   * Retrieves the value of the specified "extra" property for this
   * DIT content rule.
   *
   * @param  propertyName  The name of the "extra" property for which
   *                       to retrieve the value.
   *
   * @return  The value of the specified "extra" property for this DIT
   *          content rule, or {@code null} if no such property is
   *          defined.
   */
  public List<String> getExtraProperty(String propertyName)
  {
    assert debugEnter(CLASS_NAME, "getExtraProperty",
                      String.valueOf(propertyName));

    return extraProperties.get(propertyName);
  }



  /**
   * Specifies the provided "extra" property for this DIT content
   * rule.
   *
   * @param  name   The name for the "extra" property.  It must not be
   *                {@code null}.
   * @param  value  The value for the "extra" property, or
   *                {@code null} if the property is to be removed.
   */
  public void setExtraProperty(String name, String value)
  {
    assert debugEnter(CLASS_NAME, "setExtraProperty",
                      String.valueOf(name), String.valueOf(value));

    ensureNotNull(name);

    if (value == null)
    {
      extraProperties.remove(name);
    }
    else
    {
      LinkedList<String> values = new LinkedList<String>();
      values.add(value);

      extraProperties.put(name, values);
    }
  }



  /**
   * Specifies the provided "extra" property for this DIT content
   * rule.
   *
   * @param  name    The name for the "extra" property.  It must not
   *                 be {@code null}.
   * @param  values  The set of value for the "extra" property, or
   *                 {@code null} if the property is to be removed.
   */
  public void setExtraProperty(String name, List<String> values)
  {
    assert debugEnter(CLASS_NAME, "setExtraProperty",
                      String.valueOf(name), String.valueOf(values));

    ensureNotNull(name);

    if ((values == null) || values.isEmpty())
    {
      extraProperties.remove(name);
    }
    else
    {
      LinkedList<String> valuesCopy = new LinkedList<String>(values);
      extraProperties.put(name, valuesCopy);
    }
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
  public boolean equals(Object o)
  {
    assert debugEnter(CLASS_NAME, "equals");

    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof DITContentRule)))
    {
      return false;
    }

    DITContentRule dcr = (DITContentRule) o;
    if (! structuralClass.equals(dcr.structuralClass))
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
  public int hashCode()
  {
    assert debugEnter(CLASS_NAME, "hashCode");

    return structuralClass.hashCode();
  }



  /**
   * Retrieves the string representation of this DIT content rule in
   * the form specified in RFC 2252.
   *
   * @return  The string representation of this DIT content rule in
   *          the form specified in RFC 2252.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder buffer = new StringBuilder();
    toString(buffer, true);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this attribute type in the
   * form specified in RFC 2252 to the provided buffer.
   *
   * @param  buffer              The buffer to which the information
   *                             should be appended.
   * @param  includeFileElement  Indicates whether to include an
   *                             "extra" property that specifies the
   *                             path to the schema file from which
   *                             this DIT content rule was loaded.
   */
  public void toString(StringBuilder buffer,
  boolean includeFileElement)
  {
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder",
                      String.valueOf(includeFileElement));

    buffer.append("( ");
    buffer.append(structuralClass.getOID());

    if (! names.isEmpty())
    {
      Iterator<String> iterator = names.values().iterator();

      String firstName = iterator.next();
      if (iterator.hasNext())
      {
        buffer.append(" NAME ( '");
        buffer.append(firstName);

        while (iterator.hasNext())
        {
          buffer.append("' '");
          buffer.append(iterator.next());
        }

        buffer.append("' )");
      }
      else
      {
        buffer.append(" NAME '");
        buffer.append(firstName);
        buffer.append("'");
      }
    }

    if ((description != null) && (description.length() > 0))
    {
      buffer.append(" DESC '");
      buffer.append(description);
      buffer.append("'");
    }

    if (isObsolete)
    {
      buffer.append(" OBSOLETE");
    }

    if (! auxiliaryClasses.isEmpty())
    {
      Iterator<ObjectClass> iterator = auxiliaryClasses.iterator();

      String firstClass = iterator.next().getNameOrOID();
      if (iterator.hasNext())
      {
        buffer.append(" AUX (");
        buffer.append(firstClass);

        while (iterator.hasNext())
        {
          buffer.append(" $ ");
          buffer.append(iterator.next());
        }

        buffer.append(" )");
      }
      else
      {
        buffer.append(" AUX ");
        buffer.append(firstClass);
      }
    }

    if (! requiredAttributes.isEmpty())
    {
      Iterator<AttributeType> iterator =
           requiredAttributes.iterator();

      String firstName = iterator.next().getNameOrOID();
      if (iterator.hasNext())
      {
        buffer.append(" MUST ( ");
        buffer.append(firstName);

        while (iterator.hasNext())
        {
          buffer.append(" $ ");
          buffer.append(iterator.next().getNameOrOID());
        }

        buffer.append(" )");
      }
      else
      {
        buffer.append(" MUST ");
        buffer.append(firstName);
      }
    }

    if (! optionalAttributes.isEmpty())
    {
      Iterator<AttributeType> iterator =
           optionalAttributes.iterator();

      String firstName = iterator.next().getNameOrOID();
      if (iterator.hasNext())
      {
        buffer.append(" MAY ( ");
        buffer.append(firstName);

        while (iterator.hasNext())
        {
          buffer.append(" $ ");
          buffer.append(iterator.next().getNameOrOID());
        }

        buffer.append(" )");
      }
      else
      {
        buffer.append(" MAY ");
        buffer.append(firstName);
      }
    }

    if (! prohibitedAttributes.isEmpty())
    {
      Iterator<AttributeType> iterator =
           prohibitedAttributes.iterator();

      String firstName = iterator.next().getNameOrOID();
      if (iterator.hasNext())
      {
        buffer.append(" NOT ( ");
        buffer.append(firstName);

        while (iterator.hasNext())
        {
          buffer.append(" $ ");
          buffer.append(iterator.next().getNameOrOID());
        }

        buffer.append(" )");
      }
      else
      {
        buffer.append(" NOT ");
        buffer.append(firstName);
      }
    }

    if (! extraProperties.isEmpty())
    {
      for (String property : extraProperties.keySet())
      {
        if ((! includeFileElement) &&
            property.equals(SCHEMA_PROPERTY_FILENAME))
        {
          continue;
        }

        List<String> valueList = extraProperties.get(property);

        buffer.append(" ");
        buffer.append(property);

        if (valueList.size() == 1)
        {
          buffer.append(" '");
          buffer.append(valueList.get(0));
          buffer.append("'");
        }
        else
        {
          buffer.append(" ( ");

          for (String value : valueList)
          {
            buffer.append("'");
            buffer.append(value);
            buffer.append("' ");
          }

          buffer.append(")");
        }
      }
    }

    buffer.append(" )");
  }
}

