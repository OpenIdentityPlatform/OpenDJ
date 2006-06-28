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
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a DIT content rule, which defines the set of
 * allowed, required, and prohibited attributes for entries with a
 * given structural objectclass, and also indicates which auxiliary
 * classes that may be included in the entry.
 */
public class DITContentRule
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.DITContentRule";



  // Indicates whether this content rule is declared "obsolete".
  private boolean isObsolete;

  // The set of additional name-value pairs associated with this
  // content rule definition.
  private ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
               extraProperties;

  // The set of names for this DIT content rule, in a mapping between
  // the all-lowercase form and the user-defined form.
  private ConcurrentHashMap<String,String> names;

  // The set of auxiliary objectclasses that entries with this content
  // rule may contain, in a mapping between the objectclass and the
  // user-defined name for that class.
  private CopyOnWriteArraySet<ObjectClass> auxiliaryClasses;

  // The set of optional attribute types for this DIT content rule.
  private CopyOnWriteArraySet<AttributeType> optionalAttributes;

  // The set of prohibited attribute types for this DIT content rule.
  private CopyOnWriteArraySet<AttributeType> prohibitedAttributes;

  // The set of required attribute types for this DIT content rule.
  private CopyOnWriteArraySet<AttributeType> requiredAttributes;

  // The structural objectclass for this DIT content rule.
  private ObjectClass structuralClass;

  // The description for this attribute type.
  private String description;

  // The path to the schema file that contains this DIT content rule
  // definition.
  private String schemaFile;



  /**
   * Creates a new DIT content rule definition with the provided
   * information.
   *
   * @param  structuralClass       The structural objectclass for this
   *                               DIT content rule.
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
  public DITContentRule(ObjectClass structuralClass,
              ConcurrentHashMap<String,String> names,
              String description,
              CopyOnWriteArraySet<ObjectClass> auxiliaryClasses,
              CopyOnWriteArraySet<AttributeType> requiredAttributes,
              CopyOnWriteArraySet<AttributeType> optionalAttributes,
              CopyOnWriteArraySet<AttributeType> prohibitedAttributes,
              boolean isObsolete,
              ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
                   extraProperties)
  {
    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(structuralClass),
                              String.valueOf(names),
                              String.valueOf(description),
                              String.valueOf(auxiliaryClasses),
                              String.valueOf(requiredAttributes),
                              String.valueOf(optionalAttributes),
                              String.valueOf(prohibitedAttributes),
                              String.valueOf(isObsolete),
                              String.valueOf(extraProperties)
                            });

    this.structuralClass      = structuralClass;
    this.names                = names;
    this.description          = description;
    this.auxiliaryClasses     = auxiliaryClasses;
    this.requiredAttributes   = requiredAttributes;
    this.optionalAttributes   = optionalAttributes;
    this.prohibitedAttributes = prohibitedAttributes;
    this.isObsolete           = isObsolete;
    this.schemaFile           = null;
    this.extraProperties      = extraProperties;
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
   * Specifies the structural objectclass for this DIT content rule.
   *
   * @param  structuralClass  The structural objectclass for this DIT
   *                          content rule.
   */
  public void setStructuralClass(ObjectClass structuralClass)
  {
    assert debugEnter(CLASS_NAME, "setStructuralClass",
                      String.valueOf(structuralClass));

    this.structuralClass = structuralClass;
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
  public ConcurrentHashMap<String,String> getNames()
  {
    assert debugEnter(CLASS_NAME, "getNames");

    return names;
  }



  /**
   * Retrieves the primary name to use to reference this DIT content
   * rule.
   *
   * @return  The primary name to use to reference this DIT content
   *          rule, or <CODE>null</CODE> if there is none.
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
   * Specifies the set of names that may be used to reference this DIT
   * content rule.  The provided set must provide a mapping between
   * each name in all lowercase characters and that name in a
   * user-defined form (which may include mixed capitalization).
   *
   * @param  names  The set of names that may be used to reference
   *                this DIT content rule.
   */
  public void setNames(ConcurrentHashMap<String,String> names)
  {
    assert debugEnter(CLASS_NAME, "setNames", String.valueOf(names));

    this.names = names;
  }



  /**
   * Indicates whether the provided lowercase name may be used to
   * reference this DIT content rule.
   *
   * @param  lowerName  The name for which to make the determination,
   *                    in all lowercase characters.
   *
   * @return  <CODE>true</CODE> if the provided lowercase name may be
   *          used to reference this DIT content rule, or
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
   * reference this DIT content rule.
   *
   * @param  name  The name to add to the set of names that may be
   *               used to reference this DIT content rule.
   */
  public void addName(String name)
  {
    assert debugEnter(CLASS_NAME, "addName", String.valueOf(name));

    String lowerName = toLowerCase(name);
    names.put(lowerName, name);
  }



  /**
   * Removes the provided lowercase name from the set of names that
   * may be used to reference this DIT content rule.
   *
   * @param  lowerName  The name to remove from the set of names that
   *                    may be used to reference this DIT content
   *                    rule, in all lowercase characters.
   */
  public void removeName(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "removeName",
                      String.valueOf(lowerName));

    names.remove(lowerName);
  }



  /**
   * Retrieves the path to the schema file that contains the
   * definition for this DIT content rule.
   *
   * @return  The path to the schema file that contains the definition
   *          for this DIT content rule, or <CODE>null</CODE> if it is
   *          not known or if it is not stored in any schema file.
   */
  public String getSchemaFile()
  {
    assert debugEnter(CLASS_NAME, "getSchemaFile");

    return schemaFile;
  }



  /**
   * Specifies the path to the schema file that contains the
   * definition for this DIT content rule.
   *
   * @param  schemaFile  The path to the schema file that contains the
   *                     definition for this DIT content rule.
   */
  public void setSchemaFile(String schemaFile)
  {
    assert debugEnter(CLASS_NAME, "setSchemaFile",
                      String.valueOf(schemaFile));

    this.schemaFile = schemaFile;
  }



  /**
   * Retrieves the description for this DIT content rule.
   *
   * @return  The description for this DIT content rule, or
   *          <CODE>null</CODE> if there is none.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    return description;
  }



  /**
   * Specifies the description for this DIT content rule.
   *
   * @param  description  The description for this DIT content rule.
   */
  public void setDescription(String description)
  {
    assert debugEnter(CLASS_NAME, "setDescription",
                      String.valueOf(description));

    this.description = description;
  }



  /**
   * Retrieves the set of auxiliary objectclasses that may be used for
   * entries associated with this DIT content rule.
   *
   * @return  The set of auxiliary objectclasses that may be used for
   *          entries associated with this DIT content rule.
   */
  public CopyOnWriteArraySet<ObjectClass> getAuxiliaryClasses()
  {
    assert debugEnter(CLASS_NAME, "getAuxiliaryClasses");

    return auxiliaryClasses;
  }



  /**
   * Specifies the set of auxiliary objectclasses that may be used for
   * entries associated with this DIT content rule.
   *
   * @param  auxiliaryClasses  The set of auxiliary objectclasses that
   *                           may be used for entries associated with
   *                           this DIT content rule.
   */
  public void setAuxiliaryClasses(
                   CopyOnWriteArraySet<ObjectClass> auxiliaryClasses)
  {
    assert debugEnter(CLASS_NAME, "setAuxiliaryClasses",
                      String.valueOf(auxiliaryClasses));

    this.auxiliaryClasses = auxiliaryClasses;
  }



  /**
   * Adds the specified auxiliary objectclass to this DIT content
   * rule.
   *
   * @param  auxiliaryClass  The auxiliary class to add to this DIT
   *                         content rule.
   */
  public void addAuxiliaryClass(ObjectClass auxiliaryClass)
  {
    assert debugEnter(CLASS_NAME, "addAuxiliaryClass",
                      String.valueOf(auxiliaryClass));

    auxiliaryClasses.add(auxiliaryClass);
  }



  /**
   * Removes the specified auxiliary objectclass from this DIT content
   * rule.
   *
   * @param  auxiliaryClass  The auxiliary class to remove from this
   *                         DIT content rule.
   */
  public void removeAuxiliaryClass(ObjectClass auxiliaryClass)
  {
    assert debugEnter(CLASS_NAME, "removeAuxiliaryClass",
                      String.valueOf(auxiliaryClass));

    auxiliaryClasses.remove(auxiliaryClass);
  }



  /**
   * Indicates whether the provided auxiliary objectclass is allowed
   * for use by this DIT content rule.
   *
   * @param  auxiliaryClass  The auxiliary objectclass for which to
   *                         make the determination.
   *
   * @return  <CODE>true</CODE> if the provided auxiliary objectclass
   *          is allowed for use by this DIT content rule, or
   *          <CODE>false</CODE> if not.
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
  public CopyOnWriteArraySet<AttributeType> getRequiredAttributes()
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
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          required by this DIT content rule, or <CODE>false</CODE>
   *          if not.
   */
  public boolean isRequired(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "isRequired",
                      String.valueOf(attributeType));

    return requiredAttributes.contains(attributeType);
  }



  /**
   * Specifies the set of required attributes for this DIT content
   * rule.
   *
   * @param  requiredAttributes  The set of required attributes for
   *                             this DIT content rule.
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
   * this DIT content rule.
   *
   * @param  attributeType  The attribute type to add to the set of
   *                        required attributes for this DIT content
   *                        rule.
   */
  public void addRequiredAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "addRequiredAttribute",
                      String.valueOf(attributeType));

    requiredAttributes.add(attributeType);
  }



  /**
   * Removes the provided attribute from the set of required
   * attributes for this DIT content rule.
   *
   * @param  attributeType  The attribute type to remove from the set
   *                        of required attributes for this DIT
   *                        content rule.
   */
  public void removeRequiredAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "removeRequiredAttribute",
                      String.valueOf(attributeType));

    requiredAttributes.remove(attributeType);
  }



  /**
   * Retrieves the set of optional attributes for this DIT content
   * rule.
   *
   * @return  The set of optional attributes for this DIT content
   *          rule.
   */
  public CopyOnWriteArraySet<AttributeType> getOptionalAttributes()
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
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          optional for this DIT content rule, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isOptional(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "isOptional",
                      String.valueOf(attributeType));

    return optionalAttributes.contains(attributeType);
  }



  /**
   * Specifies the set of optional attributes for this DIT content
   * rule.
   *
   * @param  optionalAttributes  The set of optional attributes for
   *                             this DIT content rule.
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
   * this DIT content rule.
   *
   * @param  attributeType  The attribute type to add to the set of
   *                        optional attributes for this DIT content
   *                        rule.
   */
  public void addOptionalAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "addOptionalAttribute",
                      String.valueOf(attributeType));

    optionalAttributes.add(attributeType);
  }



  /**
   * Removes the provided attribute from the set of optional
   * attributes for this DIT content rule.
   *
   * @param  attributeType  The attribute type to remove from the set
   *                        of optional attributes for this DIT
   *                        content rule.
   */
  public void removeOptionalAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "removeOptionalAttribute",
                      String.valueOf(attributeType));

    optionalAttributes.remove(attributeType);
  }



  /**
   * Indicates whether the provided attribute type is in the list of
   * required or optional attributes for this DIT content rule.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          required or allowed for this DIT content rule, or
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
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          required or allowed for this DIT content rule, or
   *          <CODE>false</CODE> if it is not.
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
  public CopyOnWriteArraySet<AttributeType> getProhibitedAttributes()
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
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          prohibited for this DIT content rule, or
   *          <CODE>false</CODE> if not.
   */
  public boolean isProhibited(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "isProhibited",
                      String.valueOf(attributeType));

    return prohibitedAttributes.contains(attributeType);
  }



  /**
   * Specifies the set of prohibited attributes for this DIT content
   * rule.
   *
   * @param  prohibitedAttributes  The set of prohibited attributes
   *                               for this DIT content rule.
   */
  public void setProhibitedAttributes(
                   CopyOnWriteArraySet<AttributeType>
                       prohibitedAttributes)
  {
    assert debugEnter(CLASS_NAME, "setProhibitedAttributes",
                      String.valueOf(prohibitedAttributes));

    this.prohibitedAttributes = prohibitedAttributes;
  }



  /**
   * Adds the provided attribute to the set of prohibited attributes
   * for this DIT content rule.
   *
   * @param  attributeType  The attribute type to add to the set of
   *                        prohibited attributes for this DIT
   *                        content rule.
   */
  public void addProhibitedAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "addProhibitedAttribute",
                      String.valueOf(attributeType));

    prohibitedAttributes.add(attributeType);
  }



  /**
   * Removes the provided attribute from the set of prohibited
   * attributes for this DIT content rule.
   *
   * @param  attributeType  The attribute type to remove from the set
   *                        of prohibited attributes for this DIT
   *                        content rule.
   */
  public void removeProhibitedAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "removeProhibitedAttribute",
                      String.valueOf(attributeType));

    prohibitedAttributes.remove(attributeType);
  }



  /**
   * Indicates whether this DIT content rule is declared "obsolete".
   *
   * @return  <CODE>true</CODE> if this DIT content rule is declared
   *          "obsolete", or <CODE>false</CODE> if it is not.
   */
  public boolean isObsolete()
  {
    assert debugEnter(CLASS_NAME, "isObsolete");

    return isObsolete;
  }



  /**
   * Specifies whether this DIT content rule is declared "obsolete".
   *
   * @param  isObsolete  Specifies whether this DIT content rule is
   *                     declared "obsolete".
   */
  public void setObsolete(boolean isObsolete)
  {
    assert debugEnter(CLASS_NAME, "setObsolete",
                      String.valueOf(isObsolete));

    this.isObsolete = isObsolete;
  }



  /**
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this DIT content rule and
   * the value for that property.  The caller may alter the contents
   * of this mapping.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this DIT content
   *          rule and the value for that property.
   */
  public ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
              getExtraProperties()
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
   *          content rule, or <CODE>null</CODE> if no such property
   *          is defined.
   */
  public CopyOnWriteArrayList<String>
              getExtraProperty(String propertyName)
  {
    assert debugEnter(CLASS_NAME, "getExtraProperty",
                      String.valueOf(propertyName));

    return extraProperties.get(propertyName);
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
   * @return  <CODE>true</CODE> if the provided object is equal to
   *          this DIT content rule, or <CODE>false</CODE> if not.
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

