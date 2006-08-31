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

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure for storing and interacting
 * with an attribute type, which contains information about the format
 * of an attribute and the syntax and matching rules that should be
 * used when interacting with it.
 */
public class AttributeType
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.AttributeType";



  // The approximate matching rule for this attribute type.
  private ApproximateMatchingRule approximateMatchingRule;

  // The syntax for this attribute type.
  private AttributeSyntax syntax;

  // The superior attribute type from which this attribute type
  // inherits.
  private AttributeType superiorType;

  // The attribute usage for this attribute type.
  private AttributeUsage attributeUsage;

  // Indicates whether this attribute type is declared "collective".
  private boolean isCollective;

  // Indicates whether this attribute type is declared
  // "no-user-modification".
  private boolean isNoUserModification;

  // Indicates whether this attribute type is the objectclass type.
  private boolean isObjectClassType;

  // Indicates whether this attribute type is declared "obsolete".
  private boolean isObsolete;

  // Indicates whether this attribute type is declared "single-value".
  private boolean isSingleValue;

  // The set of additional name-value pairs associated with this
  // attribute type definition.
  private ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
               extraProperties;

  // The set of names for this attribute type, in a mapping between
  // the all-lowercase form and the user-defined form.
  private ConcurrentHashMap<String,String> typeNames;

  // The equality matching rule for this attribute type.
  private EqualityMatchingRule equalityMatchingRule;

  // The ordering matching rule for this attribute type.
  private OrderingMatchingRule orderingMatchingRule;

  // The description for this attribute type.
  private String description;

  // The OID that may be used to reference this attribute type.
  private String oid;

  // The primary name to use for this attribute type.
  private String primaryName;

  // The lower case name for this attribute type.
  private String lowerName;

  // The path to the schema configuration file that contains this
  // attribute type definition.
  private String schemaFile;

  // The substring matching rule for this attribute type.
  private SubstringMatchingRule substringMatchingRule;



  /**
   * Creates a new attribute type with the provided information.
   *
   * @param  primaryName           The primary name for this attribute
   *                               type.
   * @param  typeNames             The full set of names for this
   *                               attribute type, mapping the
   *                               lowercase names with the
   *                               user-defined values.
   * @param  oid                   The OID for this attribute type.
   * @param  description           The description for the attribute
   *                               type.
   * @param  superiorType          The reference to the superior type
   *                               for this attribute type.
   * @param  syntax                The syntax for this attribute type.
   * @param  attributeUsage        The attribute usage for this
   *                               attribute type.
   * @param  isCollective          Indicates whether this attribute
   *                               type is declared "collective".
   * @param  isNoUserModification  Indicates whether this attribute
   *                               type is declared
   *                               "no-user-modification".
   * @param  isObsolete            Indicates whether this attribute
   *                               type is declared "obsolete".
   * @param  isSingleValue         Indicates whether this attribute
   *                               type is declared "single-value".
   */
  public AttributeType(String primaryName,
                       ConcurrentHashMap<String,String> typeNames,
                       String oid, String description,
                       AttributeType superiorType,
                       AttributeSyntax syntax,
                       AttributeUsage attributeUsage,
                       boolean isCollective,
                       boolean isNoUserModification,
                       boolean isObsolete, boolean isSingleValue)
  {
    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(primaryName),
                              String.valueOf(typeNames),
                              String.valueOf(oid),
                              String.valueOf(description),
                              String.valueOf(superiorType),
                              String.valueOf(syntax),
                              String.valueOf(attributeUsage),
                              String.valueOf(isCollective),
                              String.valueOf(isNoUserModification),
                              String.valueOf(isObsolete),
                              String.valueOf(isSingleValue),
                            });


    this.primaryName             = primaryName;
    this.lowerName               = toLowerCase(primaryName);
    this.typeNames               = typeNames;
    this.oid                     = oid;
    this.description             = description;
    this.superiorType            = superiorType;
    this.syntax                  = syntax;
    this.approximateMatchingRule =
         syntax.getApproximateMatchingRule();
    this.equalityMatchingRule    = syntax.getEqualityMatchingRule();
    this.orderingMatchingRule    = syntax.getOrderingMatchingRule();
    this.substringMatchingRule   = syntax.getSubstringMatchingRule();
    this.attributeUsage          = attributeUsage;
    this.isCollective            = isCollective;
    this.isNoUserModification    = isNoUserModification;
    this.isObsolete              = isObsolete;
    this.isSingleValue           = isSingleValue;
    this.schemaFile              = null;
    this.extraProperties         =
         new ConcurrentHashMap<String,
                               CopyOnWriteArrayList<String>>(0);

    isObjectClassType = false;
    if ((oid != null) && oid.equals(OBJECTCLASS_ATTRIBUTE_TYPE_OID))
    {
      isObjectClassType = true;
    }
    else
    {
      for (String lowerName : typeNames.keySet())
      {
        if (lowerName.equals(OBJECTCLASS_ATTRIBUTE_TYPE_NAME))
        {
          isObjectClassType = true;
        }
      }
    }
  }



  /**
   * Creates a new attribute type with the provided information.
   *
   * @param  primaryName              The primary name for this
   *                                  attribute type.
   * @param  typeNames                The full set of names for this
   *                                  attribute type, mapping the
   *                                  lowercase names with the
   *                                  user-defined values.
   * @param  oid                      The OID for this attribute type.
   * @param  description              The description for the
   *                                  attribute type.
   * @param  superiorType             The reference to the superior
   *                                  type for this attribute type.
   * @param  syntax                   The syntax for this attribute
   *                                  type.
   * @param  approximateMatchingRule  The approximate matching rule
   *                                  for this attribute type.
   * @param  equalityMatchingRule     The equality matching rule for
   *                                  this attribute type.
   * @param  orderingMatchingRule     The ordering matching rule for
   *                                  this attribute type.
   * @param  substringMatchingRule    The substring matching rule for
   *                                  this attribute type.
   * @param  attributeUsage           The attribute usage for this
   *                                  attribute type.
   * @param  isCollective             Indicates whether this attribute
   *                                  type is declared "collective".
   * @param  isNoUserModification     Indicates whether this attribute
   *                                  type is declared
   *                                  "no-user-modification".
   * @param  isObsolete               Indicates whether this attribute
   *                                  type is declared "obsolete".
   * @param  isSingleValue            Indicates whether this attribute
   *                                  type is declared "single-value".
   * @param  extraProperties          A set of extra properties for
   *                                  this attribute type.
   */
  public AttributeType(String primaryName,
                       ConcurrentHashMap<String,String> typeNames,
                       String oid, String description,
                       AttributeType superiorType,
                       AttributeSyntax syntax,
                       ApproximateMatchingRule
                            approximateMatchingRule,
                       EqualityMatchingRule equalityMatchingRule,
                       OrderingMatchingRule orderingMatchingRule,
                       SubstringMatchingRule substringMatchingRule,
                       AttributeUsage attributeUsage,
                       boolean isCollective,
                       boolean isNoUserModification,
                       boolean isObsolete, boolean isSingleValue,
                       ConcurrentHashMap<String,
                                         CopyOnWriteArrayList<String>>
                            extraProperties)
  {
    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(primaryName),
                              String.valueOf(typeNames),
                              String.valueOf(oid),
                              String.valueOf(description),
                              String.valueOf(superiorType),
                              String.valueOf(syntax),
                              String.valueOf(approximateMatchingRule),
                              String.valueOf(equalityMatchingRule),
                              String.valueOf(orderingMatchingRule),
                              String.valueOf(substringMatchingRule),
                              String.valueOf(attributeUsage),
                              String.valueOf(isCollective),
                              String.valueOf(isNoUserModification),
                              String.valueOf(isObsolete),
                              String.valueOf(isSingleValue),
                              String.valueOf(extraProperties)
                            });


    this.primaryName             = primaryName;
    this.lowerName               = toLowerCase(primaryName);
    this.typeNames               = typeNames;
    this.oid                     = oid;
    this.description             = description;
    this.superiorType            = superiorType;
    this.isCollective            = isCollective;
    this.isNoUserModification    = isNoUserModification;
    this.isObsolete              = isObsolete;
    this.isSingleValue           = isSingleValue;
    this.extraProperties         = extraProperties;
    this.schemaFile              = null;


    if (attributeUsage == null)
    {
      this.attributeUsage = AttributeUsage.USER_APPLICATIONS;
    }
    else
    {
      this.attributeUsage = attributeUsage;
    }


    if (syntax == null)
    {
      if (superiorType != null)
      {
        this.syntax = superiorType.getSyntax();
      }
      else
      {
        syntax = DirectoryServer.getDefaultAttributeSyntax();
      }
    }
    else
    {
      this.syntax = syntax;
    }


    if (approximateMatchingRule == null)
    {
      if (syntax != null)
      {
        this.approximateMatchingRule =
             syntax.getApproximateMatchingRule();
      }
    }
    else
    {
      this.approximateMatchingRule = approximateMatchingRule;
    }


    if (equalityMatchingRule == null)
    {
      if (syntax != null)
      {
        this.equalityMatchingRule = syntax.getEqualityMatchingRule();
      }
    }
    else
    {
      this.equalityMatchingRule = equalityMatchingRule;
    }


    if (orderingMatchingRule == null)
    {
      if (syntax != null)
      {
        this.orderingMatchingRule = syntax.getOrderingMatchingRule();
      }
    }
    else
    {
      this.orderingMatchingRule = orderingMatchingRule;
    }


    if (substringMatchingRule == null)
    {
      if (syntax != null)
      {
        this.substringMatchingRule =
             syntax.getSubstringMatchingRule();
      }
    }
    else
    {
      this.substringMatchingRule = substringMatchingRule;
    }

    isObjectClassType = false;
    if ((oid != null) && oid.equals(OBJECTCLASS_ATTRIBUTE_TYPE_OID))
    {
      isObjectClassType = true;
    }
    else
    {
      for (String lowerName : typeNames.keySet())
      {
        if (lowerName.equals(OBJECTCLASS_ATTRIBUTE_TYPE_NAME))
        {
          isObjectClassType = true;
        }
      }
    }
  }



  /**
   * Retrieves the primary name for this attribute type.
   *
   * @return  The primary name for this attribute type.
   */
  public String getPrimaryName()
  {
    assert debugEnter(CLASS_NAME, "getPrimaryName");

    return primaryName;
  }



  /**
   * Retrieve the lower case name for this attribute type.
   *
   * @return The lower case name for this attribute type.
   */
  public String getLowerName()
  {
    return lowerName;
  }



  /**
   * Specifies the primary name for this attribute type.
   *
   * @param  primaryName  The primary name for this attribute type.
   */
  public void setPrimaryName(String primaryName)
  {
    assert debugEnter(CLASS_NAME, "setPrimaryName",
                      String.valueOf(primaryName));

    this.primaryName = primaryName;

    this.lowerName = toLowerCase(primaryName);
    typeNames.put(this.lowerName, primaryName);
  }



  /**
   * Retrieves the set of names that may be used to reference this
   * attribute type.  The returned mapping will be between an all
   * lower-case form of the name and a name in the user-defined form
   * (which may include mixed capitalization).
   *
   * @return  The set of names that may be used to reference this
   *          attribute type.
   */
  public ConcurrentHashMap<String,String> getNames()
  {
    assert debugEnter(CLASS_NAME, "getNames");

    return typeNames;
  }



  /**
   * Specifies the set of names that may be used to reference this
   * attribute type.  The provided set must contain a mapping between
   * each name in all lowercase characters and the name in a
   * user-defined form (which may include mixed capitalization).
   *
   * @param  names  The set of names that may be used to reference
   *                this attribute type.
   */
  public void setNames(ConcurrentHashMap<String,String> names)
  {
    assert debugEnter(CLASS_NAME, "setNames", String.valueOf(names));

    this.typeNames = names;
  }



  /**
   * Indicates whether this attribute type has the specified name.
   *
   * @param  lowerName  The lowercase name for which to make the
   *                    determination.
   *
   * @return  <CODE>true</CODE> if the specified name is assigned to
   *          this attribute type, or <CODE>false</CODE> if not.
   */
  public boolean hasName(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "hasName",
                      String.valueOf(lowerName));

    return typeNames.containsKey(lowerName);
  }



  /**
   * Adds the specified name to the set of names for this attribute
   * type.
   *
   * @param  name  The name to add to the set of names for this
   *               attribute type.
   */
  public void addName(String name)
  {
    assert debugEnter(CLASS_NAME, "addName", String.valueOf(name));

    String lowerName = toLowerCase(name);
    typeNames.put(lowerName, name);
  }



  /**
   * Removes the specified name from the set of names for this
   * attribute type.  This will have no effect if the specified name
   * is not associated with this attribute type.
   *
   * @param  lowerName  The lowercase name to remove from the set of
   *                    names for this attribute type.
   */
  public void removeName(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "removeName",
                      String.valueOf(lowerName));

    typeNames.remove(lowerName);
    if (lowerName.equalsIgnoreCase(primaryName))
    {
      if (typeNames.isEmpty())
      {
        primaryName = null;
      }
      else
      {
        primaryName = typeNames.values().iterator().next();
      }
    }
  }



  /**
   * Retrieves the OID for this attribute type.
   *
   * @return  The OID for this attribute type.
   */
  public String getOID()
  {
    assert debugEnter(CLASS_NAME, "getOID");

    return oid;
  }



  /**
   * Specifies the OID for this attribute type.
   *
   * @param  oid  The OID for this attribute type.
   */
  public void setOID(String oid)
  {
    assert debugEnter(CLASS_NAME, "setOID", String.valueOf(oid));

    this.oid = toLowerCase(oid);
  }



  /**
   * Retrieves the name or OID for this attribute type.  If it has one
   * or more names, then the primary name will be returned.  If it
   * does not have any names, then the OID will be returned.
   *
   * @return  The name or OID for this attribute type.
   */
  public String getNameOrOID()
  {
    assert debugEnter(CLASS_NAME, "getNameOrOID");

    if (primaryName != null)
    {
      return primaryName;
    }

    if (typeNames.isEmpty())
    {
      return oid;
    }
    else
    {
      return typeNames.values().iterator().next();
    }
  }



  /**
   * Indicates whether this attribute type has the specified name or
   * OID.
   *
   * @param  lowerValue  The lowercase value for which to make the
   *                     determination.
   *
   * @return  <CODE>true</CODE> if the provided value matches the OID
   *          or one of the names assigned to this attribute type, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hasNameOrOID(String lowerValue)
  {
    assert debugEnter(CLASS_NAME, "hasNameOrOID",
                      String.valueOf(lowerValue));

    if (typeNames.containsKey(lowerValue))
    {
      return true;
    }

    return oid.equals(lowerValue);
  }



  /**
   * Retrieves the path to the schema file that contains the
   * definition for this attribute type.
   *
   * @return  The path to the schema file that contains the definition
   *          for this attribute type, or <CODE>null</CODE> if it is
   *          not known or if it is not stored in any schema file.
   */
  public String getSchemaFile()
  {
    assert debugEnter(CLASS_NAME, "getSchemaFile");

    return schemaFile;
  }



  /**
   * Specifies the path to the schema file that contains the
   * definition for this attribute type.
   *
   * @param  schemaFile  The path to the schema file that contains the
   *                     definition for this attribute type.
   */
  public void setSchemaFile(String schemaFile)
  {
    assert debugEnter(CLASS_NAME, "setSchemaFile",
                      String.valueOf(schemaFile));

    this.schemaFile = schemaFile;
  }



  /**
   * Retrieves the description for this attribute type.
   *
   * @return  The description for this attribute type.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    return description;
  }



  /**
   * Specifies the description for this attribute type.
   *
   * @param  description  The description for this attribute type.
   */
  public void setDescription(String description)
  {
    assert debugEnter(CLASS_NAME, "setDescription", description);

    this.description = description;
  }



  /**
   * Retrieves the superior type for this attribute type.
   *
   * @return  The superior type for this attribute type, or
   *          <CODE>null</CODE> if it does not have one.
   */
  public AttributeType getSuperiorType()
  {
    assert debugEnter(CLASS_NAME, "getSuperiorType");

    return superiorType;
  }



  /**
   * Specifies the superior type to use for this attribute type.  It
   * may be <CODE>null</CODE> if it should not have a superior type.
   *
   * @param  superiorType  The superior type for this attribute type.
   */
  public void setSuperiorType(AttributeType superiorType)
  {
    assert debugEnter(CLASS_NAME, "setSuperiorType",
                      String.valueOf(superiorType));

    this.superiorType = superiorType;
  }



  /**
   * Retrieves the syntax for this attribute type.
   *
   * @return  The syntax for this attribute type.
   */
  public AttributeSyntax getSyntax()
  {
    assert debugEnter(CLASS_NAME, "getSyntax");

    return syntax;
  }



  /**
   * Retrieves the OID for this syntax associated with this attribute
   * type.
   *
   * @return  The OID for this syntax associated with this attribute
   *          type.
   */
  public String getSyntaxOID()
  {
    assert debugEnter(CLASS_NAME, "getSyntaxOID");

    return syntax.getOID();
  }



  /**
   * Specifies the syntax for this attribute type.
   *
   * @param  syntax  The syntax for this attribute type.
   */
  public void setSyntax(AttributeSyntax syntax)
  {
    assert debugEnter(CLASS_NAME, "setSyntax",
                      String.valueOf(syntax));

    this.syntax = syntax;
  }



  /**
   * Retrieves the matching rule that should be used for approximate
   * matching with this attribute type.
   *
   * @return  The matching rule that should be used for approximate
   *          matching with this attribute type.
   */
  public ApproximateMatchingRule getApproximateMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getApproximateMatchingRule");

    return approximateMatchingRule;
  }



  /**
   * Specifies the matching rule that should be used for approximate
   * matching with this attribute type.
   *
   * @param  approximateMatchingRule  The matching rule that should be
   *                                  used for approximate matching
   *                                  with this attribute type.
   */
  public void setApproximateMatchingRule(ApproximateMatchingRule
                                              approximateMatchingRule)
  {
    assert debugEnter(CLASS_NAME, "setApproximateMatchingRule",
                      String.valueOf(approximateMatchingRule));

    this.approximateMatchingRule = approximateMatchingRule;
  }



  /**
   * Retrieves the matching rule that should be used for equality
   * matching with this attribute type.
   *
   * @return  The matching rule that should be used for equality
   *          matching with this attribute type.
   */
  public EqualityMatchingRule getEqualityMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getEqualityMatchingRule");

    return equalityMatchingRule;
  }



  /**
   * Specifies the matching rule that should be used for equality
   * matching with this attribute type.
   *
   * @param  equalityMatchingRule  The matching rule that should be
   *                               used for equality matching with
   *                               this attribute type.
   */
  public void setEqualityMatchingRule(EqualityMatchingRule
                                           equalityMatchingRule)
  {
    assert debugEnter(CLASS_NAME, "setEqualityMatchingRule",
                      String.valueOf(equalityMatchingRule));

    this.equalityMatchingRule = equalityMatchingRule;
  }



  /**
   * Retrieves the matching rule that should be used for ordering with
   * this attribute type.
   *
   * @return  The matching rule that should be used for ordering with
   *          this attribute type.
   */
  public OrderingMatchingRule getOrderingMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getOrderingMatchingRule");

    return orderingMatchingRule;
  }



  /**
   * Specifies the matching rule that should be used for ordering with
   * this attribute type.
   *
   * @param  orderingMatchingRule  The matching rule that should be
   *                               used for ordering with this
   *                               attribute type.
   */
  public void setOrderingMatchingRule(OrderingMatchingRule
                                           orderingMatchingRule)
  {
    assert debugEnter(CLASS_NAME, "setOrderingMatchingRule",
                      String.valueOf(orderingMatchingRule));

    this.orderingMatchingRule = orderingMatchingRule;
  }



  /**
   * Retrieves the matching rule that should be used for substring
   * matching with this attribute type.
   *
   * @return  The matching rule that should be used for substring
   *          matching with this attribute type.
   */
  public SubstringMatchingRule getSubstringMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getSubstringMatchingRule");

    return substringMatchingRule;
  }



  /**
   * Specifies the matching rule that should be used for substring
   * matching with this attribute type.
   *
   * @param  substringMatchingRule  The matching rule that should be
   *                                used for substring matching with
   *                                this attribute type.
   */
  public void setSubstringMatchingRule(SubstringMatchingRule
                                            substringMatchingRule)
  {
    assert debugEnter(CLASS_NAME, "setSubstringMatchingRule",
                      String.valueOf(substringMatchingRule));

    this.substringMatchingRule = substringMatchingRule;
  }



  /**
   * Retrieves the usage indicator for this attribute type.
   *
   * @return  The usage indicator for this attribute type.
   */
  public AttributeUsage getUsage()
  {
    assert debugEnter(CLASS_NAME, "getUsage");

    return attributeUsage;
  }



  /**
   * Specifies the usage indicator for this attribute type.
   *
   * @param  attributeUsage  The usage indicator for this attribute
   *                         type.
   */
  public void setUsage(AttributeUsage attributeUsage)
  {
    assert debugEnter(CLASS_NAME, "setUsage",
                      String.valueOf(attributeUsage));

    this.attributeUsage = attributeUsage;
  }



  /**
   * Indicates whether this is an operational attribute.  An
   * operational attribute is one with a usage of
   * "directoryOperation", "distributedOperation", or "dSAOperation"
   * (i.e., only userApplications is not operational).
   *
   * @return  <CODE>true</CODE> if this is an operational attribute,
   *          or <CODE>false</CODE> if not.
   */
  public boolean isOperational()
  {
    assert debugEnter(CLASS_NAME, "isOperational");

    switch (attributeUsage)
    {
      case USER_APPLICATIONS:
        return false;
      case DIRECTORY_OPERATION:
      case DISTRIBUTED_OPERATION:
      case DSA_OPERATION:
        return true;
    }

    // We should never get here.  If we do, assume it's a user-defined
    // attribute.
    logError(ErrorLogCategory.CORE_SERVER,
             ErrorLogSeverity.SEVERE_WARNING,
             MSGID_UNKNOWN_ATTRIBUTE_USAGE, getNameOrOID());
    return false;
  }



  /**
   * Indicates whether this attribute type is declared "collective".
   *
   * @return  <CODE>true</CODE> if this attribute type is declared
   * "collective", or <CODE>false</CODE> if not.
   */
  public boolean isCollective()
  {
    assert debugEnter(CLASS_NAME, "isCollective");

    return isCollective;
  }



  /**
   * Specifies whether this attribute type is declared "collective".
   *
   * @param  isCollective  Specifies whether this attribute type is
   *                       declared "collective".
   */
  public void setCollective(boolean isCollective)
  {
    assert debugEnter(CLASS_NAME, "setCollective",
                      String.valueOf(isCollective));

    this.isCollective = isCollective;
  }



  /**
   * Indicates whether this attribute type is declared
   * "no-user-modification".
   *
   * @return  <CODE>true</CODE> if this attribute type is declared
   *          "no-user-modification", or <CODE>false</CODE> if not.
   */
  public boolean isNoUserModification()
  {
    assert debugEnter(CLASS_NAME, "isNoUserModification");

    return isNoUserModification;
  }



  /**
   * Specifies whether this attribute type is declared
   * "no-user-modification".
   *
   * @param  isNoUserModification  Specifies whether this attribute
   *                               type is declared
   *                               "no-user-modification".
   */
  public void setNoUserModification(boolean isNoUserModification)
  {
    assert debugEnter(CLASS_NAME, "setNoUserModification",
                      String.valueOf(isNoUserModification));

    this.isNoUserModification = isNoUserModification;
  }



  /**
   * Indicates whether this attribute type is declared "obsolete".
   *
   * @return  <CODE>true</CODE> if this attribute type is declared
   *          "obsolete", or <CODE>false</CODE> if not.
   */
  public boolean isObsolete()
  {
    assert debugEnter(CLASS_NAME, "isObsolete");

    return isObsolete;
  }



  /**
   * Specifies whether this attribute type is declared "obsolete".
   *
   * @param  isObsolete  Specifies whether this attribute type is
   *                     declared "obsolete".
   */
  public void setObsolete(boolean isObsolete)
  {
    assert debugEnter(CLASS_NAME, "setObsolete",
                      String.valueOf(isObsolete));

    this.isObsolete = isObsolete;
  }



  /**
   * Indicates whether this attribute type is declared "single-value".
   *
   * @return  <CODE>true</CODE> if this attribute type is declared
   *          "single-value", or <CODE>false</CODE> if not.
   */
  public boolean isSingleValue()
  {
    assert debugEnter(CLASS_NAME, "isSingleValue");

    return isSingleValue;
  }



  /**
   * Specifies whether this attribute type is declared "single-value".
   *
   * @param  isSingleValue  Specifies whether this attribute type is
   *                        declared "single-value".
   */
  public void setSingleValue(boolean isSingleValue)
  {
    assert debugEnter(CLASS_NAME, "setSingleValue",
                      String.valueOf(isSingleValue));

    this.isSingleValue = isSingleValue;
  }



  /**
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this attribute type and
   * the value for that property.  The caller may alter the contents
   * of this mapping.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this attribute
   *          type and the value for that property.
   */
  public ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
              getExtraProperties()
  {
    assert debugEnter(CLASS_NAME, "getExtraProperties");

    return extraProperties;
  }



  /**
   * Retrieves the value of the specified "extra" property for this
   * attribute type.
   *
   * @param  propertyName  The name of the "extra" property for which
   *                       to retrieve the value.
   *
   * @return  The value of the specified "extra" property for this
   *          attribute type, or <CODE>null</CODE> if no such property
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
   * Indicates whether this attribute type represents the
   * "objectclass" attribute.  The determination will be made based on
   * the name and/or OID.
   *
   * @return  <CODE>true</CODE> if this attribute type is the
   *          objectclass type, or <CODE>false</CODE> if not.
   */
  public boolean isObjectClassType()
  {
    assert debugEnter(CLASS_NAME, "isObjectClassType");

    return isObjectClassType;
  }



  /**
   * Attempts to normalize the provided value using the equality
   * matching rule associated with this attribute type.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized form of the provided value.
   *
   * @throws  DirectoryException  If this attribute type does not have
   *                              an equality matching rule, or if the
   *                              provided value could not be
   *                              normalized.
   */
  public ByteString normalize(ByteString value)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "normalize", String.valueOf(value));

    if (equalityMatchingRule == null)
    {
      int    msgID   = MSGID_ATTR_TYPE_NORMALIZE_NO_MR;
      String message = getMessage(msgID, String.valueOf(value),
                                  getNameOrOID());
      throw new DirectoryException(ResultCode.INAPPROPRIATE_MATCHING,
                                   message, msgID);
    }

    return equalityMatchingRule.normalizeValue(value);
  }



  /**
   * Indicates whether the provided object is equal to this attribute
   * type.  The object will be considered equal if it is an attribute
   * type with the same OID as the current type.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is equal to
   *          this attribute type, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object o)
  {
    assert debugEnter(CLASS_NAME, "equals");

    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof AttributeType)))
    {
      return false;
    }

    return oid.equals(((AttributeType) o).oid);
  }



  /**
   * Retrieves the hash code for this attribute type.  It will be
   * based on the sum of the bytes of the OID.
   *
   * @return  The hash code for this attribute type.
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
   * Generates a hash code for the specified attribute value.  If an
   * equality matching rule is defined for this type, then it will be
   * used to generate the hash code.  If the value does not have an
   * equality matching rule but does have a normalized form, then that
   * will be used to obtain the hash code.  Otherwise, it will simply
   * be the hash code of the provided value.
   *
   * @param  value  The attribute value for which to generate the hash
   *                code.
   *
   * @return  The generated hash code for the provided value.
   */
  public int generateHashCode(AttributeValue value)
  {
    assert debugEnter(CLASS_NAME, "generateHashCode",
                      String.valueOf(value));

    try
    {
      if (equalityMatchingRule == null)
      {
        ByteString normalizedValue = value.getNormalizedValue();
        if (normalizedValue == null)
        {
          return value.getValue().hashCode();
        }
        else
        {
          return normalizedValue.hashCode();
        }
      }
      else
      {
        return equalityMatchingRule.generateHashCode(value);
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "generateHashCode", e);

      try
      {
        return value.getValue().hashCode();
      }
      catch (Exception e2)
      {
        assert debugException(CLASS_NAME, "generateHashCode", e2);

        return 0;
      }
    }
  }



  /**
   * Retrieves the string representation of this attribute type in the
   * form specified in RFC 2252.
   *
   * @return  The string representation of this attribute type in the
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
   * Appends a string representation of this attribute type in the
   * form specified in RFC 2252 to the provided buffer.
   *
   * @param  buffer              The buffer to which the information
   *                             should be appended.
   * @param  includeFileElement  Indicates whether to include an
   *                             "extra" property that specifies the
   *                             path to the schema file from which
   *                             this attribute type was loaded.
   */
  public void toString(StringBuilder buffer,
                       boolean includeFileElement)
  {
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder",
                      String.valueOf(includeFileElement));

    buffer.append("( ");
    buffer.append(oid);

    if (! typeNames.isEmpty())
    {
      Iterator<String> iterator = typeNames.values().iterator();

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

    if (superiorType != null)
    {
      buffer.append(" SUP ");
      buffer.append(superiorType.getNameOrOID());
    }

    if (equalityMatchingRule != null)
    {
      buffer.append(" EQUALITY ");
      buffer.append(equalityMatchingRule.getNameOrOID());
    }

    if (orderingMatchingRule != null)
    {
      buffer.append(" ORDERING ");
      buffer.append(orderingMatchingRule.getNameOrOID());
    }

    if (substringMatchingRule != null)
    {
      buffer.append(" SUBSTR ");
      buffer.append(substringMatchingRule.getNameOrOID());
    }

    // NOTE -- We will not include any approximate matching rule
    // information here because it would break the standard and
    // anything that depends on it.
    // FIXME -- Should we encode this into one of the "extra"
    // properties?

    if (syntax != null)
    {
      buffer.append(" SYNTAX ");
      buffer.append(syntax.getOID());
    }

    if (isSingleValue)
    {
      buffer.append(" SINGLE-VALUE");
    }

    if (isCollective)
    {
      buffer.append(" COLLECTIVE");
    }

    if (isNoUserModification)
    {
      buffer.append(" NO-USER-MODIFICATION");
    }

    if (attributeUsage != null)
    {
      buffer.append(" USAGE ");
      buffer.append(attributeUsage.toString());
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

