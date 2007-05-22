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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.server.schema.DITStructureRuleSyntax;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.*;



/**
 * This class defines a DIT structure rule, which is used to indicate
 * the types of children that entries may have.
 */
public final class DITStructureRule
       implements SchemaFileElement
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // Indicates whether this DIT structure rule is declared "obsolete".
  private final boolean isObsolete;

  // The rule ID for this DIT structure rule.
  private final int ruleID;

  // The name form for this DIT structure rule.
  private final NameForm nameForm;

  // The set of additional name-value pairs associated with this DIT
  // structure rule.
  private final Map<String,List<String>> extraProperties;

  // The set of names for this DIT structure rule, in a mapping
  // between the all-lowercase form and the user-defined form.
  private final Map<String,String> names;

  // The set of superior DIT structure rules.
  private final Set<DITStructureRule> superiorRules;

  // The definition string for this DIT structure rule.
  private final String definition;

  // The description for this DIT structure rule.
  private final String description;



  /**
   * Creates a new DIT structure rule with the provided information.
   *
   * @param  definition       The definition string used to create
   *                          this DIT structure rule.  It must not be
   *                          {@code null}.
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
  public DITStructureRule(String definition, Map<String,String> names,
                          int ruleID, String description,
                          boolean isObsolete, NameForm nameForm,
                          Set<DITStructureRule> superiorRules,
                          Map<String,List<String>> extraProperties)
  {
    ensureNotNull(definition);

    this.ruleID      = ruleID;
    this.description = description;
    this.isObsolete  = isObsolete;
    this.nameForm    = nameForm;

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

    if ((superiorRules == null) || superiorRules.isEmpty())
    {
      this.superiorRules = new LinkedHashSet<DITStructureRule>(0);
    }
    else
    {
      this.superiorRules =
           new LinkedHashSet<DITStructureRule>(superiorRules);
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
   * Retrieves the definition string used to create this DIT structure
   * rule.
   *
   * @return  The definition string used to create this DIT structure
   *          rule.
   */
  public String getDefinition()
  {
    return definition;
  }



  /**
   * Creates a new instance of this DIT structure rule based on the
   * definition string.  It will also preserve other state information
   * associated with this DIT structure rule that is not included in
   * the definition string (e.g., the name of the schema file with
   * which it is associated).
   *
   * @return  The new instance of this DIT structure rule based on the
   *          definition string.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to create a new DIT structure rule
   *                              instance from the definition string.
   */
  public DITStructureRule recreateFromDefinition()
         throws DirectoryException
  {
    ByteString value  = ByteStringFactory.create(definition);
    Schema     schema = DirectoryConfig.getSchema();

    DITStructureRule dsr =
         DITStructureRuleSyntax.decodeDITStructureRule(value, schema,
                                                       false);
    dsr.setSchemaFile(getSchemaFile());

    return dsr;
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
  public Map<String,String> getNames()
  {
    return names;
  }



  /**
   * Indicates whether this DIT structure rule has the specified name.
   *
   * @param  lowerName  The lowercase name for which to make the
   *                    determination.
   *
   * @return  {@code true} if the specified name is assigned to this
   *          DIT structure rule, or {@code false} if not.
   */
  public boolean hasName(String lowerName)
  {
    return names.containsKey(lowerName);
  }



  /**
   * Retrieves the rule ID for this DIT structure rule.
   *
   * @return  The rule ID for this DIT structure rule.
   */
  public int getRuleID()
  {
    return ruleID;
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
   * Retrieves the path to the schema file that contains the
   * definition for this DIT structure rule.
   *
   * @return  The path to the schema file that contains the definition
   *          for this DIT structure rule, or {@code null} if it
   *          is not known or if it is not stored in any schema file.
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
   * definition for this DIT structure rule.
   *
   * @param  schemaFile  The path to the schema file that contains the
   *                     definition for this DIT structure rule.
   */
  public void setSchemaFile(String schemaFile)
  {
    setExtraProperty(SCHEMA_PROPERTY_FILENAME, schemaFile);
  }



  /**
   * Retrieves the description for this DIT structure rule.
   *
   * @return  The description for this DIT structure rule.
   */
  public String getDescription()
  {
    return description;
  }



  /**
   * Retrieves the name form for this DIT structure rule.
   *
   * @return  The name form for this DIT structure rule.
   */
  public NameForm getNameForm()
  {
    return nameForm;
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
    return nameForm.getStructuralClass();
  }



  /**
   * Retrieves the set of superior rules for this DIT structure rule.
   *
   * @return  The set of superior rules for this DIT structure rule.
   */
  public Set<DITStructureRule> getSuperiorRules()
  {
    return superiorRules;
  }



  /**
   * Indicates whether this DIT structure rule has one or more
   * superior rules.
   *
   * @return  {@code true} if this DIT structure rule has one or more
   *          superior rules, or {@code false} if not.
   */
  public boolean hasSuperiorRules()
  {
    return ((superiorRules != null) && (! superiorRules.isEmpty()));
  }



  /**
   * Indicates whether this DIT structure rule is declared "obsolete".
   *
   * @return  {@code true} if this DIT structure rule is declared
   *          "obsolete", or {@code false} if not.
   */
  public boolean isObsolete()
  {
    return isObsolete;
  }



  /**
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this DIT structure rule
   * and the value for that property.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this DIT
   *          structure rule and the value for that property.
   */
  public Map<String,List<String>> getExtraProperties()
  {
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
   *          structure rule, or {@code null} if no such property is
   *          defined.
   */
  public List<String> getExtraProperty(String propertyName)
  {
    return extraProperties.get(propertyName);
  }



  /**
   * Specifies the provided "extra" property for this DIT structure
   * rule.
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
   * Specifies the provided "extra" property for this DIT structure
   * rule.
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
   * Indicates whether the provided object is equal to this DIT
   * structure rule.  The object will be considered equal if it is a
   * DIT structure rule with the same OID as the current type.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if the provided object is equal to this
   *          attribute, or {@code false} if not.
   */
  public boolean equals(Object o)
  {
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

