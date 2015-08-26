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
 *      Portions Copyright 2012-2016 ForgeRock AS
 *      Portions Copyright 2013-2014 Manuel Gaupp
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
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CertificateAttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;


/**
 * This class implements the certificate attribute syntax. It is restricted to
 * accept only X.509 certificates.
 */
public class CertificateSyntax
       extends AttributeSyntax<CertificateAttributeSyntaxCfg>
       implements ConfigurationChangeListener<CertificateAttributeSyntaxCfg>
{

  /** The current configuration. */
  private volatile CertificateAttributeSyntaxCfg config;

  private ServerContext serverContext;

  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public CertificateSyntax()
  {
    super();
  }

  @Override
  public void initializeSyntax(CertificateAttributeSyntaxCfg configuration, ServerContext serverContext)
      throws ConfigException, DirectoryException
  {
    this.config = configuration;
    this.serverContext = serverContext;
    serverContext.getSchema().updateSchemaOption(SchemaOptions.ALLOW_MALFORMED_CERTIFICATES, !config.isStrictFormat());
    config.addCertificateChangeListener(this);
  }

  @Override
  public Syntax getSDKSyntax(Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_CERTIFICATE_OID);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      CertificateAttributeSyntaxCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // The configuration is always acceptable.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
      CertificateAttributeSyntaxCfg configuration)
  {
    this.config = configuration;
    final ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      serverContext.getSchema()
          .updateSchemaOption(SchemaOptions.ALLOW_MALFORMED_CERTIFICATES, !config.isStrictFormat());
    }
    catch (DirectoryException e)
    {
      ccr.setResultCode(e.getResultCode());
      ccr.addMessage(e.getMessageObject());
    }
    return ccr;
  }

  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  @Override
  public String getName()
  {
    return SYNTAX_CERTIFICATE_NAME;
  }

  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  @Override
  public String getOID()
  {
    return SYNTAX_CERTIFICATE_OID;
  }

  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  @Override
  public String getDescription()
  {
    return SYNTAX_CERTIFICATE_DESCRIPTION;
  }
}

