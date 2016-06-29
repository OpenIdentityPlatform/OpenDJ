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
import static org.opends.server.config.ConfigConstants.ATTR_ATTRIBUTE_TYPES;
import static org.opends.server.config.ConfigConstants.ATTR_LDAP_SYNTAXES;
import static org.opends.server.schema.SchemaConstants.*;

import java.util.Arrays;
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
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.MatchingRuleImpl;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.replication.plugin.HistoricalCsnOrderingMatchingRuleImpl;
import org.opends.server.schema.AciSyntax;
import org.opends.server.schema.SubtreeSpecificationSyntax;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Schema;
import org.opends.server.types.Schema.SchemaUpdater;

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
   * Reads the schema.
   *
   * @param connWrapper
   *          the connection to be used to load the schema.
   * @throws LdapException
   *           if an error occurs reading the schema.
   * @throws DirectoryException
   *           if an error occurs parsing the schema.
   * @throws InitializationException
   *           if an error occurs finding the base schema.
   * @throws ConfigException
   *           if an error occurs loading the configuration required to use the schema classes.
   */
  public void readSchema(ConnectionWrapper connWrapper) throws LdapException, DirectoryException,
      InitializationException, ConfigException
  {
    schema = getBaseSchema();
    // Add missing matching rules and attribute syntaxes to base schema to allow read of remote server schema
    // (see OPENDJ-1122 for more details)
    addMissingSyntaxesToBaseSchema(new AciSyntax(), new SubtreeSpecificationSyntax());
    addMissingMatchingRuleToBaseSchema("1.3.6.1.4.1.26027.1.4.4", "historicalCsnOrderingMatch",
        "1.3.6.1.4.1.1466.115.121.1.40", new HistoricalCsnOrderingMatchingRuleImpl());

    final SearchRequest request = newSearchRequest(
        DN.valueOf(DN_DEFAULT_SCHEMA_ROOT), BASE_OBJECT, Filter.alwaysTrue(),
        ATTR_LDAP_SYNTAXES, ATTR_ATTRIBUTE_TYPES, ATTR_OBJECTCLASSES);
    final SearchResultEntry entry = connWrapper.getConnection().searchSingleEntry(request);

    removeNonOpenDjOrOpenDsSyntaxes(entry);
    schema.updateSchema(new SchemaUpdater()
    {
      @Override
      public org.forgerock.opendj.ldap.schema.Schema update(SchemaBuilder builder)
      {
        builder.addSchema(entry, true);
        return builder.toSchema();
      }
    });
  }

  private void addMissingSyntaxesToBaseSchema(final AttributeSyntax<?>... syntaxes)
      throws DirectoryException, InitializationException, ConfigException
  {
    for (AttributeSyntax<?> syntax : syntaxes)
    {
      final ServerContext serverContext = DirectoryServer.getInstance().getServerContext();
      final org.forgerock.opendj.ldap.schema.Schema schemaNG = serverContext.getSchemaNG();
      if (!schemaNG.hasSyntax(syntax.getOID()))
      {
        syntax.initializeSyntax(null, serverContext);
      }
      schema.registerSyntax(syntax.getSDKSyntax(schemaNG), true);
    }
  }

  private void addMissingMatchingRuleToBaseSchema(final String oid, final String name, final String syntaxOID,
      final MatchingRuleImpl impl)
      throws InitializationException, ConfigException, DirectoryException
  {
    final org.forgerock.opendj.ldap.schema.Schema schemaNG = schema.getSchemaNG();
    final MatchingRule matchingRule;
    if (schemaNG.hasMatchingRule(name))
    {
      matchingRule = schemaNG.getMatchingRule(name);
    }
    else
    {
      matchingRule = new SchemaBuilder(schemaNG)
          .buildMatchingRule(oid)
            .names(name)
            .syntaxOID(syntaxOID)
            .implementation(impl)
          .addToSchema()
          .toSchema()
          .getMatchingRule(oid);
    }
    schema.registerMatchingRules(Arrays.asList(matchingRule), true);
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
