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



import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.protocols.asn1.ASN1OctetString;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
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
public class Schema
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.Schema";



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



  /**
   * Creates a new schema structure with all elements initialized but
   * empty.
   */
  public Schema()
  {
    assert debugConstructor(CLASS_NAME);

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


    syntaxSet           = new LinkedHashSet<AttributeValue>();
    attributeTypeSet    = new LinkedHashSet<AttributeValue>();
    ditContentRuleSet   = new LinkedHashSet<AttributeValue>();
    ditStructureRuleSet = new LinkedHashSet<AttributeValue>();
    matchingRuleSet     = new LinkedHashSet<AttributeValue>();
    matchingRuleUseSet  = new LinkedHashSet<AttributeValue>();
    nameFormSet         = new LinkedHashSet<AttributeValue>();
    objectClassSet      = new LinkedHashSet<AttributeValue>();

    oldestModificationTime   = System.currentTimeMillis();
    youngestModificationTime = oldestModificationTime;
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
  public final ConcurrentHashMap<String,AttributeType>
                    getAttributeTypes()
  {
    assert debugEnter(CLASS_NAME, "getAttributeTypes");

    return attributeTypes;
  }



  /**
   * Retrieves the set of defined attribute types for this schema.
   *
   * @return  The set of defined attribute types for this schema.
   */
  public final LinkedHashSet<AttributeValue> getAttributeTypeSet()
  {
    assert debugEnter(CLASS_NAME, "getAttributeTypeSet");

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
    assert debugEnter(CLASS_NAME, "hasAttributeType",
                      String.valueOf(lowerName));

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
  public final AttributeType getAttributeType(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getAttributeType",
                      String.valueOf(lowerName));

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
  public final void registerAttributeType(AttributeType attributeType,
                                          boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "registerAttributeType",
                      String.valueOf(attributeType),
                      String.valueOf(overwriteExisting));

    synchronized (attributeTypes)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(attributeType.getOID());
        if (attributeTypes.containsKey(oid))
        {
          AttributeType conflictingType = attributeTypes.get(oid);

          int    msgID   = MSGID_SCHEMA_CONFLICTING_ATTRIBUTE_OID;
          String message =
               getMessage(msgID, attributeType.getNameOrOID(), oid,
                          conflictingType.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }

        for (String name : attributeType.getNormalizedNames())
        {
          if (attributeTypes.containsKey(name))
          {
            AttributeType conflictingType = attributeTypes.get(name);

            int    msgID   = MSGID_SCHEMA_CONFLICTING_ATTRIBUTE_NAME;
            String message =
                 getMessage(msgID, attributeType.getNameOrOID(), name,
                            conflictingType.getNameOrOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message,
                           msgID);
          }
        }
      }

      attributeTypes.put(toLowerCase(attributeType.getOID()),
                         attributeType);

      for (String name : attributeType.getNormalizedNames())
      {
        attributeTypes.put(name, attributeType);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = attributeType.getDefinition();
      ASN1OctetString rawValue = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      attributeTypeSet.add(new AttributeValue(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided attribute type definition with this
   * schema.
   *
   * @param  attributeType  The attribute type to deregister with this
   *                        schema.
   */
  public final void deregisterAttributeType(
                         AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "deregisterAttributeType",
                      String.valueOf(attributeType));

    synchronized (attributeTypes)
    {
      attributeTypes.remove(toLowerCase(attributeType.getOID()),
                            attributeType);

      for (String name : attributeType.getNormalizedNames())
      {
        attributeTypes.remove(name, attributeType);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = attributeType.getDefinition();
      ASN1OctetString rawValue = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      attributeTypeSet.remove(new AttributeValue(rawValue,
                                                 normValue));
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
  public final ConcurrentHashMap<String,ObjectClass>
                    getObjectClasses()
  {
    assert debugEnter(CLASS_NAME, "getObjectClasses");

    return objectClasses;
  }



  /**
   * Retrieves the set of defined objectclasses for this schema.
   *
   * @return  The set of defined objectclasses for this schema.
   */
  public final LinkedHashSet<AttributeValue> getObjectClassSet()
  {
    assert debugEnter(CLASS_NAME, "getObjectClassSet");

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
    assert debugEnter(CLASS_NAME, "hasObjectClass",
                      String.valueOf(lowerName));

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
  public final ObjectClass getObjectClass(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getObjectClass",
                      String.valueOf(lowerName));

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
  public final void registerObjectClass(ObjectClass objectClass,
                                        boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "registerObjectClass",
                      String.valueOf(objectClass),
                      String.valueOf(overwriteExisting));

    synchronized (objectClasses)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(objectClass.getOID());
        if (objectClasses.containsKey(oid))
        {
          ObjectClass conflictingClass = objectClasses.get(oid);

          int    msgID   = MSGID_SCHEMA_CONFLICTING_OBJECTCLASS_OID;
          String message =
               getMessage(msgID, objectClass.getNameOrOID(),
                          oid, conflictingClass.getNameOrOID());
          throw new DirectoryException(
                       ResultCode.CONSTRAINT_VIOLATION, message,
                       msgID);
        }

        for (String name : objectClass.getNormalizedNames())
        {
          if (objectClasses.containsKey(name))
          {
            ObjectClass conflictingClass = objectClasses.get(name);

            int msgID = MSGID_SCHEMA_CONFLICTING_OBJECTCLASS_NAME;
            String message =
                 getMessage(msgID, objectClass.getNameOrOID(), name,
                            conflictingClass.getNameOrOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message,
                           msgID);
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
      ASN1OctetString rawValue = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      objectClassSet.add(new AttributeValue(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided objectclass definition with this schema.
   *
   * @param  objectClass  The objectclass to deregister with this
   *                      schema.
   */
  public final void deregisterObjectClass(ObjectClass objectClass)
  {
    assert debugEnter(CLASS_NAME, "deregisterObjectClass",
                      String.valueOf(objectClass));

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
      String valueString = objectClass.getDefinition();
      ASN1OctetString rawValue = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      objectClassSet.remove(new AttributeValue(rawValue, normValue));
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
  public final ConcurrentHashMap<String,AttributeSyntax> getSyntaxes()
  {
    assert debugEnter(CLASS_NAME, "getSyntaxes");

    return syntaxes;
  }



  /**
   * Retrieves the set of defined attribute syntaxes for this schema.
   *
   * @return  The set of defined attribute syntaxes for this schema.
   */
  public final LinkedHashSet<AttributeValue> getSyntaxSet()
  {
    assert debugEnter(CLASS_NAME, "getSyntaxSet");

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
    assert debugEnter(CLASS_NAME, "hasSyntax",
                      String.valueOf(lowerName));

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
  public final AttributeSyntax getSyntax(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getSyntax",
                      String.valueOf(lowerName));

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
  public final void registerSyntax(AttributeSyntax syntax,
                                   boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "registerSyntax",
                      String.valueOf(syntax),
                      String.valueOf(overwriteExisting));

    synchronized (syntaxes)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(syntax.getOID());
        if (syntaxes.containsKey(oid))
        {
          AttributeSyntax conflictingSyntax = syntaxes.get(oid);

          int    msgID   = MSGID_SCHEMA_CONFLICTING_SYNTAX_OID;
          String message =
               getMessage(msgID, syntax.getSyntaxName(),
                          oid, conflictingSyntax.getSyntaxName());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }
      }

      syntaxes.put(toLowerCase(syntax.getOID()), syntax);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = syntax.toString();
      ASN1OctetString rawValue = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      syntaxSet.add(new AttributeValue(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided attribute syntax definition with this
   * schema.
   *
   * @param  syntax  The attribute syntax to deregister with this
   *                 schema.
   */
  public final void deregisterSyntax(AttributeSyntax syntax)
  {
    assert debugEnter(CLASS_NAME, "deregisterSyntax",
                      String.valueOf(syntax));

    synchronized (syntaxes)
    {
      syntaxes.remove(toLowerCase(syntax.getOID()), syntax);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = syntax.toString();
      ASN1OctetString rawValue = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      syntaxSet.remove(new AttributeValue(rawValue, normValue));
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
  public final ConcurrentHashMap<String,MatchingRule>
                    getMatchingRules()
  {
    assert debugEnter(CLASS_NAME, "getMatchingRules");

    return matchingRules;
  }



  /**
   * Retrieves the set of defined matching rules for this schema.
   *
   * @return  The set of defined matching rules for this schema.
   */
  public final LinkedHashSet<AttributeValue> getMatchingRuleSet()
  {
    assert debugEnter(CLASS_NAME, "getMatchingRuleSet");

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
    assert debugEnter(CLASS_NAME, "hasMatchingRule",
                      String.valueOf(lowerName));

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
  public final MatchingRule getMatchingRule(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getMatchingRule",
                      String.valueOf(lowerName));

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
  public final void registerMatchingRule(MatchingRule matchingRule,
                                         boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "registerMatchingRule",
                      String.valueOf(matchingRule),
                      String.valueOf(overwriteExisting));

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

            int    msgID   = MSGID_SCHEMA_CONFLICTING_MR_OID;
            String message =
                 getMessage(msgID, matchingRule.getNameOrOID(), oid,
                            conflictingRule.getNameOrOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message,
                           msgID);
          }

          String name = matchingRule.getName();
          if (name != null)
          {
            name = toLowerCase(name);
            if (matchingRules.containsKey(name))
            {
              MatchingRule conflictingRule = matchingRules.get(name);

              int    msgID   = MSGID_SCHEMA_CONFLICTING_MR_NAME;
              String message =
                   getMessage(msgID, matchingRule.getOID(), name,
                              conflictingRule.getOID());
              throw new DirectoryException(
                             ResultCode.CONSTRAINT_VIOLATION, message,
                             msgID);
            }
          }
        }

        matchingRules.put(toLowerCase(matchingRule.getOID()),
                          matchingRule);

        String name = matchingRule.getName();
        if (name != null)
        {
          matchingRules.put(toLowerCase(name), matchingRule);
        }

        // We'll use an attribute value including the normalized value
        // rather than the attribute type because otherwise it would
        // use a very expensive matching rule (OID first component
        // match) that would kill performance.
        String valueString = matchingRule.toString();
        ASN1OctetString rawValue  = new ASN1OctetString(valueString);
        ASN1OctetString normValue =
             new ASN1OctetString(toLowerCase(valueString));
        matchingRuleSet.add(new AttributeValue(rawValue, normValue));
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
  public final void deregisterMatchingRule(MatchingRule matchingRule)
  {
    assert debugEnter(CLASS_NAME, "deregisterMatchingRule",
                      String.valueOf(matchingRule));

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

        String name = matchingRule.getName();
        if (name != null)
        {
          matchingRules.remove(toLowerCase(name), matchingRule);
        }


        // We'll use an attribute value including the normalized value
        // rather than the attribute type because otherwise it would
        // use a very expensive matching rule (OID first component
        // match) that would kill performance.
        String valueString = matchingRule.toString();
        ASN1OctetString rawValue  = new ASN1OctetString(valueString);
        ASN1OctetString normValue =
             new ASN1OctetString(toLowerCase(valueString));
        matchingRuleSet.remove(new AttributeValue(rawValue,
                                                  normValue));
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
  public final ConcurrentHashMap<String,ApproximateMatchingRule>
                    getApproximateMatchingRules()
  {
    assert debugEnter(CLASS_NAME, "getApproximateMatchingRules");

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
  public final ApproximateMatchingRule
                    getApproximateMatchingRule(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getApproximateMatchingRule",
                      String.valueOf(lowerName));

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
  public final void registerApproximateMatchingRule(
                         ApproximateMatchingRule matchingRule,
                         boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "registerApproximateMatchingRule",
                      String.valueOf(matchingRule),
                      String.valueOf(overwriteExisting));


    synchronized (matchingRules)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(matchingRule.getOID());
        if (matchingRules.containsKey(oid))
        {
          MatchingRule conflictingRule = matchingRules.get(oid);

          int    msgID   = MSGID_SCHEMA_CONFLICTING_MR_OID;
          String message =
               getMessage(msgID, matchingRule.getNameOrOID(), oid,
                          conflictingRule.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }

        String name = matchingRule.getName();
        if (name != null)
        {
          name = toLowerCase(name);
          if (matchingRules.containsKey(name))
          {
            MatchingRule conflictingRule = matchingRules.get(name);

            int    msgID   = MSGID_SCHEMA_CONFLICTING_MR_NAME;
            String message =
                 getMessage(msgID, matchingRule.getOID(), name,
                            conflictingRule.getOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message,
                           msgID);
          }
        }
      }

      String oid = toLowerCase(matchingRule.getOID());
      approximateMatchingRules.put(oid, matchingRule);
      matchingRules.put(oid, matchingRule);

      String name = matchingRule.getName();
      if (name != null)
      {
        name = toLowerCase(name);
        approximateMatchingRules.put(name, matchingRule);
        matchingRules.put(name, matchingRule);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      matchingRuleSet.add(new AttributeValue(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided approximate matching rule definition
   * with this schema.
   *
   * @param  matchingRule  The approximate matching rule to deregister
   *                       with this schema.
   */
  public final void deregisterApproximateMatchingRule(
                         ApproximateMatchingRule matchingRule)
  {
    assert debugEnter(CLASS_NAME, "deregisterApproximateMatchingRule",
                      String.valueOf(matchingRule));

    synchronized (matchingRules)
    {
      String oid = matchingRule.getOID();
      approximateMatchingRules.remove(oid, matchingRule);
      matchingRules.remove(oid, matchingRule);

      String name = matchingRule.getName();
      if (name != null)
      {
        name = toLowerCase(name);
        approximateMatchingRules.remove(name, matchingRule);
        matchingRules.remove(name, matchingRule);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      matchingRuleSet.remove(new AttributeValue(rawValue, normValue));
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
  public final ConcurrentHashMap<String,EqualityMatchingRule>
                    getEqualityMatchingRules()
  {
    assert debugEnter(CLASS_NAME, "getEqualityMatchingRules");

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
  public final EqualityMatchingRule getEqualityMatchingRule(
                                         String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getEqualityMatchingRule",
                      String.valueOf(lowerName));

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
  public final void registerEqualityMatchingRule(
                         EqualityMatchingRule matchingRule,
                         boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "registerEqualityMatchingRule",
                      String.valueOf(matchingRule),
                      String.valueOf(overwriteExisting));


    synchronized (matchingRules)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(matchingRule.getOID());
        if (matchingRules.containsKey(oid))
        {
          MatchingRule conflictingRule = matchingRules.get(oid);

          int    msgID   = MSGID_SCHEMA_CONFLICTING_MR_OID;
          String message =
               getMessage(msgID, matchingRule.getNameOrOID(), oid,
                          conflictingRule.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }

        String name = matchingRule.getName();
        if (name != null)
        {
          name = toLowerCase(name);
          if (matchingRules.containsKey(name))
          {
            MatchingRule conflictingRule = matchingRules.get(name);

            int    msgID   = MSGID_SCHEMA_CONFLICTING_MR_NAME;
            String message =
                 getMessage(msgID, matchingRule.getOID(), name,
                            conflictingRule.getOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message,
                           msgID);
          }
        }
      }

      String oid = toLowerCase(matchingRule.getOID());
      equalityMatchingRules.put(oid, matchingRule);
      matchingRules.put(oid, matchingRule);

      String name = matchingRule.getName();
      if (name != null)
      {
        name = toLowerCase(name);
        equalityMatchingRules.put(name, matchingRule);
        matchingRules.put(name, matchingRule);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      matchingRuleSet.add(new AttributeValue(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided equality matching rule definition with
   * this schema.
   *
   * @param  matchingRule  The equality matching rule to deregister
   *                       with this schema.
   */
  public final void deregisterEqualityMatchingRule(
                         EqualityMatchingRule matchingRule)
  {
    assert debugEnter(CLASS_NAME, "deregisterEqualityMatchingRule",
                      String.valueOf(matchingRule));

    synchronized (matchingRules)
    {
      String oid = matchingRule.getOID();
      equalityMatchingRules.remove(oid, matchingRule);
      matchingRules.remove(oid, matchingRule);

      String name = matchingRule.getName();
      if (name != null)
      {
        name = toLowerCase(name);
        equalityMatchingRules.remove(name, matchingRule);
        matchingRules.remove(name, matchingRule);
      }


      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      matchingRuleSet.remove(new AttributeValue(rawValue, normValue));
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
  public final ConcurrentHashMap<String,OrderingMatchingRule>
                    getOrderingMatchingRules()
  {
    assert debugEnter(CLASS_NAME, "getOrderingMatchingRules");

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
  public final OrderingMatchingRule getOrderingMatchingRule(
                                         String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getOrderingMatchingRule",
                      String.valueOf(lowerName));

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
  public final void registerOrderingMatchingRule(
                         OrderingMatchingRule matchingRule,
                         boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "registerOrderingMatchingRule",
                      String.valueOf(matchingRule),
                      String.valueOf(overwriteExisting));


    synchronized (matchingRules)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(matchingRule.getOID());
        if (matchingRules.containsKey(oid))
        {
          MatchingRule conflictingRule = matchingRules.get(oid);

          int    msgID   = MSGID_SCHEMA_CONFLICTING_MR_OID;
          String message =
               getMessage(msgID, matchingRule.getNameOrOID(), oid,
                          conflictingRule.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }

        String name = matchingRule.getName();
        if (name != null)
        {
          name = toLowerCase(name);
          if (matchingRules.containsKey(name))
          {
            MatchingRule conflictingRule = matchingRules.get(name);

            int    msgID   = MSGID_SCHEMA_CONFLICTING_MR_NAME;
            String message =
                 getMessage(msgID, matchingRule.getOID(), name,
                            conflictingRule.getOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message,
                           msgID);
          }
        }
      }

      String oid = toLowerCase(matchingRule.getOID());
      orderingMatchingRules.put(oid, matchingRule);
      matchingRules.put(oid, matchingRule);

      String name = matchingRule.getName();
      if (name != null)
      {
        name = toLowerCase(name);
        orderingMatchingRules.put(name, matchingRule);
        matchingRules.put(name, matchingRule);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      matchingRuleSet.add(new AttributeValue(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided ordering matching rule definition with
   * this schema.
   *
   * @param  matchingRule  The ordering matching rule to deregister
   *                       with this schema.
   */
  public final void deregisterOrderingMatchingRule(
                         OrderingMatchingRule matchingRule)
  {
    assert debugEnter(CLASS_NAME, "deregisterOrderingMatchingRule",
                      String.valueOf(matchingRule));

    synchronized (matchingRules)
    {
      String oid = matchingRule.getOID();
      orderingMatchingRules.remove(oid, matchingRule);
      matchingRules.remove(oid, matchingRule);

      String name = matchingRule.getName();
      if (name != null)
      {
        name = toLowerCase(name);
        orderingMatchingRules.remove(name, matchingRule);
        matchingRules.remove(name, matchingRule);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      matchingRuleSet.remove(new AttributeValue(rawValue, normValue));
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
  public final ConcurrentHashMap<String,SubstringMatchingRule>
                    getSubstringMatchingRules()
  {
    assert debugEnter(CLASS_NAME, "getSubstringMatchingRules");

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
  public final SubstringMatchingRule getSubstringMatchingRule(
                                          String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getSubstringMatchingRule",
                      String.valueOf(lowerName));

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
  public final void registerSubstringMatchingRule(
                         SubstringMatchingRule matchingRule,
                         boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "registerSubstringMatchingRule",
                      String.valueOf(matchingRule),
                      String.valueOf(overwriteExisting));


    synchronized (matchingRules)
    {
      if (! overwriteExisting)
      {
        String oid = toLowerCase(matchingRule.getOID());
        if (matchingRules.containsKey(oid))
        {
          MatchingRule conflictingRule = matchingRules.get(oid);

          int    msgID   = MSGID_SCHEMA_CONFLICTING_MR_OID;
          String message =
               getMessage(msgID, matchingRule.getNameOrOID(), oid,
                          conflictingRule.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }

        String name = matchingRule.getName();
        if (name != null)
        {
          name = toLowerCase(name);
          if (matchingRules.containsKey(name))
          {
            MatchingRule conflictingRule = matchingRules.get(name);

            int    msgID   = MSGID_SCHEMA_CONFLICTING_MR_NAME;
            String message =
                 getMessage(msgID, matchingRule.getOID(), name,
                            conflictingRule.getOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message,
                           msgID);
          }
        }
      }

      String oid = toLowerCase(matchingRule.getOID());
      substringMatchingRules.put(oid, matchingRule);
      matchingRules.put(oid, matchingRule);

      String name = matchingRule.getName();
      if (name != null)
      {
        name = toLowerCase(name);
        substringMatchingRules.put(name, matchingRule);
        matchingRules.put(name, matchingRule);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      matchingRuleSet.add(new AttributeValue(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided substring matching rule definition with
   * this schema.
   *
   * @param  matchingRule  The substring matching rule to deregister
   *                       with this schema.
   */
  public final void deregisterSubstringMatchingRule(
                         SubstringMatchingRule matchingRule)
  {
    assert debugEnter(CLASS_NAME, "deregisterSubstringMatchingRule",
                      String.valueOf(matchingRule));

    synchronized (matchingRules)
    {
      String oid = matchingRule.getOID();
      substringMatchingRules.remove(oid, matchingRule);
      matchingRules.remove(oid, matchingRule);

      String name = matchingRule.getName();
      if (name != null)
      {
        name = toLowerCase(name);
        substringMatchingRules.remove(name, matchingRule);
        matchingRules.remove(name, matchingRule);
      }

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRule.toString();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      matchingRuleSet.remove(new AttributeValue(rawValue, normValue));
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
  public final ConcurrentHashMap<MatchingRule,MatchingRuleUse>
                    getMatchingRuleUses()
  {
    assert debugEnter(CLASS_NAME, "getMatchingRuleUses");

    return matchingRuleUses;
  }



  /**
   * Retrieves the set of defined matching rule uses for this schema.
   *
   * @return  The set of defined matching rule uses for this schema.
   */
  public final LinkedHashSet<AttributeValue> getMatchingRuleUseSet()
  {
    assert debugEnter(CLASS_NAME, "getMatchingRuleUseSet");

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
    assert debugEnter(CLASS_NAME, "hasMatchingRuleUse",
                      String.valueOf(matchingRule));

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
  public final MatchingRuleUse getMatchingRuleUse(
                                    MatchingRule matchingRule)
  {
    assert debugEnter(CLASS_NAME, "getMatchingRuleUse",
                      String.valueOf(matchingRule));

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
  public final void registerMatchingRuleUse(
                         MatchingRuleUse matchingRuleUse,
                         boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "registerMatchingRuleUse",
                      String.valueOf(matchingRuleUse),
                      String.valueOf(overwriteExisting));

    synchronized (matchingRuleUses)
    {
      MatchingRule matchingRule = matchingRuleUse.getMatchingRule();

      if (! overwriteExisting)
      {
        if (matchingRuleUses.containsKey(matchingRule))
        {
          MatchingRuleUse conflictingUse =
                               matchingRuleUses.get(matchingRule);

          int    msgID   = MSGID_SCHEMA_CONFLICTING_MATCHING_RULE_USE;
          String message =
               getMessage(msgID, matchingRuleUse.getName(),
                          matchingRule.getNameOrOID(),
                          conflictingUse.getName());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }
      }

      matchingRuleUses.put(matchingRule, matchingRuleUse);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRuleUse.getDefinition();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      matchingRuleUseSet.add(new AttributeValue(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided matching rule use definition with this
   * schema.
   *
   * @param  matchingRuleUse  The matching rule use to deregister with
   *                          this schema.
   */
  public final void deregisterMatchingRuleUse(
                         MatchingRuleUse matchingRuleUse)
  {
    assert debugEnter(CLASS_NAME, "deregisterMatchingRuleUse",
                      String.valueOf(matchingRuleUse));

    synchronized (matchingRuleUses)
    {
      matchingRuleUses.remove(matchingRuleUse.getMatchingRule(),
                              matchingRuleUse);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = matchingRuleUse.getDefinition();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      matchingRuleUseSet.remove(new AttributeValue(rawValue,
                                                   normValue));
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
  public final ConcurrentHashMap<ObjectClass,DITContentRule>
                    getDITContentRules()
  {
    assert debugEnter(CLASS_NAME, "getDITContentRules");

    return ditContentRules;
  }



  /**
   * Retrieves the set of defined DIT content rules for this schema.
   *
   * @return  The set of defined DIT content rules for this schema.
   */
  public final LinkedHashSet<AttributeValue> getDITContentRuleSet()
  {
    assert debugEnter(CLASS_NAME, "getDITContentRuleSet");

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
    assert debugEnter(CLASS_NAME, "hasDITContentRule",
                      String.valueOf(objectClass));

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
  public final DITContentRule getDITContentRule(
                                   ObjectClass objectClass)
  {
    assert debugEnter(CLASS_NAME, "getDITContentRule",
                      String.valueOf(objectClass));

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
  public final void registerDITContentRule(
                          DITContentRule ditContentRule,
                          boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "registerDITContentRule",
                      String.valueOf(ditContentRule),
                      String.valueOf(overwriteExisting));

    synchronized (ditContentRules)
    {
      ObjectClass objectClass = ditContentRule.getStructuralClass();

      if (! overwriteExisting)
      {
        if (ditContentRules.containsKey(objectClass))
        {
          DITContentRule conflictingRule =
                              ditContentRules.get(objectClass);

          int    msgID   = MSGID_SCHEMA_CONFLICTING_DIT_CONTENT_RULE;
          String message = getMessage(msgID, ditContentRule.getName(),
                                      objectClass.getNameOrOID(),
                                      conflictingRule.getName());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }
      }

      ditContentRules.put(objectClass, ditContentRule);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = ditContentRule.getDefinition();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      ditContentRuleSet.add(new AttributeValue(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided DIT content rule definition with this
   * schema.
   *
   * @param  ditContentRule  The DIT content rule to deregister with
   *                         this schema.
   */
  public final void deregisterDITContentRule(
                         DITContentRule ditContentRule)
  {
    assert debugEnter(CLASS_NAME, "deregisterDITContentRule",
                      String.valueOf(ditContentRule));

    synchronized (ditContentRules)
    {
      ditContentRules.remove(ditContentRule.getStructuralClass(),
                             ditContentRule);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = ditContentRule.getDefinition();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      ditContentRuleSet.remove(new AttributeValue(rawValue,
                                                  normValue));
    }
  }



  /**
   * Retrieves the set of defined DIT structure rules for this schema.
   *
   * @return  The set of defined DIT structure rules for this schema.
   */
  public final LinkedHashSet<AttributeValue> getDITStructureRuleSet()
  {
    assert debugEnter(CLASS_NAME, "getDITStructureRuleSet");

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
  public final ConcurrentHashMap<Integer,DITStructureRule>
                    getDITStructureRulesByID()
  {
    assert debugEnter(CLASS_NAME, "getDITStructureRulesByID");

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
  public final ConcurrentHashMap<NameForm,DITStructureRule>
                    getDITStructureRulesByNameForm()
  {
    assert debugEnter(CLASS_NAME, "getDITStructureRulesByNameForm");

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
    assert debugEnter(CLASS_NAME, "hasDITStructureRule",
                      String.valueOf(ruleID));

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
    assert debugEnter(CLASS_NAME, "hasDITStructureRule",
                      String.valueOf(nameForm));

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
  public final DITStructureRule getDITStructureRule(int ruleID)
  {
    assert debugEnter(CLASS_NAME, "getDITStructureRule",
                      String.valueOf(ruleID));

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
  public final DITStructureRule getDITStructureRule(NameForm nameForm)
  {
    assert debugEnter(CLASS_NAME, "getDITStructureRule",
                      String.valueOf(nameForm));

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
  public final void registerDITStructureRule(
                         DITStructureRule ditStructureRule,
                         boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "ditStructureRule",
                      String.valueOf(ditStructureRule),
                      String.valueOf(overwriteExisting));

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

          int msgID =
               MSGID_SCHEMA_CONFLICTING_DIT_STRUCTURE_RULE_NAME_FORM;
          String message =
               getMessage(msgID, ditStructureRule.getNameOrRuleID(),
                          nameForm.getNameOrOID(),
                          conflictingRule.getNameOrRuleID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }

        if (ditStructureRulesByID.containsKey(ruleID))
        {
          DITStructureRule conflictingRule =
               ditStructureRulesByID.get(ruleID);

          int msgID = MSGID_SCHEMA_CONFLICTING_DIT_STRUCTURE_RULE_ID;
          String message =
               getMessage(msgID, ditStructureRule.getNameOrRuleID(),
                          ruleID, conflictingRule.getNameOrRuleID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }
      }

      ditStructureRulesByNameForm.put(nameForm, ditStructureRule);
      ditStructureRulesByID.put(ruleID, ditStructureRule);

      // We'll use an attribute value including the normalized value
      // rather than the attribute type because otherwise it would use
      // a very expensive matching rule (OID first component match)
      // that would kill performance.
      String valueString = ditStructureRule.getDefinition();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      ditStructureRuleSet.add(new AttributeValue(rawValue,
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
  public final void deregisterDITStructureRule(
                         DITStructureRule ditStructureRule)
  {
    assert debugEnter(CLASS_NAME, "deregisterDITStructureRule",
                      String.valueOf(ditStructureRule));

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
      String valueString = ditStructureRule.getDefinition();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      ditStructureRuleSet.remove(new AttributeValue(rawValue,
                                                    normValue));
    }
  }



  /**
   * Retrieves the set of defined name forms for this schema.
   *
   * @return  The set of defined name forms for this schema.
   */
  public final LinkedHashSet<AttributeValue> getNameFormSet()
  {
    assert debugEnter(CLASS_NAME, "getNameFormSet");

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
  public final ConcurrentHashMap<ObjectClass,NameForm>
                    getNameFormsByObjectClass()
  {
    assert debugEnter(CLASS_NAME, "getNameForms");

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
  public final ConcurrentHashMap<String,NameForm>
                    getNameFormsByNameOrOID()
  {
    assert debugEnter(CLASS_NAME, "getNameForms");

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
    assert debugEnter(CLASS_NAME, "hasNameForm",
                      String.valueOf(objectClass));

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
    assert debugEnter(CLASS_NAME, "hasNameForm",
                      String.valueOf(lowerName));

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
  public final NameForm getNameForm(ObjectClass objectClass)
  {
    assert debugEnter(CLASS_NAME, "getNameForm",
                      String.valueOf(objectClass));

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
  public final NameForm getNameForm(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getNameForm",
                      String.valueOf(lowerName));

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
  public final void registerNameForm(NameForm nameForm,
                                     boolean overwriteExisting)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "registerNameForm",
                      String.valueOf(nameForm),
                      String.valueOf(overwriteExisting));

    synchronized (nameFormsByOC)
    {
      ObjectClass objectClass = nameForm.getStructuralClass();

      if (! overwriteExisting)
      {
        if (nameFormsByOC.containsKey(objectClass))
        {
          NameForm conflictingNameForm =
               nameFormsByOC.get(objectClass);

          int    msgID   = MSGID_SCHEMA_CONFLICTING_NAME_FORM_OC;
          String message =
               getMessage(msgID, nameForm.getNameOrOID(),
                          objectClass.getNameOrOID(),
                          conflictingNameForm.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }

        String oid = toLowerCase(nameForm.getOID());
        if (nameFormsByName.containsKey(oid))
        {
          NameForm conflictingNameForm = nameFormsByName.get(oid);

          int    msgID   = MSGID_SCHEMA_CONFLICTING_NAME_FORM_OID;
          String message =
               getMessage(msgID, nameForm.getNameOrOID(), oid,
                          conflictingNameForm.getNameOrOID());
          throw new DirectoryException(
                         ResultCode.CONSTRAINT_VIOLATION, message,
                         msgID);
        }

        for (String name : nameForm.getNames().keySet())
        {
          if (nameFormsByName.containsKey(name))
          {
            NameForm conflictingNameForm = nameFormsByName.get(name);

            int    msgID   = MSGID_SCHEMA_CONFLICTING_NAME_FORM_NAME;
            String message =
                 getMessage(msgID, nameForm.getNameOrOID(), oid,
                            conflictingNameForm.getNameOrOID());
            throw new DirectoryException(
                           ResultCode.CONSTRAINT_VIOLATION, message,
                           msgID);
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
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      nameFormSet.add(new AttributeValue(rawValue, normValue));
    }
  }



  /**
   * Deregisters the provided name form definition with this schema.
   *
   * @param  nameForm  The name form definition to deregister.
   */
  public final void deregisterNameForm(NameForm nameForm)
  {
    assert debugEnter(CLASS_NAME, "deregisterNameForm",
                      String.valueOf(nameForm));

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
      String valueString = nameForm.getDefinition();
      ASN1OctetString rawValue  = new ASN1OctetString(valueString);
      ASN1OctetString normValue =
           new ASN1OctetString(toLowerCase(valueString));
      nameFormSet.remove(new AttributeValue(rawValue, normValue));
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
    assert debugEnter(CLASS_NAME, "getOldestModificationTime");

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
    assert debugEnter(CLASS_NAME, "setOldestModificationTime",
                      String.valueOf(oldestModificationTime));

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
    assert debugEnter(CLASS_NAME, "getYoungestModificationTime");

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
    assert debugEnter(CLASS_NAME, "setYoungestModificationTime",
                      String.valueOf(youngestModificationTime));

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
  public final void rebuildDependentElements(
                         SchemaFileElement element)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "rebuildDependentElements",
                      String.valueOf(element));

    try
    {
      rebuildDependentElements(element, 0);
    }
    catch (DirectoryException de)
    {
      // If we got an error as a result of a circular reference, then
      // we want to make sure that the schema element we call out is
      // the one that is at the root of the problem.
      if (de.getErrorMessageID() ==
          MSGID_SCHEMA_CIRCULAR_DEPENDENCY_REFERENCE)
      {
        int    msgID   = MSGID_SCHEMA_CIRCULAR_DEPENDENCY_REFERENCE;
        String message = getMessage(msgID, element.getDefinition());
        throw new DirectoryException(de.getResultCode(), message,
                                     msgID, de);
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
  private final void rebuildDependentElements(
                          SchemaFileElement element, int depth)
          throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "rebuildDependentElements",
                      String.valueOf(element), String.valueOf(depth));

    if (depth > 20)
    {
      // FIXME -- Is this an appropriate maximum depth for detecting
      // circular references?
      int    msgID   = MSGID_SCHEMA_CIRCULAR_DEPENDENCY_REFERENCE;
      String message = getMessage(msgID, element.getDefinition());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                   message, msgID);
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
  public final Schema duplicate()
  {
    assert debugEnter(CLASS_NAME, "duplicate");

    Schema dupSchema = new Schema();

    dupSchema.attributeTypes.putAll(attributeTypes);
    dupSchema.objectClasses.putAll(objectClasses);
    dupSchema.syntaxes.putAll(syntaxes);
    dupSchema.matchingRules.putAll(matchingRules);
    dupSchema.approximateMatchingRules.putAll(
         approximateMatchingRules);
    dupSchema.equalityMatchingRules.putAll(equalityMatchingRules);
    dupSchema.orderingMatchingRules.putAll(orderingMatchingRules);
    dupSchema.substringMatchingRules.putAll(substringMatchingRules);
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

    return dupSchema;
  }
}

