/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.types;

import static org.forgerock.util.Reject.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.ldap.schema.Syntax;

/**
 * This class defines a data structure for storing and interacting
 * with an ldap syntax, which defines the custom ldap syntaxes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)

public final class LDAPSyntaxDescription
       implements SchemaFileElement
{
  /**
   * The set of additional name-value pairs associated with this ldap
   * syntax definition.
   */
  private final Map<String,List<String>> extraProperties;

  /** The definition string used to create this ldap syntax description. */
  private final String definition;

  /** The description for this ldap syntax description. */
  private final String description;

  /** The OID of the enclosed ldap syntax description. */
  private final String oid;

  /** The LDAPSyntaxDescritpionSyntax associated with this ldap syntax. */
  private Syntax syntax;



  /**
   * Creates a new ldap syntax definition with the provided
   * information.
   *
   * @param  definition          The definition string used to create
   *                             this ldap syntax.  It must not be
   *                             {@code null}.
   * @param syntax    The ldap syntax description syntax
   *                            associated with this ldap syntax.
   * @param  extraProperties     A set of extra properties for this
   *                             ldap syntax description.
   */
  public LDAPSyntaxDescription(String definition, Syntax syntax, Map<String,List<String>> extraProperties)
  {
    ifNull(definition, syntax);

    this.syntax = syntax;
    this.oid = syntax.getOID();
    this.description = syntax.getDescription();

    int schemaFilePos = definition.indexOf(SCHEMA_PROPERTY_FILENAME);
    if (schemaFilePos > 0)
    {
      String defStr;
      try
      {
        int firstQuotePos = definition.indexOf('\'', schemaFilePos);
        int secondQuotePos = definition.indexOf('\'', firstQuotePos+1);

        defStr = definition.substring(0, schemaFilePos).trim() + " "
                 + definition.substring(secondQuotePos+1).trim();
      }
      catch (Exception e)
      {
        defStr = definition;
      }

      this.definition = defStr;
    }
    else
    {
      this.definition = definition;
    }

    if ((extraProperties == null) || extraProperties.isEmpty())
    {
      this.extraProperties = new LinkedHashMap<String,List<String>>(0);
    }
    else
    {
      this.extraProperties = new LinkedHashMap<String,List<String>>(extraProperties);
    }
  }



   /**
   * Retrieves the ldap syntax description syntax associated with
    * this ldap syntax.
   *
   * @return  The description syntax for this definition.
   */
  public Syntax getSyntax()
  {
    return syntax;
  }



  /**
   * Retrieves the description for this ldap syntax description.
   *
   * @return  The description for this ldap syntax description, or
   *                {@code true} if there is none.
   */
  public String getDescription()
  {
    return description;
  }

  /**
   * Returns the oid.
   *
   * @return the oid
   */
  public String getOID()
  {
    return oid;
  }


    /**
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this ldap syntax
   * description and the value for that property.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this ldap syntax
   *          description and the value for that property.
   */
  @Override
  public Map<String,List<String>> getExtraProperties()
  {
    return extraProperties;
  }



  /**
   * Retrieves the value of the specified "extra" property for this
   * ldap syntax description.
   *
   * @param  propertyName  The name of the "extra" property for which
   *                       to retrieve the value.
   *
   * @return  The value of the specified "extra" property for this
   *          ldap syntax description, or {@code null} if no such
   *          property is defined.
   */
  public List<String> getExtraProperty(String propertyName)
  {
    return extraProperties.get(propertyName);
  }



  /**
   * Specifies the provided "extra" property for this ldap syntax
   * description.
   *
   * @param  name    The name for the "extra" property.  It must not
   *                 be {@code null}.
   * @param  values  The set of value for the "extra" property, or
   *                 {@code null} if the property is to be removed.
   */
  public void setExtraProperty(String name, List<String> values)
  {
    ifNull(name);

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
   * Indicates whether the provided object is equal to this ldap
   * syntax. The object will be considered equal if it is a ldap
   * syntax with the same OID as the current ldap syntax description.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if the provided object is equal to this
   *          ldap syntax description, or {@code true} if not.
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (!(o instanceof LDAPSyntaxDescription))
    {
      return false;
    }
    return oid.equals(((LDAPSyntaxDescription) o).oid);
  }



  /**
   * Retrieves the hash code for this ldap syntax description.  It
   * will be  based on the sum of the bytes of the OID.
   *
   * @return  The hash code for this ldap syntax description.
   */
  @Override
  public int hashCode()
  {
    int oidLength = oid.length();
    int hashCode  = 0;
    for (int i=0; i < oidLength; i++)
    {
      hashCode += oid.charAt(i);
    }

    return hashCode;
  }



   /**
   * Retrieves the string representation of this ldap syntax
   * description in the form specified in RFC 2252.
   *
   * @return  The string representation of this ldap syntax in the
    *             form specified in RFC 2252.
   */
  @Override
  public String toString()
  {
    return definition;
  }

}
