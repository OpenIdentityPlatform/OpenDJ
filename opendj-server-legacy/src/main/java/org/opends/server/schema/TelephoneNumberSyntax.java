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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.opends.server.schema.SchemaConstants.*;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaOptions;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.TelephoneNumberAttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;

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

  @Override
  public void initializeSyntax(TelephoneNumberAttributeSyntaxCfg configuration, ServerContext serverContext)
      throws ConfigException, DirectoryException
  {
    this.serverContext = serverContext;

    // We may or may not have access to the config entry.  If we do, then see if
    // we should use the strict compliance mode. If not, just assume that we won't.
    strictMode = false;
    if (configuration != null)
    {
      currentConfig = configuration;
      currentConfig.addTelephoneNumberChangeListener(this);
      strictMode = currentConfig.isStrictFormat();
      serverContext.getSchema().updateSchemaOption(SchemaOptions.ALLOW_NON_STANDARD_TELEPHONE_NUMBERS, !strictMode);
    }
  }

  @Override
  public Syntax getSDKSyntax(Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_TELEPHONE_OID);
  }

  @Override
  public void finalizeSyntax()
  {
    currentConfig.removeTelephoneNumberChangeListener(this);
  }

  @Override
  public String getName()
  {
    return SYNTAX_TELEPHONE_NAME;
  }

  @Override
  public String getOID()
  {
    return SYNTAX_TELEPHONE_OID;
  }

  @Override
  public String getDescription()
  {
    return SYNTAX_TELEPHONE_DESCRIPTION;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      TelephoneNumberAttributeSyntaxCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // The configuration will always be acceptable.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
              TelephoneNumberAttributeSyntaxCfg configuration)
  {
    currentConfig = configuration;
    strictMode = configuration.isStrictFormat();
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      serverContext.getSchema().updateSchemaOption(SchemaOptions.ALLOW_NON_STANDARD_TELEPHONE_NUMBERS, !strictMode);
    }
    catch (DirectoryException e)
    {
      ccr.setResultCode(e.getResultCode());
      ccr.addMessage(e.getMessageObject());
    }
    return ccr;
  }
}

