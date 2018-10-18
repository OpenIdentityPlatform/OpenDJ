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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.util;

import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.forgerock.opendj.ldap.schema.Schema.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.schema.SchemaConstants.*;

import java.util.Iterator;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.server.schema.SchemaHandler;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/** Class used to retrieve the schema from the schema files. */
public class RemoteSchemaLoader extends SchemaLoader
{
  private Schema schema;

  /**
   * In remote mode we cannot load the matching rules and syntaxes from local
   * configuration, so we should instead bootstrap them from the SDK's core schema.
   */
  public RemoteSchemaLoader()
  {
    matchingRulesToKeep.clear();
    syntaxesToKeep.clear();
    matchingRulesToKeep.addAll(getCoreSchema().getMatchingRules());
    syntaxesToKeep.addAll(getCoreSchema().getSyntaxes());
  }

  /**
   * Reads and returns the schema.
   *
   * @param connWrapper
   *          the connection to be used to load the schema.
   * @return the schema
   * @throws LdapException
   *           if an error occurs reading the schema.
   * @throws DirectoryException
   *           if an error occurs parsing the schema.
   * @throws InitializationException
   *           if an error occurs finding the base schema.
   * @throws ConfigException
   *           if an error occurs loading the configuration required to use the schema classes.
   */
  public Schema readSchema(ConnectionWrapper connWrapper) throws LdapException, DirectoryException,
      InitializationException, ConfigException
  {
    Schema baseSchema = getBaseSchema();
    SchemaBuilder schemaBuilder = new SchemaBuilder(baseSchema);

    // Add missing matching rules and attribute syntaxes to base schema to allow read of remote server schema
    // (see OPENDJ-1122 for more details)
    //SchemaHandler.addServerSyntaxesAndMatchingRules(schemaBuilder);

    // Add remote schema entry
    final SearchRequest request = newSearchRequest(
        DN.valueOf(DN_DEFAULT_SCHEMA_ROOT), BASE_OBJECT, Filter.alwaysTrue(),
        ATTR_LDAP_SYNTAXES, ATTR_ATTRIBUTE_TYPES, ATTR_OBJECTCLASSES);
    final SearchResultEntry entry = connWrapper.getConnection().searchSingleEntry(request);
    removeNonOpenDjOrOpenDsSyntaxes(entry);
    new SchemaBuilder(getBaseSchema()).addSchema(entry, true);

    return buildSchema(schemaBuilder); 

  }

  private void removeNonOpenDjOrOpenDsSyntaxes(final SearchResultEntry entry) throws DirectoryException
  {
    Attribute attribute = entry.getAttribute(AttributeDescription.create(getLDAPSyntaxesAttributeType()));
    if (attribute != null)
    {
      for (Iterator<ByteString> it = attribute.iterator(); it.hasNext();)
      {
        final String definition = it.next().toString();
        if (!definition.contains(OID_OPENDS_SERVER_BASE)
            && !definition.contains(OID_OPENDJ_BASE))
        {
          it.remove();
        }
      }
    }
  }

  /**
   * Returns the schema that was read.
   *
   * @return the schema that was read.
   */
  @Override
  public Schema getSchema()
  {
    return schema;
  }
}
