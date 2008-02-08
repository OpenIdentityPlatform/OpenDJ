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



import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.server.schema.NameFormSyntax;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.*;



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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Indicates whether this name form is declared "obsolete".
  private final boolean isObsolete;

  // The set of additional name-value pairs associated with this name
  // form definition.
  private final Map<String,List<String>> extraProperties;

  // The mapping between the lowercase names and the user-provided
  // names for this name form.
  private final Map<String,String> names;

  // The reference to the structural objectclass for this name form.
  private final ObjectClass structuralClass;

  // The set of optional attribute types for this name form.
  private final Set<AttributeType> optionalAttributes;

  // The set of required attribute types for this name form.
  private final Set<AttributeType> requiredAttributes;

  // The definition string used to create this name form.
  private final String definition;

  // The description for this name form.
  private final String description;

  // The OID for this name form.
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
    ensureNotNull(definition, oid, structuralClass);

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

    if ((names == null) || names.isEmpty())
    {
      this.names = new LinkedHashMap<String,String>(0);
    }
    else
    {
      this.names = new LinkedHashMap<String,String>(names);
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
   * Retrieves the definition string used to create this name form.
   *
   * @return  The definition string used to create this name form.
   */
  public String getDefinition()
  {
    return definition;
  }



  /**
   * Creates a new instance of this name form based on the definition
   * string.  It will also preserve other state information associated
   * with this name form that is not included in the definition string
   * (e.g., the name of the schema file with which it is associated).
   *
   * @return  The new instance of this name form based on the
   *          definition string.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to create a new name form instance
   *                              from the definition string.
   */
  public NameForm recreateFromDefinition()
         throws DirectoryException
  {
    ByteString value  = ByteStringFactory.create(definition);
    Schema     schema = DirectoryConfig.getSchema();

    NameForm nf = NameFormSyntax.decodeNameForm(value, schema, false);
    nf.setSchemaFile(getSchemaFile());

    return nf;
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
    if (names.containsKey(lowerValue))
    {
      return true;
    }

    return lowerValue.equals(oid);
  }



  /**
   * Retrieves the path to the schema file that contains the
   * definition for this name form.
   *
   * @return  The path to the schema file that contains the definition
   *          for this name form, or {@code null} if it is not known
   *          or if it is not stored in any schema file.
   */
  public String getSchemaFile()
  {
    List<String> values =
         extraProperties.get(SCHEMA_PROPERTY_FILENAME);
    if ((values == null) || values.isEmpty())
    {
      return null;
    }

    return values.get(0);
  }



  /**
   * Specifies the path to the schema file that contains the
   * definition for this name form.
   *
   * @param  schemaFile  The path to the schema file that contains the
   *                     definition for this name form.
   */
  public void setSchemaFile(String schemaFile)
  {
    setExtraProperty(SCHEMA_PROPERTY_FILENAME, schemaFile);
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
    return (requiredAttributes.contains(attributeType) ||
            optionalAttributes.contains(attributeType));
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
  public Map<String,List<String>> getExtraProperties()
  {
    return extraProperties;
  }



  /**
   * Retrieves the value of the specified "extra" property for this
   * name form.
   *
   * @param  propertyName  The name of the "extra" property for which
   *                       to retrieve the value.
   *
   * @return  The value of the specified "extra" property for this
   *          name form, or {@code null} if no such property is
   *          defined.
   */
  public List<String> getExtraProperty(String propertyName)
  {
    return extraProperties.get(propertyName);
  }



  /**
   * Specifies the provided "extra" property for this name form.
   *
   * @param  name   The name for the "extra" property.  It must not be
   *                {@code null}.
   * @param  value  The value for the "extra" property, or
   *                {@code null} if the property is to be removed.
   */
  public void setExtraProperty(String name, String value)
  {
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
   * Specifies the provided "extra" property for this name form.
   *
   * @param  name    The name for the "extra" property.  It must not
   *                 be {@code null}.
   * @param  values  The set of value for the "extra" property, or
   *                 {@code null} if the property is to be removed.
   */
  public void setExtraProperty(String name, List<String> values)
  {
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
   * Indicates whether the provided object is equal to this name form.
   * The object will be considered equal if it is a name form with the
   * same OID as the current name form.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if the provided object is equal to this
   *          name form, or {@code true} if not.
   */
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof NameForm)))
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
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer, true);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this name form in the form
   * specified in RFC 2252 to the provided buffer.
   *
   * @param  buffer              The buffer to which the information
   *                             should be appended.
   * @param  includeFileElement  Indicates whether to include an
   *                             "extra" property that specifies the
   *                             path to the schema file from which
   *                             this name form was loaded.
   */
  public void toString(StringBuilder buffer,
                       boolean includeFileElement)
  {
    buffer.append("( ");
    buffer.append(oid);

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

    buffer.append(" OC ");
    buffer.append(structuralClass.getNameOrOID());

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

