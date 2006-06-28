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
 * This class defines a DIT structure rule, which is used to indicate
 * the types of children that entries may have.
 */
public class DITStructureRule
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.DITStructureRule";



  // Indicates whether this DIT structure rule is declared "obsolete".
  private boolean isObsolete;

  // The set of additional name-value pairs associated with this DIT
  // structure rule.
  private ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
               extraProperties;

  // The set of names for this DIT structure rule, in a mapping
  // between the all-lowercase form and the user-defined form.
  private ConcurrentHashMap<String,String> names;

  // The set of superior DIT structure rules.
  private CopyOnWriteArraySet<DITStructureRule> superiorRules;

  // The rule ID for this DIT structure rule.
  private int ruleID;

  // The name form for this DIT structure rule.
  private NameForm nameForm;

  // The description for this DIT structure rule.
  private String description;

  // The path to the schema file that contains this DIT structure rule
  // definition.
  private String schemaFile;



  /**
   * Creates a new DIT structure rule with the provided information.
   *
   * @param  names            The set of names for this DIT structure
   *                          rule, mapping the lowercase names to the
   *                          user-defined values.
   * @param  ruleID           The rule ID for this DIT structure rule.
   * @param  description      The description for this DIT structure
   *                          rule.
   * @param  isObsolete       Indicates whether this DIT structure
   *                          rule is declared "obsolete".
   * @param  nameForm         The name form for this DIT structure
   *                          rule.
   * @param  superiorRules    References to the superior rules for
   *                          this DIT structure rule.
   * @param  extraProperties  The set of "extra" properties associated
   *                          with this DIT structure rules.
   */
  public DITStructureRule(ConcurrentHashMap<String,String> names,
              int ruleID, String description,  boolean isObsolete,
              NameForm nameForm,
              CopyOnWriteArraySet<DITStructureRule> superiorRules,
              ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
                   extraProperties)
  {
    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(names),
                              String.valueOf(ruleID),
                              String.valueOf(description),
                              String.valueOf(isObsolete),
                              String.valueOf(nameForm),
                              String.valueOf(superiorRules),
                              String.valueOf(extraProperties)
                            });

    this.names           = names;
    this.ruleID          = ruleID;
    this.description     = description;
    this.isObsolete      = isObsolete;
    this.nameForm        = nameForm;
    this.superiorRules   = superiorRules;
    this.schemaFile      = null;
    this.extraProperties = extraProperties;
  }



  /**
   * Retrieves the set of names that may be used to reference this DIT
   * structure rule.  The returned mapping will be between an all
   * lower-case form of the name and a name in the user-defined form
   * (which may include mixed capitalization).
   *
   * @return  The set of names that may be used to reference this DIT
   *          structure rule.
   */
  public ConcurrentHashMap<String,String> getNames()
  {
    assert debugEnter(CLASS_NAME, "getNames");

    return names;
  }



  /**
   * Specifies the set of names that may be used to reference this DIT
   * structure rule.  The provided set must contain a mapping between
   * each name in all lowercase characters and the name in a
   * user-defined form (which may include mixed capitalization).
   *
   * @param  names  The set of names that may be used to reference
   *                this attribute type.
   */
  public void setNames(ConcurrentHashMap<String,String> names)
  {
    assert debugEnter(CLASS_NAME, "setNames", String.valueOf(names));

    this.names = names;
  }



  /**
   * Indicates whether this DIT structure rule has the specified name.
   *
   * @param  lowerName  The lowercase name for which to make the
   *                    determination.
   *
   * @return  <CODE>true</CODE> if the specified name is assigned to
   *          this DIT structure rule, or <CODE>false</CODE> if not.
   */
  public boolean hasName(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "hasName",
                      String.valueOf(lowerName));

    return names.containsKey(lowerName);
  }



  /**
   * Adds the specified name to the set of names for this DIT
   * structure rule.
   *
   * @param  name  The name to add to the set of names for this DIT
   *               structure rule.
   */
  public void addName(String name)
  {
    assert debugEnter(CLASS_NAME, "addName", String.valueOf(name));

    String lowerName = toLowerCase(name);
    names.put(lowerName, name);
  }



  /**
   * Removes the specified name from the set of names for this DIT
   * structure rule.  This will have no effect if the specified name
   * is not associated with this attribute type.
   *
   * @param  lowerName  The lowercase name to remove from the set of
   *                    names for this DIT structure rule.
   */
  public void removeName(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "removeName",
                      String.valueOf(lowerName));

    names.remove(lowerName);
  }



  /**
   * Retrieves the rule ID for this DIT structure rule.
   *
   * @return  The rule ID for this DIT structure rule.
   */
  public int getRuleID()
  {
    assert debugEnter(CLASS_NAME, "getRuleID");

    return ruleID;
  }



  /**
   * Specifies the rule ID for this DIT structure rule.
   *
   * @param  ruleID  The rule ID for this DIT structure rule.
   */
  public void setRuleID(int ruleID)
  {
    assert debugEnter(CLASS_NAME, "setRuleID",
                      String.valueOf(ruleID));

    this.ruleID = ruleID;
  }



  /**
   * Retrieves the name or rule ID for this DIT structure rule.  If it
   * has one or more names, then the primary name will be returned.
   * If it does not have any names, then the rule ID will be returned.
   *
   * @return  The name or rule ID for this DIT structure rule.
   */
  public String getNameOrRuleID()
  {
    assert debugEnter(CLASS_NAME, "getNameOrRuleID");

    if (names.isEmpty())
    {
      return String.valueOf(ruleID);
    }
    else
    {
      return names.values().iterator().next();
    }
  }



  /**
   * Indicates whether this DIT structure rule has the specified name
   * or rule ID.
   *
   * @param  lowerValue  The lowercase value for which to make the
   *                     determination.
   *
   * @return  <CODE>true</CODE> if the provided value matches the rule
   *          ID or one of the names assigned to this DIT structure
   *          rule, or <CODE>false</CODE> if not.
   */
  public boolean hasNameOrOID(String lowerValue)
  {
    assert debugEnter(CLASS_NAME, "hasNameOrOID",
                      String.valueOf(lowerValue));

    if (names.containsKey(lowerValue))
    {
      return true;
    }

    return lowerValue.equals(String.valueOf(ruleID));
  }



  /**
   * Retrieves the path to the schema file that contains the
   * definition for this DIT structure rule.
   *
   * @return  The path to the schema file that contains the definition
   *          for this DIT structure rule, or <CODE>null</CODE> if it
   *          is not known or if it is not stored in any schema file.
   */
  public String getSchemaFile()
  {
    assert debugEnter(CLASS_NAME, "getSchemaFile");

    return schemaFile;
  }



  /**
   * Specifies the path to the schema file that contains the
   * definition for this DIT structure rule.
   *
   * @param  schemaFile  The path to the schema file that contains the
   *                     definition for this DIT structure rule.
   */
  public void setSchemaFile(String schemaFile)
  {
    assert debugEnter(CLASS_NAME, "setSchemaFile",
                      String.valueOf(schemaFile));

    this.schemaFile = schemaFile;
  }



  /**
   * Retrieves the description for this DIT structure rule.
   *
   * @return  The description for this DIT structure rule.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    return description;
  }



  /**
   * Specifies the description for this DIT structure rule.
   *
   * @param  description  The description for this DIT structure rule.
   */
  public void setDescription(String description)
  {
    assert debugEnter(CLASS_NAME, "setDescription", description);

    this.description = description;
  }



  /**
   * Retrieves the name form for this DIT structure rule.
   *
   * @return  The name form for this DIT structure rule.
   */
  public NameForm getNameForm()
  {
    assert debugEnter(CLASS_NAME, "getNameForm");

    return nameForm;
  }



  /**
   * Specifies the name form for this DIT structure rule.
   *
   * @param  nameForm  The name form for this DIT structure rule.
   */
  public void setNameForm(NameForm nameForm)
  {
    assert debugEnter(CLASS_NAME, "setNameForm",
                      String.valueOf(nameForm));

    this.nameForm = nameForm;
  }



  /**
   * Retrieves the structural objectclass for the name form with which
   * this DIT structure rule is associated.
   *
   * @return  The structural objectclass for the name form with which
   *          this DIT structure rule is associated.
   */
  public ObjectClass getStructuralClass()
  {
    assert debugEnter(CLASS_NAME, "getStructuralObjectClass");

    return nameForm.getStructuralClass();
  }



  /**
   * Retrieves the set of superior rules for this DIT structure rule.
   *
   * @return  The set of superior rules for this DIT structure rule.
   */
  public CopyOnWriteArraySet<DITStructureRule> getSuperiorRules()
  {
    assert debugEnter(CLASS_NAME, "getSuperiorRules");

    return superiorRules;
  }



  /**
   * Indicates whether this DIT structure rule has one or more
   * superior rules.
   *
   * @return  <CODE>true</CODE> if this DIT structure rule has one or
   *          more superior rules, or <CODE>false</CODE> if not.
   */
  public boolean hasSuperiorRules()
  {
    assert debugEnter(CLASS_NAME, "hasSuperiorRules");

    return ((superiorRules != null) && (! superiorRules.isEmpty()));
  }



  /**
   * Specifies the set of superior rules for this DIT structure rule.
   *
   * @param  superiorRules  The set of superior rules for this DIT
   *                        structure rule.
   */
  public void setSuperiorRules(CopyOnWriteArraySet<DITStructureRule>
                                    superiorRules)
  {
    assert debugEnter(CLASS_NAME, "setSuperiorRules",
                      String.valueOf(superiorRules));

    this.superiorRules = superiorRules;
  }



  /**
   * Adds the provided rule as a superior rule for this DIT structure
   * rule.
   *
   * @param  superiorRule  The superior rule to add to this DIT
   *                       structure rule.
   */
  public void addSuperiorRule(DITStructureRule superiorRule)
  {
    assert debugEnter(CLASS_NAME, "addSuperiorRule",
                      String.valueOf(superiorRule));

    superiorRules.add(superiorRule);
  }



  /**
   * Removes the provided rule as a superior rule for this DIT
   * structure rule.  It will have no effect if the provided rule is
   * not a superior rule for this DIT structure rule.
   *
   * @param  superiorRule  The superior rule to remove from this DIT
   *                       structure rule.
   */
  public void removeSuperiorRule(DITStructureRule superiorRule)
  {
    assert debugEnter(CLASS_NAME, "removeSuperiorRule",
                      String.valueOf(superiorRule));

    superiorRules.remove(superiorRule);
  }



  /**
   * Indicates whether this DIT structure rule is declared "obsolete".
   *
   * @return  <CODE>true</CODE> if this DIT structure rule is declared
   *          "obsolete", or <CODE>false</CODE> if not.
   */
  public boolean isObsolete()
  {
    assert debugEnter(CLASS_NAME, "isObsolete");

    return isObsolete;
  }



  /**
   * Specifies whether this DIT structure rule is declared "obsolete".
   *
   * @param  isObsolete  Specifies whether this DIT structure rule is
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
   * properties that may be associated with this DIT structure rule
   * and the value for that property.  The caller may alter the
   * contents of this mapping.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this DIT
   *          structure rule and the value for that property.
   */
  public ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
              getExtraProperties()
  {
    assert debugEnter(CLASS_NAME, "getExtraProperties");

    return extraProperties;
  }



  /**
   * Retrieves the value of the specified "extra" property for this
   * DIT structure rule.
   *
   * @param  propertyName  The name of the "extra" property for which
   *                       to retrieve the value.
   *
   * @return  The value of the specified "extra" property for this DIT
   *          structure rule, or <CODE>null</CODE> if no such property
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
   * structure rule.  The object will be considered equal if it is a
   * DIT structure rule with the same OID as the current type.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is equal to
   *          this attribute, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object o)
  {
    assert debugEnter(CLASS_NAME, "equals");

    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof DITStructureRule)))
    {
      return false;
    }

    return (ruleID == ((DITStructureRule) o).ruleID);
  }



  /**
   * Retrieves the hash code for this DIT structure rule.  It will be
   * equal to the rule ID.
   *
   * @return  The hash code for this DIT structure rule.
   */
  public int hashCode()
  {
    assert debugEnter(CLASS_NAME, "hashCode");

    return ruleID;
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
   *                             this DIT structure rule was loaded.
   */
  public void toString(StringBuilder buffer,
                       boolean includeFileElement)
  {
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder",
                      String.valueOf(includeFileElement));

    buffer.append("( ");
    buffer.append(ruleID);

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

    buffer.append(" FORM ");
    buffer.append(nameForm.getNameOrOID());

    if ((superiorRules != null) && (! superiorRules.isEmpty()))
    {
      Iterator<DITStructureRule> iterator = superiorRules.iterator();

      int firstRule = iterator.next().getRuleID();
      if (iterator.hasNext())
      {
        buffer.append(" SUP ( ");
        buffer.append(firstRule);

        while (iterator.hasNext())
        {
          buffer.append(" ");
          buffer.append(iterator.next().getRuleID());
        }

        buffer.append(" )");
      }
      else
      {
        buffer.append(" SUP ");
        buffer.append(firstRule);
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

