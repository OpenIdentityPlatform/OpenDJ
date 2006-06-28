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



import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure for storing and interacting
 * with a name form, which defines the attribute type(s) that must
 * and/or may be used in the RDN of an entry with a given structural
 * objectclass.
 */
public class NameForm
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
      "org.opends.server.types.NameForm";



  // Indicates whether this name form is declared "obsolete".
  private boolean isObsolete;

  // The set of additional name-value pairs associated with this name
  // form definition.
  private ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
               extraProperties;

  // The mapping between the lowercase names and the user-provided
  // names for this name form.
  private ConcurrentHashMap<String,String> names;

  // The set of optional attribute types for this name form.
  private CopyOnWriteArraySet<AttributeType> optionalAttributes;

  // The set of required attribute types for this name form.
  private CopyOnWriteArraySet<AttributeType> requiredAttributes;

  // The reference to the structural objectclass for this name form.
  private ObjectClass structuralClass;

  // The description for this name form.
  private String description;

  // The OID for this name form.
  private String oid;

  // The path to the schema file that contains the definition for this
  // name form.
  private String schemaFile;



  /**
   * Creates a new name form definition with the provided information.
   *
   * @param  names               The set of names that may be used to
   *                             reference this name form.
   * @param  oid                 The OID for this name form.
   * @param  description         The description for this name form.
   * @param  isObsolete          Indicates whether this name form is
   *                             declared "obsolete".
   * @param  structuralClass     The structural objectclass with which
   *                             this name form is associated.
   * @param  requiredAttributes  The set of required attribute types
   *                             for this name form.
   * @param  optionalAttributes  The set of optional attribute types
   *                             for this name form.
   * @param  extraProperties     A set of extra properties for this
   *                             name form.
   */
  public NameForm(ConcurrentHashMap<String,String> names, String oid,
              String description, boolean isObsolete,
              ObjectClass structuralClass,
              CopyOnWriteArraySet<AttributeType> requiredAttributes,
              CopyOnWriteArraySet<AttributeType> optionalAttributes,
              ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
                   extraProperties)
  {
    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(names),
                              String.valueOf(oid),
                              String.valueOf(description),
                              String.valueOf(isObsolete),
                              String.valueOf(structuralClass),
                              String.valueOf(requiredAttributes),
                              String.valueOf(optionalAttributes),
                              String.valueOf(extraProperties)
                            });

    this.names              = names;
    this.oid                = oid;
    this.description        = description;
    this.isObsolete         = isObsolete;
    this.structuralClass    = structuralClass;
    this.requiredAttributes = requiredAttributes;
    this.optionalAttributes = optionalAttributes;
    this.schemaFile         = null;
    this.extraProperties    = extraProperties;
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
  public ConcurrentHashMap<String,String> getNames()
  {
    assert debugEnter(CLASS_NAME, "getNames");

    return names;
  }



  /**
   * Specifies the set of names that may be used to reference this
   * name form.  The provided set must provide a mapping between each
   * name in all lowercase characters and that name in a user-defined
   * form (which may include mixed capitalization).
   *
   * @param  names  The set of names that may be used to reference
   *                this name form.
   */
  public void setNames(ConcurrentHashMap<String,String> names)
  {
    assert debugEnter(CLASS_NAME, "setNames", String.valueOf(names));

    this.names = names;
  }



  /**
   * Indicates whether the provided lowercase name may be used to
   * reference this name form.
   *
   * @param  lowerName  The name for which to make the determination,
   *                    in all lowercase characters.
   *
   * @return  <CODE>true</CODE> if the provided lowercase name may be
   *          used to reference this name form, or <CODE>false</CODE>
   *          if not.
   */
  public boolean hasName(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "hasName",
                      String.valueOf(lowerName));

    return names.containsKey(lowerName);
  }



  /**
   * Adds the provided name to the set of names that may be used to
   * reference this name form.
   *
   * @param  name  The name to add to the set of names that may be
   *               used to reference this name form.
   */
  public void addName(String name)
  {
    assert debugEnter(CLASS_NAME, "addName", String.valueOf(name));

    String lowerName = toLowerCase(name);
    names.put(lowerName, name);
  }



  /**
   * Removes the provided lowercase name from the set of names that
   * may be used to reference this name form.
   *
   * @param  lowerName  The name to remove from the set of names that
   *                    may be used to reference this name form, in
   *                    all lowercase characters.
   */
  public void removeName(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "removeName",
                      String.valueOf(lowerName));

    names.remove(lowerName);
  }



  /**
   * Retrieves the OID for this name form.
   *
   * @return  The OID for this name form.
   */
  public String getOID()
  {
    assert debugEnter(CLASS_NAME, "getOID");

    return oid;
  }



  /**
   * Specifies the OID for this name form.
   *
   * @param  oid  The OID for this name form.
   */
  public void setOID(String oid)
  {
    assert debugEnter(CLASS_NAME, "setOID", String.valueOf(oid));

    this.oid = oid;
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
    assert debugEnter(CLASS_NAME, "getNameOrOID");

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
   * @return  <CODE>true</CODE> if the provided lowercase value is one
   *          of the names or the OID of this name form, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean hasNameOrOID(String lowerValue)
  {
    assert debugEnter(CLASS_NAME, "hasNameOrOID",
                      String.valueOf(lowerValue));

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
   *          for this name form, or <CODE>null</CODE> if it is not
   *          known or if it is not stored in any schema file.
   */
  public String getSchemaFile()
  {
    assert debugEnter(CLASS_NAME, "getSchemaFile");

    return schemaFile;
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
    assert debugEnter(CLASS_NAME, "setSchemaFile",
                      String.valueOf(schemaFile));

    this.schemaFile = schemaFile;
  }



  /**
   * Retrieves the description for this name form.
   *
   * @return  The description for this name form, or <CODE>null</CODE>
   *          if there is none.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    return description;
  }



  /**
   * Specifies the description for this name form.
   *
   * @param  description  The description for this name form.
   */
  public void setDescription(String description)
  {
    assert debugEnter(CLASS_NAME, "setDescription",
                      String.valueOf(description));

    this.description = description;
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
    assert debugEnter(CLASS_NAME, "getStructuralClass");

    return structuralClass;
  }



  /**
   * Specifies the structural objectclass for this name form.
   *
   * @param  structuralClass  The structural objectclass for this name
   *                          form.
   */
  public void setStructuralClass(ObjectClass structuralClass)
  {
    assert debugEnter(CLASS_NAME, "setStructuralClass",
                      String.valueOf(structuralClass));

    this.structuralClass = structuralClass;
  }



  /**
   * Retrieves the set of required attributes for this name form.
   *
   * @return  The set of required attributes for this name form.
   */
  public CopyOnWriteArraySet<AttributeType> getRequiredAttributes()
  {
    assert debugEnter(CLASS_NAME, "getRequiredAttributes");

    return requiredAttributes;
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * required attribute list for this name form.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          required by this name form, or <CODE>false</CODE> if
   *          not.
   */
  public boolean isRequired(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "isRequired",
                      String.valueOf(attributeType));

    return requiredAttributes.contains(attributeType);
  }



  /**
   * Specifies the set of required attributes for this name form.
   *
   * @param  requiredAttributes  The set of required attributes for
   *                             this name form.
   */
  public void setRequiredAttributes(CopyOnWriteArraySet<AttributeType>
                                         requiredAttributes)
  {
    assert debugEnter(CLASS_NAME, "setRequiredAttributes",
                      String.valueOf(requiredAttributes));

    this.requiredAttributes = requiredAttributes;
  }



  /**
   * Adds the provided attribute to the set of required attributes for
   * this name form.
   *
   * @param  attributeType  The attribute type to add to the set of
   *                        required attributes for this name form.
   */
  public void addRequiredAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "addRequiredAttribute",
                      String.valueOf(attributeType));

    requiredAttributes.add(attributeType);
  }



  /**
   * Removes the provided attribute from the set of required
   * attributes for this name form.
   *
   * @param  attributeType  The attribute type to remove from the set
   *                        of required attributes for this name form.
   */
  public void removeRequiredAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "removeRequiredAttribute",
                      String.valueOf(attributeType));

    requiredAttributes.remove(attributeType);
  }



  /**
   * Retrieves the set of optional attributes for this name form.
   *
   * @return  The set of optional attributes for this name form.
   */
  public CopyOnWriteArraySet<AttributeType> getOptionalAttributes()
  {
    assert debugEnter(CLASS_NAME, "getOptionalAttributes");

    return optionalAttributes;
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * optional attribute list for this name form.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          optional for this name form, or <CODE>false</CODE> if
   *          not.
   */
  public boolean isOptional(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "isOptional",
                      String.valueOf(attributeType));

    return optionalAttributes.contains(attributeType);
  }



  /**
   * Specifies the set of optional attributes for this name form.
   *
   * @param  optionalAttributes  The set of optional attributes for
   *                             this name form.
   */
  public void setOptionalAttributes(CopyOnWriteArraySet<AttributeType>
                                         optionalAttributes)
  {
    assert debugEnter(CLASS_NAME, "setOptionalAttributes",
                      String.valueOf(optionalAttributes));

    this.optionalAttributes = optionalAttributes;
  }



  /**
   * Adds the provided attribute to the set of optional attributes for
   * this name form.
   *
   * @param  attributeType  The attribute type to add to the set of
   *                        optional attributes for this name form.
   */
  public void addOptionalAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "addOptionalAttribute",
                      String.valueOf(attributeType));

    optionalAttributes.add(attributeType);
  }



  /**
   * Removes the provided attribute from the set of optional
   * attributes for this name form.
   *
   * @param  attributeType  The attribute type to remove from the set
   *                        optional attributes for this name form.
   */
  public void removeOptionalAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "removeOptionalAttribute",
                      String.valueOf(attributeType));

    optionalAttributes.remove(attributeType);
  }



  /**
   * Indicates whether the provided attribute type is in the list of
   * required or optional attributes for this name form.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          required or allowed for this name form, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean isRequiredOrOptional(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "isRequiredOrOptional",
                      String.valueOf(attributeType));

    return (requiredAttributes.contains(attributeType) ||
            optionalAttributes.contains(attributeType));
  }



  /**
   * Indicates whether this name form is declared "obsolete".
   *
   * @return  <CODE>true</CODE> if this name form is declared
   *          "obsolete", or <CODE>false</CODE> if it is not.
   */
  public boolean isObsolete()
  {
    assert debugEnter(CLASS_NAME, "isObsolete");

    return isObsolete;
  }



  /**
   * Specifies whether this name form is declared "obsolete".
   *
   * @param  isObsolete  Specifies whether this name form is declared
   *                     "obsolete".
   */
  public void setObsolete(boolean isObsolete)
  {
    assert debugEnter(CLASS_NAME, "setObsolete",
                      String.valueOf(isObsolete));

    this.isObsolete = isObsolete;
  }



  /**
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this name form and the
   * value for that property.  The caller may alter the contents of
   * this mapping.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this name form
   *          and the value for that property.
   */
  public ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
              getExtraProperties()
  {
    assert debugEnter(CLASS_NAME, "getExtraProperties");

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
   *          name form, or <CODE>null</CODE> if no such property is
   *          defined.
   */
  public CopyOnWriteArrayList<String>
              getExtraProperty(String propertyName)
  {
    assert debugEnter(CLASS_NAME, "getExtraProperty",
                      String.valueOf(propertyName));

    return extraProperties.get(propertyName);
  }



  /**
   * Indicates whether the provided object is equal to this name form.
   * The object will be considered equal if it is a name form with the
   * same OID as the current name form.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is equal to
   *          this name form, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object o)
  {
    assert debugEnter(CLASS_NAME, "equals");

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
    assert debugEnter(CLASS_NAME, "hashCode");

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
    assert debugEnter(CLASS_NAME, "toString");

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
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder",
                      String.valueOf(includeFileElement));

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
        CopyOnWriteArrayList<String> valueList =
             extraProperties.get(property);

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

    if (includeFileElement && (schemaFile != null) &&
        (! extraProperties.containsKey(SCHEMA_PROPERTY_FILENAME)))
    {
      buffer.append(" ");
      buffer.append(SCHEMA_PROPERTY_FILENAME);
      buffer.append(" '");
      buffer.append(schemaFile);
      buffer.append("'");
    }

    buffer.append(" )");
  }
}

