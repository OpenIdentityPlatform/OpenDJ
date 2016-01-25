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
import org.forgerock.opendj.server.config.server.JPEGAttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;

/**
 * This class implements the JPEG attribute syntax.  This is actually
 * two specifications - JPEG and JFIF. As an extension we allow JPEG
 * and Exif, which is what most digital cameras use. We only check for
 * valid JFIF and Exif headers.
 */
public class JPEGSyntax
       extends AttributeSyntax<JPEGAttributeSyntaxCfg>
       implements ConfigurationChangeListener<JPEGAttributeSyntaxCfg>
{

  /** The current configuration for this JPEG syntax. */
  private volatile JPEGAttributeSyntaxCfg config;

  private ServerContext serverContext;

  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public JPEGSyntax()
  {
    super();
  }

  @Override
  public void initializeSyntax(JPEGAttributeSyntaxCfg configuration, ServerContext serverContext)
      throws ConfigException, DirectoryException
  {
    this.config = configuration;
    this.serverContext = serverContext;
    serverContext.getSchema().updateSchemaOption(SchemaOptions.ALLOW_MALFORMED_JPEG_PHOTOS, !config.isStrictFormat());
    config.addJPEGChangeListener(this);
  }

  @Override
  public Syntax getSDKSyntax(Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_JPEG_OID);
  }

  @Override
  public String getName()
  {
    return SYNTAX_JPEG_NAME;
  }

  @Override
  public String getOID()
  {
    return SYNTAX_JPEG_OID;
  }

  @Override
  public String getDescription()
  {
    return SYNTAX_JPEG_DESCRIPTION;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      JPEGAttributeSyntaxCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // The configuration will always be acceptable.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
              JPEGAttributeSyntaxCfg configuration)
  {
    this.config = configuration;
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      serverContext.getSchema().updateSchemaOption(SchemaOptions.ALLOW_MALFORMED_JPEG_PHOTOS, !config.isStrictFormat());
    }
    catch (DirectoryException e)
    {
      ccr.setResultCode(e.getResultCode());
      ccr.addMessage(e.getMessageObject());
    }
    return ccr;
  }
}

