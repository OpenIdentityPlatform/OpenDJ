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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.schema;



import static com.sun.opends.sdk.util.Messages.*;
import static org.opends.sdk.schema.SchemaConstants.EXTENSIBLE_OBJECT_OBJECTCLASS_NAME;
import static org.opends.sdk.schema.SchemaConstants.EXTENSIBLE_OBJECT_OBJECTCLASS_OID;
import static org.opends.sdk.schema.SchemaConstants.TOP_OBJECTCLASS_NAME;

import java.util.*;

import com.sun.opends.sdk.util.Message;
import org.opends.sdk.util.Validator;



/**
 * This class defines a data structure for storing and interacting with
 * an objectclass, which contains a collection of attributes that must
 * and/or may be present in an entry with that objectclass.
 * <p>
 * Where ordered sets of names, attribute types, or extra properties are
 * provided, the ordering will be preserved when the associated fields
 * are accessed via their getters or via the {@link #toString()}
 * methods.
 */
public final class ObjectClass extends SchemaElement
{
  // The OID that may be used to reference this definition.
  private final String oid;

  // The set of user defined names for this definition.
  private final List<String> names;

  // Indicates whether this definition is declared "obsolete".
  private final boolean isObsolete;

  // The reference to the superior objectclasses.
  private final Set<String> superiorClassOIDs;

  // The objectclass type for this objectclass.
  private final ObjectClassType objectClassType;

  // The set of required attribute types for this objectclass.
  private final Set<String> requiredAttributeOIDs;

  // The set of optional attribute types for this objectclass.
  private final Set<String> optionalAttributeOIDs;

  // The definition string used to create this objectclass.
  private final String definition;

  private Set<ObjectClass> superiorClasses = Collections.emptySet();
  private Set<AttributeType> declaredRequiredAttributes =
      Collections.emptySet();
  private Set<AttributeType> requiredAttributes =
      Collections.emptySet();
  private Set<AttributeType> declaredOptionalAttributes =
      Collections.emptySet();
  private Set<AttributeType> optionalAttributes =
      Collections.emptySet();
  private boolean validated = false;



  ObjectClass(String oid, List<String> names, String description,
      boolean obsolete, Set<String> superiorClassOIDs,
      Set<String> requiredAttributeOIDs,
      Set<String> optionalAttributeOIDs,
      ObjectClassType objectClassType,
      Map<String, List<String>> extraProperties, String definition)
  {
    super(description, extraProperties);

    Validator.ensureNotNull(oid, names);
    Validator.ensureNotNull(superiorClassOIDs, requiredAttributeOIDs,
        optionalAttributeOIDs, objectClassType);
    this.oid = oid;
    this.names = names;
    this.isObsolete = obsolete;
    this.superiorClassOIDs = superiorClassOIDs;
    this.objectClassType = objectClassType;
    this.requiredAttributeOIDs = requiredAttributeOIDs;
    this.optionalAttributeOIDs = optionalAttributeOIDs;

    if (definition != null)
    {
      this.definition = definition;
    }
    else
    {
      this.definition = buildDefinition();
    }
  }



  /**
   * Construct a extensibleObject object class where the set of allowed
   * attribute types of this object class is implicitly the set of all
   * attribute types of userApplications usage.
   *
   * @param description
   *          The description for this schema definition
   * @param extraProperties
   *          The map of "extra" properties for this schema definition
   */
  ObjectClass(String description,
      Map<String, List<String>> extraProperties)
  {
    super(description, extraProperties);
    this.oid = EXTENSIBLE_OBJECT_OBJECTCLASS_OID;
    this.names =
        Collections.singletonList(EXTENSIBLE_OBJECT_OBJECTCLASS_NAME);
    this.isObsolete = false;
    this.superiorClassOIDs =
        Collections.singleton(TOP_OBJECTCLASS_NAME);
    this.objectClassType = ObjectClassType.AUXILIARY;
    this.requiredAttributeOIDs = Collections.emptySet();
    this.optionalAttributeOIDs = Collections.emptySet();

    this.definition = buildDefinition();
  }



  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }

    if (o instanceof ObjectClass)
    {
      final ObjectClass other = (ObjectClass) o;
      return oid.equals(other.oid);
    }

    return false;
  }



  /**
   * Retrieves the list of optional attributes for this objectclass.
   * Note that this set will not automatically include any optional
   * attributes for superior objectclasses.
   *
   * @return Returns the list of optional attributes for this
   *         objectclass.
   */
  public Iterable<AttributeType> getDeclaredOptionalAttributes()
  {
    return declaredOptionalAttributes;
  }



  /**
   * Retrieves the list of required attributes for this objectclass.
   * Note that this set will not automatically include any required
   * attributes for superior objectclasses.
   *
   * @return Returns the list of required attributes for this
   *         objectclass.
   */
  public Iterable<AttributeType> getDeclaredRequiredAttributes()
  {
    return declaredRequiredAttributes;
  }



  /**
   * Retrieves the name or OID for this schema definition. If it has one
   * or more names, then the primary name will be returned. If it does
   * not have any names, then the OID will be returned.
   *
   * @return The name or OID for this schema definition.
   */
  public String getNameOrOID()
  {
    if (names.isEmpty())
    {
      return oid;
    }
    return names.get(0);
  }



  /**
   * Retrieves an iterable over the set of user-defined names that may
   * be used to reference this schema definition.
   *
   * @return Returns an iterable over the set of user-defined names that
   *         may be used to reference this schema definition.
   */
  public Iterable<String> getNames()
  {
    return names;
  }



  /**
   * Retrieves the objectclass type for this objectclass.
   *
   * @return The objectclass type for this objectclass.
   */
  public ObjectClassType getObjectClassType()
  {

    return objectClassType;
  }



  /**
   * Retrieves the OID for this schema definition.
   *
   * @return The OID for this schema definition.
   */
  public String getOID()
  {

    return oid;
  }



  /**
   * Retrieves the list of all optional attributes for this objectclass
   * and any superior objectclasses that it might have.
   *
   * @return Returns the list of all optional attributes for this
   *         objectclass and any superior objectclasses that it might
   *         have.
   */
  public Iterable<AttributeType> getOptionalAttributes()
  {
    return optionalAttributes;
  }



  /**
   * Retrieves the list of all required attributes for this objectclass
   * and any superior objectclasses that it might have.
   *
   * @return Returns the list of all required attributes for this
   *         objectclass and any superior objectclasses that it might
   *         have.
   */
  public Iterable<AttributeType> getRequiredAttributes()
  {
    return requiredAttributes;
  }



  /**
   * Retrieves the reference to the superior classes for this
   * objectclass.
   *
   * @return The list of superior classes for this objectlass.
   */
  public Iterable<ObjectClass> getSuperiorClasses()
  {
    return superiorClasses;
  }



  @Override
  public int hashCode()
  {
    return oid.hashCode();
  }



  /**
   * Indicates whether this schema definition has the specified name.
   *
   * @param name
   *          The name for which to make the determination.
   * @return <code>true</code> if the specified name is assigned to this
   *         schema definition, or <code>false</code> if not.
   */
  public boolean hasName(String name)
  {
    for (final String n : names)
    {
      if (n.equalsIgnoreCase(name))
      {
        return true;
      }
    }
    return false;
  }



  /**
   * Indicates whether this schema definition has the specified name or
   * OID.
   *
   * @param value
   *          The value for which to make the determination.
   * @return <code>true</code> if the provided value matches the OID or
   *         one of the names assigned to this schema definition, or
   *         <code>false</code> if not.
   */
  public boolean hasNameOrOID(String value)
  {
    return hasName(value) || getOID().equals(value);
  }



  /**
   * Indicates whether this objectclass is a descendant of the provided
   * class.
   *
   * @param objectClass
   *          The objectClass for which to make the determination.
   * @return <code>true</code> if this objectclass is a descendant of
   *         the provided class, or <code>false</code> if not.
   */
  public boolean isDescendantOf(ObjectClass objectClass)
  {
    for (final ObjectClass sup : superiorClasses)
    {
      if (sup.equals(objectClass) || sup.isDescendantOf(objectClass))
      {
        return true;
      }
    }
    return false;
  }



  /**
   * Indicates whether this schema definition is declared "obsolete".
   *
   * @return <code>true</code> if this schema definition is declared
   *         "obsolete", or <code>false</code> if not.
   */
  public boolean isObsolete()
  {
    return isObsolete;
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * optional attribute list for this or any of its superior
   * objectclasses.
   *
   * @param attributeType
   *          The attribute type for which to make the determination.
   * @return <code>true</code> if the provided attribute type is
   *         optional for this objectclass or any of its superior
   *         classes, or <code>false</code> if not.
   */
  public boolean isOptional(AttributeType attributeType)
  {
    return optionalAttributes.contains(attributeType);
  }



  /**
   * Indicates whether the provided attribute type is included in the
   * required attribute list for this or any of its superior
   * objectclasses.
   *
   * @param attributeType
   *          The attribute type for which to make the determination.
   * @return <code>true</code> if the provided attribute type is
   *         required by this objectclass or any of its superior
   *         classes, or <code>false</code> if not.
   */
  public boolean isRequired(AttributeType attributeType)
  {
    return requiredAttributes.contains(attributeType);
  }



  /**
   * Indicates whether the provided attribute type is in the list of
   * required or optional attributes for this objectclass or any of its
   * superior classes.
   *
   * @param attributeType
   *          The attribute type for which to make the determination.
   * @return <code>true</code> if the provided attribute type is
   *         required or allowed for this objectclass or any of its
   *         superior classes, or <code>false</code> if it is not.
   */
  public boolean isRequiredOrOptional(AttributeType attributeType)
  {
    return isRequired(attributeType) || isOptional(attributeType);
  }



  /**
   * Retrieves the string representation of this schema definition in
   * the form specified in RFC 2252.
   *
   * @return The string representation of this schema definition in the
   *         form specified in RFC 2252.
   */
  @Override
  public String toString()
  {
    return definition;
  }



  ObjectClass duplicate()
  {
    return new ObjectClass(oid, names, description, isObsolete,
        superiorClassOIDs, requiredAttributeOIDs,
        optionalAttributeOIDs, objectClassType, extraProperties,
        definition);
  }



  @Override
  void toStringContent(StringBuilder buffer)
  {
    buffer.append(oid);

    if (!names.isEmpty())
    {
      final Iterator<String> iterator = names.iterator();

      final String firstName = iterator.next();
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

    if (description != null && description.length() > 0)
    {
      buffer.append(" DESC '");
      buffer.append(description);
      buffer.append("'");
    }

    if (isObsolete)
    {
      buffer.append(" OBSOLETE");
    }

    if (!superiorClassOIDs.isEmpty())
    {
      final Iterator<String> iterator = superiorClassOIDs.iterator();

      final String firstName = iterator.next();
      if (iterator.hasNext())
      {
        buffer.append(" SUP ( ");
        buffer.append(firstName);

        while (iterator.hasNext())
        {
          buffer.append(" $ ");
          buffer.append(iterator.next());
        }

        buffer.append(" )");
      }
      else
      {
        buffer.append(" SUP ");
        buffer.append(firstName);
      }
    }

    if (objectClassType != null)
    {
      buffer.append(" ");
      buffer.append(objectClassType.toString());
    }

    if (!requiredAttributeOIDs.isEmpty())
    {
      final Iterator<String> iterator =
          requiredAttributeOIDs.iterator();

      final String firstName = iterator.next();
      if (iterator.hasNext())
      {
        buffer.append(" MUST ( ");
        buffer.append(firstName);

        while (iterator.hasNext())
        {
          buffer.append(" $ ");
          buffer.append(iterator.next());
        }

        buffer.append(" )");
      }
      else
      {
        buffer.append(" MUST ");
        buffer.append(firstName);
      }
    }

    if (!optionalAttributeOIDs.isEmpty())
    {
      final Iterator<String> iterator =
          optionalAttributeOIDs.iterator();

      final String firstName = iterator.next();
      if (iterator.hasNext())
      {
        buffer.append(" MAY ( ");
        buffer.append(firstName);

        while (iterator.hasNext())
        {
          buffer.append(" $ ");
          buffer.append(iterator.next());
        }

        buffer.append(" )");
      }
      else
      {
        buffer.append(" MAY ");
        buffer.append(firstName);
      }
    }
  }



  @Override
  void validate(List<Message> warnings, Schema schema)
      throws SchemaException
  {
    if (validated)
    {
      return;
    }
    validated = true;

    // Init a flag to check to inheritance from top (only needed for
    // structural object classes) per RFC 4512
    boolean derivesTop = objectClassType != ObjectClassType.STRUCTURAL;

    if (!superiorClassOIDs.isEmpty())
    {
      superiorClasses =
          new HashSet<ObjectClass>(superiorClassOIDs.size());
      ObjectClass superiorClass;
      for (final String superClassOid : superiorClassOIDs)
      {
        try
        {
          superiorClass = schema.getObjectClass(superClassOid);
        }
        catch (final UnknownSchemaElementException e)
        {
          final Message message =
              WARN_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_SUPERIOR_CLASS.get(
                  oid, superClassOid);
          throw new SchemaException(message, e);
        }

        // Make sure that the inheritance configuration is acceptable.
        final ObjectClassType superiorType =
            superiorClass.getObjectClassType();
        switch (objectClassType)
        {
        case ABSTRACT:
          // Abstract classes may only inherit from other abstract
          // classes.
          if (superiorType != ObjectClassType.ABSTRACT)
          {
            final Message message =
                WARN_ATTR_SYNTAX_OBJECTCLASS_INVALID_SUPERIOR_TYPE.get(
                    oid, objectClassType.toString(), superiorType
                        .toString(), superiorClass.getNameOrOID());
            throw new SchemaException(message);
          }
          break;

        case AUXILIARY:
          // Auxiliary classes may only inherit from abstract classes or
          // other auxiliary classes.
          if (superiorType != ObjectClassType.ABSTRACT
              && superiorType != ObjectClassType.AUXILIARY)
          {
            final Message message =
                WARN_ATTR_SYNTAX_OBJECTCLASS_INVALID_SUPERIOR_TYPE.get(
                    oid, objectClassType.toString(), superiorType
                        .toString(), superiorClass.getNameOrOID());
            throw new SchemaException(message);
          }
          break;

        case STRUCTURAL:
          // Structural classes may only inherit from abstract classes
          // or other structural classes.
          if (superiorType != ObjectClassType.ABSTRACT
              && superiorType != ObjectClassType.STRUCTURAL)
          {
            final Message message =
                WARN_ATTR_SYNTAX_OBJECTCLASS_INVALID_SUPERIOR_TYPE.get(
                    oid, objectClassType.toString(), superiorType
                        .toString(), superiorClass.getNameOrOID());
            throw new SchemaException(message);
          }
          break;
        }

        // All existing structural object classes defined in this schema
        // are implicitly guaranteed to inherit from top
        if (!derivesTop && superiorType == ObjectClassType.STRUCTURAL)
        {
          derivesTop = true;
        }

        // Validate superior object class so we can inherit its
        // attributes.
        superiorClass.validate(warnings, schema);

        // Inherit all required attributes from superior class.
        Iterator<AttributeType> i =
            superiorClass.getRequiredAttributes().iterator();
        if (i.hasNext() && requiredAttributes == Collections.EMPTY_SET)
        {
          requiredAttributes = new HashSet<AttributeType>();
        }
        while (i.hasNext())
        {
          requiredAttributes.add(i.next());
        }

        // Inherit all optional attributes from superior class.
        i = superiorClass.getRequiredAttributes().iterator();
        if (i.hasNext() && requiredAttributes == Collections.EMPTY_SET)
        {
          requiredAttributes = new HashSet<AttributeType>();
        }
        while (i.hasNext())
        {
          requiredAttributes.add(i.next());
        }

        superiorClasses.add(superiorClass);
      }
    }

    if (!derivesTop)
    {
      derivesTop = isDescendantOf(schema.getObjectClass("2.5.6.0"));
    }

    // Structural classes must have the "top" objectclass somewhere
    // in the superior chain.
    if (!derivesTop)
    {
      final Message message =
          WARN_ATTR_SYNTAX_OBJECTCLASS_STRUCTURAL_SUPERIOR_NOT_TOP
              .get(oid);
      throw new SchemaException(message);
    }

    if (oid.equals(EXTENSIBLE_OBJECT_OBJECTCLASS_OID))
    {
      declaredOptionalAttributes =
          new HashSet<AttributeType>(requiredAttributeOIDs.size());
      for (final AttributeType attributeType : schema
          .getAttributeTypes())
      {
        if (attributeType.getUsage() == AttributeUsage.USER_APPLICATIONS)
        {
          declaredOptionalAttributes.add(attributeType);
        }
      }
      optionalAttributes = declaredRequiredAttributes;
      return;
    }

    if (!requiredAttributeOIDs.isEmpty())
    {
      declaredRequiredAttributes =
          new HashSet<AttributeType>(requiredAttributeOIDs.size());
      AttributeType attributeType;
      for (final String requiredAttribute : requiredAttributeOIDs)
      {
        try
        {
          attributeType = schema.getAttributeType(requiredAttribute);
        }
        catch (final UnknownSchemaElementException e)
        {
          // This isn't good because it means that the objectclass
          // requires an attribute type that we don't know anything
          // about.
          final Message message =
              WARN_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_REQUIRED_ATTR.get(
                  oid, requiredAttribute);
          throw new SchemaException(message, e);
        }
        declaredRequiredAttributes.add(attributeType);
      }
      if (requiredAttributes == Collections.EMPTY_SET)
      {
        requiredAttributes = declaredRequiredAttributes;
      }
      else
      {
        requiredAttributes.addAll(declaredRequiredAttributes);
      }
    }

    if (!optionalAttributeOIDs.isEmpty())
    {
      declaredOptionalAttributes =
          new HashSet<AttributeType>(optionalAttributeOIDs.size());
      AttributeType attributeType;
      for (final String optionalAttribute : optionalAttributeOIDs)
      {
        try
        {
          attributeType = schema.getAttributeType(optionalAttribute);
        }
        catch (final UnknownSchemaElementException e)
        {
          // This isn't good because it means that the objectclass
          // requires an attribute type that we don't know anything
          // about.
          final Message message =
              WARN_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_OPTIONAL_ATTR.get(
                  oid, optionalAttribute);
          throw new SchemaException(message, e);
        }
        declaredOptionalAttributes.add(attributeType);
      }
      if (optionalAttributes == Collections.EMPTY_SET)
      {
        optionalAttributes = declaredOptionalAttributes;
      }
      else
      {
        optionalAttributes.addAll(declaredOptionalAttributes);
      }
    }
  }
}
