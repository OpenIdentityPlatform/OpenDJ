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



import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure for storing and interacting
 * with an objectclass, which contains a collection of attributes that
 * must and/or may be present in an entry with that objectclass.
 */
public class ObjectClass
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.ObjectClass";



  // Indicates whether this objectclass is declared "obsolete".
  private boolean isObsolete;

  // The set of additional name-value pairs associated with this
  // objectclass definition.
  private ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
               extraProperties;

  // The mapping between the lowercase names and the user-provided
  // names for this objectclass.
  private ConcurrentHashMap<String,String> names;

  // The set of optional attribute types for this objectclass.
  private CopyOnWriteArraySet<AttributeType> optionalAttributes;

  // The set of required attribute types for this objectclass.
  private CopyOnWriteArraySet<AttributeType> requiredAttributes;

  // The reference to the superior objectclass.
  private ObjectClass superiorClass;

  // The objectclass type for this objectclass.
  private ObjectClassType objectClassType;

  // The description for this objectclass.
  private String description;

  // The OID for this objectclass.
  private String oid;

  // The primary name for this objectclass.
  private String primaryName;

  // The path to the schema file that contains this objectclass
  // definition.
  private String schemaFile;



  /**
   * Creates a new objectclass definition with the provided
   * information.
   *
   * @param  primaryName         The primary name for this
   *                             objectclass.
   * @param  names               The set of names that may be used to
   *                             reference this objectclass.
   * @param  oid                 The OID for this objectclass.
   * @param  description         The description for this objectclass.
   * @param  superiorClass       The superior class for this
   *                             objectclass.
   * @param  requiredAttributes  The set of required attribute types
   *                             for this objectclass.
   * @param  optionalAttributes  The set of optional attribute types
   *                             for this objectclass.
   * @param  objectClassType     The objectclass type for this
   *                             objectclass.
   * @param  isObsolete          Indicates whether this objectclass is
   *                             declared "obsolete".
   * @param  extraProperties     A set of extra properties for this
   *                             objectclass.
   */
  public ObjectClass(String primaryName,
             ConcurrentHashMap<String,String> names, String oid,
             String description, ObjectClass superiorClass,
             CopyOnWriteArraySet<AttributeType> requiredAttributes,
             CopyOnWriteArraySet<AttributeType> optionalAttributes,
             ObjectClassType objectClassType, boolean isObsolete,
             ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
                  extraProperties)
  {
    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(primaryName),
                              String.valueOf(names),
                              String.valueOf(oid),
                              String.valueOf(description),
                              String.valueOf(superiorClass),
                              String.valueOf(requiredAttributes),
                              String.valueOf(optionalAttributes),
                              String.valueOf(objectClassType),
                              String.valueOf(isObsolete),
                              String.valueOf(extraProperties)
                            });

    this.primaryName        = primaryName;
    this.names              = names;
    this.oid                = oid;
    this.description        = description;
    this.superiorClass      = superiorClass;
    this.requiredAttributes = requiredAttributes;
    this.optionalAttributes = optionalAttributes;
    this.objectClassType    = objectClassType;
    this.isObsolete         = isObsolete;
    this.schemaFile         = null;
    this.extraProperties    = extraProperties;
  }



  /**
   * Retrieves the primary name for this objectclass.
   *
   * @return  The primary name for this objectClass, or
   *          <CODE>null</CODE> if there is none.
   */
  public String getPrimaryName()
  {
    assert debugEnter(CLASS_NAME, "getPrimaryName");

    return primaryName;
  }



  /**
   * Specifies the primary name for this objectclass.
   *
   * @param  primaryName  The primary name for this objectclass.
   */
  public void setPrimaryName(String primaryName)
  {
    assert debugEnter(CLASS_NAME, "setPrimaryName",
                      String.valueOf(primaryName));

    this.primaryName = primaryName;

    String lowerName = toLowerCase(primaryName);
    names.put(lowerName, primaryName);
  }



  /**
   * Retrieves the set of names that may be used to reference this
   * objectclass.   The returned object will be a mapping between each
   * name in all lowercase characters and that name in a user-defined
   * form (which may include mixed capitalization).
   *
   * @return  The set of names that may be used to reference this
   *          objectclass.
   */
  public ConcurrentHashMap<String,String> getNames()
  {
    assert debugEnter(CLASS_NAME, "getNames");

    return names;
  }



  /**
   * Specifies the set of names that may be used to reference this
   * objectclass.  The provided set must provide a mapping between
   * each name in all lowercase characters and that name in a
   * user-defined form (which may include mixed capitalization).
   *
   * @param  names  The set of names that may be used to reference
   *                this objectclass.
   */
  public void setNames(ConcurrentHashMap<String,String> names)
  {
    assert debugEnter(CLASS_NAME, "setNames", String.valueOf(names));

    this.names = names;
  }



  /**
   * Indicates whether the provided lowercase name may be used to
   * reference this objectclass.
   *
   * @param  lowerName  The name for which to make the determination,
   *                    in all lowercase characters.
   *
   * @return  <CODE>true</CODE> if the provided lowercase name may be
   *          used to reference this objectclass, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hasName(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "hasName",
                      String.valueOf(lowerName));

    return names.containsKey(lowerName);
  }



  /**
   * Adds the provided name to the set of names that may be used to
   * reference this objectclass.
   *
   * @param  name  The name to add to the set of names that may be
   *               used to reference this objectclass.
   */
  public void addName(String name)
  {
    assert debugEnter(CLASS_NAME, "addName", String.valueOf(name));

    String lowerName = toLowerCase(name);
    names.put(lowerName, name);
  }



  /**
   * Removes the provided lowercase name from the set of names that
   * may be used to reference this objectclass.
   *
   * @param  lowerName  The name to remove from the set of names that
   *                    may be used to reference this objectclass, in
   *                    all lowercase characters.
   */
  public void removeName(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "removeName",
                      String.valueOf(lowerName));

    names.remove(lowerName);
    if (lowerName.equalsIgnoreCase(primaryName))
    {
      if (names.isEmpty())
      {
        primaryName = null;
      }
      else
      {
        primaryName = names.values().iterator().next();
      }
    }
  }



  /**
   * Retrieves the OID for this objectclass.
   *
   * @return  The OID for this objectclass.
   */
  public String getOID()
  {
    assert debugEnter(CLASS_NAME, "getOID");

    return oid;
  }



  /**
   * Specifies the OID for this objectclass.
   *
   * @param  oid  The OID for this objectclass.
   */
  public void setOID(String oid)
  {
    assert debugEnter(CLASS_NAME, "setOID", String.valueOf(oid));

    this.oid = oid;
  }



  /**
   * Retrieves the name or OID that should be used to reference this
   * objectclass.  If a primary name is defined, then that will be
   * returned.  Otherwise, the OID will be returned.
   *
   * @return  The primary name if one is defined for this objectclass,
   *          or the OID if there is no primary name.
   */
  public String getNameOrOID()
  {
    assert debugEnter(CLASS_NAME, "getNameOrOID");

    if (primaryName == null)
    {
      return oid;
    }
    else
    {
      return primaryName;
    }
  }



  /**
   * Indicates whether the provided lowercase value is equal to the
   * OID or any of the names that may be used to reference this
   * objectclass.
   *
   * @param  lowerValue  The value, in all lowercase characters, that
   *                     may be used to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided lowercase value is one
   *          of the names or the OID of this objectclass, or
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
   * definition for this objectclass.
   *
   * @return  The path to the schema file that contains the definition
   *          for this objectclass, or <CODE>null</CODE> if it is not
   *          known or if it is not stored in any schema file.
   */
  public String getSchemaFile()
  {
    assert debugEnter(CLASS_NAME, "getSchemaFile");

    return schemaFile;
  }



  /**
   * Specifies the path to the schema file that contains the
   * definition for this objectclass.
   *
   * @param  schemaFile  The path to the schema file that contains the
   *                     definition for this objectclass.
   */
  public void setSchemaFile(String schemaFile)
  {
    assert debugEnter(CLASS_NAME, "setSchemaFile",
                      String.valueOf(schemaFile));

    this.schemaFile = schemaFile;
  }



  /**
   * Retrieves the description for this objectclass.
   *
   * @return  The description for this objectclass, or
   *          <CODE>null</CODE> if there is none.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    return description;
  }



  /**
   * Specifies the description for this objectclass.
   *
   * @param  description  The description for this objectclass.
   */
  public void setDescription(String description)
  {
    assert debugEnter(CLASS_NAME, "setDescription",
                      String.valueOf(description));

    this.description = description;
  }



  /**
   * Retrieves the reference to the superior class for this
   * objectclass.
   *
   * @return  The reference to the superior class for this
   *          objectlass, or <CODE>null</CODE> if there is none.
   */
  public ObjectClass getSuperiorClass()
  {
    assert debugEnter(CLASS_NAME, "getSuperiorClass");

    return superiorClass;
  }



  /**
   * Specifies the superior class for this objectclass.
   *
   * @param  superiorClass  The superior class for this objectclass.
   */
  public void setSuperiorClass(ObjectClass superiorClass)
  {
    assert debugEnter(CLASS_NAME, "setSuperiorClass",
                      String.valueOf(superiorClass));

    this.superiorClass = superiorClass;
  }



  /**
   * Indicates whether this objectclass is a descendant of the
   * provided class.
   *
   * @param  objectClass  The objectClass for which to make the
   *                      determination.
   *
   * @return  <CODE>true</CODE> if this objectclass is a descendant of
   *          the provided class, or <CODE>false</CODE> if not.
   */
  public boolean isDescendantOf(ObjectClass objectClass)
  {
    assert debugEnter(CLASS_NAME, "isDescendantOf",
                      String.valueOf(objectClass));

    if (superiorClass == null)
    {
      return false;
    }

    return (superiorClass.equals(objectClass) ||
            superiorClass.isDescendantOf(objectClass));
  }



  /**
   * Retrieves the set of required attributes for this objectclass.
   * Note that this list will not automatically include any required
   * attributes for superior objectclasses.
   *
   * @return  The set of required attributes for this objectclass.
   */
  public CopyOnWriteArraySet<AttributeType> getRequiredAttributes()
  {
    assert debugEnter(CLASS_NAME, "getRequiredAttributes");

    return requiredAttributes;
  }



  /**
   * Retrieves the set of all required attributes for this objectclass
   * and any superior objectclasses that it might have.
   *
   * @return  The set of all required attributes for this objectclass
   *          and any superior objectclasses that it might have.
   */
  public Set<AttributeType> getRequiredAttributeChain()
  {
    assert debugEnter(CLASS_NAME, "getRequiredAttributeChain");

    if (superiorClass == null)
    {
      return requiredAttributes;
    }
    else
    {
      HashSet<AttributeType> attrs = new HashSet<AttributeType>();
      attrs.addAll(requiredAttributes);
      attrs.addAll(superiorClass.getRequiredAttributeChain());
      return attrs;
    }
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * required attribute list for this or any of its superior
   * objectclasses.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          required by this objectclass or any of its superior
   *          classes, or <CODE>false</CODE> if not.
   */
  public boolean isRequired(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "isRequired",
                      String.valueOf(attributeType));

    if (requiredAttributes.contains(attributeType))
    {
      return true;
    }

    if (superiorClass != null)
    {
      return superiorClass.isRequired(attributeType);
    }

    return false;
  }



  /**
   * Specifies the set of required attributes for this objectclass.
   *
   * @param  requiredAttributes  The set of required attributes for
   *                             this objectclass.
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
   * this objectclass.
   *
   * @param  attributeType  The attribute type to add to the set of
   *                        required attributes for this objectclass.
   */
  public void addRequiredAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "addRequiredAttribute",
                      String.valueOf(attributeType));

    requiredAttributes.add(attributeType);
  }



  /**
   * Removes the provided attribute from the set of required
   * attributes for this objectclass.
   *
   * @param  attributeType  The attribute type to remove from the set
   *                        of required attributes for this
   *                        objectclass.
   */
  public void removeRequiredAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "removeRequiredAttribute",
                      String.valueOf(attributeType));

    requiredAttributes.remove(attributeType);
  }



  /**
   * Retrieves the set of optional attributes for this objectclass.
   * Note that this list will not automatically include any optional
   * attributes for superior objectclasses.
   *
   * @return  The set of optional attributes for this objectclass.
   */
  public CopyOnWriteArraySet<AttributeType> getOptionalAttributes()
  {
    assert debugEnter(CLASS_NAME, "getOptionalAttributes");

    return optionalAttributes;
  }



  /**
   * Retrieves the set of all optional attributes for this objectclass
   * and any superior objectclasses that it might have.
   *
   * @return  The set of all optional attributes for this objectclass
   *          and any superior objectclasses that it might have.
   */
  public Set<AttributeType> getOptionalAttributeChain()
  {
    assert debugEnter(CLASS_NAME, "getOptionalAttributeChain");

    if (superiorClass == null)
    {
      return optionalAttributes;
    }
    else
    {
      HashSet<AttributeType> attrs = new HashSet<AttributeType>();
      attrs.addAll(optionalAttributes);
      attrs.addAll(superiorClass.getOptionalAttributeChain());
      return attrs;
    }
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * optional attribute list for this or any of its superior
   * objectclasses.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          optional for this objectclass or any of its superior
   *          classes, or <CODE>false</CODE> if not.
   */
  public boolean isOptional(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "isOptional",
                      String.valueOf(attributeType));

    if (optionalAttributes.contains(attributeType))
    {
      return true;
    }

    if (isExtensibleObject() &&
        (! requiredAttributes.contains(attributeType)))
    {
      // FIXME -- Do we need to do other checks here, like whether the
      //          attribute type is actually defined in the schema?
      //          What about DIT content rules?
      return true;
    }

    if (superiorClass != null)
    {
      return superiorClass.isOptional(attributeType);
    }

    return false;
  }



  /**
   * Specifies the set of optional attributes for this objectclass.
   *
   * @param  optionalAttributes  The set of optional attributes for
   *                             this objectclass.
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
   * this objectclass.
   *
   * @param  attributeType  The attribute type to add to the set of
   *                        optional attributes for this objectclass.
   */
  public void addOptionalAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "addOptionalAttribute",
                      String.valueOf(attributeType));

    optionalAttributes.add(attributeType);
  }



  /**
   * Removes the provided attribute from the set of optional
   * attributes for this objectclass.
   *
   * @param  attributeType  The attribute type to remove from the set
   *                        of optional attributes for this
   *                        objectclass.
   */
  public void removeOptionalAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "removeOptionalAttribute",
                      String.valueOf(attributeType));

    optionalAttributes.remove(attributeType);
  }



  /**
   * Indicates whether the provided attribute type is in the list of
   * required or optional attributes for this objectclass or any of
   * its superior classes.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          required or allowed for this objectclass or any of its
   *          superior classes, or <CODE>false</CODE> if it is not.
   */
  public boolean isRequiredOrOptional(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "isRequiredOrOptional",
                      String.valueOf(attributeType));

    if (requiredAttributes.contains(attributeType) ||
        optionalAttributes.contains(attributeType))
    {
      return true;
    }

    if (isExtensibleObject())
    {
      // FIXME -- Do we need to do other checks here, like whether the
      //          attribute type is actually defined in the schema?
      //          What about DIT content rules?
      return true;
    }

    if (superiorClass != null)
    {
      return superiorClass.isRequiredOrOptional(attributeType);
    }

    return false;
  }



  /**
   * Retrieves the objectclass type for this objectclass.
   *
   * @return  The objectclass type for this objectclass.
   */
  public ObjectClassType getObjectClassType()
  {
    assert debugEnter(CLASS_NAME, "getObjectClassType");

    return objectClassType;
  }



  /**
   * Specifies the objectclass type for this objectclass.
   *
   * @param  objectClassType  The objectclass type for this
   *                          objectclass.
   */
  public void setObjectClassType(ObjectClassType objectClassType)
  {
    assert debugEnter(CLASS_NAME, "setObjectClassType",
                      String.valueOf(objectClassType));
  }



  /**
   * Indicates whether this objectclass is declared "obsolete".
   *
   * @return  <CODE>true</CODE> if this objectclass is declared
   *          "obsolete", or <CODE>false</CODE> if it is not.
   */
  public boolean isObsolete()
  {
    assert debugEnter(CLASS_NAME, "isObsolete");

    return isObsolete;
  }



  /**
   * Specifies whether this objectclass is declared "obsolete".
   *
   * @param  isObsolete  Specifies whether this objectclass is
   *                     declared "obsolete".
   */
  public void setObsolete(boolean isObsolete)
  {
    assert debugEnter(CLASS_NAME, "setObsolete",
                      String.valueOf(isObsolete));

    this.isObsolete = isObsolete;
  }



  /**
   * Indicates whether this objectclass is the extensibleObject
   * objectclass.
   *
   * @return  <CODE>true</CODE> if this objectclass is the
   *          extensibleObject objectclass, or <CODE>false</CODE> if
   *          it is not.
   */
  public boolean isExtensibleObject()
  {
    assert debugEnter(CLASS_NAME, "isExtensibleObject");

    return (names.containsKey(OC_EXTENSIBLE_OBJECT_LC) ||
            oid.equals(OID_EXTENSIBLE_OBJECT));
  }



  /**
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this objectclass and the
   * value for that property.  The caller may alter the contents of
   * this mapping.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this objectclass
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
   * objectclass.
   *
   * @param  propertyName  The name of the "extra" property for which
   *                       to retrieve the value.
   *
   * @return  The value of the specified "extra" property for this
   *          objectclass, or <CODE>null</CODE> if no such property is
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
   * Indicates whether the provided object is equal to this
   * objectclass.  The object will be considered equal if it is an
   * attribute type with the same OID as the current type.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is equal to
   *          this objectclass, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object o)
  {
    assert debugEnter(CLASS_NAME, "equals");

    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof ObjectClass)))
    {
      return false;
    }

    return oid.equals(((ObjectClass) o).oid);
  }



  /**
   * Retrieves the hash code for this objectclass.  It will be based
   * on the sum of the bytes of the OID.
   *
   * @return  The hash code for this objectclass.
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
   * Retrieves the string representation of this objectclass in the
   * form specified in RFC 2252.
   *
   * @return  The string representation of this objectclass in the
   *          form specified in RFC 2252.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    StringBuilder buffer = new StringBuilder();
    toString(buffer, true);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this objectclass in the form
   * specified in RFC 2252 to the provided buffer.
   *
   * @param  buffer              The buffer to which the information
   *                             should be appended.
   * @param  includeFileElement  Indicates whether to include an
   *                             "extra" property that specifies the
   *                             path to the schema file from which
   *                             this objectclass was loaded.
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

    if (superiorClass != null)
    {
      buffer.append(" SUP ");
      buffer.append(superiorClass.getNameOrOID());
    }

    if (objectClassType != null)
    {
      buffer.append(" ");
      buffer.append(objectClassType.toString());
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

