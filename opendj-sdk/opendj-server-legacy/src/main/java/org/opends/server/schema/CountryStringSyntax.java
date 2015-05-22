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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS
 *      Portions Copyright 2012 Manuel Gaupp
 */
package org.opends.server.schema;


import static org.opends.server.schema.SchemaConstants.*;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Option;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaOptions;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CountryStringAttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.ServerContext;

/**
 * This class defines the country string attribute syntax, which should be a
 * two-character ISO 3166 country code.  However, for maintainability, it will
 * accept any value consisting entirely of two printable characters.  In most
 * ways, it will behave like the directory string attribute syntax.
 */
public class CountryStringSyntax
       extends AttributeSyntax<CountryStringAttributeSyntaxCfg>
       implements ConfigurationChangeListener<CountryStringAttributeSyntaxCfg>
{

  /** The current configuration. */
  private volatile CountryStringAttributeSyntaxCfg config;

  private ServerContext serverContext;

  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public CountryStringSyntax()
  {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public void initializeSyntax(CountryStringAttributeSyntaxCfg configuration, ServerContext serverContext)
         throws ConfigException
  {
    this.config = configuration;
    this.serverContext = serverContext;
    updateNewSchema();
    config.addCountryStringChangeListener(this);
  }

  /** Update the option in new schema if it changes from current value. */
  private void updateNewSchema()
  {
    Option<Boolean> option = SchemaOptions.STRICT_FORMAT_FOR_COUNTRY_STRINGS;
    if (config.isStrictFormat() != serverContext.getSchemaNG().getOption(option))
    {
      SchemaUpdater schemaUpdater = serverContext.getSchemaUpdater();
      schemaUpdater.updateSchema(
          schemaUpdater.getSchemaBuilder().setOption(option, config.isStrictFormat()).toSchema());
    }
  }

  /** {@inheritDoc} */
  @Override
  public Syntax getSDKSyntax(Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_COUNTRY_STRING_OID);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      CountryStringAttributeSyntaxCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // The configuration is always acceptable.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      CountryStringAttributeSyntaxCfg configuration)
  {
    this.config = configuration;
    updateNewSchema();
    return new ConfigChangeResult();
  }




  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  @Override
  public String getName()
  {
    return SYNTAX_COUNTRY_STRING_NAME;
  }

  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  @Override
  public String getOID()
  {
    return SYNTAX_COUNTRY_STRING_OID;
  }

  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  @Override
  public String getDescription()
  {
    return SYNTAX_COUNTRY_STRING_DESCRIPTION;
  }
}

