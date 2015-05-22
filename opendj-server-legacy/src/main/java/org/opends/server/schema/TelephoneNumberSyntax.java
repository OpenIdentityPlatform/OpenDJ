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
import org.opends.server.admin.std.server.TelephoneNumberAttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.ServerContext;

/**
 * This class implements the telephone number attribute syntax, which is defined
 * in RFC 2252.  Note that this can have two modes of operation, depending on
 * its configuration.  Most of the time, it will be very lenient when deciding
 * what to accept, and will allow anything but only pay attention to the digits.
 * However, it can also be configured in a "strict" mode, in which case it will
 * only accept values in the E.123 international telephone number format.
 */
public class TelephoneNumberSyntax
       extends AttributeSyntax<TelephoneNumberAttributeSyntaxCfg>
       implements ConfigurationChangeListener<TelephoneNumberAttributeSyntaxCfg>
{

  /** Indicates whether this matching rule should operate in strict mode. */
  private boolean strictMode;

  /** The current configuration for this telephone number syntax. */
  private TelephoneNumberAttributeSyntaxCfg currentConfig;

  private ServerContext serverContext;

  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public TelephoneNumberSyntax()
  {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public void initializeSyntax(TelephoneNumberAttributeSyntaxCfg configuration, ServerContext serverContext)
         throws ConfigException
  {
    this.serverContext = serverContext;

    // We may or may not have access to the config entry.  If we do, then see if
    // we should use the strict compliance mode.  If not, just assume that we
    // won't.
    strictMode = false;
    if (configuration != null)
    {
      currentConfig = configuration;
      currentConfig.addTelephoneNumberChangeListener(this);
      strictMode = currentConfig.isStrictFormat();
      updateNewSchema();
    }
  }

  /** Update the option in new schema if it changes from current value. */
  private void updateNewSchema()
  {
    Option<Boolean> option = SchemaOptions.ALLOW_NON_STANDARD_TELEPHONE_NUMBERS;
    if (strictMode == serverContext.getSchemaNG().getOption(option))
    {
      SchemaUpdater schemaUpdater = serverContext.getSchemaUpdater();
      schemaUpdater.updateSchema(schemaUpdater.getSchemaBuilder().setOption(option, !strictMode).toSchema());
    }
  }

  /** {@inheritDoc} */
  @Override
  public Syntax getSDKSyntax(Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_TELEPHONE_OID);
  }

  /**
   * Performs any finalization that may be necessary for this attribute syntax.
   */
  @Override
  public void finalizeSyntax()
  {
    currentConfig.removeTelephoneNumberChangeListener(this);
  }

  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  @Override
  public String getName()
  {
    return SYNTAX_TELEPHONE_NAME;
  }

  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  @Override
  public String getOID()
  {
    return SYNTAX_TELEPHONE_OID;
  }

  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  @Override
  public String getDescription()
  {
    return SYNTAX_TELEPHONE_DESCRIPTION;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      TelephoneNumberAttributeSyntaxCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // The configuration will always be acceptable.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
              TelephoneNumberAttributeSyntaxCfg configuration)
  {
    currentConfig = configuration;
    strictMode = configuration.isStrictFormat();
    updateNewSchema();

    return new ConfigChangeResult();
  }
}

