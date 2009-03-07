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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.messages.Message;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.ExtensibleMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.schema.CaseIgnoreEqualityMatchingRule;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure that holds information about
 * the components of the Directory Server schema.  It includes the
 * following kinds of elements:
 *
 * <UL>
 *   <LI>Attribute type definitions</LI>
 *   <LI>Objectclass definitions</LI>
 *   <LI>Attribute syntax definitions</LI>
 *   <LI>Matching rule definitions</LI>
 *   <LI>Matching rule use definitions</LI>
 *   <LI>DIT content rule definitions</LI>
 *   <LI>DIT structure rule definitions</LI>
 *   <LI>Name form definitions</LI>
 * </UL>
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class Schema
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The matching rule that will be used to normalize schema element
  // definitions.
  private MatchingRule normalizationMatchingRule;

  // The set of subordinate attribute types registered within the
  // server schema.
  private ConcurrentHashMap<AttributeType,List<AttributeType>>
               subordinateTypes;

  // The set of attribute type definitions for this schema, mapped
  // between the lowercase names and OID for the definition and the
  // attribute type itself.
  private ConcurrentHashMap<String,AttributeType> attributeTypes;

  // The set of objectclass definitions for this schema, mapped
  // between the lowercase names and OID for the definition and the
  // objectclass itself.
  private ConcurrentHashMap<String,ObjectClass> objectClasses;

  // The set of attribute syntaxes for this schema, mapped between the
  // OID for the syntax and the syntax itself.
  private ConcurrentHashMap<String,AttributeSyntax> syntaxes;

  // The entire set of matching rules for this schema, mapped between
  // the lowercase names and OID for the definition and the matching
  // rule itself.
  private ConcurrentHashMap<String,MatchingRule> matchingRules;

  // The set of approximate matching rules for this schema, mapped
  // between the lowercase names and OID for the definition and the
  // matching rule itself.
  private ConcurrentHashMap<String,ApproximateMatchingRule>
               approximateMatchingRules;

  // The set of equality matching rules for this schema, mapped
  // between the lowercase names and OID for the definition and the
  // matching rule itself.
  private ConcurrentHashMap<String,EqualityMatchingRule>
               equalityMatchingRules;

  // The set of ordering matching rules for this schema, mapped
  // between the lowercase names and OID for the definition and the
  // matching rule itself.
  private ConcurrentHashMap<String,OrderingMatchingRule>
               orderingMatchingRules;

  // The set of substring matching rules for this schema, mapped
  // between the lowercase names and OID for the definition and the
  // matching rule itself.
  private ConcurrentHashMap<String,SubstringMatchingRule>
               substringMatchingRules;

  // The set of extensible matching rules for this schema, mapped
  // between the lowercase names and OID for the definition and the
  // matching rule itself.
  private ConcurrentHashMap<String,ExtensibleMatchingRule>
               extensibleMatchingRules;

  // The set of matching rule uses for this schema, mapped between the
  // matching rule for the definition and the matching rule use
  // itself.
  private ConcurrentHashMap<MatchingRule,MatchingRuleUse>
               matchingRuleUses;

  // The set of DIT content rules for this schema, mapped between the
  // structural objectclass for the definition and the DIT content
  // rule itself.
  private ConcurrentHashMap<ObjectClass,DITContentRule>
               ditContentRules;

  // The set of DIT structure rules for this schema, mapped between
  // the name form for the definition and the DIT structure rule
  // itself.
  private ConcurrentHashMap<Integer,DITStructureRule>
               ditStructureRulesByID;

  // The set of DIT structure rules for this schema, mapped between
  // the name form for the definition and the DIT structure rule
  // itself.
  private ConcurrentHashMap<NameForm,DITStructureRule>
               ditStructureRulesByNameForm;

  // The set of name forms for this schema, mapped between the
  // structural objectclass for the definition and the name form
  // itself.
  private ConcurrentHashMap<ObjectClass,NameForm> nameFormsByOC;

  // The set of name forms for this schema, mapped between the
  // names/OID and the name form itself.
  private ConcurrentHashMap<String,NameForm> nameFormsByName;

  // The set of pre-encoded attribute syntax representations.
  private LinkedHashSet<AttributeValue> syntaxSet;

  // The set of pre-encoded attribute type representations.
  private LinkedHashSet<AttributeValue> attributeTypeSet;

  // The set of pre-encoded DIT content rule representations.
  private LinkedHashSet<AttributeValue> ditContentRuleSet;

  // The set of pre-encoded DIT structure rule representations.
  private LinkedHashSet<AttributeValue> ditStructureRuleSet;

  // The set of pre-encoded matching rule representations.
  private LinkedHashSet<AttributeValue> matchingRuleSet;

  // The set of pre-encoded matching rule use representations.
  private LinkedHashSet<AttributeValue> matchingRuleUseSet;

  // The set of pre-encoded name form representations.
  private LinkedHashSet<AttributeValue> nameFormSet;

  // The set of pre-encoded objectclass representations.
  private LinkedHashSet<AttributeValue> objectClassSet;

  // The oldest modification timestamp for any schema configuration
  // file.
  private long oldestModificationTime;

  // The youngest modification timestamp for any schema configuration
  // file.
  private long youngestModificationTime;

  // A set of extra attributes that are not used directly by
  // the schema but may be used by other component to store
  // information in the schema.
  // ex : Replication uses this to store its state and GenerationID.

  private Map<String, Attribute> extraAttributes =
    new HashMap<String, Attribute>();



  /**
   * Creates a new schema structure with all elements initialized but
   * empty.
   */
  public Schema()
  {
    attributeTypes = new ConcurrentHashMap<String,AttributeType>();
    objectClasses = new ConcurrentHashMap<String,ObjectClass>();
    syntaxes = new ConcurrentHashMap<String,AttributeSyntax>();
    matchingRules = new ConcurrentHashMap<String,MatchingRule>();
    approximateMatchingRules =
         new ConcurrentHashMap<String,ApproximateMatchingRule>();
    equalityMatchingRules =
         new ConcurrentHashMap<String,EqualityMatchingRule>();
    orderingMatchingRules =
         new ConcurrentHashMap<String,OrderingMatchingRule>();
    substringMatchingRules =
         new ConcurrentHashMap<String,SubstringMatchingRule>();
    extensibleMatchingRules =
        new ConcurrentHashMap<String,ExtensibleMatchingRule>();
    matchingRuleUses =
         new ConcurrentHashMap<MatchingRule,MatchingRuleUse>();
    ditContentRules =
         new ConcurrentHashMap<ObjectClass,DITContentRule>();
    ditStructureRulesByID =
         new ConcurrentHashMap<Integer,DITStructureRule>();
    ditStructureRulesByNameForm =
         new ConcurrentHashMap<NameForm,DITStructureRule>();
    nameFormsByOC = new ConcurrentHashMap<ObjectClass,NameForm>();
    nameFormsByName = new ConcurrentHashMap<String,NameForm>();
    subordinateTypes =
         new ConcurrentHashMap<AttributeType,List<AttributeType>>();


    syntaxSet           = new LinkedHashSet<AttributeValue>();
    attributeTypeSet    = new LinkedHashSet<AttributeValue>();
    ditContentRuleSet   = new LinkedHashSet<AttributeValue>();
    ditStructureRuleSet = new LinkedHashSet<AttributeValue>();
    matchingRuleSet     = new LinkedHashSet<AttributeValue>();
    matchingRuleUseSet  = new LinkedHashSet<AttributeValue>();
    nameFormSet         = new LinkedHashSet<AttributeValue>();
    objectClassSet      = new LinkedHashSet<AttributeValue>();

    normalizationMatchingRule = new CaseIgnoreEqualityMatchingRule();
    oldestModificationTime    = System.currentTimeMillis();
    youngestModificationTime  = oldestModificationTime;
  }



  /**
   * Retrieves the attribute type definitions for this schema, as a
   * mapping between the lowercase names and OIDs for the attribute
   * type and the attribute type itself.  Each attribute type may be
   * associated with multiple keys (once for the OID and again for
   * each name).  The contents of the returned mapping must not be
   * altered.
   *
   * @return  The attribute type definitions for this schema.
   */
  public ConcurrentHashMap<String,AttributeType> getAttributeTypes()
  {
    return attributeTypes;
  }



  /**
   * Retrieves the set of defined attribute types for this schema.
   *
   * @return  The set of defined attribute types for this schema.
   */
  public LinkedHashSet<AttributeValue> getAttributeTypeSet()
  {
    return attributeTypeSet;
  }



  /**
   * Indicates whether this schema definition includes an attribute
   * type with the provided name or OID.
   *
   * @param  lowerName  The name or OID for which to make the
   *                    determination, formatted in all lowercase
   *                    characters.
   *
   * @return  {@code true} if this schema contains an attribute type
   *          with the provided name or OID, or {@code false} if not.
   */
  public boolean hasAttributeType(String lowerName)
  {
    return attributeTypes.containsKey(lowerName);
  }



  /**
   * Retrieves the attribute type definition with the specified name
   * or OID.
   *
   * @param  lowerName  The name or OID of the attribute type to
   *                    retrieve, formatted in all lowercase
   *                    characters.
   *
   * @return  The requested attribute type, or <CODE>null</CODE> if no
   *          type is registered with the provided name or OID.
   */
  public AttributeType getAttributeType(String lowerName)
  {
    return attributeTypes.get(lowerName);
  }



  /**
   * Registers the provided attribute type definition with this
   * schema.
   *
   * @param  attributeType      The attribute type to register with
   *                            this schema.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another attribute
   *                            type with the same OID or name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerAttributeType(AttributeType attributeType,
                                    boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (attributeTypes)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(attributeType.getOID());
        if (attributeTypes.containsKey(oid))
        {
          AttributeType conflictingType = attributeTypes.get(oid);

          Message message = ERR_SCHEMA_CONFLICTING_ATTRIBUTE_OID.
              get(attributeType.getNameOrOID(), oid,
                  conflictingType.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }

        for (String name : attributeType.getNormalizedNames())
        {
          if (attributeTypes.containsKey(name))
          {
            AttributeType conflictingType = attributeTypes.get(name);

            Message message = ERR_SCHEMA_CONFLICTING_ATTRIBUTE_NAME.
                get(attributeType.getNameOrOID(), name,
                    conflictingType.getNameOrOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message);
          }
        }
      }

      attributeTypes.put(toLowerCase(attributeType.getOID()),
                         attributeType);

      for (String name : attributeType.getNormalizedNames())
      {
        attributeTypes.put(name, attributeType);
      }

      AttributeType superiorType = attributeType.getSuperiorType();
      if (superiorType != null)
      {
        registerSubordinateType(attributeType, superiorType);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = attributeType.getDefinition();
      ByteString rawValue = ByteString.valueOf(valueString);
      ByteString normValue =
          normalizationMatchingRule.normalizeValue(rawValue);
      attributeTypeSet.add(AttributeValues.create(rawValue,
          normValue));
    }
  }



  /**
   * Deregisters the provided attribute type definition with this
   * schema.
   *
   * @param  attributeType  The attribute type to deregister with this
   *                        schema.
   */
  public void deregisterAttributeType(AttributeType attributeType)
  {
    synchronized (attributeTypes)
    {
      attributeTypes.remove(toLowerCase(attributeType.getOID()),
                            attributeType);

      for (String name : attributeType.getNormalizedNames())
      {
        attributeTypes.remove(name, attributeType);
      }

      AttributeType superiorType = attributeType.getSuperiorType();
      if (superiorType != null)
      {
        deregisterSubordinateType(attributeType, superiorType);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = attributeType.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        attributeTypeSet.remove(AttributeValues.create(rawValue,
                                                   normValue));
      }
      catch (Exception e)
      {
        String valueString = attributeType.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        attributeTypeSet.remove(AttributeValues.create(rawValue,
                                                   normValue));
      }
    }
  }



  /**
   * Registers the provided attribute type as a subtype of the given
   * superior attribute type, recursively following any additional
   * elements in the superior chain.
   *
   * @param  attributeType  The attribute type to be registered as a
   *                        subtype for the given superior type.
   * @param  superiorType   The superior type for which to register
   *                        the given attribute type as a subtype.
   */
  private void registerSubordinateType(AttributeType attributeType,
                                       AttributeType superiorType)
  {
    List<AttributeType> subTypes = subordinateTypes.get(superiorType);
    if (subTypes == null)
    {
      superiorType.setMayHaveSubordinateTypes();
      subTypes = new LinkedList<AttributeType>();
      subTypes.add(attributeType);
      subordinateTypes.put(superiorType, subTypes);
    }
    else if (! subTypes.contains(attributeType))
    {
      superiorType.setMayHaveSubordinateTypes();
      subTypes.add(attributeType);

      AttributeType higherSuperior = superiorType.getSuperiorType();
      if (higherSuperior != null)
      {
        registerSubordinateType(attributeType, higherSuperior);
      }
    }
  }



  /**
   * Deregisters the provided attribute type as a subtype of the given
   * superior attribute type, recursively following any additional
   * elements in the superior chain.
   *
   * @param  attributeType  The attribute type to be deregistered as a
   *                        subtype for the given superior type.
   * @param  superiorType   The superior type for which to deregister
   *                        the given attribute type as a subtype.
   */
  private void deregisterSubordinateType(AttributeType attributeType,
                                         AttributeType superiorType)
  {
    List<AttributeType> subTypes = subordinateTypes.get(superiorType);
    if (subTypes != null)
    {
      if (subTypes.remove(attributeType))
      {
        AttributeType higherSuperior = superiorType.getSuperiorType();
        if (higherSuperior != null)
        {
          deregisterSubordinateType(attributeType, higherSuperior);
        }
      }
    }
  }



  /**
   * Retrieves the set of subtypes registered for the given attribute
   * type.
   *
   * @param  attributeType  The attribute type for which to retrieve
   *                        the set of registered subtypes.
   *
   * @return  The set of subtypes registered for the given attribute
   *          type, or an empty set if there are no subtypes
   *          registered for the attribute type.
   */
  public Iterable<AttributeType>
              getSubTypes(AttributeType attributeType)
  {
    List<AttributeType> subTypes =
         subordinateTypes.get(attributeType);
    if (subTypes == null)
    {
      return Collections.<AttributeType>emptyList();
    }
    else
    {
      return subTypes;
    }
  }



  /**
   * Retrieves the objectclass definitions for this schema, as a
   * mapping between the lowercase names and OIDs for the objectclass
   * and the objectclass itself.  Each objectclass may be associated
   * with multiple keys (once for the OID and again for each name).
   * The contents of the returned mapping must not be altered.
   *
   * @return  The objectclass definitions for this schema.
   */
  public ConcurrentHashMap<String,ObjectClass> getObjectClasses()
  {
    return objectClasses;
  }



  /**
   * Retrieves the set of defined objectclasses for this schema.
   *
   * @return  The set of defined objectclasses for this schema.
   */
  public LinkedHashSet<AttributeValue> getObjectClassSet()
  {
    return objectClassSet;
  }



  /**
   * Indicates whether this schema definition includes an objectclass
   * with the provided name or OID.
   *
   * @param  lowerName  The name or OID for which to make the
   *                    determination, formatted in all lowercase
   *                    characters.
   *
   * @return  {@code true} if this schema contains an objectclass with
   *          the provided name or OID, or {@code false} if not.
   */
  public boolean hasObjectClass(String lowerName)
  {
    return objectClasses.containsKey(lowerName);
  }



  /**
   * Retrieves the objectclass definition with the specified name or
   * OID.
   *
   * @param  lowerName  The name or OID of the objectclass to
   *                    retrieve, formatted in all lowercase
   *                    characters.
   *
   * @return  The requested objectclass, or <CODE>null</CODE> if no
   *          class is registered with the provided name or OID.
   */
  public ObjectClass getObjectClass(String lowerName)
  {
    return objectClasses.get(lowerName);
  }



  /**
   * Registers the provided objectclass definition with this schema.
   *
   * @param  objectClass        The objectclass to register with this
   *                            schema.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another objectclass
   *                            with the same OID or name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>.
   */
  public void registerObjectClass(ObjectClass objectClass,
                                  boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (objectClasses)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(objectClass.getOID());
        if (objectClasses.containsKey(oid))
        {
          ObjectClass conflictingClass = objectClasses.get(oid);

          Message message = ERR_SCHEMA_CONFLICTING_OBJECTCLASS_OID.
              get(objectClass.getNameOrOID(), oid,
                  conflictingClass.getNameOrOID());
          throw new DirectoryException(
                       ResultCode.CONSTRAINT_VIOLATION, message);
        }

        for (String name : objectClass.getNormalizedNames())
        {
          if (objectClasses.containsKey(name))
          {
            ObjectClass conflictingClass = objectClasses.get(name);

            Message message = ERR_SCHEMA_CONFLICTING_OBJECTCLASS_NAME.
                get(objectClass.getNameOrOID(), name,
                    conflictingClass.getNameOrOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message);
          }
        }
      }

      objectClasses.put(toLowerCase(objectClass.getOID()),
                        objectClass);

      for (String name : objectClass.getNormalizedNames())
      {
        objectClasses.put(name, objectClass);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = objectClass.getDefinition();
      ByteString rawValue = ByteString.valueOf(valueString);
      ByteString normValue =
          normalizationMatchingRule.normalizeValue(rawValue);
      objectClassSet.add(AttributeValues.create(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided objectclass definition with this schema.
   *
   * @param  objectClass  The objectclass to deregister with this
   *                      schema.
   */
  public void deregisterObjectClass(ObjectClass objectClass)
  {
    synchronized (objectClasses)
    {
      objectClasses.remove(toLowerCase(objectClass.getOID()),
                           objectClass);

      for (String name : objectClass.getNormalizedNames())
      {
        objectClasses.remove(name, objectClass);
      }


      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = objectClass.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        objectClassSet.remove(AttributeValues.create(rawValue,
                                                 normValue));
      }
      catch (Exception e)
      {
        String valueString = objectClass.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        objectClassSet.remove(AttributeValues.create(rawValue,
                                                 normValue));
      }
    }
  }



  /**
   * Retrieves the attribute syntax definitions for this schema, as a
   * mapping between the OID for the syntax and the syntax itself.
   * Each syntax should only be present once, since its only key is
   * its OID.  The contents of the returned mapping must not be
   * altered.
   *
   * @return  The attribute syntax definitions for this schema.
   */
  public ConcurrentHashMap<String,AttributeSyntax> getSyntaxes()
  {
    return syntaxes;
  }



  /**
   * Retrieves the set of defined attribute syntaxes for this schema.
   *
   * @return  The set of defined attribute syntaxes for this schema.
   */
  public LinkedHashSet<AttributeValue> getSyntaxSet()
  {
    return syntaxSet;
  }



  /**
   * Indicates whether this schema definition includes an attribute
   * syntax with the provided name or OID.
   *
   * @param  lowerName  The name or OID for which to make the
   *                    determination, formatted in all lowercase
   *                    characters.
   *
   * @return  {@code true} if this schema contains an attribute syntax
   *          with the provided name or OID, or {@code false} if not.
   */
  public boolean hasSyntax(String lowerName)
  {
    return syntaxes.containsKey(lowerName);
  }



  /**
   * Retrieves the attribute syntax definition with the OID.
   *
   * @param  lowerName  The OID of the attribute syntax to retrieve,
   *                    formatted in all lowercase characters.
   *
   * @return  The requested attribute syntax, or <CODE>null</CODE> if
   *          no syntax is registered with the provided OID.
   */
  public AttributeSyntax getSyntax(String lowerName)
  {
    return syntaxes.get(lowerName);
  }



  /**
   * Registers the provided attribute syntax definition with this
   * schema.
   *
   * @param  syntax             The attribute syntax to register with
   *                            this schema.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another attribute
   *                            syntax with the same OID).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerSyntax(AttributeSyntax syntax,
                             boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (syntaxes)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(syntax.getOID());
        if (syntaxes.containsKey(oid))
        {
          AttributeSyntax conflictingSyntax = syntaxes.get(oid);

          Message message = ERR_SCHEMA_CONFLICTING_SYNTAX_OID.
              get(syntax.getSyntaxName(), oid,
                  conflictingSyntax.getSyntaxName());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }
      }

      syntaxes.put(toLowerCase(syntax.getOID()), syntax);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = syntax.toString();
      ByteString rawValue = ByteString.valueOf(valueString);
      ByteString normValue =
          normalizationMatchingRule.normalizeValue(rawValue);
      syntaxSet.add(AttributeValues.create(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided attribute syntax definition with this
   * schema.
   *
   * @param  syntax  The attribute syntax to deregister with this
   *                 schema.
   */
  public void deregisterSyntax(AttributeSyntax syntax)
  {
    synchronized (syntaxes)
    {
      syntaxes.remove(toLowerCase(syntax.getOID()), syntax);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = syntax.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        syntaxSet.remove(AttributeValues.create(rawValue, normValue));
      }
      catch (Exception e)
      {
        String valueString = syntax.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        syntaxSet.remove(AttributeValues.create(rawValue, normValue));
      }
    }
  }



  /**
   * Retrieves the entire set of matching rule definitions for this
   * schema, as a mapping between the lowercase names and OIDs for the
   * matching rule and the matching rule itself.  Each matching rule
   * may be associated with multiple keys (once for the OID and again
   * for each name).  This should be a superset of the sets of
   * approximate, equality, ordering, and substring matching rules.
   * The contents of the returned mapping must not be altered.
   *
   * @return  The matching rule definitions for this schema.
   */
  public ConcurrentHashMap<String,MatchingRule> getMatchingRules()
  {
    return matchingRules;
  }



  /**
   * Retrieves the set of defined matching rules for this schema.
   *
   * @return  The set of defined matching rules for this schema.
   */
  public LinkedHashSet<AttributeValue> getMatchingRuleSet()
  {
    return matchingRuleSet;
  }



  /**
   * Indicates whether this schema definition includes a matching rule
   * with the provided name or OID.
   *
   * @param  lowerName  The name or OID for which to make the
   *                    determination, formatted in all lowercase
   *                    characters.
   *
   * @return  {@code true} if this schema contains a matching rule
   *          with the provided name or OID, or {@code false} if not.
   */
  public boolean hasMatchingRule(String lowerName)
  {
    return matchingRules.containsKey(lowerName);
  }



  /**
   * Retrieves the matching rule definition with the specified name or
   * OID.
   *
   * @param  lowerName  The name or OID of the matching rule to
   *                    retrieve, formatted in all lowercase
   *                    characters.
   *
   * @return  The requested matching rule, or <CODE>null</CODE> if no
   *          rule is registered with the provided name or OID.
   */
  public MatchingRule getMatchingRule(String lowerName)
  {
    return matchingRules.get(lowerName);
  }



  /**
   * Registers the provided matching rule definition with this schema.
   *
   * @param  matchingRule       The matching rule to register with
   *                            this schema.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e.,
   *                            another matching rule with the same
   *                            OID or name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerMatchingRule(MatchingRule matchingRule,
                                   boolean overwriteExisting)
         throws DirectoryException
  {
    if (matchingRule instanceof ApproximateMatchingRule)
    {
      registerApproximateMatchingRule(
           (ApproximateMatchingRule) matchingRule, overwriteExisting);
    }
    else if (matchingRule instanceof EqualityMatchingRule)
    {
      registerEqualityMatchingRule(
           (EqualityMatchingRule) matchingRule, overwriteExisting);
    }
    else if (matchingRule instanceof OrderingMatchingRule)
    {
      registerOrderingMatchingRule(
           (OrderingMatchingRule) matchingRule, overwriteExisting);
    }
    else if (matchingRule instanceof SubstringMatchingRule)
    {
      registerSubstringMatchingRule(
           (SubstringMatchingRule) matchingRule, overwriteExisting);
    }
   else if(matchingRule instanceof ExtensibleMatchingRule)
   {
      registerExtensibleMatchingRule(
           (ExtensibleMatchingRule) matchingRule,overwriteExisting);
   }
    else
    {
      synchronized (matchingRules)
      {
        if (! overwriteExisting)
        {
          String oid = toLowerCase(matchingRule.getOID());
          if (matchingRules.containsKey(oid))
          {
            MatchingRule conflictingRule = matchingRules.get(oid);

            Message message = ERR_SCHEMA_CONFLICTING_MR_OID.
                get(matchingRule.getNameOrOID(), oid,
                    conflictingRule.getNameOrOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message);
          }

          for(String name:matchingRule.getAllNames())
          {
            if (name != null)
            {
              name = toLowerCase(name);
              if (matchingRules.containsKey(name))
              {
                MatchingRule conflictingRule =
                        matchingRules.get(name);

                Message message = ERR_SCHEMA_CONFLICTING_MR_NAME.
                    get(matchingRule.getOID(), name,
                        conflictingRule.getOID());
                throw new DirectoryException(
                              ResultCode.CONSTRAINT_VIOLATION,
                              message);
              }
            }
          }
        }
        matchingRules.put(toLowerCase(matchingRule.getOID()),
                          matchingRule);

        for(String name:matchingRule.getAllNames())
        {
          if (name != null)
          {
            matchingRules.put(toLowerCase(name), matchingRule);
          }
        }
        // We'll use an attribute value including the normalized value
        // rather than the attribute type because otherwise it would
        // use a very expensive matching rule (OID first component
        // match) that would kill performance.
        String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        matchingRuleSet.add(
            AttributeValues.create(rawValue, normValue));
      }
    }
  }



  /**
   * Deregisters the provided matching rule definition with this
   * schema.
   *
   * @param  matchingRule  The matching rule to deregister with this
   *                       schema.
   */
  public void deregisterMatchingRule(MatchingRule matchingRule)
  {
    if (matchingRule instanceof ApproximateMatchingRule)
    {
      deregisterApproximateMatchingRule(
           (ApproximateMatchingRule) matchingRule);
    }
    else if (matchingRule instanceof EqualityMatchingRule)
    {
      deregisterEqualityMatchingRule(
           (EqualityMatchingRule) matchingRule);
    }
    else if (matchingRule instanceof OrderingMatchingRule)
    {
      deregisterOrderingMatchingRule(
           (OrderingMatchingRule) matchingRule);
    }
    else if (matchingRule instanceof SubstringMatchingRule)
    {
      deregisterSubstringMatchingRule(
           (SubstringMatchingRule) matchingRule);
    }
    else
    {
      synchronized (matchingRules)
      {
        matchingRules.remove(toLowerCase(matchingRule.getOID()),
                             matchingRule);

        for(String name:matchingRule.getAllNames())
        {
          if (name != null)
          {
            matchingRules.remove(toLowerCase(name), matchingRule);
          }
        }

        // We'll use an attribute value including the normalized value
        // rather than the attribute type because otherwise it would
        // use a very expensive matching rule (OID first component
        // match) that would kill performance.
        try
        {
          String valueString = matchingRule.toString();
          ByteString rawValue = ByteString.valueOf(valueString);
          ByteString normValue =
              normalizationMatchingRule.normalizeValue(rawValue);
          matchingRuleSet.remove(AttributeValues.create(rawValue,
              normValue));
        }
        catch (Exception e)
        {
          String valueString = matchingRule.toString();
          ByteString rawValue = ByteString.valueOf(valueString);
          ByteString normValue =
              ByteString.valueOf(toLowerCase(valueString));
          matchingRuleSet.remove(AttributeValues.create(rawValue,
                                                    normValue));
        }
      }
    }
  }



  /**
   * Retrieves the approximate matching rule definitions for this
   * schema, as a mapping between the lowercase names and OIDs for the
   * matching rule and the matching rule itself.  Each matching rule
   * may be associated with multiple keys (once for the OID and again
   * for each name).  The contents of the returned mapping must not be
   * altered.
   *
   * @return  The approximate matching rule definitions for this
   *          schema.
   */
  public ConcurrentHashMap<String,ApproximateMatchingRule>
              getApproximateMatchingRules()
  {
    return approximateMatchingRules;
  }



  /**
   * Retrieves the approximate matching rule definition with the
   * specified name or OID.
   *
   * @param  lowerName  The name or OID of the matching rule to
   *                    retrieve, formatted in all lowercase
   *                    characters.
   *
   * @return  The requested matching rule, or <CODE>null</CODE> if no
   *          approximate matching rule is registered with the
   *          provided name or OID.
   */
  public ApproximateMatchingRule getApproximateMatchingRule(
                                      String lowerName)
  {
    return approximateMatchingRules.get(lowerName);
  }



  /**
   * Registers the provided approximate matching rule with this
   * schema.
   *
   * @param  matchingRule       The approximate matching rule to
   *                            register.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another matching rule
   *                            with the same OID or name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerApproximateMatchingRule(
                   ApproximateMatchingRule matchingRule,
                   boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (matchingRules)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(matchingRule.getOID());
        if (matchingRules.containsKey(oid))
        {
          MatchingRule conflictingRule = matchingRules.get(oid);

          Message message = ERR_SCHEMA_CONFLICTING_MR_OID.
              get(matchingRule.getNameOrOID(), oid,
                  conflictingRule.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }

       for(String name:matchingRule.getAllNames())
       {
        if (name != null)
        {
          name = toLowerCase(name);
          if (matchingRules.containsKey(name))
          {
            MatchingRule conflictingRule = matchingRules.get(name);

            Message message = ERR_SCHEMA_CONFLICTING_MR_NAME.
                get(matchingRule.getOID(), name,
                    conflictingRule.getOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message);
          }
        }
       }
      }

      String oid = toLowerCase(matchingRule.getOID());
      approximateMatchingRules.put(oid, matchingRule);
      matchingRules.put(oid, matchingRule);

      for(String name:matchingRule.getAllNames())
      {
        if (name != null)
        {
          name = toLowerCase(name);
          approximateMatchingRules.put(name, matchingRule);
          matchingRules.put(name, matchingRule);
        }
      }
      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
      ByteString rawValue = ByteString.valueOf(valueString);
      ByteString normValue =
          normalizationMatchingRule.normalizeValue(rawValue);
      matchingRuleSet.add(AttributeValues.create(rawValue,
          normValue));
    }
  }



  /**
   * Deregisters the provided approximate matching rule definition
   * with this schema.
   *
   * @param  matchingRule  The approximate matching rule to deregister
   *                       with this schema.
   */
  public void deregisterApproximateMatchingRule(
                   ApproximateMatchingRule matchingRule)
  {
    synchronized (matchingRules)
    {
      String oid = matchingRule.getOID();
      approximateMatchingRules.remove(oid, matchingRule);
      matchingRules.remove(oid, matchingRule);

      for(String name:matchingRule.getAllNames())
      {
        if (name != null)
        {
          name = toLowerCase(name);
          approximateMatchingRules.remove(name, matchingRule);
          matchingRules.remove(name, matchingRule);
        }
      }
      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        matchingRuleSet.remove(AttributeValues.create(rawValue,
                                                  normValue));
      }
      catch (Exception e)
      {
        String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        matchingRuleSet.remove(AttributeValues.create(rawValue,
                                                  normValue));
      }
    }
  }



  /**
   * Retrieves the equality matching rule definitions for this schema,
   * as a mapping between the lowercase names and OIDs for the
   * matching rule and the matching rule itself.  Each matching rule
   * may be associated with multiple keys (once for the OID and again
   * for each name).  The contents of the returned mapping must not be
   * altered.
   *
   * @return  The equality matching rule definitions for this schema.
   */
  public ConcurrentHashMap<String,EqualityMatchingRule>
              getEqualityMatchingRules()
  {
    return equalityMatchingRules;
  }



  /**
   * Retrieves the equality matching rule definition with the
   * specified name or OID.
   *
   * @param  lowerName  The name or OID of the matching rule to
   *                    retrieve, formatted in all lowercase
   *                    characters.
   *
   * @return  The requested matching rule, or <CODE>null</CODE> if no
   *          equality matching rule is registered with the provided
   *          name or OID.
   */
  public EqualityMatchingRule getEqualityMatchingRule(
                                   String lowerName)
  {
    return equalityMatchingRules.get(lowerName);
  }



  /**
   * Registers the provided equality matching rule with this schema.
   *
   * @param  matchingRule       The equality matching rule to
   *                            register.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another matching rule
   *                            with the same OID or name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerEqualityMatchingRule(
                   EqualityMatchingRule matchingRule,
                   boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (matchingRules)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(matchingRule.getOID());
        if (matchingRules.containsKey(oid))
        {
          MatchingRule conflictingRule = matchingRules.get(oid);

          Message message = ERR_SCHEMA_CONFLICTING_MR_OID.
              get(matchingRule.getNameOrOID(), oid,
                  conflictingRule.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }

        for(String name:matchingRule.getAllNames())
        {
           if (name != null)
           {
              name = toLowerCase(name);
              if (matchingRules.containsKey(name))
              {
                MatchingRule conflictingRule =
                        matchingRules.get(name);

                Message message = ERR_SCHEMA_CONFLICTING_MR_NAME.
                    get(matchingRule.getOID(), name,
                        conflictingRule.getOID());
                throw new DirectoryException(
                               ResultCode.CONSTRAINT_VIOLATION,
                               message);
              }
           }
        }
      }

      String oid = toLowerCase(matchingRule.getOID());
      equalityMatchingRules.put(oid, matchingRule);
      matchingRules.put(oid, matchingRule);

      for(String name:matchingRule.getAllNames())
      {
        if (name != null)
        {
          name = toLowerCase(name);
          equalityMatchingRules.put(name, matchingRule);
          matchingRules.put(name, matchingRule);
        }
      }
      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
      ByteString rawValue = ByteString.valueOf(valueString);
      ByteString normValue =
          normalizationMatchingRule.normalizeValue(rawValue);
      matchingRuleSet.add(AttributeValues.create(rawValue,
          normValue));
    }
  }



  /**
   * Deregisters the provided equality matching rule definition with
   * this schema.
   *
   * @param  matchingRule  The equality matching rule to deregister
   *                       with this schema.
   */
  public void deregisterEqualityMatchingRule(
                   EqualityMatchingRule matchingRule)
  {
    synchronized (matchingRules)
    {
      String oid = matchingRule.getOID();
      equalityMatchingRules.remove(oid, matchingRule);
      matchingRules.remove(oid, matchingRule);

      for(String name:matchingRule.getAllNames())
      {
        if (name != null)
        {
          name = toLowerCase(name);
          equalityMatchingRules.remove(name, matchingRule);
          matchingRules.remove(name, matchingRule);
        }
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        matchingRuleSet.remove(AttributeValues.create(rawValue,
                                                  normValue));
      }
      catch (Exception e)
      {
        String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        matchingRuleSet.remove(AttributeValues.create(rawValue,
                                                  normValue));
      }
    }
  }



  /**
   * Retrieves the ordering matching rule definitions for this schema,
   * as a mapping between the lowercase names and OIDs for the
   * matching rule and the matching rule itself.  Each matching rule
   * may be associated with multiple keys (once for the OID and again
   * for each name).  The contents of the returned mapping must not be
   * altered.
   *
   * @return  The ordering matching rule definitions for this schema.
   */
  public ConcurrentHashMap<String,OrderingMatchingRule>
              getOrderingMatchingRules()
  {
    return orderingMatchingRules;
  }



  /**
   * Retrieves the ordering matching rule definition with the
   * specified name or OID.
   *
   * @param  lowerName  The name or OID of the matching rule to
   *                    retrieve, formatted in all lowercase
   *                    characters.
   *
   * @return  The requested matching rule, or <CODE>null</CODE> if no
   *          ordering matching rule is registered with the provided
   *          name or OID.
   */
  public OrderingMatchingRule getOrderingMatchingRule(
                                   String lowerName)
  {
    return orderingMatchingRules.get(lowerName);
  }



  /**
   * Registers the provided ordering matching rule with this schema.
   *
   * @param  matchingRule       The ordering matching rule to
   *                            register.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another matching rule
   *                            with the same OID or name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerOrderingMatchingRule(
                   OrderingMatchingRule matchingRule,
                   boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (matchingRules)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(matchingRule.getOID());
        if (matchingRules.containsKey(oid))
        {
          MatchingRule conflictingRule = matchingRules.get(oid);

          Message message = ERR_SCHEMA_CONFLICTING_MR_OID.
              get(matchingRule.getNameOrOID(), oid,
                  conflictingRule.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }

        for(String name:matchingRule.getAllNames())
        {
          if (name != null)
          {
            name = toLowerCase(name);
            if (matchingRules.containsKey(name))
            {
              MatchingRule conflictingRule = matchingRules.get(name);

              Message message = ERR_SCHEMA_CONFLICTING_MR_NAME.
                  get(matchingRule.getOID(), name,
                      conflictingRule.getOID());
              throw new DirectoryException(
                             ResultCode.CONSTRAINT_VIOLATION,
                             message);
            }
          }
        }
      }

      String oid = toLowerCase(matchingRule.getOID());
      orderingMatchingRules.put(oid, matchingRule);
      matchingRules.put(oid, matchingRule);

      for(String name:matchingRule.getAllNames())
      {
        if (name != null)
        {
          name = toLowerCase(name);
          orderingMatchingRules.put(name, matchingRule);
          matchingRules.put(name, matchingRule);
        }
      }
      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
      matchingRuleSet.add(AttributeValues.create(rawValue,
          normValue));
    }
  }



  /**
   * Deregisters the provided ordering matching rule definition with
   * this schema.
   *
   * @param  matchingRule  The ordering matching rule to deregister
   *                       with this schema.
   */
  public void deregisterOrderingMatchingRule(
                   OrderingMatchingRule matchingRule)
  {
    synchronized (matchingRules)
    {
      String oid = matchingRule.getOID();
      orderingMatchingRules.remove(oid, matchingRule);
      matchingRules.remove(oid, matchingRule);

      for(String name:matchingRule.getAllNames())
      {
        if (name != null)
        {
          name = toLowerCase(name);
          orderingMatchingRules.remove(name, matchingRule);
          matchingRules.remove(name, matchingRule);
        }
      }
      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        matchingRuleSet.remove(AttributeValues.create(rawValue,
                                                  normValue));
      }
      catch (Exception e)
      {
        String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        matchingRuleSet.remove(AttributeValues.create(rawValue,
                                                  normValue));
      }
    }
  }



  /**
   * Retrieves the substring matching rule definitions for this
   * schema, as a mapping between the lowercase names and OIDs for the
   * matching rule and the matching rule itself.  Each matching rule
   * may be associated with multiple keys (once for the OID and again
   * for each name).  The contents of the returned mapping must not be
   * altered.
   *
   * @return  The substring matching rule definitions for this schema.
   */
  public ConcurrentHashMap<String,SubstringMatchingRule>
              getSubstringMatchingRules()
  {
    return substringMatchingRules;
  }



  /**
   * Retrieves the substring matching rule definition with the
   * specified name or OID.
   *
   * @param  lowerName  The name or OID of the matching rule to
   *                    retrieve, formatted in all lowercase
   *                    characters.
   *
   * @return  The requested matching rule, or <CODE>null</CODE> if no
   *          substring matching rule is registered with the provided
   *          name or OID.
   */
  public SubstringMatchingRule getSubstringMatchingRule(
                                    String lowerName)
  {
    return substringMatchingRules.get(lowerName);
  }



  /**
   * Registers the provided substring matching rule with this schema.
   *
   * @param  matchingRule       The substring matching rule to
   *                            register.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another matching rule
   *                            with the same OID or name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerSubstringMatchingRule(
                   SubstringMatchingRule matchingRule,
                   boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (matchingRules)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(matchingRule.getOID());
        if (matchingRules.containsKey(oid))
        {
          MatchingRule conflictingRule = matchingRules.get(oid);

          Message message = ERR_SCHEMA_CONFLICTING_MR_OID.
              get(matchingRule.getNameOrOID(), oid,
                  conflictingRule.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }

        for(String name:matchingRule.getAllNames())
        {
          if (name != null)
          {
            name = toLowerCase(name);
            if (matchingRules.containsKey(name))
            {
              MatchingRule conflictingRule = matchingRules.get(name);

              Message message = ERR_SCHEMA_CONFLICTING_MR_NAME.
                  get(matchingRule.getOID(), name,
                      conflictingRule.getOID());
              throw new DirectoryException(
                             ResultCode.CONSTRAINT_VIOLATION,
                             message);
            }
          }
        }
      }

      String oid = toLowerCase(matchingRule.getOID());
      substringMatchingRules.put(oid, matchingRule);
      matchingRules.put(oid, matchingRule);

      for(String name:matchingRule.getAllNames())
      {
        if (name != null)
        {
          name = toLowerCase(name);
          substringMatchingRules.put(name, matchingRule);
          matchingRules.put(name, matchingRule);
        }
      }
      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
      matchingRuleSet.add(AttributeValues.create(rawValue,
          normValue));
    }
  }



  /**
   * Deregisters the provided substring matching rule definition with
   * this schema.
   *
   * @param  matchingRule  The substring matching rule to deregister
   *                       with this schema.
   */
  public void deregisterSubstringMatchingRule(
                   SubstringMatchingRule matchingRule)
  {
    synchronized (matchingRules)
    {
      String oid = matchingRule.getOID();
      substringMatchingRules.remove(oid, matchingRule);
      matchingRules.remove(oid, matchingRule);

      for(String name:matchingRule.getAllNames())
      {
        if (name != null)
        {
          name = toLowerCase(name);
          substringMatchingRules.remove(name, matchingRule);
          matchingRules.remove(name, matchingRule);
        }
      }
      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        matchingRuleSet.remove(AttributeValues.create(rawValue,
                                                  normValue));
      }
      catch (Exception e)
      {
        String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        matchingRuleSet.remove(AttributeValues.create(rawValue,
                                                  normValue));
      }
    }
  }



  /**
   * Retrieves the extensible matching rule definitions for this
   * schema, as a mapping between the lowercase names and OIDs for the
   * matching rule and the matching rule itself.  Each matching rule
   * may be associated with multiple keys (once for the OID and again
   * for each name).
   *
   * @return  The extensible matching rule definitions for this
   *          schema.
   */
  public Map<String,ExtensibleMatchingRule>
              getExtensibleMatchingRules()
  {
    return Collections.unmodifiableMap(extensibleMatchingRules);
  }



  /**
   * Retrieves the extensible matching rule definition with the
   * specified name or OID.
   *
   * @param  lowerName  The name or OID of the matching rule to
   *                    retrieve, formatted in all lowercase
   *                    characters.
   *
   * @return  The requested matching rule, or <CODE>null</CODE> if no
   *          extensible matching rule is registered with the
   *          provided name or OID.
   */
  public ExtensibleMatchingRule getExtensibleMatchingRule(
                                      String lowerName)
  {
    //An ExtensibleMatchingRule can be of multiple types.
    MatchingRule rule = matchingRules.get(lowerName);
    if(rule instanceof ExtensibleMatchingRule)
    {
      return (ExtensibleMatchingRule)rule;
    }
    return null;
  }



  /**
   * Registers the provided extensible matching rule with this
   * schema.
   *
   * @param  matchingRule       The extensible matching rule to
   *                            register.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another matching rule
   *                            with the same OID or name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerExtensibleMatchingRule(
                   ExtensibleMatchingRule matchingRule,
                   boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (matchingRules)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(matchingRule.getOID());
        if (matchingRules.containsKey(oid))
        {
          MatchingRule conflictingRule = matchingRules.get(oid);

          Message message = ERR_SCHEMA_CONFLICTING_MR_OID.
              get(matchingRule.getNameOrOID(), oid,
                  conflictingRule.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }

       for(String name:matchingRule.getAllNames())
       {
        if (name != null)
        {
          name = toLowerCase(name);
          if (matchingRules.containsKey(name))
          {
            MatchingRule conflictingRule = matchingRules.get(name);

            Message message = ERR_SCHEMA_CONFLICTING_MR_NAME.
                get(matchingRule.getOID(), name,
                    conflictingRule.getOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message);
          }
        }
       }
      }

      String oid = toLowerCase(matchingRule.getOID());
      extensibleMatchingRules.put(oid, matchingRule);
      matchingRules.put(oid, matchingRule);

      for(String name:matchingRule.getAllNames())
      {
        if (name != null)
        {
          name = toLowerCase(name);
          extensibleMatchingRules.put(name, matchingRule);
          matchingRules.put(name, matchingRule);
        }
      }
      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
      ByteString rawValue  = ByteString.valueOf(valueString);
      ByteString normValue = normalizationMatchingRule.normalizeValue(
                                  rawValue);
      matchingRuleSet.add(
          AttributeValues.create(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided extensible  matching rule definition
   * with this schema.
   *
   * @param  matchingRule  The extensible matching rule to deregister
   *                       with this schema.
   */
    public void deregisterExtensibleMatchingRule(
                   ExtensibleMatchingRule matchingRule)
   {
    synchronized (matchingRules)
    {
      String oid = matchingRule.getOID();
      extensibleMatchingRules.remove(oid, matchingRule);
      matchingRules.remove(oid, matchingRule);

      for(String name:matchingRule.getAllNames())
      {
        if (name != null)
        {
          name = toLowerCase(name);
          extensibleMatchingRules.remove(name, matchingRule);
          matchingRules.remove(name, matchingRule);
        }
      }
      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            normalizationMatchingRule.normalizeValue(rawValue);
        matchingRuleSet.remove(AttributeValues.create(rawValue,
            normValue));
      }
      catch (Exception e)
      {
        String valueString = matchingRule.toString();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        matchingRuleSet.remove(AttributeValues.create(rawValue,
            normValue));
      }
    }
  }



  /**
   * Retrieves the matching rule use definitions for this schema, as a
   * mapping between the matching rule for the matching rule use
   * definition and the matching rule use itself.  Each matching rule
   * use should only be present once, since its only key is its
   * matching rule.  The contents of the returned mapping must not be
   * altered.
   *
   * @return  The matching rule use definitions for this schema.
   */
  public ConcurrentHashMap<MatchingRule,MatchingRuleUse>
              getMatchingRuleUses()
  {
    return matchingRuleUses;
  }



  /**
   * Retrieves the set of defined matching rule uses for this schema.
   *
   * @return  The set of defined matching rule uses for this schema.
   */
  public LinkedHashSet<AttributeValue> getMatchingRuleUseSet()
  {
    return matchingRuleUseSet;
  }



  /**
   * Indicates whether this schema definition includes a matching rule
   * use for the provided matching rule.
   *
   * @param  matchingRule  The matching rule for which to make the
   *                       determination.
   *
   * @return  {@code true} if this schema contains a matching rule use
   *          for the provided matching rule, or {@code false} if not.
   */
  public boolean hasMatchingRuleUse(MatchingRule matchingRule)
  {
    return matchingRuleUses.containsKey(matchingRule);
  }



  /**
   * Retrieves the matching rule use definition for the specified
   * matching rule.
   *
   * @param  matchingRule  The matching rule for which to retrieve the
   *                       matching rule use definition.
   *
   * @return  The matching rule use definition, or <CODE>null</CODE>
   *          if none exists for the specified matching rule.
   */
  public MatchingRuleUse getMatchingRuleUse(MatchingRule matchingRule)
  {
    return matchingRuleUses.get(matchingRule);
  }



  /**
   * Registers the provided matching rule use definition with this
   * schema.
   *
   * @param  matchingRuleUse    The matching rule use definition to
   *                            register.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another matching rule
   *                            use with the same matching rule).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerMatchingRuleUse(MatchingRuleUse matchingRuleUse,
                                      boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (matchingRuleUses)
    {
      MatchingRule matchingRule = matchingRuleUse.getMatchingRule();

      if (! overwriteExisting)
      {
        if (matchingRuleUses.containsKey(matchingRule))
        {
          MatchingRuleUse conflictingUse =
                               matchingRuleUses.get(matchingRule);

          Message message = ERR_SCHEMA_CONFLICTING_MATCHING_RULE_USE.
              get(matchingRuleUse.getName(),
                  matchingRule.getNameOrOID(),
                  conflictingUse.getName());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }
      }

      matchingRuleUses.put(matchingRule, matchingRuleUse);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRuleUse.getDefinition();
      ByteString rawValue = ByteString.valueOf(valueString);
      ByteString normValue =
          normalizationMatchingRule.normalizeValue(rawValue);
      matchingRuleUseSet.add(AttributeValues.create(rawValue,
          normValue));
    }
  }



  /**
   * Deregisters the provided matching rule use definition with this
   * schema.
   *
   * @param  matchingRuleUse  The matching rule use to deregister with
   *                          this schema.
   */
  public void deregisterMatchingRuleUse(
                   MatchingRuleUse matchingRuleUse)
  {
    synchronized (matchingRuleUses)
    {
      matchingRuleUses.remove(matchingRuleUse.getMatchingRule(),
                              matchingRuleUse);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = matchingRuleUse.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        matchingRuleUseSet.remove(AttributeValues.create(rawValue,
                                                     normValue));
      }
      catch (Exception e)
      {
        String valueString = matchingRuleUse.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        matchingRuleUseSet.remove(AttributeValues.create(rawValue,
                                                     normValue));
      }
    }
  }



  /**
   * Retrieves the DIT content rule definitions for this schema, as a
   * mapping between the objectclass for the rule and the DIT content
   * rule itself.  Each DIT content rule should only be present once,
   * since its only key is its objectclass.  The contents of the
   * returned mapping must not be altered.
   *
   * @return  The DIT content rule definitions for this schema.
   */
  public ConcurrentHashMap<ObjectClass,DITContentRule>
              getDITContentRules()
  {
    return ditContentRules;
  }



  /**
   * Retrieves the set of defined DIT content rules for this schema.
   *
   * @return  The set of defined DIT content rules for this schema.
   */
  public LinkedHashSet<AttributeValue> getDITContentRuleSet()
  {
    return ditContentRuleSet;
  }



  /**
   * Indicates whether this schema definition includes a DIT content
   * rule for the provided objectclass.
   *
   * @param  objectClass  The objectclass for which to make the
   *                      determination.
   *
   * @return  {@code true} if this schema contains a DIT content rule
   *          for the provided objectclass, or {@code false} if not.
   */
  public boolean hasDITContentRule(ObjectClass objectClass)
  {
    return ditContentRules.containsKey(objectClass);
  }



  /**
   * Retrieves the DIT content rule definition for the specified
   * objectclass.
   *
   * @param  objectClass  The objectclass for the DIT content rule to
   *                      retrieve.
   *
   * @return  The requested DIT content rule, or <CODE>null</CODE> if
   *          no DIT content rule is registered with the provided
   *          objectclass.
   */
  public DITContentRule getDITContentRule(ObjectClass objectClass)
  {
    return ditContentRules.get(objectClass);
  }



  /**
   * Registers the provided DIT content rule definition with this
   * schema.
   *
   * @param  ditContentRule     The DIT content rule to register.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another DIT content
   *                            rule with the same objectclass).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerDITContentRule(DITContentRule ditContentRule,
                                     boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (ditContentRules)
    {
      ObjectClass objectClass = ditContentRule.getStructuralClass();

      if (! overwriteExisting)
      {
        if (ditContentRules.containsKey(objectClass))
        {
          DITContentRule conflictingRule =
                              ditContentRules.get(objectClass);

          Message message = ERR_SCHEMA_CONFLICTING_DIT_CONTENT_RULE.
              get(ditContentRule.getName(),
                  objectClass.getNameOrOID(),
                  conflictingRule.getName());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }
      }

      ditContentRules.put(objectClass, ditContentRule);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = ditContentRule.getDefinition();
      ByteString rawValue = ByteString.valueOf(valueString);
      ByteString normValue =
          normalizationMatchingRule.normalizeValue(rawValue);
      ditContentRuleSet.add(AttributeValues.create(rawValue,
          normValue));
    }
  }



  /**
   * Deregisters the provided DIT content rule definition with this
   * schema.
   *
   * @param  ditContentRule  The DIT content rule to deregister with
   *                         this schema.
   */
  public void deregisterDITContentRule(DITContentRule ditContentRule)
  {
    synchronized (ditContentRules)
    {
      ditContentRules.remove(ditContentRule.getStructuralClass(),
                             ditContentRule);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = ditContentRule.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        ditContentRuleSet.remove(AttributeValues.create(rawValue,
                                                    normValue));
      }
      catch (Exception e)
      {
        String valueString = ditContentRule.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        ditContentRuleSet.remove(AttributeValues.create(rawValue,
                                                    normValue));
      }
    }
  }



  /**
   * Retrieves the set of defined DIT structure rules for this schema.
   *
   * @return  The set of defined DIT structure rules for this schema.
   */
  public LinkedHashSet<AttributeValue> getDITStructureRuleSet()
  {
    return ditStructureRuleSet;
  }



  /**
   * Retrieves the DIT structure rule definitions for this schema, as
   * a mapping between the rule ID for the rule and the DIT structure
   * rule itself.  Each DIT structure rule should only be present
   * once, since its only key is its rule ID.  The contents of the
   * returned mapping must not be altered.
   *
   * @return  The DIT structure rule definitions for this schema.
   */
  public ConcurrentHashMap<Integer,DITStructureRule>
              getDITStructureRulesByID()
  {
    return ditStructureRulesByID;
  }



  /**
   * Retrieves the DIT structure rule definitions for this schema, as
   * a mapping between the name form for the rule and the DIT
   * structure rule itself.  Each DIT structure rule should only be
   * present once, since its only key is its name form.  The contents
   * of the returned mapping must not be altered.
   *
   * @return  The DIT structure rule definitions for this schema.
   */
  public ConcurrentHashMap<NameForm,DITStructureRule>
              getDITStructureRulesByNameForm()
  {
    return ditStructureRulesByNameForm;
  }



  /**
   * Indicates whether this schema definition includes a DIT structure
   * rule with the provided rule ID.
   *
   * @param  ruleID  The rule ID for which to make the determination.
   *
   * @return  {@code true} if this schema contains a DIT structure
   *          rule with the provided rule ID, or {@code false} if not.
   */
  public boolean hasDITStructureRule(int ruleID)
  {
    return ditStructureRulesByID.containsKey(ruleID);
  }



  /**
   * Indicates whether this schema definition includes a DIT structure
   * rule for the provided name form.
   *
   * @param  nameForm  The name form for which to make the
   *                   determination.
   *
   * @return  {@code true} if this schema contains a DIT structure
   *          rule for the provided name form, or {@code false} if
   *          not.
   */
  public boolean hasDITStructureRule(NameForm nameForm)
  {
    return ditStructureRulesByNameForm.containsKey(nameForm);
  }



  /**
   * Retrieves the DIT structure rule definition with the provided
   * rule ID.
   *
   * @param  ruleID  The rule ID for the DIT structure rule to
   *                 retrieve.
   *
   * @return  The requested DIT structure rule, or <CODE>null</CODE>
   *          if no DIT structure rule is registered with the provided
   *          rule ID.
   */
  public DITStructureRule getDITStructureRule(int ruleID)
  {
    return ditStructureRulesByID.get(ruleID);
  }



  /**
   * Retrieves the DIT structure rule definition for the provided name
   * form.
   *
   * @param  nameForm  The name form for the DIT structure rule to
   *                   retrieve.
   *
   * @return  The requested DIT structure rule, or <CODE>null</CODE>
   *          if no DIT structure rule is registered with the provided
   *          name form.
   */
  public DITStructureRule getDITStructureRule(NameForm nameForm)
  {
    return ditStructureRulesByNameForm.get(nameForm);
  }



  /**
   * Registers the provided DIT structure rule definition with this
   * schema.
   *
   * @param  ditStructureRule   The DIT structure rule to register.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another DIT structure
   *                            rule with the same name form).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerDITStructureRule(
                   DITStructureRule ditStructureRule,
                   boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (ditStructureRulesByNameForm)
    {
      NameForm nameForm = ditStructureRule.getNameForm();
      int      ruleID   = ditStructureRule.getRuleID();

      if (! overwriteExisting)
      {
        if (ditStructureRulesByNameForm.containsKey(nameForm))
        {
          DITStructureRule conflictingRule =
               ditStructureRulesByNameForm.get(nameForm);

          Message message =
              ERR_SCHEMA_CONFLICTING_DIT_STRUCTURE_RULE_NAME_FORM.
                get(ditStructureRule.getNameOrRuleID(),
                    nameForm.getNameOrOID(),
                    conflictingRule.getNameOrRuleID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }

        if (ditStructureRulesByID.containsKey(ruleID))
        {
          DITStructureRule conflictingRule =
               ditStructureRulesByID.get(ruleID);

          Message message =
              ERR_SCHEMA_CONFLICTING_DIT_STRUCTURE_RULE_ID.
                get(ditStructureRule.getNameOrRuleID(), ruleID,
                    conflictingRule.getNameOrRuleID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }
      }

      ditStructureRulesByNameForm.put(nameForm, ditStructureRule);
      ditStructureRulesByID.put(ruleID, ditStructureRule);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = ditStructureRule.getDefinition();
      ByteString rawValue = ByteString.valueOf(valueString);
      ByteString normValue =
          normalizationMatchingRule.normalizeValue(rawValue);
      ditStructureRuleSet.add(AttributeValues.create(rawValue,
                                                 normValue));
    }
  }



  /**
   * Deregisters the provided DIT structure rule definition with this
   * schema.
   *
   * @param  ditStructureRule  The DIT structure rule to deregister
   *                           with this schema.
   */
  public void deregisterDITStructureRule(
                   DITStructureRule ditStructureRule)
  {
    synchronized (ditStructureRulesByNameForm)
    {
      ditStructureRulesByNameForm.remove(
           ditStructureRule.getNameForm(), ditStructureRule);
      ditStructureRulesByID.remove(ditStructureRule.getRuleID(),
                                   ditStructureRule);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = ditStructureRule.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        ditStructureRuleSet.remove(AttributeValues.create(rawValue,
                                                      normValue));
      }
      catch (Exception e)
      {
        String valueString = ditStructureRule.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        ditStructureRuleSet.remove(AttributeValues.create(rawValue,
                                                      normValue));
      }
    }
  }



  /**
   * Retrieves the set of defined name forms for this schema.
   *
   * @return  The set of defined name forms for this schema.
   */
  public LinkedHashSet<AttributeValue> getNameFormSet()
  {
    return nameFormSet;
  }



  /**
   * Retrieves the name form definitions for this schema, as a mapping
   * between the objectclass for the name form and the name form
   * itself.  Each name form should only be present once, since its
   * only key is its objectclass.  The contents of the returned
   * mapping must not be altered.
   *
   * @return  The name form definitions for this schema.
   */
  public ConcurrentHashMap<ObjectClass,NameForm>
              getNameFormsByObjectClass()
  {
    return nameFormsByOC;
  }



  /**
   * Retrieves the name form definitions for this schema, as a mapping
   * between the names/OID for the name form and the name form itself.
   * Each name form may be present multiple times with different names
   * and its OID.  The contents of the returned mapping must not be
   * altered.
   *
   * @return  The name form definitions for this schema.
   */
  public ConcurrentHashMap<String,NameForm> getNameFormsByNameOrOID()
  {
    return nameFormsByName;
  }



  /**
   * Indicates whether this schema definition includes a name form for
   * the specified objectclass.
   *
   * @param  objectClass  The objectclass for which to make the
   *                      determination.
   *
   * @return  {@code true} if this schema contains a name form for the
   *          provided objectclass, or {@code false} if not.
   */
  public boolean hasNameForm(ObjectClass objectClass)
  {
    return nameFormsByOC.containsKey(objectClass);
  }



  /**
   * Indicates whether this schema definition includes a name form
   * with the specified name or OID.
   *
   * @param  lowerName  The name or OID for which to make the
   *                    determination, formatted in all lowercase
   *                    characters.
   *
   * @return  {@code true} if this schema contains a name form with
   *          the provided name or OID, or {@code false} if not.
   */
  public boolean hasNameForm(String lowerName)
  {
    return nameFormsByName.containsKey(lowerName);
  }



  /**
   * Retrieves the name form definition for the specified objectclass.
   *
   * @param  objectClass  The objectclass for the name form to
   *                      retrieve.
   *
   * @return  The requested name form, or <CODE>null</CODE> if no name
   *          form is registered with the provided objectClass.
   */
  public NameForm getNameForm(ObjectClass objectClass)
  {
    return nameFormsByOC.get(objectClass);
  }



  /**
   * Retrieves the name form definition with the provided name or OID.
   *
   * @param  lowerName  The name or OID of the name form to retrieve,
   *                    formatted in all lowercase characters.
   *
   * @return  The requested name form, or <CODE>null</CODE> if no name
   *          form is registered with the provided name or OID.
   */
  public NameForm getNameForm(String lowerName)
  {
    return nameFormsByName.get(lowerName);
  }



  /**
   * Registers the provided name form definition with this schema.
   *
   * @param  nameForm           The name form definition to register.
   * @param  overwriteExisting  Indicates whether to overwrite an
   *                            existing mapping if there are any
   *                            conflicts (i.e., another name form
   *                            with the same objectclass).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag
   *                              is set to <CODE>false</CODE>
   */
  public void registerNameForm(NameForm nameForm,
                               boolean overwriteExisting)
         throws DirectoryException
  {
    synchronized (nameFormsByOC)
    {
      ObjectClass objectClass = nameForm.getStructuralClass();

      if (! overwriteExisting)
      {
        if (nameFormsByOC.containsKey(objectClass))
        {
          NameForm conflictingNameForm =
               nameFormsByOC.get(objectClass);

          Message message = ERR_SCHEMA_CONFLICTING_NAME_FORM_OC.
              get(nameForm.getNameOrOID(), objectClass.getNameOrOID(),
                  conflictingNameForm.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }

        String oid = toLowerCase(nameForm.getOID());
        if (nameFormsByName.containsKey(oid))
        {
          NameForm conflictingNameForm = nameFormsByName.get(oid);

          Message message = ERR_SCHEMA_CONFLICTING_NAME_FORM_OID.
              get(nameForm.getNameOrOID(), oid,
                  conflictingNameForm.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message);
        }

        for (String name : nameForm.getNames().keySet())
        {
          if (nameFormsByName.containsKey(name))
          {
            NameForm conflictingNameForm = nameFormsByName.get(name);

            Message message = ERR_SCHEMA_CONFLICTING_NAME_FORM_NAME.
                get(nameForm.getNameOrOID(), oid,
                    conflictingNameForm.getNameOrOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message);
          }
        }
      }

      nameFormsByOC.put(objectClass, nameForm);
      nameFormsByName.put(toLowerCase(nameForm.getOID()), nameForm);

      for (String name : nameForm.getNames().keySet())
      {
        nameFormsByName.put(name, nameForm);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = nameForm.getDefinition();
      ByteString rawValue = ByteString.valueOf(valueString);
      ByteString normValue =
          normalizationMatchingRule.normalizeValue(rawValue);
      nameFormSet.add(AttributeValues.create(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided name form definition with this schema.
   *
   * @param  nameForm  The name form definition to deregister.
   */
  public void deregisterNameForm(NameForm nameForm)
  {
    synchronized (nameFormsByOC)
    {
      nameFormsByOC.remove(nameForm.getStructuralClass(), nameForm);
      nameFormsByName.remove(toLowerCase(nameForm.getOID()),
                             nameForm);

      for (String name : nameForm.getNames().keySet())
      {
        nameFormsByName.remove(name, nameForm);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      try
      {
        String valueString = nameForm.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
             normalizationMatchingRule.normalizeValue(rawValue);
        nameFormSet.remove(AttributeValues.create(rawValue,
            normValue));
      }
      catch (Exception e)
      {
        String valueString = nameForm.getDefinition();
        ByteString rawValue = ByteString.valueOf(valueString);
        ByteString normValue =
            ByteString.valueOf(toLowerCase(valueString));
        nameFormSet.remove(AttributeValues.create(rawValue,
            normValue));
      }
    }
  }



  /**
   * Retrieves the modification timestamp for the file in the schema
   * configuration directory with the oldest last modified time.
   *
   * @return  The modification timestamp for the file in the schema
   *          configuration directory with the oldest last modified
   *          time.
   */
  public long getOldestModificationTime()
  {
    return oldestModificationTime;
  }



  /**
   * Sets the modification timestamp for the oldest file in the schema
   * configuration directory.
   *
   * @param  oldestModificationTime  The modification timestamp for
   *                                 the oldest file in the schema
   *                                 configuration directory.
   */
  public void setOldestModificationTime(long oldestModificationTime)
  {
    this.oldestModificationTime = oldestModificationTime;
  }



  /**
   * Retrieves the modification timestamp for the file in the schema
   * configuration directory with the youngest last modified time.
   *
   * @return  The modification timestamp for the file in the schema
   *          configuration directory with the youngest last modified
   *          time.
   */
  public long getYoungestModificationTime()
  {
    return youngestModificationTime;
  }



  /**
   * Sets the modification timestamp for the youngest file in the
   * schema configuration directory.
   *
   * @param  youngestModificationTime  The modification timestamp for
   *                                   the youngest file in the schema
   *                                   configuration directory.
   */
  public void setYoungestModificationTime(
                   long youngestModificationTime)
  {
    this.youngestModificationTime = youngestModificationTime;
  }



  /**
   * Recursively rebuilds all schema elements that are dependent upon
   * the provided element.  This must be invoked whenever an existing
   * schema element is modified in order to ensure that any elements
   * that depend on it should also be recreated to reflect the change.
   * <BR><BR>
   * The following conditions create dependencies between schema
   * elements:
   * <UL>
   *   <LI>If an attribute type references a superior attribute type,
   *       then it is dependent upon that superior attribute
   *       type.</LI>
   *   <LI>If an objectclass requires or allows an attribute type,
   *       then it is dependent upon that attribute type.</LI>
   *   <LI>If a name form requires or allows an attribute type in the
   *       RDN, then it is dependent upon that attribute type.</LI>
   *   <LI>If a DIT content rule requires, allows, or forbids the use
   *       of an attribute type, then it is dependent upon that
   *       attribute type.</LI>
   *   <LI>If a matching rule use references an attribute type, then
   *       it is dependent upon that attribute type.</LI>
   *   <LI>If an objectclass references a superior objectclass, then
   *       it is dependent upon that superior objectclass.</LI>
   *   <LI>If a name form references a structural objectclass, then it
   *       is dependent upon that objectclass.</LI>
   *   <LI>If a DIT content rule references a structural or auxiliary
   *       objectclass, then it is dependent upon that
   *       objectclass.</LI>
   *   <LI>If a DIT structure rule references a name form, then it is
   *       dependent upon that name form.</LI>
   *   <LI>If a DIT structure rule references a superior DIT structure
   *       rule, then it is dependent upon that superior DIT structure
   *       rule.</LI>
   * </UL>
   *
   * @param  element  The element for which to recursively rebuild all
   *                  dependent elements.
   *
   * @throws  DirectoryException  If a problem occurs while rebuilding
   *                              any of the schema elements.
   */
  public void rebuildDependentElements(SchemaFileElement element)
         throws DirectoryException
  {
    try
    {
      rebuildDependentElements(element, 0);
    }
    catch (DirectoryException de)
    {
      // If we got an error as a result of a circular reference, then
      // we want to make sure that the schema element we call out is
      // the one that is at the root of the problem.
      if (de.getMessageObject().getDescriptor().equals(
          ERR_SCHEMA_CIRCULAR_DEPENDENCY_REFERENCE))
      {
        Message message = ERR_SCHEMA_CIRCULAR_DEPENDENCY_REFERENCE.
            get(element.getDefinition());
        throw new DirectoryException(de.getResultCode(), message,
                                     de);
      }


      // It wasn't a circular reference error, so just re-throw the
      // exception.
      throw de;
    }
  }



  /**
   * Recursively rebuilds all schema elements that are dependent upon
   * the provided element, increasing the depth for each level of
   * recursion to protect against errors due to circular references.
   *
   * @param  element  The element for which to recursively rebuild all
   *                  dependent elements.
   * @param  depth    The current recursion depth.
   *
   * @throws  DirectoryException  If a problem occurs while rebuilding
   *                              any of the schema elements.
   */
  private void rebuildDependentElements(SchemaFileElement element,
                                        int depth)
          throws DirectoryException
  {
    if (depth > 20)
    {
      // FIXME -- Is this an appropriate maximum depth for detecting
      // circular references?
      Message message = ERR_SCHEMA_CIRCULAR_DEPENDENCY_REFERENCE.get(
          element.getDefinition());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                   message);
    }


    // Figure out what type of element we're dealing with and make the
    // appropriate determinations for that element.
    if (element instanceof AttributeType)
    {
      AttributeType t = (AttributeType) element;

      for (AttributeType at : attributeTypes.values())
      {
        if ((at.getSuperiorType() != null) &&
            at.getSuperiorType().equals(t))
        {
          AttributeType newAT = at.recreateFromDefinition();
          deregisterAttributeType(at);
          registerAttributeType(newAT, true);
          rebuildDependentElements(at, depth+1);
        }
      }

      for (ObjectClass oc : objectClasses.values())
      {
        if (oc.getRequiredAttributes().contains(t) ||
            oc.getOptionalAttributes().contains(t))
        {
          ObjectClass newOC = oc.recreateFromDefinition();
          deregisterObjectClass(oc);
          registerObjectClass(newOC, true);
          rebuildDependentElements(oc, depth+1);
        }
      }

      for (NameForm nf : nameFormsByOC.values())
      {
        if (nf.getRequiredAttributes().contains(t) ||
            nf.getOptionalAttributes().contains(t))
        {
          NameForm newNF = nf.recreateFromDefinition();
          deregisterNameForm(nf);
          registerNameForm(newNF, true);
          rebuildDependentElements(nf, depth+1);
        }
      }

      for (DITContentRule dcr : ditContentRules.values())
      {
        if (dcr.getRequiredAttributes().contains(t) ||
            dcr.getOptionalAttributes().contains(t) ||
            dcr.getProhibitedAttributes().contains(t))
        {
          DITContentRule newDCR = dcr.recreateFromDefinition();
          deregisterDITContentRule(dcr);
          registerDITContentRule(newDCR, true);
          rebuildDependentElements(dcr, depth+1);
        }
      }

      for (MatchingRuleUse mru : matchingRuleUses.values())
      {
        if (mru.getAttributes().contains(t))
        {
          MatchingRuleUse newMRU = mru.recreateFromDefinition();
          deregisterMatchingRuleUse(mru);
          registerMatchingRuleUse(newMRU, true);
          rebuildDependentElements(mru, depth+1);
        }
      }
    }
    else if (element instanceof ObjectClass)
    {
      ObjectClass c = (ObjectClass) element;

      for (ObjectClass oc : objectClasses.values())
      {
        if ((oc.getSuperiorClass() != null) &&
            oc.getSuperiorClass().equals(c))
        {
          ObjectClass newOC = oc.recreateFromDefinition();
          deregisterObjectClass(oc);
          registerObjectClass(newOC, true);
          rebuildDependentElements(oc, depth+1);
        }
      }

      NameForm nf = nameFormsByOC.get(c);
      if (nf != null)
      {
        NameForm newNF = nf.recreateFromDefinition();
        deregisterNameForm(nf);
        registerNameForm(newNF, true);
        rebuildDependentElements(nf, depth+1);
      }

      for (DITContentRule dcr : ditContentRules.values())
      {
        if (dcr.getStructuralClass().equals(c) ||
            dcr.getAuxiliaryClasses().contains(c))
        {
          DITContentRule newDCR = dcr.recreateFromDefinition();
          deregisterDITContentRule(dcr);
          registerDITContentRule(newDCR, true);
          rebuildDependentElements(dcr, depth+1);
        }
      }
    }
    else if (element instanceof NameForm)
    {
      NameForm n = (NameForm) element;
      DITStructureRule dsr = ditStructureRulesByNameForm.get(n);
      if (dsr != null)
      {
        DITStructureRule newDSR = dsr.recreateFromDefinition();
        deregisterDITStructureRule(dsr);
        registerDITStructureRule(newDSR, true);
        rebuildDependentElements(dsr, depth+1);
      }
    }
    else if (element instanceof DITStructureRule)
    {
      DITStructureRule d = (DITStructureRule) element;
      for (DITStructureRule dsr : ditStructureRulesByID.values())
      {
        if (dsr.getSuperiorRules().contains(d))
        {
          DITStructureRule newDSR = dsr.recreateFromDefinition();
          deregisterDITStructureRule(dsr);
          registerDITStructureRule(newDSR, true);
          rebuildDependentElements(dsr, depth+1);
        }
      }
    }
  }



  /**
   * Creates a new <CODE>Schema</CODE> object that is a duplicate of
   * this one.  It elements may be added and removed from the
   * duplicate without impacting this version.
   *
   * @return  A new <CODE>Schema</CODE> object that is a duplicate of
   *          this one.
   */
  public Schema duplicate()
  {
    Schema dupSchema = new Schema();

    dupSchema.attributeTypes.putAll(attributeTypes);
    dupSchema.subordinateTypes.putAll(subordinateTypes);
    dupSchema.objectClasses.putAll(objectClasses);
    dupSchema.syntaxes.putAll(syntaxes);
    dupSchema.matchingRules.putAll(matchingRules);
    dupSchema.approximateMatchingRules.putAll(
         approximateMatchingRules);
    dupSchema.equalityMatchingRules.putAll(equalityMatchingRules);
    dupSchema.orderingMatchingRules.putAll(orderingMatchingRules);
    dupSchema.substringMatchingRules.putAll(substringMatchingRules);
    dupSchema.extensibleMatchingRules.putAll(extensibleMatchingRules);
    dupSchema.matchingRuleUses.putAll(matchingRuleUses);
    dupSchema.ditContentRules.putAll(ditContentRules);
    dupSchema.ditStructureRulesByID.putAll(ditStructureRulesByID);
    dupSchema.ditStructureRulesByNameForm.putAll(
         ditStructureRulesByNameForm);
    dupSchema.nameFormsByOC.putAll(nameFormsByOC);
    dupSchema.nameFormsByName.putAll(nameFormsByName);
    dupSchema.syntaxSet.addAll(syntaxSet);
    dupSchema.attributeTypeSet.addAll(attributeTypeSet);
    dupSchema.ditContentRuleSet.addAll(ditContentRuleSet);
    dupSchema.ditStructureRuleSet.addAll(ditStructureRuleSet);
    dupSchema.matchingRuleSet.addAll(matchingRuleSet);
    dupSchema.matchingRuleUseSet.addAll(matchingRuleUseSet);
    dupSchema.nameFormSet.addAll(nameFormSet);
    dupSchema.objectClassSet.addAll(objectClassSet);
    dupSchema.oldestModificationTime   = oldestModificationTime;
    dupSchema.youngestModificationTime = youngestModificationTime;
    if (extraAttributes != null)
    {
      dupSchema.extraAttributes =
        new HashMap<String, Attribute>(extraAttributes);
    }

    return dupSchema;
  }


  /**
   * Get the extraAttributes stored in this schema.
   *
   * @return  The extraAttributes stored in this schema.
   */
  public Map<String, Attribute> getExtraAttributes()
  {
    return extraAttributes;
  }


  /**
   * Add a new extra Attribute for this schema.
   *
   * @param  name     The identifier of the extra Attribute.
   *
   * @param  attr     The extra attribute that must be added to
   *                  this Schema.
   */
  public void addExtraAttribute(String name, Attribute attr)
  {
    extraAttributes.put(name, attr);
  }


  /**
   * Writes a single file containing all schema element definitions,
   * which can be used on startup to determine whether the schema
   * files were edited with the server offline.
   */
  public static void writeConcatenatedSchema()
  {
    String concatFilePath = null;
    try
    {
      LinkedHashSet<String> attributeTypes =
           new LinkedHashSet<String>();
      LinkedHashSet<String> objectClasses =
           new LinkedHashSet<String>();
      LinkedHashSet<String> nameForms = new LinkedHashSet<String>();
      LinkedHashSet<String> ditContentRules =
           new LinkedHashSet<String>();
      LinkedHashSet<String> ditStructureRules =
           new LinkedHashSet<String>();
      LinkedHashSet<String> matchingRuleUses =
           new LinkedHashSet<String>();
      genConcatenatedSchema(attributeTypes, objectClasses, nameForms,
                            ditContentRules, ditStructureRules,
                            matchingRuleUses);


      File configFile = new File(DirectoryServer.getConfigFile());
      File configDirectory  = configFile.getParentFile();
      File upgradeDirectory = new File(configDirectory, "upgrade");
      upgradeDirectory.mkdir();
      File concatFile       = new File(upgradeDirectory,
                                       SCHEMA_CONCAT_FILE_NAME);
      concatFilePath = concatFile.getAbsolutePath();

      File tempFile = new File(concatFilePath + ".tmp");
      BufferedWriter writer =
           new BufferedWriter(new FileWriter(tempFile, false));
      writer.write("dn: " + DirectoryServer.getSchemaDN().toString());
      writer.newLine();
      writer.write("objectClass: top");
      writer.newLine();
      writer.write("objectClass: ldapSubentry");
      writer.newLine();
      writer.write("objectClass: subschema");
      writer.newLine();

      for (String line : attributeTypes)
      {
        writer.write(ATTR_ATTRIBUTE_TYPES);
        writer.write(": ");
        writer.write(line);
        writer.newLine();
      }

      for (String line : objectClasses)
      {
        writer.write(ATTR_OBJECTCLASSES);
        writer.write(": ");
        writer.write(line);
        writer.newLine();
      }

      for (String line : nameForms)
      {
        writer.write(ATTR_NAME_FORMS);
        writer.write(": ");
        writer.write(line);
        writer.newLine();
      }

      for (String line : ditContentRules)
      {
        writer.write(ATTR_DIT_CONTENT_RULES);
        writer.write(": ");
        writer.write(line);
        writer.newLine();
      }

      for (String line : ditStructureRules)
      {
        writer.write(ATTR_DIT_STRUCTURE_RULES);
        writer.write(": ");
        writer.write(line);
        writer.newLine();
      }

      for (String line : matchingRuleUses)
      {
        writer.write(ATTR_MATCHING_RULE_USE);
        writer.write(": ");
        writer.write(line);
        writer.newLine();
      }

      writer.close();

      if (concatFile.exists())
      {
        concatFile.delete();
      }
      tempFile.renameTo(concatFile);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // This is definitely not ideal, but it's not the end of the
      // world.  The worst that should happen is that the schema
      // changes could potentially be sent to the other servers again
      // when this server is restarted, which shouldn't hurt anything.
      // Still, we should log a warning message.
      logError(ERR_SCHEMA_CANNOT_WRITE_CONCAT_SCHEMA_FILE.get(
          String.valueOf(concatFilePath), getExceptionMessage(e)));
    }
  }



  /**
   * Reads the files contained in the schema directory and generates a
   * concatenated view of their contents in the provided sets.
   *
   * @param  attributeTypes     The set into which to place the
   *                            attribute types read from the schema
   *                            files.
   * @param  objectClasses      The set into which to place the object
   *                            classes read from the schema files.
   * @param  nameForms          The set into which to place the name
   *                            forms read from the schema files.
   * @param  ditContentRules    The set into which to place the DIT
   *                            content rules read from the schema
   *                            files.
   * @param  ditStructureRules  The set into which to place the DIT
   *                            structure rules read from the schema
   *                            files.
   * @param  matchingRuleUses   The set into which to place the
   *                            matching rule uses read from the
   *                            schema files.
   *
   * @throws  IOException  If a problem occurs while reading the
   *                       schema file elements.
   */
  public static void genConcatenatedSchema(
                          LinkedHashSet<String> attributeTypes,
                          LinkedHashSet<String> objectClasses,
                          LinkedHashSet<String> nameForms,
                          LinkedHashSet<String> ditContentRules,
                          LinkedHashSet<String> ditStructureRules,
                          LinkedHashSet<String> matchingRuleUses)
          throws IOException
  {
    // Get a sorted list of the files in the schema directory.
    String schemaDirectory =
                SchemaConfigManager.getSchemaDirectoryPath(false);
    TreeSet<File> schemaFiles = new TreeSet<File>();
    for (File f : new File(schemaDirectory).listFiles())
    {
      if (f.isFile())
      {
        schemaFiles.add(f);
      }
    }

    schemaDirectory =
      SchemaConfigManager.getSchemaDirectoryPath(true);
    for (File f : new File(schemaDirectory).listFiles())
    {
      if (f.isFile())
      {
        schemaFiles.add(f);
      }
    }


    // Open each of the files in order and read the elements that they
    // contain, appending them to the appropriate lists.
    for (File f : schemaFiles)
    {
      // Read the contents of the file into a list with one schema
      // element per list element.
      LinkedList<StringBuilder> lines =
           new LinkedList<StringBuilder>();
      BufferedReader reader =
           new BufferedReader(new FileReader(f));

      while (true)
      {
        String line = reader.readLine();
        if (line == null)
        {
          break;
        }
        else if (line.startsWith("#") || (line.length() == 0))
        {
          continue;
        }
        else if (line.startsWith(" "))
        {
          lines.getLast().append(line.substring(1));
        }
        else
        {
          lines.add(new StringBuilder(line));
        }
      }

      reader.close();


      // Iterate through each line in the list.  Find the colon and
      // get the attribute name at the beginning.  If it's someting
      // that we don't recognize, then skip it.  Otherwise, add the
      // X-SCHEMA-FILE extension and add it to the appropriate schema
      // element list.
      for (StringBuilder buffer : lines)
      {
        // Get the line and add the X-SCHEMA-FILE extension to the end
        // of it.  All of them should end with " )" but some might
        // have the parenthesis crammed up against the last character
        // so deal with that as well.
        String line = buffer.toString().trim();
        if (line.endsWith(" )"))
        {
         line = line.substring(0, line.length()-1) +
                SCHEMA_PROPERTY_FILENAME + " '" + f.getName() + "' )";
        }
        else if (line.endsWith(")"))
        {
         line = line.substring(0, line.length()-1) + " " +
                SCHEMA_PROPERTY_FILENAME + " '" + f.getName() + "' )";
        }
        else
        {
          continue;
        }

        String value;
        String lowerLine = toLowerCase(line);
        if (lowerLine.startsWith(ATTR_ATTRIBUTE_TYPES_LC))
        {
          value = line.substring(
                       ATTR_ATTRIBUTE_TYPES.length()+1).trim();
          attributeTypes.add(value);
        }
        else if (lowerLine.startsWith(ATTR_OBJECTCLASSES_LC))
        {
          value = line.substring(
                       ATTR_OBJECTCLASSES.length()+1).trim();
          objectClasses.add(value);
        }
        else if (lowerLine.startsWith(ATTR_NAME_FORMS_LC))
        {
          value = line.substring(ATTR_NAME_FORMS.length()+1).trim();
          nameForms.add(value);
        }
        else if (lowerLine.startsWith(ATTR_DIT_CONTENT_RULES_LC))
        {
          value = line.substring(
                     ATTR_DIT_CONTENT_RULES.length()+1).trim();
          ditContentRules.add(value);
        }
        else if (lowerLine.startsWith(ATTR_DIT_STRUCTURE_RULES_LC))
        {
          value = line.substring(
                     ATTR_DIT_STRUCTURE_RULES.length()+1).trim();
          ditStructureRules.add(value);
        }
        else if (lowerLine.startsWith(ATTR_MATCHING_RULE_USE_LC))
        {
          value = line.substring(
                     ATTR_MATCHING_RULE_USE.length()+1).trim();
          matchingRuleUses.add(value);
        }
      }
    }
  }



  /**
   * Reads data from the specified concatenated schema file into the
   * provided sets.
   *
   * @param  concatSchemaFile   The path to the concatenated schema
   *                            file to be read.
   * @param  attributeTypes     The set into which to place the
   *                            attribute types read from the
   *                            concatenated schema file.
   * @param  objectClasses      The set into which to place the object
   *                            classes read from the concatenated
   *                            schema file.
   * @param  nameForms          The set into which to place the name
   *                            forms read from the concatenated
   *                            schema file.
   * @param  ditContentRules    The set into which to place the DIT
   *                            content rules read from the
   *                            concatenated schema file.
   * @param  ditStructureRules  The set into which to place the DIT
   *                            structure rules read from the
   *                            concatenated schema file.
   * @param  matchingRuleUses   The set into which to place the
   *                            matching rule uses read from the
   *                            concatenated schema file.
   *
   * @throws  IOException  If a problem occurs while reading the
   *                       schema file elements.
   */
  public static void readConcatenatedSchema(String concatSchemaFile,
                          LinkedHashSet<String> attributeTypes,
                          LinkedHashSet<String> objectClasses,
                          LinkedHashSet<String> nameForms,
                          LinkedHashSet<String> ditContentRules,
                          LinkedHashSet<String> ditStructureRules,
                          LinkedHashSet<String> matchingRuleUses)
          throws IOException
  {
    BufferedReader reader =
         new BufferedReader(new FileReader(concatSchemaFile));
    while (true)
    {
      String line = reader.readLine();
      if (line == null)
      {
        break;
      }

      String value;
      String lowerLine = toLowerCase(line);
      if (lowerLine.startsWith(ATTR_ATTRIBUTE_TYPES_LC))
      {
        value =
             line.substring(ATTR_ATTRIBUTE_TYPES.length()+1).trim();
        attributeTypes.add(value);
      }
      else if (lowerLine.startsWith(ATTR_OBJECTCLASSES_LC))
      {
        value = line.substring(ATTR_OBJECTCLASSES.length()+1).trim();
        objectClasses.add(value);
      }
      else if (lowerLine.startsWith(ATTR_NAME_FORMS_LC))
      {
        value = line.substring(ATTR_NAME_FORMS.length()+1).trim();
        nameForms.add(value);
      }
      else if (lowerLine.startsWith(ATTR_DIT_CONTENT_RULES_LC))
      {
        value = line.substring(
                     ATTR_DIT_CONTENT_RULES.length()+1).trim();
        ditContentRules.add(value);
      }
      else if (lowerLine.startsWith(ATTR_DIT_STRUCTURE_RULES_LC))
      {
        value = line.substring(
                     ATTR_DIT_STRUCTURE_RULES.length()+1).trim();
        ditStructureRules.add(value);
      }
      else if (lowerLine.startsWith(ATTR_MATCHING_RULE_USE_LC))
      {
        value = line.substring(
                     ATTR_MATCHING_RULE_USE.length()+1).trim();
        matchingRuleUses.add(value);
      }
    }

    reader.close();
  }



  /**
   * Compares the provided sets of schema element definitions and
   * writes any differences found into the given list of
   * modifications.
   *
   * @param  oldElements  The set of elements of the specified type
   *                      read from the previous concatenated schema
   *                      files.
   * @param  newElements  The set of elements of the specified type
   *                      read from the server's current schema.
   * @param  elementType  The attribute type associated with the
   *                      schema element being compared.
   * @param  mods         The list of modifications into which any
   *                      identified differences should be written.
   */
  public static void compareConcatenatedSchema(
                          LinkedHashSet<String> oldElements,
                          LinkedHashSet<String> newElements,
                          AttributeType elementType,
                          LinkedList<Modification> mods)
  {
    AttributeType attributeTypesType =
      DirectoryServer.getAttributeType(ATTR_ATTRIBUTE_TYPES_LC, true);

    AttributeBuilder builder = new AttributeBuilder(elementType);
    for (String s : oldElements)
    {
      if (!newElements.contains(s))
      {
        builder.add(AttributeValues.create(attributeTypesType, s));
      }
    }

    if (!builder.isEmpty())
    {
      mods.add(new Modification(ModificationType.DELETE,
                                builder.toAttribute()));
    }

    builder.setAttributeType(elementType);
    for (String s : newElements)
    {
      if (!oldElements.contains(s))
      {
        builder.add(AttributeValues.create(attributeTypesType, s));
      }
    }

    if (!builder.isEmpty())
    {
      mods.add(new Modification(ModificationType.ADD,
                                builder.toAttribute()));
    }
  }



  /**
   * Destroys the structures maintained by the schema so that they are
   * no longer usable. This should only be called at the end of the
   * server shutdown process, and it can help detect inappropriate
   * cached references.
   */
  @org.opends.server.types.PublicAPI(
       stability=org.opends.server.types.StabilityLevel.PRIVATE,
       mayInstantiate=false,
       mayExtend=false,
       mayInvoke=true)
  public synchronized void destroy()
  {
    if (approximateMatchingRules != null)
    {
      approximateMatchingRules.clear();
      approximateMatchingRules = null;
    }

    if (attributeTypes != null)
    {
      attributeTypes.clear();
      attributeTypes = null;
    }

    if (attributeTypeSet != null)
    {
      attributeTypeSet.clear();
      attributeTypeSet = null;
    }

    if (ditContentRules != null)
    {
      ditContentRules.clear();
      ditContentRules = null;
    }

    if (ditContentRuleSet != null)
    {
      ditContentRuleSet.clear();
      ditContentRuleSet = null;
    }

    if (ditStructureRulesByID != null)
    {
      ditStructureRulesByID.clear();
      ditStructureRulesByID = null;
    }

    if (ditStructureRulesByNameForm != null)
    {
      ditStructureRulesByNameForm.clear();
      ditStructureRulesByNameForm = null;
    }

    if (ditStructureRuleSet != null)
    {
      ditStructureRuleSet.clear();
      ditStructureRuleSet = null;
    }

    if (equalityMatchingRules != null)
    {
      equalityMatchingRules.clear();
      equalityMatchingRules = null;
    }

    if (matchingRules != null)
    {
      matchingRules.clear();
      matchingRules = null;
    }

    if (matchingRuleSet != null)
    {
      matchingRuleSet.clear();
      matchingRuleSet = null;
    }

    if (matchingRuleUses != null)
    {
      matchingRuleUses.clear();
      matchingRuleUses = null;
    }

    if (matchingRuleUseSet != null)
    {
      matchingRuleUseSet.clear();
      matchingRuleUseSet = null;
    }

    if (nameFormsByName != null)
    {
      nameFormsByName.clear();
      nameFormsByName = null;
    }

    if (nameFormsByOC != null)
    {
      nameFormsByOC.clear();
      nameFormsByOC = null;
    }

    if (nameFormSet != null)
    {
      nameFormSet.clear();
      nameFormSet = null;
    }

    if (objectClasses != null)
    {
      objectClasses.clear();
      objectClasses = null;
    }

    if (objectClassSet != null)
    {
      objectClassSet.clear();
      objectClassSet = null;
    }

    if (orderingMatchingRules != null)
    {
      orderingMatchingRules.clear();
      orderingMatchingRules = null;
    }

    if (subordinateTypes != null)
    {
      subordinateTypes.clear();
      subordinateTypes = null;
    }

    if (substringMatchingRules != null)
    {
      substringMatchingRules.clear();
      substringMatchingRules = null;
    }

    if (extraAttributes != null)
    {
      extraAttributes.clear();
      extraAttributes = null;
    }

    if (syntaxes != null)
    {
      syntaxes.clear();
      syntaxes = null;
    }

    if (syntaxSet != null)
    {
      syntaxSet.clear();
      syntaxSet = null;
    }

    if (extensibleMatchingRules != null)
    {
      extensibleMatchingRules.clear();
      extensibleMatchingRules = null;
    }

  }
}

