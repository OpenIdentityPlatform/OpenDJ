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
 *      Portions copyright 2013 ForgeRock AS.
 */


package org.opends.server.types;


import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


import org.opends.server.schema.LDAPSyntaxDescriptionSyntax;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.Validator.*;



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
  // The set of additional name-value pairs associated with this ldap
  // syntax definition.
  private final Map<String,List<String>> extraProperties;

  // The definition string used to create this ldap syntax
  //description.
  private final String definition;

  // The description for this ldap syntax description.
  private final String description;

  // The OID of the enclosed ldap syntax description.
  private final String oid;

  //The LDAPSyntaxDescritpionSyntax associated with this ldap syntax.
  private LDAPSyntaxDescriptionSyntax descriptionSyntax;



  /**
   * Creates a new ldap syntax definition with the provided
   * information.
   *
   * @param  definition          The definition string used to create
   *                             this ldap syntax.  It must not be
   *                             {@code null}.
   * @param descriptionSyntax    The ldap syntax description syntax
   *                            associated with this ldap syntax.
   * @param  description         The description for this ldap
   *                                        syntax.
   * @param  extraProperties     A set of extra properties for this
   *                             ldap syntax description.
   */
  public LDAPSyntaxDescription(String definition,
                  LDAPSyntaxDescriptionSyntax descriptionSyntax,
                  String description,
                  Map<String,List<String>> extraProperties)
  {
    ensureNotNull(definition,descriptionSyntax);

    this.descriptionSyntax = descriptionSyntax;
    this.oid = descriptionSyntax.getOID();
    this.description     = description;

    int schemaFilePos = definition.indexOf(SCHEMA_PROPERTY_FILENAME);
    if (schemaFilePos > 0)
    {
      String defStr;
      try
      {
        int firstQuotePos = definition.indexOf('\'', schemaFilePos);
        int secondQuotePos = definition.indexOf('\'',
                                                firstQuotePos+1);

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
   * Retrieves the definition string used to create this ldap syntax
   * description.
   *
   * @return  The definition string used to create this ldap syntax
     *            description.
   */
  public String getDefinition()
  {
    return definition;
  }



   /**
   * Retrieves the ldap syntax description syntax associated with
    * this ldap syntax.
   *
   * @return  The description syntax for this defition.
   */
  public LDAPSyntaxDescriptionSyntax getLdapSyntaxDescriptionSyntax()
  {
    return descriptionSyntax;
  }



  /**
   * {@inheritDoc}
   */
  public LDAPSyntaxDescription recreateFromDefinition(Schema schema)
         throws DirectoryException
  {
    ByteString value  = ByteString.valueOf(definition);
    LDAPSyntaxDescription ls =
            LDAPSyntaxDescriptionSyntax.decodeLDAPSyntax(value,
            schema, false);
    ls.setSchemaFile(getSchemaFile());
    return ls;
  }



  /**
   * Retrieves the path to the schema file that contains the
   * definition for this ldap syntax description.
   *
   * @return  The path to the schema file that contains the
   *           definition for this ldap syntax description, or
   *           {@code null} if it is not known or if it is not stored
   *           in any schema file.
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
   * definition for this ldap syntax description.
   *
   * @param  schemaFile  The path to the schema file that contains
   *                the definition for this ldap syntax description.
   */
  public void setSchemaFile(String schemaFile)
  {
    setExtraProperty(SCHEMA_PROPERTY_FILENAME, schemaFile);
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
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this ldap syntax
   * description and the value for that property.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this ldap syntax
   *          description and the value for that property.
   */
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
   * @param  name   The name for the "extra" property.  It must not
   *                          be {@code null}.
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

    if ((o == null) || (! (o instanceof LDAPSyntaxDescription)))
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
    StringBuilder buffer = new StringBuilder();
    toString(buffer, true);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this ldap syntax in the form
   * specified in RFC 2252 to the provided buffer.
   *
   * @param  buffer              The buffer to which the information
   *                             should be appended.
   * @param  includeFileElement  Indicates whether to include an
   *                             "extra" property that specifies the
   *                             path to the schema file from which
   *                             this ldap syntax was loaded.
   */
  public void toString(StringBuilder buffer,
                       boolean includeFileElement)
  {
    buffer.append("( ");
    buffer.append(oid);

    if ((description != null) && (description.length() > 0))
    {
      buffer.append(" DESC '");
      buffer.append(description);
      buffer.append("'");
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
