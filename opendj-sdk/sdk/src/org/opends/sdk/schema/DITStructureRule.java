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



import static com.sun.opends.sdk.util.Messages.ERR_ATTR_SYNTAX_DSR_UNKNOWN_NAME_FORM;
import static com.sun.opends.sdk.util.Messages.ERR_ATTR_SYNTAX_DSR_UNKNOWN_RULE_ID;

import java.util.*;

import com.sun.opends.sdk.util.Message;
import org.opends.sdk.util.Validator;



/**
 * This class defines a DIT structure rule, which is used to indicate
 * the types of children that entries may have.
 */
public final class DITStructureRule extends SchemaElement
{
  // The rule ID for this DIT structure rule.
  private final Integer ruleID;

  // The set of user defined names for this definition.
  private final List<String> names;

  // Indicates whether this definition is declared "obsolete".
  private final boolean isObsolete;

  // The name form for this DIT structure rule.
  private final String nameFormOID;

  // The set of superior DIT structure rules.
  private final Set<Integer> superiorRuleIDs;

  // The definition string used to create this objectclass.
  private final String definition;

  private NameForm nameForm;
  private Set<DITStructureRule> superiorRules = Collections.emptySet();



  DITStructureRule(Integer ruleID, List<String> names,
      String description, boolean obsolete, String nameFormOID,
      Set<Integer> superiorRuleIDs,
      Map<String, List<String>> extraProperties, String definition)
  {
    super(description, extraProperties);

    Validator.ensureNotNull(ruleID, nameFormOID, superiorRuleIDs);
    this.ruleID = ruleID;
    this.names = names;
    this.isObsolete = obsolete;
    this.nameFormOID = nameFormOID;
    this.superiorRuleIDs = superiorRuleIDs;

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
   * Retrieves the name form for this DIT structure rule.
   * 
   * @return The name form for this DIT structure rule.
   */
  public NameForm getNameForm()
  {
    return nameForm;
  }



  /**
   * Retrieves the name or rule ID for this schema definition. If it has
   * one or more names, then the primary name will be returned. If it
   * does not have any names, then the OID will be returned.
   * 
   * @return The name or OID for this schema definition.
   */
  public String getNameOrRuleID()
  {
    if (names.isEmpty())
    {
      return ruleID.toString();
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
   * Retrieves the rule ID for this DIT structure rule.
   * 
   * @return The rule ID for this DIT structure rule.
   */
  public Integer getRuleID()
  {
    return ruleID;
  }



  /**
   * Retrieves the set of superior rules for this DIT structure rule.
   * 
   * @return The set of superior rules for this DIT structure rule.
   */
  public Iterable<DITStructureRule> getSuperiorRules()
  {
    return superiorRules;
  }



  @Override
  public int hashCode()
  {
    return ruleID.hashCode();
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



  DITStructureRule duplicate()
  {
    return new DITStructureRule(ruleID, names, description, isObsolete,
        nameFormOID, superiorRuleIDs, extraProperties, definition);
  }



  @Override
  void toStringContent(StringBuilder buffer)
  {
    buffer.append(ruleID);

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

    buffer.append(" FORM ");
    buffer.append(nameFormOID);

    if (superiorRuleIDs != null && !superiorRuleIDs.isEmpty())
    {
      final Iterator<Integer> iterator = superiorRuleIDs.iterator();

      final Integer firstRule = iterator.next();
      if (iterator.hasNext())
      {
        buffer.append(" SUP ( ");
        buffer.append(firstRule);

        while (iterator.hasNext())
        {
          buffer.append(" ");
          buffer.append(iterator.next());
        }

        buffer.append(" )");
      }
      else
      {
        buffer.append(" SUP ");
        buffer.append(firstRule);
      }
    }
  }



  @Override
  void validate(List<Message> warnings, Schema schema)
      throws SchemaException
  {
    try
    {
      nameForm = schema.getNameForm(nameFormOID);
    }
    catch (final UnknownSchemaElementException e)
    {
      final Message message =
          ERR_ATTR_SYNTAX_DSR_UNKNOWN_NAME_FORM.get(definition,
              nameFormOID);
      throw new SchemaException(message, e);
    }

    if (!superiorRuleIDs.isEmpty())
    {
      superiorRules =
          new HashSet<DITStructureRule>(superiorRuleIDs.size());
      DITStructureRule rule;
      for (final Integer id : superiorRuleIDs)
      {
        try
        {
          rule = schema.getDITStructureRule(id);
        }
        catch (final UnknownSchemaElementException e)
        {
          final Message message =
              ERR_ATTR_SYNTAX_DSR_UNKNOWN_RULE_ID.get(definition, id);
          throw new SchemaException(message, e);
        }
        superiorRules.add(rule);
      }
    }
  }
}
