/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2023-2024 3A Systems, LLC.
 */
package org.opends.server.types;

import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.LDIFWriter.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.DITContentRule;
import org.forgerock.opendj.ldap.schema.DITStructureRule;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.NameForm;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.ObjectClassType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;
import org.opends.server.api.CompressedSchema;
import org.opends.server.api.ProtocolElement;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SubentryManager;
import org.opends.server.types.SubEntry.CollectiveConflictBehavior;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFWriter;

/**
 * This class defines a data structure for a Directory Server entry.
 * It includes a DN and a set of attributes.
 * <BR><BR>
 * The entry also contains a volatile attachment object, which should
 * be used to associate the entry with a special type of object that
 * is based on its contents.  For example, if the entry holds access
 * control information, then the attachment might be an object that
 * contains a representation of that access control definition in a
 * more useful form.  This is only useful if the entry is to be
 * cached, since the attachment may be accessed if the entry is
 * retrieved from the cache, but if the entry is retrieved from the
 * backend repository it cannot be guaranteed to contain any
 * attachment (and in most cases will not).  This attachment is
 * volatile in that it is not always guaranteed to be present, it may
 * be removed or overwritten at any time, and it will be invalidated
 * and removed if the entry is altered in any way.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public class Entry
       implements ProtocolElement
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The set of operational attributes for this entry. */
  private Map<AttributeType,List<Attribute>> operationalAttributes;

  /** The set of user attributes for this entry. */
  private Map<AttributeType,List<Attribute>> userAttributes;

  /**
   * The set of suppressed real attributes for this entry. It contains real
   * attributes that have been overridden by virtual attributes.
   */
  private final Map<AttributeType, List<Attribute>> suppressedAttributes = new LinkedHashMap<>();

  /** The set of objectclasses for this entry. */
  private Map<ObjectClass,String> objectClasses;

  private Attribute objectClassAttribute;

  /** The DN for this entry. */
  private DN dn;

  /** A generic attachment that may be used to associate this entry with some other object. */
  private transient Object attachment;

  /**
   * Creates a new entry with the provided information.
   *
   * @param  dn                     The distinguished name for this
   *                                entry.
   * @param  objectClasses          The set of objectclasses for this
   *                                entry as a mapping between the
   *                                objectclass and the name to use to
   *                                reference it.
   * @param  userAttributes         The set of user attributes for
   *                                this entry as a mapping between
   *                                the attribute type and the list of
   *                                attributes with that type.
   * @param  operationalAttributes  The set of operational attributes
   *                                for this entry as a mapping
   *                                between the attribute type and the
   *                                list of attributes with that type.
   */
  public Entry(DN dn, Map<ObjectClass,String> objectClasses,
               Map<AttributeType,List<Attribute>> userAttributes,
               Map<AttributeType,List<Attribute>> operationalAttributes)
  {
    setDN(dn);

    this.objectClasses = newMapIfNull(objectClasses);
    this.userAttributes = newMapIfNull(userAttributes);
    this.operationalAttributes = newMapIfNull(operationalAttributes);
  }

  /**
   * Returns a new Map if the passed in Map is null.
   *
   * @param <K>
   *          the type of the key
   * @param <V>
   *          the type of the value
   * @param map
   *          the map to test
   * @return a new Map if the passed in Map is null.
   */
  private <K, V> Map<K, V> newMapIfNull(Map<K, V> map)
  {
    if (map != null)
    {
      return map;
    }
    return new LinkedHashMap<>();
  }



  /**
   * Retrieves the distinguished name for this entry.
   *
   * @return  The distinguished name for this entry.
   */
  public DN getName()
  {
    return dn;
  }



  /**
   * Specifies the distinguished name for this entry.
   *
   * @param  dn  The distinguished name for this entry.
   */
  public void setDN(DN dn)
  {
    if (dn == null)
    {
      this.dn = DN.rootDN();
    }
    else
    {
      this.dn = dn;
    }

    attachment = null;
  }



  /**
   * Retrieves the set of objectclasses defined for this entry.  The
   * caller should be allowed to modify the contents of this list, but
   * if it does then it should also invalidate the attachment.
   *
   * @return  The set of objectclasses defined for this entry.
   */
  public Map<ObjectClass,String> getObjectClasses()
  {
    return objectClasses;
  }



  /**
   * Indicates whether this entry has the specified objectclass.
   *
   * @param  objectClass  The objectclass for which to make the
   *                      determination.
   *
   * @return  {@code true} if this entry has the specified
   *          objectclass, or {@code false} if not.
   */
  public boolean hasObjectClass(ObjectClass objectClass)
  {
    return objectClasses.containsKey(objectClass);
  }



  /**
   * Retrieves the structural objectclass for this entry.
   *
   * @return  The structural objectclass for this entry, or
   *          {@code null} if there is none for some reason.  If
   *          there are multiple structural classes in the entry, then
   *          the first will be returned.
   */
  public ObjectClass getStructuralObjectClass()
  {
    ObjectClass structuralClass = null;

    for (ObjectClass oc : objectClasses.keySet())
    {
      if (oc.getObjectClassType() == ObjectClassType.STRUCTURAL)
      {
        if (structuralClass == null)
        {
          structuralClass = oc;
        }
        else if (oc.isDescendantOf(structuralClass))
        {
          structuralClass = oc;
        }
      }
    }

    return structuralClass;
  }



  /**
   * Adds the provided objectClass to this entry.
   *
   * @param  oc The objectClass to add to this entry.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to add the objectclass to this
   *                              entry.
   */
  public void addObjectClass(ObjectClass oc)
         throws DirectoryException
  {
    attachment = null;

    if (objectClasses.containsKey(oc))
    {
      LocalizableMessage message = ERR_ENTRY_ADD_DUPLICATE_OC.get(oc.getNameOrOID(), dn);
      throw new DirectoryException(OBJECTCLASS_VIOLATION, message);
    }

    objectClasses.put(oc, oc.getNameOrOID());
  }



  /** Iterator over a {@code Collection<List<Attribute>>}. */
  private static final class CollectionListIterator implements Iterator<Attribute>
  {
    private final Iterator<List<Attribute>> parentIt;
    private List<Attribute> subList = Collections.emptyList();
    private Iterator<Attribute> subIt = subList.iterator();

    private CollectionListIterator(Collection<List<Attribute>> list)
    {
      this.parentIt = Reject.checkNotNull(list).iterator();
      advance();
    }

    private void advance()
    {
      while (!subIt.hasNext())
      {
        if (!parentIt.hasNext())
        {
          return;
        }
        subList = parentIt.next();
        subIt = subList.iterator();
      }
    }

    @Override
    public boolean hasNext()
    {
      return subIt.hasNext();
    }

    @Override
    public Attribute next()
    {
      final Attribute result = subIt.next();
      if (!subIt.hasNext())
      {
        advance();
      }
      return result;
    }

    @Override
    public void remove()
    {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Returns an {@code Iterable} containing all of the attributes in this entry,
   * excluding the objectClass attribute.
   * <p>
   * The returned {@code Iterable} may NOT be used to remove attributes.
   *
   * @return An {@code Iterable} containing all of the attributes.
   */
  public Iterable<Attribute> getAllAttributes()
  {
    /** Iterator over all the attributes of this entry. */
    final class AllAttributesIterator implements Iterator<Attribute>
    {
      private boolean iteratesOnOperationalAttributes;
      private Iterator<Attribute> currentIterator = new CollectionListIterator(getUserAttributes().values());

      @Override
      public boolean hasNext()
      {
        if (currentIterator.hasNext())
        {
          return true;
        }
        if (iteratesOnOperationalAttributes)
        {
          return false;
        }
        iteratesOnOperationalAttributes = true;
        currentIterator = new CollectionListIterator(getOperationalAttributes().values());
        return currentIterator.hasNext();
      }

      @Override
      public Attribute next()
      {
        return currentIterator.next();
      }

      @Override
      public void remove()
      {
        currentIterator.remove();
      }
    }

    /** Can return an iterator over all the attributes of this entry. */
    final class AllAttributesIterable implements Iterable<Attribute>
    {
      @Override
      public Iterator<Attribute> iterator()
      {
        return new AllAttributesIterator();
      }
    }
    return new AllAttributesIterable();
  }

  /**
   * Retrieves the entire set of user (i.e., non-operational)
   * attributes for this entry.  The caller should be allowed to
   * modify the contents of this list, but if it does then it should
   * also invalidate the attachment.
   *
   * @return  The entire set of user attributes for this entry.
   */
  public Map<AttributeType,List<Attribute>> getUserAttributes()
  {
    return userAttributes;
  }



  /**
   * Retrieves the entire set of operational attributes for this
   * entry.  The caller should be allowed to modify the contents of
   * this list, but if it does then it should also invalidate the
   * attachment.
   *
   * @return  The entire set of operational attributes for this entry.
   */
  public Map<AttributeType,List<Attribute>> getOperationalAttributes()
  {
    return operationalAttributes;
  }



  /**
   * Retrieves an attribute holding the objectclass information for
   * this entry.  The returned attribute must not be altered.
   *
   * @return  An attribute holding the objectclass information for
   *          this entry, or {@code null} if it does not have any
   *          objectclass information.
   */
  public Attribute getObjectClassAttribute()
  {
    if (objectClasses == null || objectClasses.isEmpty())
    {
      return null;
    }

    if(objectClassAttribute == null)
    {
      AttributeBuilder builder = new AttributeBuilder(CoreSchema.getObjectClassAttributeType());
      builder.addAllStrings(objectClasses.values());
      objectClassAttribute = builder.toAttribute();
    }

    return objectClassAttribute;
  }



  /**
   * Indicates whether this entry contains the specified attribute.
   * Any subordinate attribute of the specified attribute will also be
   * used in the determination.
   *
   * @param attributeType
   *          The attribute type for which to make the determination.
   * @return {@code true} if this entry contains the specified
   *         attribute, or {@code false} if not.
   */
  public boolean hasAttribute(AttributeType attributeType)
  {
    return hasAttribute(AttributeDescription.create(attributeType), true);
  }


  /**
   * Indicates whether this entry contains the specified attribute.
   *
   * @param  attributeType       The attribute type for which to
   *                             make the determination.
   * @param  includeSubordinates Whether to include any subordinate
   *                             attributes of the attribute type
   *                             being retrieved.
   *
   * @return  {@code true} if this entry contains the specified
   *          attribute, or {@code false} if not.
   */
  public boolean hasAttribute(AttributeType attributeType,
                              boolean includeSubordinates)
  {
    return hasAttribute(AttributeDescription.create(attributeType), includeSubordinates);
  }



  /**
   * Indicates whether this entry contains the specified attribute
   * with all of the options in the provided set. Any subordinate
   * attribute of the specified attribute will also be used in the
   * determination.
   *
   * @param attributeDescription
   *          The attribute description for which to make the determination.
   * @return {@code true} if this entry contains the specified
   *         attribute, or {@code false} if not.
   */
  public boolean hasAttribute(AttributeDescription attributeDescription)
  {
    return hasAttribute(attributeDescription, true);
  }

  /**
   * Indicates whether this entry contains the specified attribute with all of the options in the
   * provided set.
   *
   * @param attributeDescription
   *          The attribute description for which to make the determination.
   * @param includeSubordinates
   *          Whether to include any subordinate attributes of the attribute type being retrieved.
   * @return {@code true} if this entry contains the specified attribute, or {@code false}
   *         if not.
   */
  public boolean hasAttribute(AttributeDescription attributeDescription, boolean includeSubordinates)
  {
    AttributeType attributeType = attributeDescription.getAttributeType();
    if (attributeType.isObjectClass())
    {
      return !objectClasses.isEmpty() && !attributeDescription.hasOptions();
    }

    if (!includeSubordinates)
    {
      // It's possible that there could be an attribute without any
      // values, which we should treat as not having the requested attribute.
      Attribute attribute = getAttribute(attributeDescription);
      return attribute != null && !attribute.isEmpty();
    }

    return hasAttributeOrSubType(attributeDescription, userAttributes)
        || hasAttributeOrSubType(attributeDescription, operationalAttributes);
  }

  /**
   * Returns the attributes Map corresponding to the operational status of the
   * supplied attribute type.
   *
   * @param attrType
   *          the attribute type
   * @return the user of operational attributes Map
   */
  private Map<AttributeType, List<Attribute>> getUserOrOperationalAttributes(
      AttributeType attrType)
  {
    if (attrType.isOperational())
    {
      return operationalAttributes;
    }
    return userAttributes;
  }

  /**
   * Return the List of attributes for the passed in attribute type.
   *
   * @param attrType
   *          the attribute type
   * @return the List of user or operational attributes
   */
  private List<Attribute> getAllAttributes0(AttributeType attrType)
  {
    return getUserOrOperationalAttributes(attrType).get(attrType);
  }

  /**
   * Puts the supplied List of attributes for the passed in attribute type into
   * the map of attributes.
   *
   * @param attrType
   *          the attribute type
   * @param attributes
   *          the List of user or operational attributes to put
   */
  private void putAttributes(AttributeType attrType, List<Attribute> attributes)
  {
    getUserOrOperationalAttributes(attrType).put(attrType, attributes);
  }

  /**
   * Removes the List of attributes for the passed in attribute type from the
   * map of attributes.
   *
   * @param attrType
   *          the attribute type
   */
  private void removeAttributes(AttributeType attrType)
  {
    getUserOrOperationalAttributes(attrType).remove(attrType);
  }

  /**
   * Retrieves the requested attribute element(s) for the specified
   * attribute type. The list returned may include multiple elements
   * if the same attribute exists in the entry multiple times with
   * different sets of options. It may also include any subordinate
   * attributes of the attribute being retrieved.
   *
   * @param attributeType
   *          The attribute type to retrieve.
   * @return The requested attribute element(s) for the specified
   *         attribute type, or an empty list if the specified
   *         attribute type is not present in this entry.
   */
  public List<Attribute> getAllAttributes(AttributeType attributeType)
  {
    return getAllAttributes(attributeType, true);
  }


  /**
   * Retrieves the requested attribute element(s) for the specified
   * attribute type.  The list returned may include multiple elements
   * if the same attribute exists in the entry multiple times with
   * different sets of options.
   *
   * @param  attributeType       The attribute type to retrieve.
   * @param  includeSubordinates Whether to include any subordinate
   *                             attributes of the attribute type
   *                             being retrieved.
   *
   * @return  The requested attribute element(s) for the specified
   *          attribute type, or an empty list if the specified
   *          attribute type is not present in this entry.
   */
  public List<Attribute> getAllAttributes(AttributeType attributeType,
                                      boolean includeSubordinates)
  {
    if (includeSubordinates && !attributeType.isObjectClass())
    {
      List<Attribute> attributes = new LinkedList<>();
      addAttributeTypeOrSubTypeValue(attributes, attributeType, userAttributes);
      addAttributeTypeOrSubTypeValue(attributes, attributeType, operationalAttributes);
      return attributes;
    }

    List<Attribute> attributes = userAttributes.get(attributeType);
    if (attributes != null)
    {
      return attributes;
    }
    attributes = operationalAttributes.get(attributeType);
    if (attributes != null)
    {
      return attributes;
    }
    if (attributeType.isObjectClass() && !objectClasses.isEmpty())
    {
      return newArrayList(getObjectClassAttribute());
    }
    return Collections.emptyList();
  }

  private void addAttributeTypeOrSubTypeValue(Collection<Attribute> results, AttributeType attrType,
      Map<AttributeType, List<Attribute>> attrsMap)
  {
    for (Map.Entry<AttributeType, List<Attribute>> mapEntry : attrsMap.entrySet())
    {
      if (attrType.isSuperTypeOf(mapEntry.getKey()))
      {
        results.addAll(mapEntry.getValue());
      }
    }
  }

  private void addAttributeTypeOrSubTypeValue(Collection<Attribute> results, AttributeDescription attrDesc,
      Map<AttributeType, List<Attribute>> attrsMap)
  {
    for (Map.Entry<AttributeType, List<Attribute>> mapEntry : attrsMap.entrySet())
    {
      if (!attrDesc.getAttributeType().isSuperTypeOf(mapEntry.getKey()))
      {
        continue;
      }

      for (Attribute attribute : mapEntry.getValue())
      {
        if (attrDesc.isSuperTypeOf(attribute.getAttributeDescription()))
        {
          results.add(attribute);
        }
      }
    }
  }

  private boolean hasAttributeOrSubType(AttributeDescription attrDesc, Map<AttributeType, List<Attribute>> attrsMap)
  {
    for (Map.Entry<AttributeType, List<Attribute>> mapEntry : attrsMap.entrySet())
    {
      if (!attrDesc.getAttributeType().isSuperTypeOf(mapEntry.getKey()))
      {
        continue;
      }

      for (Attribute attribute : mapEntry.getValue())
      {
        // It's possible that there could be an attribute without any values,
        // which we should treat as not having the requested attribute.
        if (!attribute.isEmpty() && attrDesc.isSuperTypeOf(attribute.getAttributeDescription()))
        {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Retrieves the requested attribute element(s) for the attribute
   * with the specified name or OID.  The list returned may include
   * multiple elements if the same attribute exists in the entry
   * multiple times with different sets of options. It may also
   * include any subordinate attributes of the attribute being
   * retrieved.
   * <BR><BR>
   * Note that this method should only be used in cases in which the
   * Directory Server schema has no reference of an attribute type
   * with the specified name.  It is not as accurate or efficient as
   * the version of this method that takes an
   * <CODE>AttributeType</CODE> argument.
   *
   * @param  nameOrOID  The name or OID of the attribute to return
   * @return  The requested attribute element(s) for the specified
   *          attribute type, or an empty list if the specified
   *          attribute type is not present in this entry.
   */
  public Iterable<Attribute> getAllAttributes(String nameOrOID)
  {
    for (AttributeType attr : userAttributes.keySet())
    {
      if (attr.hasNameOrOID(nameOrOID))
      {
        return getAllAttributes(attr);
      }
    }

    for (AttributeType attr : operationalAttributes.keySet())
    {
      if (attr.hasNameOrOID(nameOrOID))
      {
        return getAllAttributes(attr);
      }
    }

    if (CoreSchema.getObjectClassAttributeType().hasNameOrOID(nameOrOID)
        && !objectClasses.isEmpty())
    {
      return newLinkedList(getObjectClassAttribute());
    }
    return Collections.emptyList();
  }

  /**
   * Retrieves the requested attribute element(s) for the specified
   * attribute description.  The list returned may include multiple elements
   * if the same attribute exists in the entry multiple times with
   * different sets of options. It may also include any subordinate
   * attributes of the attribute being retrieved.
   *
   * @param  attributeDescription The attribute description to retrieve.
   * @return  The requested attribute element(s) for the specified
   *          attribute type, or an empty list if the specified
   *          attribute type is not present in this entry with the
   *          provided set of options.
   */
  public Iterable<Attribute> getAllAttributes(AttributeDescription attributeDescription)
  {
    AttributeType attributeType = attributeDescription.getAttributeType();

    final List<Attribute> attributes = new LinkedList<>();
    if (!attributeType.isObjectClass())
    {
      addAttributeTypeOrSubTypeValue(attributes, attributeDescription, userAttributes);
      addAttributeTypeOrSubTypeValue(attributes, attributeDescription, operationalAttributes);
      return attributes;
    }

    List<Attribute> attrs = userAttributes.get(attributeType);
    if (attrs == null)
    {
      attrs = operationalAttributes.get(attributeType);
      if (attrs == null)
      {
        if (attributeType.isObjectClass()
            && !objectClasses.isEmpty()
            && !attributeDescription.hasOptions())
        {
          attributes.add(getObjectClassAttribute());
          return attributes;
        }
        return Collections.emptyList();
      }
    }
    attributes.addAll(attrs);

    onlyKeepAttributesWithAllOptions(attributes, attributeDescription);

    return attributes;
  }

  /**
   * Returns a parser for the named attribute contained in this entry.
   * <p>
   * The attribute description will be decoded using the schema associated
   * with this entry (usually the default schema).
   *
   * @param attributeDescription
   *            The name of the attribute to be parsed.
   * @return A parser for the named attribute.
   * @throws LocalizedIllegalArgumentException
   *             If {@code attributeDescription} could not be decoded using
   *             the schema associated with this entry.
   * @throws NullPointerException
   *             If {@code attributeDescription} was {@code null}.
   */
  public AttributeParser parseAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    Iterator<Attribute> it = getAllAttributes(attributeDescription).iterator();
    return AttributeParser.parseAttribute(it.hasNext() ? it.next() : null);
  }



  /**
   * Indicates whether this entry contains the specified user
   * attribute.
   *
   * @param attributeType
   *          The attribute type for which to make the determination.
   * @return {@code true} if this entry contains the specified
   *         user attribute, or {@code false} if not.
   */
  public boolean hasUserAttribute(AttributeType attributeType)
  {
    return hasAttribute(userAttributes, attributeType);
  }



  /**
   * Retrieves the requested user attribute element(s) for the
   * specified attribute type.  The list returned may include multiple
   * elements if the same attribute exists in the entry multiple times
   * with different sets of options.
   *
   * @param  attributeType  The attribute type to retrieve.
   *
   * @return  The requested attribute element(s) for the specified
   *          attribute type, or an empty list if there is no such
   *          user attribute.
   */
  public List<Attribute> getUserAttribute(AttributeType attributeType)
  {
    return getAllAttributes(attributeType, userAttributes);
  }

  private List<Attribute> getAllAttributes(AttributeType attributeType,
      Map<AttributeType, List<Attribute>> attrs)
  {
    List<Attribute> results = new LinkedList<>();
    addAttributeTypeOrSubTypeValue(results, attributeType, attrs);
    return results;
  }

  private List<Attribute> getAllAttributes(AttributeDescription attributeDescription,
      Map<AttributeType, List<Attribute>> attrs)
  {
    List<Attribute> results = new LinkedList<>();
    addAttributeTypeOrSubTypeValue(results, attributeDescription, attrs);
    return results;
  }

  /**
   * Removes all the attributes that do not have all the supplied options.
   *
   * @param attributes
   *          the attributes to filter.
   * @param attributeDescription
   *          contains the options to look for
   */
  private void onlyKeepAttributesWithAllOptions(List<Attribute> attributes, AttributeDescription attributeDescription)
  {
    for (Iterator<Attribute> it = attributes.iterator(); it.hasNext();)
    {
      Attribute a = it.next();
      if (!a.getAttributeDescription().isSubTypeOf(attributeDescription))
      {
        it.remove();
      }
    }
  }

  /**
   * Indicates whether this entry contains the specified operational
   * attribute.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  {@code true} if this entry contains the specified
   *          operational attribute, or {@code false} if not.
   */
  public boolean hasOperationalAttribute(AttributeType attributeType)
  {
    return hasAttribute(operationalAttributes, attributeType);
  }

  private boolean hasAttribute(Map<AttributeType, List<Attribute>> attributes, AttributeType attributeType)
  {
    for (AttributeType key : attributes.keySet())
    {
      if (attributeType.isSuperTypeOf(key))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Retrieves the requested operational attribute element(s) for the
   * specified attribute type.  The list returned may include multiple
   * elements if the same attribute exists in the entry multiple times
   * with different sets of options.
   *
   * @param  attributeType  The attribute type to retrieve.
   *
   * @return  The requested attribute element(s) for the specified
   *          attribute type, or an empty list if there is no such
   *          operational attribute.
   */
  public List<Attribute> getOperationalAttribute(AttributeType attributeType)
  {
    return getAllAttributes(attributeType, operationalAttributes);
  }




  /**
   * Retrieves the requested operational attribute element(s) for the
   * specified attribute type.  The list returned may include multiple
   * elements if the same attribute exists in the entry multiple times
   * with different sets of options.
   *
   * @param  attributeDescription  The attribute description to retrieve.
   *
   * @return  The requested attribute element(s) for the specified
   *          attribute type, or an empty list if there is no such
   *          operational attribute with the specified set of options.
   */
  public List<Attribute> getOperationalAttribute(AttributeDescription attributeDescription)
  {
    return getAllAttributes(attributeDescription, operationalAttributes);
  }



  /**
   * Puts the provided attribute in this entry.  If an attribute
   * already exists with the provided type, it will be overwritten.
   * Otherwise, a new attribute will be added.  Note that no
   * validation will be performed.
   *
   * @param  attributeType  The attribute type for the set of
   *                        attributes to add.
   * @param  attributeList  The set of attributes to add for the given
   *                        type.
   */
  public void putAttribute(AttributeType attributeType, List<Attribute> attributeList)
  {
    attachment = null;


    // See if there is already a set of attributes with the specified type.
    // If so, then overwrite it.
    List<Attribute> attrList = userAttributes.get(attributeType);
    if (attrList != null)
    {
      userAttributes.put(attributeType, attributeList);
      return;
    }

    attrList = operationalAttributes.get(attributeType);
    if (attrList != null)
    {
      operationalAttributes.put(attributeType, attributeList);
      return;
    }

    putAttributes(attributeType, attributeList);
  }



  /**
   * Ensures that this entry contains the provided attribute and its
   * values. If an attribute with the provided type already exists,
   * then its attribute values will be merged.
   * <p>
   * This method handles object class additions but will not perform
   * any object class validation. In particular, it will create
   * default object classes when an object class is unknown.
   * <p>
   * This method implements LDAP modification add semantics, with the
   * exception that it allows empty attributes to be added.
   *
   * @param attribute
   *          The attribute to add or merge with this entry.
   * @param duplicateValues
   *          A list to which any duplicate values will be added.
   */
  public void addAttribute(Attribute attribute, Collection<? super ByteString> duplicateValues)
  {
    setAttribute(attribute, duplicateValues, false /* merge */);
  }



  /**
   * Puts the provided attribute into this entry. If an attribute with
   * the provided type and options already exists, then it will be
   * replaced. If the provided attribute is empty then any existing
   * attribute will be completely removed.
   * <p>
   * This method handles object class replacements but will not
   * perform any object class validation. In particular, it will
   * create default object classes when an object class is unknown.
   * <p>
   * This method implements LDAP modification replace semantics.
   *
   * @param attribute
   *          The attribute to replace in this entry.
   */
  public void replaceAttribute(Attribute attribute)
  {
    // There can never be duplicate values for a replace.
    setAttribute(attribute, null, true /* replace */);
  }



  /**
   * Increments an attribute in this entry by the amount specified in
   * the provided attribute.
   *
   * @param attribute
   *          The attribute identifying the attribute to be increment
   *          and the amount it is to be incremented by. The attribute
   *          must contain a single value.
   * @throws DirectoryException
   *           If a problem occurs while attempting to increment the
   *           provided attribute. This may occur if the provided
   *           attribute was not single valued or if it could not be
   *           parsed as an integer of if the existing attribute
   *           values could not be parsed as integers.
   */
  public void incrementAttribute(Attribute attribute) throws DirectoryException
  {
    AttributeDescription attrDesc = attribute.getAttributeDescription();
    Attribute a = getAttribute(attrDesc);
    if (a == null)
    {
      LocalizableMessage message = ERR_ENTRY_INCREMENT_NO_SUCH_ATTRIBUTE.get(attrDesc);
      throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE, message);
    }

    // Decode the increment.
    Iterator<ByteString> i = attribute.iterator();
    if (!i.hasNext())
    {
      LocalizableMessage message = ERR_ENTRY_INCREMENT_INVALID_VALUE_COUNT.get(attrDesc);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    String incrementValue = i.next().toString();
    long increment = parseLong(incrementValue, attrDesc);

    if (i.hasNext())
    {
      LocalizableMessage message = ERR_ENTRY_INCREMENT_INVALID_VALUE_COUNT.get(attrDesc);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    // Increment each attribute value by the specified amount.
    AttributeBuilder builder = new AttributeBuilder(a.getAttributeDescription());
    for (ByteString v : a)
    {
      long currentValue = parseLong(v.toString(), attrDesc);
      long newValue = currentValue + increment;
      builder.add(String.valueOf(newValue));
    }

    replaceAttribute(builder.toAttribute());
  }

  private long parseLong(String value, AttributeDescription attrDesc) throws DirectoryException
  {
    try
    {
      return Long.parseLong(value);
    }
    catch (NumberFormatException e)
    {
      LocalizableMessage message = ERR_ENTRY_INCREMENT_CANNOT_PARSE_AS_INT.get(attrDesc);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
  }

  /**
   * Removes all instances of the specified attribute type from this
   * entry, including any instances with options. If the provided
   * attribute type is the objectclass type, then all objectclass
   * values will be removed (but must be replaced for the entry to be
   * valid). If the specified attribute type is not present in this
   * entry, then this method will have no effect.
   *
   * @param attributeType
   *          The attribute type for the attribute to remove from this
   *          entry.
   * @return {@code true} if the attribute was found and
   *         removed, or {@code false} if it was not present in
   *         the entry.
   */
  public boolean removeAttribute(AttributeType attributeType)
  {
    attachment = null;

    if (attributeType.isObjectClass())
    {
      objectClasses.clear();
      return true;
    }
    return userAttributes.remove(attributeType) != null
        || operationalAttributes.remove(attributeType) != null;
  }



  /**
   * Ensures that this entry does not contain the provided attribute
   * values. If the provided attribute is empty, then all values of
   * the associated attribute type will be removed. Otherwise, only
   * the specified values will be removed.
   * <p>
   * This method handles object class deletions.
   * <p>
   * This method implements LDAP modification delete semantics.
   *
   * @param attribute
   *          The attribute containing the information to use to
   *          perform the removal.
   * @param missingValues
   *          A list to which any values contained in the provided
   *          attribute but not present in the entry will be added.
   * @return {@code true} if the attribute type was present and
   *         the specified values that were present were removed, or
   *         {@code false} if the attribute type was not
   *         present in the entry. If the attribute type was present
   *         but only contained some of the values in the provided
   *         attribute, then this method will return {@code true}
   *         but will add those values to the provided list.
   */
  public boolean removeAttribute(Attribute attribute, Collection<? super ByteString> missingValues)
  {
    attachment = null;

    AttributeDescription attrDesc = attribute.getAttributeDescription();
    AttributeType attrType = attrDesc.getAttributeType();
    if (attrType.isObjectClass())
    {
      return removeObjectClassAttribute(attribute, missingValues);
    }

    return removeNonObjectClassAttribute(attribute, missingValues);
  }

  private boolean removeObjectClassAttribute(Attribute attribute, Collection<? super ByteString> missingValues)
  {
    AttributeType attrType = attribute.getAttributeDescription().getAttributeType();
    if (attribute.isEmpty())
    {
      objectClasses.clear();
      return true;
    }

    boolean allSuccessful = true;

    MatchingRule rule = attrType.getEqualityMatchingRule();
    for (ByteString v : attribute)
    {
      String ocName = toLowerName(rule, v);

      for (ObjectClass oc : objectClasses.keySet())
      {
        if (oc.hasNameOrOID(ocName))
        {
          objectClasses.remove(oc);
          return true;
        }
      }

      allSuccessful = false;
      missingValues.add(v);
    }

    return allSuccessful;
  }

  private boolean removeNonObjectClassAttribute(Attribute attribute, Collection<? super ByteString> missingValues)
  {
    AttributeDescription attrDesc = attribute.getAttributeDescription();
    AttributeType attrType = attrDesc.getAttributeType();
    List<Attribute> attributes = getAllAttributes0(attrType);
    if (attributes == null)
    {
      // There are no attributes with the same attribute type.
      for (ByteString v : attribute)
      {
        missingValues.add(v);
      }
      return false;
    }

    // There are already attributes with the same attribute type.
    for (ListIterator<Attribute> it = attributes.listIterator(); it.hasNext();)
    {
      Attribute a = it.next();
      if (a.getAttributeDescription().equals(attrDesc))
      {
        if (attribute.isEmpty())
        {
          // Remove the entire attribute.
          it.remove();
        }
        else
        {
          // Remove Specified values.
          AttributeBuilder builder = new AttributeBuilder(a);
          for (ByteString v : attribute)
          {
            if (!builder.remove(v))
            {
              missingValues.add(v);
            }
          }

          // Remove / replace the attribute as necessary.
          if (!builder.isEmpty())
          {
            it.set(builder.toAttribute());
          }
          else
          {
            it.remove();
          }
        }

        // If the attribute list is now empty remove it.
        if (attributes.isEmpty())
        {
          removeAttributes(attrType);
        }

        return true;
      }
    }

    // No matching attribute found.
    return false;
  }

  private String toLowerName(MatchingRule rule, ByteString value)
  {
    try
    {
      return normalize(rule, value).toString();
    }
    catch (Exception e)
    {
      logger.traceException(e);
      return toLowerCase(value.toString());
    }
  }

  /**
   * Indicates whether this entry contains the specified attribute value.
   *
   * @param attributeDescription
   *          The attribute description for the attribute.
   * @param value
   *          The value for the attribute.
   * @return {@code true} if this entry contains the specified attribute value, {@code false}
   *         otherwise.
   */
  public boolean hasValue(AttributeDescription attributeDescription, ByteString value)
  {
    Attribute attr = getAttribute(attributeDescription);
    return attr != null && attr.contains(value);
  }

  /**
   * Indicates whether this entry contains the specified attribute value.
   *
   * @param attributeType
   *          The attribute type for the attribute.
   * @param value
   *          The value for the attribute.
   * @return {@code true} if this entry contains the specified attribute value, {@code false}
   *         otherwise.
   */
  public boolean hasValue(AttributeType attributeType, ByteString value)
  {
    for (Attribute a : getAllAttributes(attributeType))
    {
      if (!a.getAttributeDescription().hasOptions() && a.contains(value))
      {
        return true;
      }
    }
    return false;
  }



  /**
   * Applies the provided modification to this entry.  No schema
   * checking will be performed.
   *
   * @param  mod  The modification to apply to this entry.
   * @param  relaxConstraints indicates if the modification
   *                          constraints are relaxed to match
   *                          the ones of a set (add existing
   *                          value and delete absent value do not fail)
   *
   * @throws  DirectoryException  If a problem occurs while
   *                              attempting to apply the
   *                              modification. Note
   *                              that even if a problem occurs, then
   *                              the entry may have been altered in some way.
   */
  public void applyModification(Modification mod, boolean relaxConstraints)
         throws DirectoryException
  {
    AttributeType t = mod.getAttribute().getAttributeDescription().getAttributeType();
    if (t.isObjectClass())
    {
      applyModificationToObjectclass(mod, relaxConstraints);
    }
    else
    {
      applyModificationToNonObjectclass(mod, relaxConstraints);
    }
  }

  private void applyModificationToObjectclass(Modification mod, boolean relaxConstraints) throws DirectoryException
  {
    Attribute a = mod.getAttribute();

    Map<ObjectClass, String> ocs = new LinkedHashMap<>();
    for (ByteString v : a)
    {
      String ocName = v.toString();
      ocs.put(getSchema().getObjectClass(ocName), ocName);
    }

    AttributeDescription attrDesc = a.getAttributeDescription();
    switch (mod.getModificationType().asEnum())
    {
    case ADD:
      for (Map.Entry<ObjectClass, String> entry : ocs.entrySet())
      {
        ObjectClass oc = entry.getKey();
        if (objectClasses.containsKey(oc))
        {
          if (!relaxConstraints)
          {
            LocalizableMessage message = ERR_ENTRY_DUPLICATE_VALUES.get(attrDesc);
            throw new DirectoryException(ATTRIBUTE_OR_VALUE_EXISTS, message);
          }
        }
        else
        {
          objectClasses.put(oc, entry.getValue());
        }
      }
      objectClassAttribute = null;
      break;

    case DELETE:
      for (ObjectClass oc : ocs.keySet())
      {
        if (objectClasses.remove(oc) == null && !relaxConstraints)
        {
          LocalizableMessage message = ERR_ENTRY_NO_SUCH_VALUE.get(attrDesc);
          throw new DirectoryException(NO_SUCH_ATTRIBUTE, message);
        }
      }
      objectClassAttribute = null;
      break;

    case REPLACE:
      objectClasses = ocs;
      objectClassAttribute = null;
      break;

    case INCREMENT:
      LocalizableMessage message = ERR_ENTRY_OC_INCREMENT_NOT_SUPPORTED.get();
      throw new DirectoryException(CONSTRAINT_VIOLATION, message);

    default:
      message = ERR_ENTRY_UNKNOWN_MODIFICATION_TYPE.get(mod.getModificationType());
      throw new DirectoryException(UNWILLING_TO_PERFORM, message);
    }
  }

  private static Schema getSchema()
  {
    return DirectoryServer.getInstance().getServerContext().getSchema();
  }

  private void applyModificationToNonObjectclass(Modification mod, boolean relaxConstraints) throws DirectoryException
  {
    Attribute a = mod.getAttribute();
    switch (mod.getModificationType().asEnum())
    {
    case ADD:
      List<ByteString> duplicateValues = new LinkedList<>();
      addAttribute(a, duplicateValues);
      if (!duplicateValues.isEmpty() && !relaxConstraints)
      {
        LocalizableMessage message = ERR_ENTRY_DUPLICATE_VALUES.get(a.getAttributeDescription());
        throw new DirectoryException(ATTRIBUTE_OR_VALUE_EXISTS, message);
      }
      break;

    case DELETE:
      List<ByteString> missingValues = new LinkedList<>();
      removeAttribute(a, missingValues);
      if (!missingValues.isEmpty() && !relaxConstraints)
      {
        LocalizableMessage message = ERR_ENTRY_NO_SUCH_VALUE.get(a.getAttributeDescription());
        throw new DirectoryException(NO_SUCH_ATTRIBUTE, message);
      }
      break;

    case REPLACE:
      replaceAttribute(a);
      break;

    case INCREMENT:
      incrementAttribute(a);
      break;

    default:
      LocalizableMessage message = ERR_ENTRY_UNKNOWN_MODIFICATION_TYPE.get(mod.getModificationType());
      throw new DirectoryException(UNWILLING_TO_PERFORM, message);
    }
  }

  /**
   * Applies the provided modification to this entry.  No schema
   * checking will be performed.
   *
   * @param  mod  The modification to apply to this entry.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to apply the modification.  Note
   *                              that even if a problem occurs, then
   *                              the entry may have been altered in some way.
   */
  public void applyModification(Modification mod) throws DirectoryException
  {
    applyModification(mod, false);
  }

  /**
   * Applies all of the provided modifications to this entry.
   *
   * @param  mods  The modifications to apply to this entry.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to apply the modifications.  Note
   *                              that even if a problem occurs, then
   *                              the entry may have been altered in some way.
   */
  public void applyModifications(List<Modification> mods)
         throws DirectoryException
  {
    for (Modification m : mods)
    {
      applyModification(m);
    }
  }



  /**
   * Indicates whether this entry conforms to the server's schema
   * requirements.  The checks performed by this method include:
   *
   * <UL>
   *   <LI>Make sure that all required attributes are present, either
   *       in the list of user or operational attributes.</LI>
   *   <LI>Make sure that all user attributes are allowed by at least
   *       one of the objectclasses.  The operational attributes will
   *       not be checked in this manner.</LI>
   *   <LI>Make sure that all single-valued attributes contained in
   *       the entry have only a single value.</LI>
   *   <LI>Make sure that the entry contains a single structural
   *       objectclass.</LI>
   *   <LI>Make sure that the entry complies with any defined name
   *       forms, DIT content rules, and DIT structure rules.</LI>
   * </UL>
   *
   * @param  parentEntry             The entry that is the immediate
   *                                 parent of this entry, which may
   *                                 be checked for DIT structure rule
   *                                 conformance.  This may be
   *                                 {@code null} if there is no
   *                                 parent or if it is unavailable
   *                                to the caller.
   * @param  parentProvided          Indicates whether the caller
   *                                 attempted to provide the parent.
   *                                 If not, then the parent entry
   *                                 will be loaded on demand if it is
   *                                 required.
   * @param  validateNameForms       Indicates whether to validate the
   *                                 entry against name form
   *                                 definitions.  This should only be
   *                                 {@code true} for add and modify
   *                                 DN operations, as well as for
   *                                 for imports.
   * @param  validateStructureRules  Indicates whether to validate the
   *                                 entry against DIT structure rule
   *                                 definitions.  This should only
   *                                 be {@code true} for add and
   *                                 modify DN operations.
   * @param  invalidReason           The buffer to which an
   *                                 explanation will be appended if
   *                                 this entry does not conform to
   *                                 the server's schema
   *                                 configuration.
   *
   * @return  {@code true} if this entry conforms to the server's
   *          schema requirements, or {@code false} if it does not.
   */
  public boolean conformsToSchema(Entry parentEntry,
                                  boolean parentProvided,
                                  boolean validateNameForms,
                                  boolean validateStructureRules,
                                  LocalizableMessageBuilder invalidReason)
  {
    // Get the structural objectclass for the entry.  If there isn't
    // one, or if there's more than one, then see if that's OK.
    AcceptRejectWarn structuralPolicy =
         DirectoryServer.getCoreConfigManager().getSingleStructuralObjectClassPolicy();
    ObjectClass structuralClass = null;
    boolean multipleOCErrorLogged = false;
    for (ObjectClass oc : objectClasses.keySet())
    {
      if (oc.getObjectClassType() == ObjectClassType.STRUCTURAL)
      {
        if (structuralClass == null || oc.isDescendantOf(structuralClass))
        {
          structuralClass = oc;
        }
        else if (! structuralClass.isDescendantOf(oc))
        {
          LocalizableMessage message =
                  ERR_ENTRY_SCHEMA_MULTIPLE_STRUCTURAL_CLASSES.get(
                    dn,
                    structuralClass.getNameOrOID(),
                    oc.getNameOrOID());

          if (structuralPolicy == AcceptRejectWarn.REJECT)
          {
            invalidReason.append(message);
            return false;
          }
          else if (structuralPolicy == AcceptRejectWarn.WARN
              && !multipleOCErrorLogged)
          {
            logger.error(message);
            multipleOCErrorLogged = true;
          }
        }
      }
    }

    NameForm         nameForm         = null;
    DITContentRule   ditContentRule   = null;
    DITStructureRule ditStructureRule = null;
    if (structuralClass == null)
    {
      LocalizableMessage message = ERR_ENTRY_SCHEMA_NO_STRUCTURAL_CLASS.get(dn);
      if (structuralPolicy == AcceptRejectWarn.REJECT)
      {
        invalidReason.append(message);
        return false;
      }
      else if (structuralPolicy == AcceptRejectWarn.WARN)
      {
        logger.error(message);
      }

      if (! checkAttributesAndObjectClasses(null,
              structuralPolicy, invalidReason))
      {
          return false;
      }

    }
    else
    {
      ditContentRule = getSchema().getDITContentRule(structuralClass);
      if (ditContentRule != null && ditContentRule.isObsolete())
      {
        ditContentRule = null;
      }

      if (! checkAttributesAndObjectClasses(ditContentRule,
                 structuralPolicy, invalidReason))
      {
        return false;
      }

      if (validateNameForms)
      {
        /**
         * There may be multiple nameforms registered with this
         * structural objectclass.However, we need to select only one
         * of the nameforms and its corresponding DITstructure rule.
         * We will iterate over all the nameforms and see if atleast
         * one is acceptable before rejecting the entry.
         * DITStructureRules corresponding to other non-acceptable
         * nameforms are not applied.
         */
        Collection<NameForm> forms = getSchema().getNameForms(structuralClass);
        if (forms != null)
        {
          List<NameForm> listForms = new ArrayList<>(forms);
          boolean matchFound = false;
          boolean obsolete = true;
          for(int index=0; index <listForms.size(); index++)
          {
            NameForm nf = listForms.get(index);
            if(!nf.isObsolete())
            {
              obsolete = false;
              matchFound = checkNameForm(nf, structuralPolicy, invalidReason);

              if(matchFound)
              {
                nameForm = nf;
                break;
              }

              if(index != listForms.size()-1)
              {
                invalidReason.append(",");
              }
            }
          }
          if(! obsolete && !matchFound)
          {
            // We couldn't match this entry against any of the nameforms.
            return false;
          }
        }


        if (validateStructureRules && nameForm != null)
        {
          for (DITStructureRule ditRule : getSchema().getDITStructureRules(nameForm))
          {
            if (!ditRule.isObsolete())
            {
              ditStructureRule = ditRule;
              break;
            }
          }
        }
      }
    }


    // If there is a DIT content rule for this entry, then make sure
    // that the entry is in compliance with it.
    if (ditContentRule != null
       && !checkDITContentRule(ditContentRule, structuralPolicy, invalidReason))
    {
      return false;
    }

    return checkDITStructureRule(ditStructureRule, structuralClass,
        parentEntry, parentProvided, validateStructureRules, structuralPolicy,
        invalidReason);
  }



  /**
   * Checks the attributes and object classes contained in this entry
   * to determine whether they conform to the server schema
   * requirements.
   *
   * @param  ditContentRule    The DIT content rule for this entry, if
   *                           any.
   * @param  structuralPolicy  The policy that should be used for
   *                           structural object class compliance.
   * @param  invalidReason     A buffer into which an invalid reason
   *                           may be added.
   *
   * @return {@code true} if this entry passes all of the checks, or
   *         {@code false} if there are any failures.
   */
  private boolean checkAttributesAndObjectClasses(
                       DITContentRule ditContentRule,
                       AcceptRejectWarn structuralPolicy,
                       LocalizableMessageBuilder invalidReason)
  {
    // Make sure that we recognize all of the objectclasses, that all
    // auxiliary classes are allowed by the DIT content rule, and that
    // all attributes required by the object classes are present.
    for (ObjectClass o : objectClasses.keySet())
    {
      if (getSchema().getObjectClass(o.getOID()).isPlaceHolder())
      {
        invalidReason.append(ERR_ENTRY_SCHEMA_UNKNOWN_OC.get(dn, o.getNameOrOID()));
        return false;
      }

      if (o.getObjectClassType() == ObjectClassType.AUXILIARY
          && ditContentRule != null && !ditContentRule.getAuxiliaryClasses().contains(o))
      {
        LocalizableMessage message =
                ERR_ENTRY_SCHEMA_DISALLOWED_AUXILIARY_CLASS.get(
                  dn,
                  o.getNameOrOID(),
                  ditContentRule.getNameOrOID());
        if (structuralPolicy == AcceptRejectWarn.REJECT)
        {
          invalidReason.append(message);
          return false;
        }
        else if (structuralPolicy == AcceptRejectWarn.WARN)
        {
          logger.error(message);
        }
      }

      for (AttributeType t : o.getDeclaredRequiredAttributes())
      {
        if (!userAttributes.containsKey(t)
            && !operationalAttributes.containsKey(t)
            && !t.isObjectClass())
        {
          LocalizableMessage message =
                  ERR_ENTRY_SCHEMA_MISSING_REQUIRED_ATTR_FOR_OC.get(
                    dn,
                    t.getNameOrOID(),
                    o.getNameOrOID());
          invalidReason.append(message);
          return false;
        }
      }
    }


    // Make sure all the user attributes are allowed, have at least
    // one value, and if they are single-valued that they have exactly
    // one value.
    for (Map.Entry<AttributeType, List<Attribute>> entry : userAttributes.entrySet())
    {
      AttributeType t = entry.getKey();
      boolean found = false;
      for (ObjectClass o : objectClasses.keySet())
      {
        if (o.isRequiredOrOptional(t))
        {
          found = true;
          break;
        }
      }

      if (!found && ditContentRule != null
          && ditContentRule.isRequiredOrOptional(t))
      {
        found = true;
      }

      if (! found)
      {
        LocalizableMessage message =
                ERR_ENTRY_SCHEMA_DISALLOWED_USER_ATTR_FOR_OC.get( dn, t.getNameOrOID());
        invalidReason.append(message);
        return false;
      }

      List<Attribute> attrList = entry.getValue();
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          if (a.isEmpty())
          {
            invalidReason.append(ERR_ENTRY_SCHEMA_ATTR_NO_VALUES.get(dn, t.getNameOrOID()));
            return false;
          }
          else if (t.isSingleValue() && a.size() != 1)
          {
            invalidReason.append(ERR_ENTRY_SCHEMA_ATTR_SINGLE_VALUED.get(dn, t.getNameOrOID()));
            return false;
          }
        }
      }
    }


    // Iterate through all of the operational attributes and make sure
    // that all of the single-valued attributes only have one value.
    for (Map.Entry<AttributeType, List<Attribute>> entry : operationalAttributes.entrySet())
    {
      AttributeType t = entry.getKey();
      if (t.isSingleValue())
      {
        List<Attribute> attrList = entry.getValue();
        if (attrList != null)
        {
          for (Attribute a : attrList)
          {
            if (a.size() > 1)
            {
              invalidReason.append(ERR_ENTRY_SCHEMA_ATTR_SINGLE_VALUED.get(dn, t.getNameOrOID()));
              return false;
            }
          }
        }
      }
    }


    // If we've gotten here, then things are OK.
    return true;
  }



  /**
   * Performs any processing needed for name form validation.
   *
   * @param  nameForm          The name form to validate against this
   *                           entry.
   * @param  structuralPolicy  The policy that should be used for
   *                           structural object class compliance.
   * @param  invalidReason     A buffer into which an invalid reason
   *                           may be added.
   *
   * @return {@code true} if this entry passes all of the checks, or
   *         {@code false} if there are any failures.
   */
  private boolean checkNameForm(NameForm nameForm,
                       AcceptRejectWarn structuralPolicy,
                       LocalizableMessageBuilder invalidReason)
  {
    RDN rdn = dn.rdn();
    if (rdn != null)
    {
        // Make sure that all the required attributes are present.
        for (AttributeType t : nameForm.getRequiredAttributes())
        {
          if (! rdn.hasAttributeType(t))
          {
            LocalizableMessage message =
                    ERR_ENTRY_SCHEMA_RDN_MISSING_REQUIRED_ATTR.get(
                      dn,
                      t.getNameOrOID(),
                      nameForm.getNameOrOID());

            if (structuralPolicy == AcceptRejectWarn.REJECT)
            {
              invalidReason.append(message);
              return false;
            }
            else if (structuralPolicy == AcceptRejectWarn.WARN)
            {
              logger.error(message);
            }
          }
        }

          // Make sure that all attributes in the RDN are allowed.
          for (AVA ava : rdn)
          {
            AttributeType t = ava.getAttributeType();
            if (! nameForm.isRequiredOrOptional(t))
            {
              LocalizableMessage message =
                      ERR_ENTRY_SCHEMA_RDN_DISALLOWED_ATTR.get(
                        dn,
                        t.getNameOrOID(),
                        nameForm.getNameOrOID());

              if (structuralPolicy == AcceptRejectWarn.REJECT)
              {
                invalidReason.append(message);
                return false;
              }
              else if (structuralPolicy == AcceptRejectWarn.WARN)
              {
                logger.error(message);
              }
            }
          }
    }

    // If we've gotten here, then things are OK.
    return true;
  }



  /**
   * Performs any processing needed for DIT content rule validation.
   *
   * @param  ditContentRule    The DIT content rule to validate
   *                           against this entry.
   * @param  structuralPolicy  The policy that should be used for
   *                           structural object class compliance.
   * @param  invalidReason     A buffer into which an invalid reason
   *                           may be added.
   *
   * @return {@code true} if this entry passes all of the checks, or
   *         {@code false} if there are any failures.
   */
  private boolean checkDITContentRule(DITContentRule ditContentRule,
                       AcceptRejectWarn structuralPolicy,
                       LocalizableMessageBuilder invalidReason)
  {
    // Make sure that all of the required attributes are present.
    for (AttributeType t : ditContentRule.getRequiredAttributes())
    {
      if (!userAttributes.containsKey(t)
          && !operationalAttributes.containsKey(t)
          && !t.isObjectClass())
      {
        LocalizableMessage message =
                ERR_ENTRY_SCHEMA_MISSING_REQUIRED_ATTR_FOR_DCR.get(
                  dn,
                  t.getNameOrOID(),
                  ditContentRule.getNameOrOID());

        if (structuralPolicy == AcceptRejectWarn.REJECT)
        {
          invalidReason.append(message);
          return false;
        }
        else if (structuralPolicy == AcceptRejectWarn.WARN)
        {
          logger.error(message);
        }
      }
    }

    // Make sure that none of the prohibited attributes are present.
    for (AttributeType t : ditContentRule.getProhibitedAttributes())
    {
      if (userAttributes.containsKey(t) ||
          operationalAttributes.containsKey(t))
      {
        LocalizableMessage message =
                ERR_ENTRY_SCHEMA_PROHIBITED_ATTR_FOR_DCR.get(
                  dn,
                  t.getNameOrOID(),
                  ditContentRule.getNameOrOID());

        if (structuralPolicy == AcceptRejectWarn.REJECT)
        {
          invalidReason.append(message);
          return false;
        }
        else if (structuralPolicy == AcceptRejectWarn.WARN)
        {
          logger.error(message);
        }
      }
    }

    // If we've gotten here, then things are OK.
    return true;
  }



  /**
   * Performs any processing needed for DIT structure rule validation.
   *
   * @param  ditStructureRule        The DIT structure rule for this
   *                                 entry.
   * @param  structuralClass         The structural object class for
   *                                 this entry.
   * @param  parentEntry             The parent entry, if available
   *                                 and applicable.
   * @param  parentProvided          Indicates whether the parent
   *                                 entry was provided.
   * @param  validateStructureRules  Indicates whether to check to see
   *                                 if this entry violates a DIT
   *                                 structure rule for its parent.
   * @param  structuralPolicy        The policy that should be used
   *                                 for structural object class
   *                                 compliance.
   * @param  invalidReason           A buffer into which an invalid
   *                                 reason may be added.
   *
   * @return {@code true} if this entry passes all of the checks, or
   *         {@code false} if there are any failures.
   */
  private boolean checkDITStructureRule(
                       DITStructureRule ditStructureRule,
                       ObjectClass structuralClass,
                       Entry parentEntry, boolean parentProvided,
                       boolean validateStructureRules,
                       AcceptRejectWarn structuralPolicy,
                       LocalizableMessageBuilder invalidReason)
  {
    // If there is a DIT structure rule for this entry, then make sure
    // that the entry is in compliance with it.
    if (ditStructureRule != null && !ditStructureRule.getSuperiorRules().isEmpty())
    {
      if (parentProvided)
      {
        if (parentEntry != null)
        {
          boolean dsrValid =
               validateDITStructureRule(ditStructureRule,
                                        structuralClass, parentEntry,
                                        structuralPolicy,
                                        invalidReason);
          if (! dsrValid)
          {
            return false;
          }
        }
      }
      else
      {
        // Get the DN of the parent entry if possible.
        DN parentDN = DirectoryServer.getInstance().getServerContext().getBackendConfigManager()
            .getParentDNInSuffix(dn);
        if (parentDN != null)
        {
          try
          {
            parentEntry = DirectoryServer.getEntry(parentDN);
            if (parentEntry == null)
            {
              LocalizableMessage message = ERR_ENTRY_SCHEMA_DSR_NO_PARENT_ENTRY.get(dn, parentDN);

              if (structuralPolicy == AcceptRejectWarn.REJECT)
              {
                invalidReason.append(message);
                return false;
              }
              else if (structuralPolicy == AcceptRejectWarn.WARN)
              {
                logger.error(message);
              }
            }
            else
            {
              boolean dsrValid =
                   validateDITStructureRule(ditStructureRule,
                                            structuralClass,
                                            parentEntry,
                                            structuralPolicy,
                                            invalidReason);
              if (! dsrValid)
              {
                return false;
              }
            }
          }
          catch (Exception e)
          {
            logger.traceException(e);

            LocalizableMessage message =
                 ERR_ENTRY_SCHEMA_COULD_NOT_CHECK_DSR.get(
                         dn,
                         ditStructureRule.getNameOrRuleID(),
                         getExceptionMessage(e));

            if (structuralPolicy == AcceptRejectWarn.REJECT)
            {
              invalidReason.append(message);
              return false;
            }
            else if (structuralPolicy == AcceptRejectWarn.WARN)
            {
              logger.error(message);
            }
          }
        }
      }
    }
    else if (validateStructureRules)
    {
      // There is no DIT structure rule for this entry, but there may
      // be one for the parent entry.  If there is such a rule for the
      // parent entry, then this entry will not be valid.
      boolean parentExists = false;
      ObjectClass parentStructuralClass = null;
      if (parentEntry != null)
      {
        parentExists = true;
        parentStructuralClass = parentEntry.getStructuralObjectClass();
      }
      else if (! parentProvided)
      {
        DN parentDN = DirectoryServer.getInstance().getServerContext().getBackendConfigManager()
            .getParentDNInSuffix(getName());
        if (parentDN != null)
        {
          try
          {
            parentEntry = DirectoryServer.getEntry(parentDN);
            if (parentEntry == null)
            {
              LocalizableMessage message =
                   ERR_ENTRY_SCHEMA_DSR_NO_PARENT_ENTRY.get(
                       dn, parentDN);

              if (structuralPolicy == AcceptRejectWarn.REJECT)
              {
                invalidReason.append(message);
                return false;
              }
              else if (structuralPolicy == AcceptRejectWarn.WARN)
              {
                logger.error(message);
              }
            }
            else
            {
              parentExists = true;
              parentStructuralClass = parentEntry.getStructuralObjectClass();
            }
          }
          catch (Exception e)
          {
            logger.traceException(e);

            LocalizableMessage message =
                 ERR_ENTRY_SCHEMA_COULD_NOT_CHECK_PARENT_DSR.get(
                     dn, getExceptionMessage(e));

            if (structuralPolicy == AcceptRejectWarn.REJECT)
            {
              invalidReason.append(message);
              return false;
            }
            else if (structuralPolicy == AcceptRejectWarn.WARN)
            {
              logger.error(message);
            }
          }
        }
      }

      if (parentExists)
      {
        if (parentStructuralClass == null)
        {
          LocalizableMessage message = ERR_ENTRY_SCHEMA_DSR_NO_PARENT_OC.get(dn, parentEntry.getName());
          if (structuralPolicy == AcceptRejectWarn.REJECT)
          {
            invalidReason.append(message);
            return false;
          }
          else if (structuralPolicy == AcceptRejectWarn.WARN)
          {
            logger.error(message);
          }
        }
        else if (!structuralClass.equals(parentStructuralClass))
        {
          Collection<NameForm> allNFs = getSchema().getNameForms(parentStructuralClass);
          if(allNFs != null)
          {
            for(NameForm parentNF : allNFs)
            {
              if (!parentNF.isObsolete())
              {
                for (DITStructureRule parentDSR : getSchema().getDITStructureRules(parentNF))
                {
                  if (!parentDSR.isObsolete())
                  {
                    LocalizableMessage message = ERR_ENTRY_SCHEMA_VIOLATES_PARENT_DSR.get(dn, parentEntry.getName());
                    if (structuralPolicy == AcceptRejectWarn.REJECT)
                    {
                      invalidReason.append(message);
                      return false;
                    }
                    else if (structuralPolicy == AcceptRejectWarn.WARN)
                    {
                      logger.error(message);
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // If we've gotten here, then things are OK.
    return true;
  }



  /**
   * Determines whether this entry is in conformance to the provided
   * DIT structure rule.
   *
   * @param  dsr               The DIT structure rule to use in the
   *                           determination.
   * @param  structuralClass   The structural objectclass for this
   *                           entry to use in the determination.
   * @param  parentEntry       The reference to the parent entry to
   *                           check.
   * @param  structuralPolicy  The policy that should be used around
   *                           enforcement of DIT structure rules.
   * @param  invalidReason     The buffer to which the invalid reason
   *                           should be appended if a problem is
   *                           found.
   *
   * @return  {@code true} if this entry conforms to the provided
   *          DIT structure rule, or {@code false} if not.
   */
  private boolean validateDITStructureRule(DITStructureRule dsr,
                       ObjectClass structuralClass, Entry parentEntry,
                       AcceptRejectWarn structuralPolicy,
                       LocalizableMessageBuilder invalidReason)
  {
    ObjectClass oc = parentEntry.getStructuralObjectClass();
    if (oc == null)
    {
      LocalizableMessage message = ERR_ENTRY_SCHEMA_DSR_NO_PARENT_OC.get(
          dn, parentEntry.getName());

      if (structuralPolicy == AcceptRejectWarn.REJECT)
      {
        invalidReason.append(message);
        return false;
      }
      else if (structuralPolicy == AcceptRejectWarn.WARN)
      {
        logger.error(message);
      }
    }

    if (!containsSuperiorRuleWithObjectClass(dsr, oc))
    {
      LocalizableMessage message =
              ERR_ENTRY_SCHEMA_DSR_DISALLOWED_SUPERIOR_OC.get(
                dn,
                dsr.getNameOrRuleID(),
                structuralClass.getNameOrOID(),
                oc.getNameOrOID());

      if (structuralPolicy == AcceptRejectWarn.REJECT)
      {
        invalidReason.append(message);
        return false;
      }
      else if (structuralPolicy == AcceptRejectWarn.WARN)
      {
        logger.error(message);
      }
    }

    return true;
  }

  private boolean containsSuperiorRuleWithObjectClass(DITStructureRule dsr, ObjectClass oc)
  {
    for (DITStructureRule superior : dsr.getSuperiorRules())
    {
      if (superior.getNameForm().getStructuralClass().equals(oc))
      {
        return true;
      }
    }
    return false;
  }



  /**
   * Retrieves the attachment for this entry.
   *
   * @return  The attachment for this entry, or {@code null} if there is none.
   */
  public Object getAttachment()
  {
    return attachment;
  }



  /**
   * Specifies the attachment for this entry.  This will replace any
   * existing attachment that might be defined.
   *
   * @param  attachment  The attachment for this entry,
   *                     or {@code null} if there should not be an attachment.
   */
  public void setAttachment(Object attachment)
  {
    this.attachment = attachment;
  }



  /**
   * Creates a duplicate of this entry that may be altered without
   * impacting the information in this entry.
   *
   * @param  processVirtual  Indicates whether virtual attribute
   *                         processing should be performed for the
   *                         entry.
   *
   * @return  A duplicate of this entry that may be altered without
   *          impacting the information in this entry.
   */
  public Entry duplicate(boolean processVirtual)
  {
    Map<ObjectClass, String> objectClassesCopy = new HashMap<>(objectClasses);

    Map<AttributeType, List<Attribute>> userAttrsCopy = new HashMap<>(userAttributes.size());
    deepCopy(userAttributes, userAttrsCopy, false, false, false,
        true, false);

    Map<AttributeType, List<Attribute>> operationalAttrsCopy =
         new HashMap<>(operationalAttributes.size());
    deepCopy(operationalAttributes, operationalAttrsCopy, false,
        false, false, true, false);

    // Put back all the suppressed attributes where they belonged to.
    // Then hopefully processVirtualAttributes() will rebuild the suppressed
    // attribute list correctly.
    for (Map.Entry<AttributeType, List<Attribute>> entry : suppressedAttributes.entrySet())
    {
      AttributeType t = entry.getKey();
      List<Attribute> attrList = entry.getValue();
      if (t.isOperational())
      {
        operationalAttrsCopy.put(t, attrList);
      }
      else
      {
        userAttrsCopy.put(t, attrList);
      }
    }

    Entry e = new Entry(dn, objectClassesCopy, userAttrsCopy,
                        operationalAttrsCopy);
    if (processVirtual)
    {
      e.processVirtualAttributes();
    }
    return e;
  }



  /**
   * Performs a deep copy from the source map to the target map.
   * In this case, the attributes in the list will be duplicates
   * rather than re-using the same reference.
   *
   * @param source
   *          The source map from which to obtain the information.
   * @param target
   *          The target map into which to place the copied
   *          information.
   * @param omitValues
   *          Indicates whether to omit attribute values when
   *          processing.
   * @param omitEmpty
   *          Indicates whether to omit empty attributes when
   *          processing.
   * @param omitReal
   *          Indicates whether to exclude real attributes.
   * @param omitVirtual
   *          Indicates whether to exclude virtual attributes.
   * @param mergeDuplicates
   *          Indicates whether duplicate attributes should be merged.
   */
  private void deepCopy(Map<AttributeType,List<Attribute>> source,
                        Map<AttributeType,List<Attribute>> target,
                        boolean omitValues,
                        boolean omitEmpty,
                        boolean omitReal,
                        boolean omitVirtual,
                        boolean mergeDuplicates)
  {
    for (Map.Entry<AttributeType, List<Attribute>> mapEntry :
      source.entrySet())
    {
      AttributeType t = mapEntry.getKey();
      List<Attribute> sourceList = mapEntry.getValue();
      List<Attribute> targetList = new ArrayList<>(sourceList.size());

      for (Attribute a : sourceList)
      {
        if ((omitReal && a.isReal())
            || (omitVirtual && a.isVirtual())
            || (omitEmpty && a.isEmpty()))
        {
          continue;
        }

        if (omitValues)
        {
          a = Attributes.empty(a);
        }

        if (!targetList.isEmpty() && mergeDuplicates)
        {
          // Ensure that there is only one attribute with the same type and options.
          // This is not very efficient but will occur very rarely.
          boolean found = false;
          for (ListIterator<Attribute> it = targetList.listIterator(); it.hasNext();)
          {
            Attribute otherAttribute = it.next();
            if (otherAttribute.getAttributeDescription().equals(a.getAttributeDescription()))
            {
              it.set(Attributes.merge(a, otherAttribute));
              found = true;
            }
          }

          if (!found)
          {
            targetList.add(a);
          }
        }
        else
        {
          targetList.add(a);
        }
      }

      if (!targetList.isEmpty())
      {
        target.put(t, targetList);
      }
    }
  }



  /**
   * Indicates whether this entry meets the criteria to consider it a referral
   * (e.g., it contains the "referral" objectclass and a "ref" attribute).
   *
   * @return  {@code true} if this entry meets the criteria to
   *          consider it a referral, or {@code false} if not.
   */
  public boolean isReferral()
  {
    return hasObjectClassOrAttribute(OC_REFERRAL, ATTR_REFERRAL_URL);
  }

  /**
   * Returns whether the current entry has a specific object class or attribute.
   *
   * @param objectClassName
   *          the name of the object class to look for
   * @param attrTypeName
   *          the attribute type name of the object class to look for
   * @return true if the current entry has the object class or the attribute,
   *         false otherwise
   */
  private boolean hasObjectClassOrAttribute(String objectClassName, String attrTypeName)
  {
    ObjectClass oc = getSchema().getObjectClass(objectClassName);
    if (oc.isPlaceHolder())
    {
      // This should not happen
      // The server doesn't have this objectclass defined.
      logger.trace("No %s objectclass is defined in the server schema.", objectClassName);
      return containsObjectClassByName(objectClassName);
    }
    if (!objectClasses.containsKey(oc))
    {
      return false;
    }

    AttributeType attrType = getSchema().getAttributeType(attrTypeName);
    if (attrType.isPlaceHolder())
    {
      // This should not happen
      // The server doesn't have this attribute type defined.
      logger.trace("No %s attribute type is defined in the server schema.", attrTypeName);
      return false;
    }
    return userAttributes.containsKey(attrType)
        || operationalAttributes.containsKey(attrType);
  }

  /**
   * Whether the object class name exists in the objectClass of this entry.
   *
   * @param objectClassName
   *          the name of the object class to look for
   * @return true if the object class name exists in the objectClass of this
   *         entry, false otherwise
   */
  private boolean containsObjectClassByName(String objectClassName)
  {
    for (String ocName : objectClasses.values())
    {
      if (objectClassName.equalsIgnoreCase(ocName))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Retrieves the set of referral URLs that are included in this
   * referral entry.  This should only be called if
   * {@link #isReferral()} returns {@code true}.
   *
   * @return  The set of referral URLs that are included in this entry
   *          if it is a referral, or {@code null} if it is not a referral.
   */
  public Set<String> getReferralURLs()
  {
    AttributeType referralType = getSchema().getAttributeType(ATTR_REFERRAL_URL);
    if (referralType.isPlaceHolder())
    {
      // This should not happen -- The server doesn't have a ref attribute type defined.
      logger.trace("No %s attribute type is defined in the server schema.", ATTR_REFERRAL_URL);
      return null;
    }

    List<Attribute> refAttrs = userAttributes.get(referralType);
    if (refAttrs == null)
    {
      refAttrs = operationalAttributes.get(referralType);
      if (refAttrs == null)
      {
        return null;
      }
    }

    Set<String> referralURLs = new LinkedHashSet<>();
    for (Attribute a : refAttrs)
    {
      for (ByteString v : a)
      {
        referralURLs.add(v.toString());
      }
    }

    return referralURLs;
  }



  /**
   * Indicates whether this entry meets the criteria to consider it an
   * alias (e.g., it contains the "aliasObject" objectclass and a
   * "alias" attribute).
   *
   * @return  {@code true} if this entry meets the criteria to
   *          consider it an alias, or {@code false} if not.
   */
  public boolean isAlias()
  {
    return hasObjectClassOrAttribute(OC_ALIAS, ATTR_ALIAS_DN);
  }



  /**
   * Retrieves the DN of the entry referenced by this alias entry.
   * This should only be called if <CODE>isAlias()</CODE> returns
   * {@code true}.
   *
   * @return  The DN of the entry referenced by this alias entry, or
   *          {@code null} if it is not an alias.
   *
   * @throws  DirectoryException  If there is an aliasedObjectName
   *                              attribute but its value cannot be
   *                              parsed as a DN.
   */
  public DN getAliasedDN() throws DirectoryException
  {
    AttributeType aliasType = getSchema().getAttributeType(ATTR_ALIAS_DN);
    if (aliasType.isPlaceHolder())
    {
      // This should not happen -- The server doesn't have an aliasedObjectName attribute type defined.
      logger.trace("No %s attribute type is defined in the server schema.", ATTR_ALIAS_DN);
      return null;
    }

    List<Attribute> aliasAttrs = userAttributes.get(aliasType);
    if (aliasAttrs == null)
    {
      aliasAttrs = operationalAttributes.get(aliasType);
      if (aliasAttrs == null)
      {
        return null;
      }
    }

    if (!aliasAttrs.isEmpty())
    {
      // There should only be a single alias attribute in an entry,
      // and we'll skip the check for others for performance reasons.
      // We would just end up taking the first one anyway. The same
      // is true with the set of values, since it should be a
      // single-valued attribute.
      Attribute aliasAttr = aliasAttrs.get(0);
      if (!aliasAttr.isEmpty())
      {
        return DN.valueOf(aliasAttr.iterator().next().toString());
      }
    }
    return null;
  }



  /**
   * Indicates whether this entry meets the criteria to consider it an
   * LDAP subentry (i.e., it contains the "ldapSubentry" objectclass).
   *
   * @return  {@code true} if this entry meets the criteria to
   *          consider it an LDAP subentry, or {@code false} if
   *          not.
   */
  public boolean isLDAPSubentry()
  {
    return hasObjectClass(OC_LDAP_SUBENTRY_LC);
  }

  /**
   * Returns whether the current entry has a specific object class.
   *
   * @param objectClassLowerCase
   *          the lowercase name of the object class to look for
   * @return true if the current entry has the object class, false otherwise
   */
  private boolean hasObjectClass(String objectClassLowerCase)
  {
    ObjectClass oc = getSchema().getObjectClass(objectClassLowerCase);
    if (oc.isPlaceHolder())
    {
      // This should not happen
      // The server doesn't have this object class defined.
      logger.trace("No %s objectclass is defined in the server schema.", objectClassLowerCase);
      return containsObjectClassByName(objectClassLowerCase);
    }

    // Make the determination based on whether this entry has this objectclass.
    return objectClasses.containsKey(oc);
  }



  /**
   * Indicates whether this entry meets the criteria to consider it
   * an RFC 3672 LDAP subentry (i.e., it contains the "subentry"
   * objectclass).
   *
   * @return  {@code true} if this entry meets the criteria to
   *          consider it an RFC 3672 LDAP subentry, or <CODE>false
   *          </CODE> if not.
   */
  public boolean isSubentry()
  {
    return hasObjectClass(OC_SUBENTRY);
  }



  /**
   * Indicates whether the entry meets the criteria to consider it an
   * RFC 3671 LDAP collective attributes subentry (i.e., it contains
   * the "collectiveAttributeSubentry" objectclass).
   *
   * @return  {@code true} if this entry meets the criteria to
   *          consider it an RFC 3671 LDAP collective attributes
   *          subentry, or {@code false} if not.
   */
  public boolean isCollectiveAttributeSubentry()
  {
    return hasObjectClass(OC_COLLECTIVE_ATTR_SUBENTRY_LC);
  }



  /**
   * Indicates whether the entry meets the criteria to consider it an
   * inherited collective attributes subentry (i.e., it contains
   * the "inheritedCollectiveAttributeSubentry" objectclass).
   *
   * @return  {@code true} if this entry meets the criteria to
   *          consider it an inherited collective attributes
   *          subentry, or {@code false} if not.
   */
  public boolean isInheritedCollectiveAttributeSubentry()
  {
    return hasObjectClass(OC_INHERITED_COLLECTIVE_ATTR_SUBENTRY_LC);
  }



  /**
   * Indicates whether the entry meets the criteria to consider it an inherited
   * from DN collective attributes subentry (i.e., it contains the
   * "inheritedFromDNCollectiveAttributeSubentry" objectclass).
   *
   * @return {@code true} if this entry meets the criteria to consider it
   *         an inherited from DN collective attributes subentry, or
   *         {@code false} if not.
   */
  public boolean isInheritedFromDNCollectiveAttributeSubentry()
  {
    return hasObjectClass(OC_INHERITED_FROM_DN_COLLECTIVE_ATTR_SUBENTRY_LC);
  }



  /**
   * Indicates whether the entry meets the criteria to consider it
   * an inherited from RDN collective attributes subentry (i.e.,
   * it contains the "inheritedFromRDNCollectiveAttributeSubentry"
   * objectclass).
   *
   * @return  {@code true} if this entry meets the criteria to
   *          consider it an inherited from RDN collective attributes
   *          subentry, or {@code false} if not.
   */
  public boolean isInheritedFromRDNCollectiveAttributeSubentry()
  {
    return hasObjectClass(OC_INHERITED_FROM_RDN_COLLECTIVE_ATTR_SUBENTRY_LC);
  }



  /**
   * Indicates whether the entry meets the criteria to consider it a
   * LDAP password policy subentry (i.e., it contains the "pwdPolicy"
   * objectclass of LDAP Password Policy Internet-Draft).
   *
   * @return  {@code true} if this entry meets the criteria to
   *          consider it a LDAP Password Policy Internet-Draft
   *          subentry, or {@code false} if not.
   */
  public boolean isPasswordPolicySubentry()
  {
    return hasObjectClass(OC_PWD_POLICY_SUBENTRY_LC);
  }



  /**
   * Indicates whether this entry falls within the range of the
   * provided search base DN and scope.
   *
   * @param  baseDN  The base DN for which to make the determination.
   * @param  scope   The search scope for which to make the
   *                 determination.
   *
   * @return  {@code true} if this entry is within the given
   *          base and scope, or {@code false} if it is not.
   */
  public boolean matchesBaseAndScope(DN baseDN, SearchScope scope)
  {
    return dn.isInScopeOf(baseDN, scope);
  }



  /**
   * Performs any necessary collective attribute processing for this
   * entry.  This should only be called at the time the entry is
   * decoded or created within the backend.
   */
  private void processCollectiveAttributes()
  {
    if (isSubentry() || isLDAPSubentry())
    {
      return;
    }

    SubentryManager manager =
            DirectoryServer.getSubentryManager();
    if(manager == null)
    {
      //Subentry manager may not have been initialized by
      //a component that doesn't require it.
      return;
    }
    // Get applicable collective subentries.
    List<SubEntry> collectiveAttrSubentries =
            manager.getCollectiveSubentries(this);

    if (collectiveAttrSubentries == null || collectiveAttrSubentries.isEmpty())
    {
      // Nothing to see here, move along.
      return;
    }

    // Get collective attribute exclusions.
    AttributeType exclusionsType = getSchema().getAttributeType(ATTR_COLLECTIVE_EXCLUSIONS_LC);
    List<Attribute> exclusionsAttrList = operationalAttributes.get(exclusionsType);
    List<String> excludedAttrNames = new ArrayList<>();
    if (exclusionsAttrList != null && !exclusionsAttrList.isEmpty())
    {
      for (Attribute attr : exclusionsAttrList)
      {
        for (ByteString attrValue : attr)
        {
          String excludedAttrName = attrValue.toString().toLowerCase();
          if (VALUE_COLLECTIVE_EXCLUSIONS_EXCLUDE_ALL_LC.equals(excludedAttrName)
              || OID_COLLECTIVE_EXCLUSIONS_EXCLUDE_ALL.equals(excludedAttrName))
          {
            return;
          }
          excludedAttrNames.add(excludedAttrName);
        }
      }
    }

    // Process collective attributes.
    for (SubEntry subEntry : collectiveAttrSubentries)
    {
      if (subEntry.isCollective() || subEntry.isInheritedCollective())
      {
        Entry inheritFromEntry = null;
        if (subEntry.isInheritedCollective())
        {
          if (subEntry.isInheritedFromDNCollective() &&
              hasAttribute(subEntry.getInheritFromDNType()))
          {
            try
            {
              DN inheritFromDN = null;
              for (Attribute attr : getAllAttributes(subEntry.getInheritFromDNType()))
              {
                for (ByteString value : attr)
                {
                  inheritFromDN = DN.valueOf(value);
                  // Respect subentry root scope.
                  if (!inheritFromDN.isSubordinateOrEqualTo(
                       subEntry.getDN().parent()))
                  {
                    inheritFromDN = null;
                  }
                  break;
                }
              }
              if (inheritFromDN == null)
              {
                continue;
              }

              // TODO : ACI check; needs re-factoring to happen.
              inheritFromEntry = DirectoryServer.getEntry(inheritFromDN);
            }
            catch (DirectoryException de)
            {
              logger.traceException(de);
            }
          }
          else if (subEntry.isInheritedFromRDNCollective() &&
                   hasAttribute(subEntry.getInheritFromRDNAttrType()))
          {
            DN inheritFromDN = subEntry.getInheritFromBaseDN();
            if (inheritFromDN != null)
            {
              try
              {
                for (Attribute attr : getAllAttributes(subEntry.getInheritFromRDNAttrType()))
                {
                  inheritFromDN = subEntry.getInheritFromBaseDN();
                  for (ByteString value : attr)
                  {
                    inheritFromDN = inheritFromDN.child(
                        new RDN(subEntry.getInheritFromRDNType(), value));
                    break;
                  }
                }

                // TODO : ACI check; needs re-factoring to happen.
                inheritFromEntry = DirectoryServer.getEntry(inheritFromDN);
              }
              catch (DirectoryException de)
              {
                logger.traceException(de);
              }
            }
            else
            {
              continue;
            }
          }
        }
        List<Attribute> collectiveAttrList = subEntry.getCollectiveAttributes();
        for (Attribute collectiveAttr : collectiveAttrList)
        {
          AttributeType attributeType = collectiveAttr.getAttributeDescription().getAttributeType();
          if (hasAnyNameOrOID(attributeType, excludedAttrNames))
          {
            continue;
          }
          if (subEntry.isInheritedCollective())
          {
            if (inheritFromEntry != null)
            {
              collectiveAttr = inheritFromEntry.getAttribute(collectiveAttr.getAttributeDescription());
              if (collectiveAttr == null || collectiveAttr.isEmpty())
              {
                continue;
              }
              collectiveAttr = new CollectiveVirtualAttribute(collectiveAttr);
            }
            else
            {
              continue;
            }
          }
          List<Attribute> attrList = userAttributes.get(attributeType);
          if (attrList == null || attrList.isEmpty())
          {
            attrList = operationalAttributes.get(attributeType);
            if (attrList == null || attrList.isEmpty())
            {
              // There aren't any conflicts, so we can just add the attribute to the entry.
              putAttributes(attributeType, newLinkedList(collectiveAttr));
            }
            else
            {
              // There is a conflict with an existing operational attribute.
              resolveCollectiveConflict(subEntry.getConflictBehavior(),
                  collectiveAttr, attrList, operationalAttributes, attributeType);
            }
          }
          else
          {
            // There is a conflict with an existing user attribute.
            resolveCollectiveConflict(subEntry.getConflictBehavior(),
                collectiveAttr, attrList, userAttributes, attributeType);
          }
        }
      }
    }
  }

  private boolean hasAnyNameOrOID(AttributeType attributeType, Collection<String> attrNames)
  {
    for (String attrName : attrNames)
    {
      if (attributeType.hasNameOrOID(attrName))
      {
        return true;
      }
    }
    return false;
  }

  private ByteString normalize(MatchingRule matchingRule, ByteString value)
      throws DirectoryException
  {
    try
    {
      return matchingRule.normalizeAttributeValue(value);
    }
    catch (DecodeException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
          e.getMessageObject(), e);
    }
  }

  /**
   * Resolves a conflict arising with a collective attribute.
   *
   * @param conflictBehavior
   *          the behavior of the conflict
   * @param collectiveAttr
   *          the attribute in conflict
   * @param attrList
   *          the List of attribute where to resolve the conflict
   * @param attributes
   *          the Map of attributes where to solve the conflict
   * @param attributeType
   *          the attribute type used with the Map
   */
  private void resolveCollectiveConflict(
      CollectiveConflictBehavior conflictBehavior, Attribute collectiveAttr,
      List<Attribute> attrList, Map<AttributeType, List<Attribute>> attributes,
      AttributeType attributeType)
  {
    if (attrList.get(0).isVirtual())
    {
      attrList.add(collectiveAttr);
      return;
    }

    // The conflict is with a real attribute. See what the
    // conflict behavior is and figure out how to handle it.
    switch (conflictBehavior)
    {
    case REAL_OVERRIDES_VIRTUAL:
      // We don't need to update the entry because the real attribute will take
      // precedence.
      break;

    case VIRTUAL_OVERRIDES_REAL:
      // We need to move the real attribute to the suppressed list
      // and replace it with the virtual attribute.
      suppressedAttributes.put(attributeType, attrList);
      attributes.put(attributeType, newLinkedList(collectiveAttr));
      break;

    case MERGE_REAL_AND_VIRTUAL:
      // We need to add the virtual attribute to the
      // list and keep the existing real attribute(s).
      attrList.add(collectiveAttr);
      break;
    }
  }



  /**
   * Performs any necessary virtual attribute processing for this
   * entry.  This should only be called at the time the entry is
   * decoded or created within the backend.
   */
  public void processVirtualAttributes()
  {
    for (VirtualAttributeRule rule : DirectoryServer.getVirtualAttributes(this))
    {
      AttributeType attributeType = rule.getAttributeType();
      List<Attribute> attrList = userAttributes.get(attributeType);
      if (attrList == null || attrList.isEmpty())
      {
        attrList = operationalAttributes.get(attributeType);
        if (attrList == null || attrList.isEmpty())
        {
          // There aren't any conflicts, so we can just add the attribute to the entry.
          Attribute attr = new VirtualAttribute(attributeType, this, rule);
          putAttributes(attributeType, newLinkedList(attr));
        }
        else
        {
          // There is a conflict with an existing operational attribute.
          resolveVirtualConflict(rule, attrList, operationalAttributes, attributeType);
        }
      }
      else
      {
        // There is a conflict with an existing user attribute.
        resolveVirtualConflict(rule, attrList, userAttributes, attributeType);
      }
    }

    // Collective attributes.
    processCollectiveAttributes();
  }

  /**
   * Resolves a conflict arising with a virtual attribute.
   *
   * @param rule
   *          the VirtualAttributeRule in conflict
   * @param attrList
   *          the List of attribute where to resolve the conflict
   * @param attributes
   *          the Map of attribute where to resolve the conflict
   * @param attributeType
   *          the attribute type used with the Map
   */
  private void resolveVirtualConflict(VirtualAttributeRule rule,
      List<Attribute> attrList, Map<AttributeType, List<Attribute>> attributes,
      AttributeType attributeType)
  {
    if (attrList.get(0).isVirtual())
    {
      // The existing attribute is already virtual, so we've got
      // a different conflict, but we'll let the first win.
      // FIXME -- Should we handle this differently?
      return;
    }

    // The conflict is with a real attribute. See what the
    // conflict behavior is and figure out how to handle it.
    switch (rule.getConflictBehavior())
    {
    case REAL_OVERRIDES_VIRTUAL:
      // We don't need to update the entry because the real
      // attribute will take precedence.
      break;

    case VIRTUAL_OVERRIDES_REAL:
      // We need to move the real attribute to the suppressed
      // list and replace it with the virtual attribute.
      suppressedAttributes.put(attributeType, attrList);
      Attribute attr = new VirtualAttribute(attributeType, this, rule);
      attributes.put(attributeType, newLinkedList(attr));
      break;

    case MERGE_REAL_AND_VIRTUAL:
      // We need to add the virtual attribute to the list and
      // keep the existing real attribute(s).
      attrList.add(new VirtualAttribute(attributeType, this, rule));
      break;
    }
  }


  /**
   * Encodes this entry into a form that is suitable for long-term
   * persistent storage.  The encoding will have a version number so
   * that if the way we store entries changes in the future we will
   * still be able to read entries encoded in an older format.
   *
   * @param  buffer  The buffer to encode into.
   * @param  config  The configuration that may be used to control how
   *                 the entry is encoded.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to encode the entry.
   */
  public void encode(ByteStringBuilder buffer,
                     EntryEncodeConfig config)
         throws DirectoryException
  {
    encodeV3(buffer, config);
  }

  /**
   * Encodes this entry using the V3 encoding.
   *
   * @param  buffer  The buffer to encode into.
   * @param  config  The configuration that should be used to encode
   *                 the entry.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to encode the entry.
   */
  private void encodeV3(ByteStringBuilder buffer,
                        EntryEncodeConfig config)
         throws DirectoryException
  {
    // The version number will be one byte.
    buffer.appendByte(0x03);

    // Get the encoded representation of the config.
    config.encode(buffer);

    // If we should include the DN, then it will be encoded as a
    // one-to-five byte length followed by the UTF-8 byte
    // representation.
    if (! config.excludeDN())
    {
      // TODO: Can we encode the DN directly into buffer?
      byte[] dnBytes  = getBytes(dn.toString());
      buffer.appendBERLength(dnBytes.length);
      buffer.appendBytes(dnBytes);
    }


    // Encode the object classes in the appropriate manner.
    if (config.compressObjectClassSets())
    {
      config.getCompressedSchema().encodeObjectClasses(buffer, objectClasses);
    }
    else
    {
      // Encode number of OCs and 0 terminated names.
      buffer.appendBERLength(objectClasses.size());
      for (String ocName : objectClasses.values())
      {
        buffer.appendUtf8(ocName);
        buffer.appendByte(0x00);
      }
    }


    // Encode the user attributes in the appropriate manner.
    encodeAttributes(buffer, userAttributes, config);


    // The operational attributes will be encoded in the same way as
    // the user attributes.
    encodeAttributes(buffer, operationalAttributes, config);
  }

  /**
   * Encode the given attributes of an entry.
   *
   * @param  buffer  The buffer to encode into.
   * @param  attributes The attributes to encode.
   * @param  config  The configuration that may be used to control how
   *                 the entry is encoded.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to encode the entry.
   */
  private void encodeAttributes(ByteStringBuilder buffer,
                    Map<AttributeType,List<Attribute>> attributes,
                                EntryEncodeConfig config)
      throws DirectoryException
  {
    int numAttributes = 0;

    // First count how many attributes are there to encode.
    for (List<Attribute> attrList : attributes.values())
    {
      Attribute a;
      for (int i = 0; i < attrList.size(); i++)
      {
        a = attrList.get(i);
        if (a.isVirtual() || a.isEmpty())
        {
          continue;
        }

        numAttributes++;
      }
    }

    // Encoded one-to-five byte number of attributes
    buffer.appendBERLength(numAttributes);

    if (config.compressAttributeDescriptions())
    {
      for (List<Attribute> attrList : attributes.values())
      {
        for (Attribute a : attrList)
        {
          if (a.isVirtual() || a.isEmpty())
          {
            continue;
          }

          config.getCompressedSchema().encodeAttribute(buffer, a);
        }
      }
    }
    else
    {
      // The attributes will be encoded as a sequence of:
      // - A UTF-8 byte representation of the attribute name.
      // - A zero delimiter
      // - A one-to-five byte number of values for the attribute
      // - A sequence of:
      //   - A one-to-five byte length for the value
      //   - A UTF-8 byte representation for the value
      for (List<Attribute> attrList : attributes.values())
      {
        for (Attribute a : attrList)
        {
          buffer.appendBytes(getBytes(a.getAttributeDescription().toString()));
          buffer.appendByte(0x00);

          buffer.appendBERLength(a.size());
          for(ByteString v : a)
          {
            buffer.appendBERLength(v.length());
            buffer.appendBytes(v);
          }
        }
      }
    }
  }


  /**
   * Decodes the provided byte array as an entry.
   *
   * @param  entryBuffer  The byte array containing the data to be
   *                      decoded.
   *
   * @return  The decoded entry.
   *
   * @throws  DirectoryException  If the provided byte array cannot be
   *                              decoded as an entry.
   */
  public static Entry decode(ByteSequenceReader entryBuffer)
         throws DirectoryException
  {
    return decode(entryBuffer,
                  DirectoryServer.getDefaultCompressedSchema());
  }



    /**
   * Decodes the provided byte array as an entry using the V3
   * encoding.
   *
   * @param  entryBuffer       The byte buffer containing the data to
   *                           be decoded.
   * @param  compressedSchema  The compressed schema manager to use
   *                           when decoding tokenized schema
   *                           elements.
   *
   * @return  The decoded entry.
   *
   * @throws  DirectoryException  If the provided byte array cannot be
   *                              decoded as an entry.
   */
  public static Entry decode(ByteSequenceReader entryBuffer,
                             CompressedSchema compressedSchema)
         throws DirectoryException
  {
    try
    {
      // The first byte must be the entry version.  If it's not one
      // we recognize, then that's an error.
      Byte version = entryBuffer.readByte();
      if (version != 0x03 && version != 0x02 && version != 0x01)
      {
        LocalizableMessage message = ERR_ENTRY_DECODE_UNRECOGNIZED_VERSION.get(
            byteToHex(version));
        throw new DirectoryException(
                       DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
                       message);
      }

      EntryEncodeConfig config;
      if(version != 0x01)
      {
        // Next is the length of the encoded configuration.
        int configLength = entryBuffer.readBERLength();

        // Next is the encoded configuration itself.
        config = EntryEncodeConfig.decode(entryBuffer, configLength, compressedSchema);
      }
      else
      {
        config = EntryEncodeConfig.DEFAULT_CONFIG;
      }

      // If we should have included the DN in the entry, then it's
      // next.
      DN dn;
      if (config.excludeDN())
      {
        dn = DN.rootDN();
      }
      else
      {
        // Next is the length of the DN.  It may be a single byte or
        // multiple bytes.
        int dnLength = entryBuffer.readBERLength();


        // Next is the DN itself.
        ByteSequence dnBytes = entryBuffer.readByteSequence(dnLength);
        dn = DN.valueOf(dnBytes.toByteString());
      }


      // Next is the set of encoded object classes.  The encoding will
      // depend on the configuration.
      Map<ObjectClass,String> objectClasses =
          decodeObjectClasses(version, entryBuffer, config);


      // Now, we should iterate through the user and operational attributes and
      // decode each one.
      Map<AttributeType, List<Attribute>> userAttributes =
          decodeAttributes(version, entryBuffer, config);
      Map<AttributeType, List<Attribute>> operationalAttributes =
          decodeAttributes(version, entryBuffer, config);


      // We've got everything that we need, so create and return the entry.
      return new Entry(dn, objectClasses, userAttributes,
          operationalAttributes);
    }
    catch (DirectoryException de)
    {
      throw de;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_ENTRY_DECODE_EXCEPTION.get(getExceptionMessage(e));
      throw new DirectoryException(
                     DirectoryServer.getCoreConfigManager().getServerErrorResultCode(),
                     message, e);
    }
  }


  /**
   * Decode the object classes of an encoded entry.
   *
   * @param  ver The version of the entry encoding.
   * @param  entryBuffer The byte sequence containing the encoded
   *                     entry.
   * @param  config  The configuration that may be used to control how
   *                 the entry is encoded.
   *
   * @return  A map of the decoded object classes.
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to encode the entry.
   */
  private static Map<ObjectClass,String> decodeObjectClasses(
      byte ver, ByteSequenceReader entryBuffer,
      EntryEncodeConfig config) throws DirectoryException
  {
    // Next is the set of encoded object classes.  The encoding will
    // depend on the configuration.
    if (config.compressObjectClassSets())
    {
      return config.getCompressedSchema().decodeObjectClasses(entryBuffer);
    }

    Map<ObjectClass, String> objectClasses;
    {
      if(ver < 0x03)
      {
        // Next is the length of the object classes. It may be a
        // single byte or multiple bytes.
        int ocLength = entryBuffer.readBERLength();

        // The set of object classes will be encoded as a single
        // string with the object class names separated by zeros.
        objectClasses = new LinkedHashMap<>();
        int startPos = entryBuffer.position();
        for (int i=0; i < ocLength; i++)
        {
          if (entryBuffer.readByte() == 0x00)
          {
            int endPos = entryBuffer.position() - 1;
            addObjectClass(objectClasses, entryBuffer, startPos, endPos);

            entryBuffer.skip(1);
            startPos = entryBuffer.position();
          }
        }
        int endPos = entryBuffer.position();
        addObjectClass(objectClasses, entryBuffer, startPos, endPos);
      }
      else
      {
        // Next is the number of zero terminated object classes.
        int numOC = entryBuffer.readBERLength();
        objectClasses = new LinkedHashMap<>(numOC);
        for(int i = 0; i < numOC; i++)
        {
          int startPos = entryBuffer.position();
          while(entryBuffer.readByte() != 0x00)
          {}
          int endPos = entryBuffer.position() - 1;
          addObjectClass(objectClasses, entryBuffer, startPos, endPos);
          entryBuffer.skip(1);
        }
      }
    }

    return objectClasses;
  }

  /**
   * Adds the objectClass contained in the buffer to the map of object class.
   *
   * @param objectClasses
   *          the Map where to add the objectClass
   * @param entryBuffer
   *          the buffer containing the objectClass name
   * @param startPos
   *          the starting position in the buffer
   * @param endPos
   *          the ending position in the buffer
   */
  private static void addObjectClass(Map<ObjectClass, String> objectClasses,
      ByteSequenceReader entryBuffer, int startPos, int endPos)
  {
    entryBuffer.position(startPos);
    final String ocName = entryBuffer.readStringUtf8(endPos - startPos);
    objectClasses.put(getSchema().getObjectClass(ocName), ocName);
  }

  /**
   * Decode the attributes of an encoded entry.
   *
   * @param  ver The version of the entry encoding.
   * @param  entryBuffer The byte sequence containing the encoded
   *                     entry.
   * @param  config  The configuration that may be used to control how
   *                 the entry is encoded.
   *
   * @return  A map of the decoded object classes.
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to encode the entry.
   */
  private static Map<AttributeType, List<Attribute>>
  decodeAttributes(Byte ver, ByteSequenceReader entryBuffer,
                   EntryEncodeConfig config) throws DirectoryException
  {
    // Next is the total number of attributes.  It may be a
    // single byte or multiple bytes.
    int attrs = entryBuffer.readBERLength();


    // Now, we should iterate through the attributes and decode each one.
    Map<AttributeType, List<Attribute>> attributes = new LinkedHashMap<>(attrs);
    if (config.compressAttributeDescriptions())
    {
      for (int i=0; i < attrs; i++)
      {
        if(ver < 0x03)
        {
          // Version 2 includes a total attribute length
          entryBuffer.readBERLength();
        }
        // Decode the attribute.
        Attribute a = config.getCompressedSchema().decodeAttribute(entryBuffer);
        AttributeType attrType = a.getAttributeDescription().getAttributeType();
        List<Attribute> attrList = attributes.get(attrType);
        if (attrList == null)
        {
          attrList = new ArrayList<>(1);
          attributes.put(attrType, attrList);
        }
        attrList.add(a);
      }
    }
    else
    {
      int startPos;
      int endPos;
      for (int i=0; i < attrs; i++)
      {
        // First, we have the zero-terminated attribute name.
        startPos = entryBuffer.position();
        while (entryBuffer.readByte() != 0x00)
        {}
        endPos = entryBuffer.position()-1;
        entryBuffer.position(startPos);
        String name = entryBuffer.readStringUtf8(endPos - startPos);
        entryBuffer.skip(1);

        final AttributeBuilder builder = new AttributeBuilder(name);

        // Next, we have the number of values.
        int numValues = entryBuffer.readBERLength();

        // Next, we have the sequence of length-value pairs.
        for (int j=0; j < numValues; j++)
        {
          int valueLength = entryBuffer.readBERLength();
          builder.add(entryBuffer.readByteSequence(valueLength).toByteString());
        }


        // Create the attribute and add it to the set of attributes.
        Attribute a = builder.toAttribute();
        AttributeType attributeType = a.getAttributeDescription().getAttributeType();
        List<Attribute> attrList = attributes.get(attributeType);
        if (attrList == null)
        {
          attrList = new ArrayList<>(1);
          attributes.put(attributeType, attrList);
        }
        attrList.add(a);
      }
    }

    return attributes;
  }

  /**
   * Retrieves a list of the lines for this entry in LDIF form.  Long
   * lines will not be wrapped automatically.
   *
   * @return  A list of the lines for this entry in LDIF form.
   */
  public List<StringBuilder> toLDIF()
  {
    List<StringBuilder> ldifLines = new LinkedList<>();

    // First, append the DN.
    StringBuilder dnLine = new StringBuilder("dn");
    appendLDIFSeparatorAndValue(dnLine, ByteString.valueOfUtf8(dn.toString()));
    ldifLines.add(dnLine);

    // Next, add the set of objectclasses.
    for (String s : objectClasses.values())
    {
      StringBuilder ocLine = new StringBuilder("objectClass: ").append(s);
      ldifLines.add(ocLine);
    }

    // Finally, add the set of user and operational attributes.
    addLinesForAttributes(ldifLines, userAttributes);
    addLinesForAttributes(ldifLines, operationalAttributes);

    return ldifLines;
  }


  /**
   * Add LDIF lines for each passed in attributes.
   *
   * @param ldifLines
   *          the List where to add the LDIF lines
   * @param attributes
   *          the List of attributes to convert into LDIf lines
   */
  private void addLinesForAttributes(List<StringBuilder> ldifLines,
      Map<AttributeType, List<Attribute>> attributes)
  {
    for (List<Attribute> attrList : attributes.values())
    {
      for (Attribute a : attrList)
      {
        String attrName = a.getAttributeDescription().toString();
        for (ByteString v : a)
        {
          StringBuilder attrLine = new StringBuilder(attrName);
          appendLDIFSeparatorAndValue(attrLine, v);
          ldifLines.add(attrLine);
        }
      }
    }
  }


  /**
   * Writes this entry in LDIF form according to the provided
   * configuration.
   *
   * @param  exportConfig  The configuration that specifies how the
   *                       entry should be written.
   *
   * @return  {@code true} if the entry is actually written, or
   *          {@code false} if it is not for some reason.
   *
   * @throws  IOException  If a problem occurs while writing the
   *                       information.
   *
   * @throws  LDIFException  If a problem occurs while trying to
   *                         determine whether to write the entry.
   */
  public boolean toLDIF(LDIFExportConfig exportConfig)
         throws IOException, LDIFException
  {
    // See if this entry should be included in the export at all.
    try
    {
      if (! exportConfig.includeEntry(this))
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("Skipping entry %s because of the export configuration.", dn);
        }
        return false;
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new LDIFException(ERR_LDIF_COULD_NOT_EVALUATE_FILTERS_FOR_EXPORT.get(dn, e), e);
    }


    // Invoke LDIF export plugins on the entry if appropriate.
    if (exportConfig.invokeExportPlugins())
    {
      PluginResult.ImportLDIF pluginResult =
           getPluginConfigManager().invokeLDIFExportPlugins(exportConfig, this);
      if (! pluginResult.continueProcessing())
      {
        return false;
      }
    }


    // Get the information necessary to write the LDIF.
    BufferedWriter writer     = exportConfig.getWriter();
    int            wrapColumn = exportConfig.getWrapColumn();
    boolean        wrapLines  = wrapColumn > 1;


    // First, write the DN.  It will always be included.
    StringBuilder dnLine = new StringBuilder("dn");
    appendLDIFSeparatorAndValue(dnLine, ByteString.valueOfUtf8(dn.toString()));
    LDIFWriter.writeLDIFLine(dnLine, writer, wrapLines, wrapColumn);


    // Next, the set of objectclasses.
    final boolean typesOnly = exportConfig.typesOnly();
    if (exportConfig.includeObjectClasses())
    {
      if (typesOnly)
      {
        StringBuilder ocLine = new StringBuilder("objectClass:");
        LDIFWriter.writeLDIFLine(ocLine, writer, wrapLines, wrapColumn);
      }
      else
      {
        for (String s : objectClasses.values())
        {
          StringBuilder ocLine = new StringBuilder("objectClass: ").append(s);
          LDIFWriter.writeLDIFLine(ocLine, writer, wrapLines, wrapColumn);
        }
      }
    }
    else
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("Skipping objectclasses for entry %s because of the export configuration.", dn);
      }
    }


    // Now the set of user attributes.
    writeLDIFLines(userAttributes, typesOnly, "user", exportConfig, writer,
        wrapColumn, wrapLines);


    // Next, the set of operational attributes.
    if (exportConfig.includeOperationalAttributes())
    {
      writeLDIFLines(operationalAttributes, typesOnly, "operational",
          exportConfig, writer, wrapColumn, wrapLines);
    }
    else
    {
      if (logger.isTraceEnabled())
      {
        logger.trace(
            "Skipping all operational attributes for entry %s " +
            "because of the export configuration.", dn);
      }
    }


    // If we are not supposed to include virtual attributes, then
    // write any attributes that may normally be suppressed by a
    // virtual attribute.
    if (! exportConfig.includeVirtualAttributes())
    {
      for (Map.Entry<AttributeType, List<Attribute>> entry : suppressedAttributes.entrySet())
      {
        AttributeType t = entry.getKey();
        if (exportConfig.includeAttribute(t))
        {
          for (Attribute a : entry.getValue())
          {
            writeLDIFLine(a, typesOnly, writer, wrapLines, wrapColumn);
          }
        }
      }
    }


    // Make sure there is a blank line after the entry.
    writer.newLine();


    return true;
  }


  /**
   * Writes the provided List of attributes to LDIF using the provided
   * information.
   *
   * @param attributes
   *          the List of attributes to write as LDIF
   * @param typesOnly
   *          if true, only writes the type information, else writes the type
   *          information and values for the attribute.
   * @param attributeType
   *          the type of attribute being written to LDIF
   * @param exportConfig
   *          configures the export to LDIF
   * @param writer
   *          The writer to which the data should be written. It must not be {@code null}.
   * @param wrapLines
   *          Indicates whether to wrap long lines.
   * @param wrapColumn
   *          The column at which long lines should be wrapped.
   * @throws IOException
   *           If a problem occurs while writing the information.
   */
  private void writeLDIFLines(Map<AttributeType, List<Attribute>> attributes,
      final boolean typesOnly, String attributeType,
      LDIFExportConfig exportConfig, BufferedWriter writer, int wrapColumn,
      boolean wrapLines) throws IOException
  {
    for (Map.Entry<AttributeType, List<Attribute>> entry : attributes.entrySet())
    {
      AttributeType attrType = entry.getKey();
      if (exportConfig.includeAttribute(attrType))
      {
        List<Attribute> attrList = entry.getValue();
        for (Attribute a : attrList)
        {
          if (a.isVirtual() && !exportConfig.includeVirtualAttributes())
          {
            continue;
          }

          writeLDIFLine(a, typesOnly, writer, wrapLines, wrapColumn);
        }
      }
      else
      {
        if (logger.isTraceEnabled())
        {
          logger.trace("Skipping %s attribute %s for entry %s "
              + "because of the export configuration.", attributeType, attrType.getNameOrOID(), dn);
        }
      }
    }
  }


  /**
   * Writes the provided attribute to LDIF using the provided information.
   *
   * @param attribute
   *          the attribute to write to LDIF
   * @param typesOnly
   *          if true, only writes the type information, else writes the type
   *          information and values for the attribute.
   * @param writer
   *          The writer to which the data should be written. It must not be {@code null}.
   * @param wrapLines
   *          Indicates whether to wrap long lines.
   * @param wrapColumn
   *          The column at which long lines should be wrapped.
   * @throws IOException
   *           If a problem occurs while writing the information.
   */
  private void writeLDIFLine(Attribute attribute, final boolean typesOnly,
      BufferedWriter writer, boolean wrapLines, int wrapColumn)
      throws IOException
  {
    String attrName = attribute.getAttributeDescription().toString();
    if (typesOnly)
    {
      StringBuilder attrLine = new StringBuilder(attrName);
      attrLine.append(":");

      LDIFWriter.writeLDIFLine(attrLine, writer, wrapLines, wrapColumn);
    }
    else
    {
      for (ByteString v : attribute)
      {
        StringBuilder attrLine = new StringBuilder(attrName);
        appendLDIFSeparatorAndValue(attrLine, v);
        LDIFWriter.writeLDIFLine(attrLine, writer, wrapLines, wrapColumn);
      }
    }
  }



  /**
   * Retrieves the name of the protocol associated with this protocol
   * element.
   *
   * @return  The name of the protocol associated with this protocol
   *          element.
   */
  @Override
  public String getProtocolElementName()
  {
    return "Entry";
  }



  /**
   * Retrieves a hash code for this entry.
   *
   * @return  The hash code for this entry.
   */
  @Override
  public int hashCode()
  {
    int hashCode = dn.hashCode();
    for (ObjectClass oc : objectClasses.keySet())
    {
      hashCode += oc.hashCode();
    }

    hashCode += hashCode(userAttributes.values());
    hashCode += hashCode(operationalAttributes.values());
    return hashCode;
  }

  /**
   * Computes the hashCode for the list of attributes list.
   *
   * @param attributesLists
   *          the attributes for which to compute the hashCode
   * @return the hashCode for the list of attributes list.
   */
  private int hashCode(Collection<List<Attribute>> attributesLists)
  {
    int result = 0;
    for (List<Attribute> attributes : attributesLists)
    {
      for (Attribute a : attributes)
      {
        result += a.hashCode();
      }
    }
    return result;
  }



  /**
   * Indicates whether the provided object is equal to this entry.  In
   * order for the object to be considered equal, it must be an entry
   * with the same DN, set of object classes, and set of user and
   * operational attributes.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if the provided object may be considered
   *          equal to this entry, or {@code false} if not.
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (o == null)
    {
      return false;
    }
    if (! (o instanceof Entry))
    {
      return false;
    }

    Entry e = (Entry) o;
    return dn.equals(e.dn)
        && objectClasses.keySet().equals(e.objectClasses.keySet())
        && equals(userAttributes, e.userAttributes)
        && equals(operationalAttributes, e.operationalAttributes);
  }

  /**
   * Returns whether the 2 Maps are equal.
   *
   * @param attributes1
   *          the first Map of attributes
   * @param attributes2
   *          the second Map of attributes
   * @return true if the 2 Maps are equal, false otherwise
   */
  private boolean equals(Map<AttributeType, List<Attribute>> attributes1,
      Map<AttributeType, List<Attribute>> attributes2)
  {
    for (Map.Entry<AttributeType, List<Attribute>> entry : attributes1.entrySet())
    {
      AttributeType at = entry.getKey();
      List<Attribute> list1 = entry.getValue();
      List<Attribute> list2 = attributes2.get(at);
      if (list2 == null || list1.size() != list2.size())
      {
        return false;
      }
      for (Attribute a : list1)
      {
        if (!list2.contains(a))
        {
          return false;
        }
      }
    }
    return true;
  }



  /**
   * Retrieves a string representation of this protocol element.
   *
   * @return  A string representation of this protocol element.
   */
  @Override
  public String toString()
  {
    return toLDIFString();
  }



  /**
   * Appends a string representation of this protocol element to the
   * provided buffer.
   *
   * @param  buffer  The buffer into which the string representation
   *                 should be written.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append(this);
  }



  /**
   * Appends a string representation of this protocol element to the
   * provided buffer.
   *
   * @param  buffer  The buffer into which the string representation
   *                 should be written.
   * @param  indent  The number of spaces that should be used to
   *                 indent the resulting string representation.
   */
  @Override
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    for (StringBuilder b : toLDIF())
    {
      buffer.append(indentBuf);
      buffer.append(b);
      buffer.append(EOL);
    }
  }



  /**
   * Retrieves a string representation of this entry in LDIF form.
   *
   * @return  A string representation of this entry in LDIF form.
   */
  public String toLDIFString()
  {
    StringBuilder buffer = new StringBuilder();

    for (StringBuilder ldifLine : toLDIF())
    {
      buffer.append(ldifLine);
      buffer.append(EOL);
    }

    return buffer.toString();
  }



  /**
   * Appends a single-line representation of this entry to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 written.
   */
  public void toSingleLineString(StringBuilder buffer)
  {
    buffer.append("Entry(dn=\"");
    buffer.append(dn);
    buffer.append("\",objectClasses={");
    Utils.joinAsString(buffer, ",", objectClasses.values());
    buffer.append("},userAttrs={");
    appendAttributes(buffer, userAttributes.values());
    buffer.append("},operationalAttrs={");
    appendAttributes(buffer, operationalAttributes.values());
    buffer.append("})");
  }

  /**
   * Appends the attributes to the StringBuilder.
   *
   * @param buffer
   *          the StringBuilder where to append
   * @param attributesLists
   *          the attributesLists to append
   */
  private void appendAttributes(StringBuilder buffer,
      Collection<List<Attribute>> attributesLists)
  {
    boolean firstAttr = true;
    for (List<Attribute> attributes : attributesLists)
    {
      for (Attribute a : attributes)
      {
        if (firstAttr)
        {
          firstAttr = false;
        }
        else
        {
          buffer.append(",");
        }

        buffer.append(a.getAttributeDescription());

        buffer.append("={");
        Utils.joinAsString(buffer, ",", a);
        buffer.append("}");
      }
    }
  }



  /**
   * Retrieves the requested attribute element for the specified
   * attribute type and options or {@code null} if this entry
   * does not contain an attribute with the specified attribute type
   * and options.
   *
   * @param attributeDescription
   *          The attribute description to retrieve.
   * @return The requested attribute element for the specified
   *         attribute type and options, or {@code null} if the
   *         specified attribute type is not present in this entry
   *         with the provided set of options.
   */
  public Attribute getAttribute(AttributeDescription attributeDescription)
  {
    List<Attribute> attributes = getAllAttributes0(attributeDescription.getAttributeType());
    if (attributes != null)
    {
      for (Attribute attribute : attributes)
      {
        if (attribute.getAttributeDescription().equals(attributeDescription))
        {
          return attribute;
        }
      }
    }
    else if (attributeDescription.getAttributeType().equals(CoreSchema.getObjectClassAttributeType()))
    {
      return getObjectClassAttribute();
    }
    return null;
  }



  /**
   * Adds the provided attribute to this entry. If an attribute with
   * the provided type and options already exists, then it will be
   * either merged or replaced depending on the value of
   * <code>replace</code>.
   *
   * @param attribute
   *          The attribute to add/replace in this entry.
   * @param duplicateValues
   *          A list to which any duplicate values will be added.
   * @param replace
   *          {@code true} if the attribute should replace any
   *          existing attribute.
   */
  private void setAttribute(Attribute attribute,
      Collection<? super ByteString> duplicateValues, boolean replace)
  {
    attachment = null;

    if (attribute.getAttributeDescription().getAttributeType().isObjectClass())
    {
      setObjectClassAttribute(attribute, duplicateValues, replace);
    }
    else
    {
      setNonObjectClassAttribute(attribute, duplicateValues, replace);
    }
  }

  private void setObjectClassAttribute(
      Attribute attribute, Collection<? super ByteString> duplicateValues, boolean replace)
  {
    AttributeType attrType = attribute.getAttributeDescription().getAttributeType();
    // We will not do any validation of the object classes - this is
    // left to the caller.
    if (replace)
    {
      objectClasses.clear();
    }

    MatchingRule rule = attrType.getEqualityMatchingRule();
    for (ByteString v : attribute)
    {
      String name = v.toString();
      String lowerName = toLowerName(rule, v);

      // Create a default object class if necessary.
      ObjectClass oc = getSchema().getObjectClass(lowerName);

      if (replace)
      {
        objectClasses.put(oc, name);
      }
      else
      {
        if (objectClasses.containsKey(oc))
        {
          duplicateValues.add(v);
        }
        else
        {
          objectClasses.put(oc, name);
        }
      }
    }
  }

  private void setNonObjectClassAttribute(
      Attribute attribute, Collection<? super ByteString> duplicateValues, boolean replace)
  {
    AttributeDescription attrDesc = attribute.getAttributeDescription();
    AttributeType attrType = attrDesc.getAttributeType();
    List<Attribute> attributes = getAllAttributes0(attrType);
    if (attributes == null)
    {
      // Do nothing if we are deleting a non-existing attribute.
      if (replace && attribute.isEmpty())
      {
        return;
      }

      // We are adding the first attribute with this attribute type.
      putAttributes(attrType, newArrayList(attribute));
      return;
    }

    // There are already attributes with the same attribute type.
    for (ListIterator<Attribute> it = attributes.listIterator(); it.hasNext();)
    {
      Attribute a = it.next();
      if (a.getAttributeDescription().equals(attrDesc))
      {
        if (replace)
        {
          if (!attribute.isEmpty())
          {
            it.set(attribute);
          }
          else
          {
            it.remove();

            if (attributes.isEmpty())
            {
              removeAttributes(attrType);
            }
          }
        }
        else
        {
          AttributeBuilder builder = new AttributeBuilder(a);
          for (ByteString v : attribute)
          {
            if (!builder.add(v))
            {
              duplicateValues.add(v);
            }
          }
          it.set(builder.toAttribute());
        }
        return;
      }
    }

    // There were no attributes with the same options.
    if (replace && attribute.isEmpty())
    {
      // Do nothing.
      return;
    }

    attributes.add(attribute);
  }



  /**
   * Returns an entry containing only those attributes of this entry
   * which match the provided criteria.
   *
   * @param attrNameList
   *          The list of attributes to include, may include wild
   *          cards.
   * @param omitValues
   *          Indicates whether to omit attribute values when
   *          processing.
   * @param omitReal
   *          Indicates whether to exclude real attributes.
   * @param omitVirtual
   *          Indicates whether to exclude virtual attributes.
   * @return An entry containing only those attributes of this entry
   *         which match the provided criteria.
   */
  public Entry filterEntry(Set<String> attrNameList,
      boolean omitValues, boolean omitReal, boolean omitVirtual)
  {
    final AttributeType ocType = CoreSchema.getObjectClassAttributeType();

    Map<ObjectClass, String> objectClassesCopy;
    Map<AttributeType, List<Attribute>> userAttrsCopy;
    Map<AttributeType, List<Attribute>> operationalAttrsCopy;

    if (attrNameList == null || attrNameList.isEmpty())
    {
      // Common case: return filtered user attributes.
      userAttrsCopy = new LinkedHashMap<>(userAttributes.size());
      operationalAttrsCopy = new LinkedHashMap<>(0);

      if (omitReal)
      {
        objectClassesCopy = new LinkedHashMap<>(0);
      }
      else if (omitValues)
      {
        objectClassesCopy = new LinkedHashMap<>(0);

        // Add empty object class attribute.
        userAttrsCopy.put(ocType, newArrayList(Attributes.empty(ocType)));
      }
      else
      {
        objectClassesCopy = new LinkedHashMap<>(objectClasses);

        // First, add the objectclass attribute.
        Attribute ocAttr = getObjectClassAttribute();
        if (ocAttr != null)
        {
          userAttrsCopy.put(ocType, newArrayList(ocAttr));
        }
      }

      // Copy all user attributes.
      deepCopy(userAttributes, userAttrsCopy, omitValues, true,
          omitReal, omitVirtual, true);
    }
    else
    {
      // Incrementally build table of attributes.
      if (omitReal || omitValues)
      {
        objectClassesCopy = new LinkedHashMap<>(0);
      }
      else
      {
        objectClassesCopy = new LinkedHashMap<>(objectClasses.size());
      }

      userAttrsCopy = new LinkedHashMap<>(userAttributes.size());
      operationalAttrsCopy = new LinkedHashMap<>(operationalAttributes.size());

      for (String attrName : attrNameList)
      {
        if ("*".equals(attrName))
        {
          // This is a special placeholder indicating that all user
          // attributes should be returned.
          if (!omitReal)
          {
            if (omitValues)
            {
              // Add empty object class attribute.
              userAttrsCopy.put(ocType, newArrayList(Attributes.empty(ocType)));
            }
            else
            {
              // Add the objectclass attribute.
              objectClassesCopy.putAll(objectClasses);
              Attribute ocAttr = getObjectClassAttribute();
              if (ocAttr != null)
              {
                userAttrsCopy.put(ocType, newArrayList(ocAttr));
              }
            }
          }

          // Copy all user attributes.
          deepCopy(userAttributes, userAttrsCopy, omitValues, true,
              omitReal, omitVirtual, true);
          continue;
        }
        else if ("+".equals(attrName))
        {
          // This is a special placeholder indicating that all
          // operational attributes should be returned.
          deepCopy(operationalAttributes, operationalAttrsCopy,
              omitValues, true, omitReal, omitVirtual, true);
          continue;
        }

        final AttributeDescription attrDesc;
        try
        {
          attrDesc = AttributeDescription.valueOf(attrName);
        }
        catch (LocalizedIllegalArgumentException e)
        {
          // For compatibility tolerate and ignore illegal attribute types, instead of
          // aborting with a ProtocolError (2) as per the RFC. See OPENDJ-2813.
          logger.traceException(e);
          continue;
        }
        attrName = attrDesc.getNameOrOID();
        final AttributeType attrType = attrDesc.getAttributeType();
        if (attrType.isPlaceHolder())
        {
          // Unrecognized attribute type - do best effort search.
          for (Map.Entry<AttributeType, List<Attribute>> e : userAttributes.entrySet())
          {
            AttributeType t = e.getKey();
            if (t.hasNameOrOID(attrType.getNameOrOID()))
            {
              mergeAttributeLists(e.getValue(), userAttrsCopy, attrDesc,
                  omitValues, omitReal, omitVirtual);
              continue;
            }
          }

          for (Map.Entry<AttributeType, List<Attribute>> e : operationalAttributes.entrySet())
          {
            AttributeType t = e.getKey();
            if (t.hasNameOrOID(attrType.getNameOrOID()))
            {
              mergeAttributeLists(e.getValue(), operationalAttrsCopy, attrDesc,
                 omitValues, omitReal, omitVirtual);
              continue;
            }
          }
        }
        else
        {
          // Recognized attribute type.
          if (attrType.isObjectClass()) {
            if (!omitReal)
            {
              if (omitValues)
              {
                userAttrsCopy.put(ocType, newArrayList(Attributes.empty(ocType, attrName)));
              }
              else
              {
                Attribute ocAttr = getObjectClassAttribute();
                if (ocAttr != null)
                {
                  if (!attrName.equals(ocAttr.getAttributeDescription().getNameOrOID()))
                  {
                    // User requested non-default object class type name.
                    AttributeBuilder builder = new AttributeBuilder(AttributeDescription.create(attrName, ocType));
                    builder.addAll(ocAttr);
                    ocAttr = builder.toAttribute();
                  }

                  userAttrsCopy.put(ocType, newArrayList(ocAttr));
                }
              }
            }
          }
          else
          {
            List<Attribute> attrList = getUserAttribute(attrType);
            if (!attrList.isEmpty())
            {
              mergeAttributeLists(attrList, userAttrsCopy, attrDesc,
                  omitValues, omitReal, omitVirtual);
            }
            else
            {
              attrList = getOperationalAttribute(attrType);
              if (!attrList.isEmpty())
              {
                mergeAttributeLists(attrList, operationalAttrsCopy, attrDesc,
                    omitValues, omitReal, omitVirtual);
              }
            }
          }
        }
      }
    }

    return new Entry(dn, objectClassesCopy, userAttrsCopy,
                     operationalAttrsCopy);
  }

  private void mergeAttributeLists(List<Attribute> sourceList,
      Map<AttributeType, List<Attribute>> destMap, AttributeDescription attrDesc,
      boolean omitValues, boolean omitReal, boolean omitVirtual)
  {
    if (sourceList == null)
    {
      return;
    }

    final String attrName = attrDesc.getNameOrOID();
    for (Attribute attribute : sourceList)
    {
      AttributeDescription subAttrDesc = attribute.getAttributeDescription();
      if (attribute.isEmpty()
          || (omitReal && attribute.isReal())
          || (omitVirtual && attribute.isVirtual())
          || !subAttrDesc.isSubTypeOf(attrDesc))
      {
        continue;
      }

      // If a non-default attribute name was provided or if the
      // attribute has options then we will need to rebuild the
      // attribute so that it contains the user-requested names and options.
      final AttributeType subAttrType = subAttrDesc.getAttributeType();

      if ((attrName != null && !attrName.equals(subAttrDesc.getNameOrOID()))
          || attrDesc.hasOptions())
      {
        // We want to use the user-provided name only if this attribute has
        // the same type as the requested type. This might not be the case for
        // sub-types e.g. requesting "name" and getting back "cn" - we don't
        // want to rename "name" to "cn".
        AttributeType attrType = attrDesc.getAttributeType();
        AttributeDescription newAttrDesc;
        if (attrName == null || !subAttrType.equals(attrType))
        {
          newAttrDesc = AttributeDescription.create(subAttrDesc.getNameOrOID(), subAttrDesc.getAttributeType());
        }
        else
        {
          newAttrDesc = AttributeDescription.create(attrName, subAttrDesc.getAttributeType());
        }

        AttributeBuilder builder = new AttributeBuilder(newAttrDesc);
        builder.setOptions(attrDesc.getOptions());
        // Now add in remaining options from original attribute
        // (this will not overwrite options already present).
        builder.setOptions(subAttrDesc.getOptions());
        if (!omitValues)
        {
          builder.addAll(attribute);
        }
        attribute = builder.toAttribute();
      }
      else if (omitValues)
      {
        attribute = Attributes.empty(attribute);
      }

      // Now put the attribute into the destination map.
      // Be careful of duplicates.
      List<Attribute> attrList = destMap.get(subAttrType);

      if (attrList == null)
      {
        // Assume that they'll all go in the one list. This isn't
        // always the case, for example if the list contains sub-types.
        attrList = new ArrayList<>(sourceList.size());
        attrList.add(attribute);
        destMap.put(subAttrType, attrList);
      }
      else
      {
        // The attribute may have already been put in the list.
        //
        // This may occur in two cases:
        //
        // 1) The attribute is identified by more than one attribute
        //    type description in the attribute list (e.g. in a wildcard).
        //
        // 2) The attribute has both a real and virtual component.
        //
        boolean found = false;
        for (ListIterator<Attribute> it = attrList.listIterator(); it.hasNext();)
        {
          Attribute otherAttribute = it.next();
          if (otherAttribute.getAttributeDescription().equals(subAttrDesc))
          {
            // Assume that wildcards appear first in an attribute
            // list with more specific attribute names afterwards:
            // let the attribute name and options from the later
            // attribute take preference.
            it.set(Attributes.merge(attribute, otherAttribute));
            found = true;
          }
        }

        if (!found)
        {
          attrList.add(attribute);
        }
      }
    }
  }

  /**
   * Retrieves the entire set of attributes for this entry. This includes both user and operational
   * attributes.
   *
   * @return The entire set of attributes for this entry.
   */
  @Deprecated
  public List<Attribute> getAttributes()
  {
    return collect(getAllAttributes(), new ArrayList<Attribute>());
  }

  /**
   * Kept for ABI compatibility.
   *
   * @param attributeType
   *          The attribute type to retrieve.
   * @return The requested attributes.
   * @see #getAllAttributes(AttributeType)
   */
  @Deprecated
  public List<Attribute> getAttribute(AttributeType attributeType)
  {
    return getAllAttributes(attributeType);
  }

  /**
   * Kept for ABI compatibility.
   *
   * @param attributeType
   *          The attribute type to retrieve.
   * @param includeSubordinates
   *          Whether to include any subordinate attributes of the attribute type being retrieved.
   * @return The requested attributes.
   * @see #getAllAttributes(AttributeType,boolean)
   */
  @Deprecated
  public List<Attribute> getAttribute(AttributeType attributeType, boolean includeSubordinates)
  {
    return getAllAttributes(attributeType, includeSubordinates);
  }

  /**
   * Kept for ABI compatibility.
   *
   * @param nameOrOID
   *          The name or OID of the attribute to return
   * @return The requested attributes.
   * @see #getAllAttributes(String)
   */
  @Deprecated
  public List<Attribute> getAttribute(String nameOrOID)
  {
    return collect(getAllAttributes(nameOrOID), new ArrayList<Attribute>());
  }

  // Cannot preserve this API because it conflicts with another method
  // @Deprecated
  // public List<Attribute> getAttribute(AttributeDescription attributeDescription)
  // {
  // return collect(getAllAttributes(attributeDescription), new ArrayList<Attribute>());
  // }

  /**
   * Ensures that this entry contains the provided attribute and its values.
   *
   * @param attribute
   *          The attribute to add or merge with this entry.
   * @param duplicateValues
   *          A list to which any duplicate values will be added.
   * @see #addAttribute(Attribute, Collection)
   */
  @Deprecated
  public void addAttribute(Attribute attribute, List<ByteString> duplicateValues)
  {
    addAttribute(attribute, (Collection<ByteString>) duplicateValues);
  }

  /**
   * Ensures that this entry does not contain the provided attribute values.
   *
   * @param attribute
   *          The attribute containing the information to use to perform the removal.
   * @param missingValues
   *          A list to which any values contained in the provided attribute but not present in the
   *          entry will be added.
   * @return whether the attribute type was present and the specified values that were present were
   *         removed.
   * @see #removeAttribute(Attribute, Collection)
   */
  @Deprecated
  public boolean removeAttribute(Attribute attribute, List<ByteString> missingValues)
  {
    return removeAttribute(attribute, (Collection<ByteString>) missingValues);
  }
}
