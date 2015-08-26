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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2016 ForgeRock AS
 */
package org.opends.server.schema;

import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.opends.server.schema.SchemaConstants.*;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AttributeTypeDescriptionAttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;

/**
 * This class defines the attribute type description syntax, which is used to
 * hold attribute type definitions in the server schema.  The format of this
 * syntax is defined in RFC 2252.
 */
@RemoveOnceSDKSchemaIsUsed
public class AttributeTypeSyntax
       extends AttributeSyntax<AttributeTypeDescriptionAttributeSyntaxCfg>
       implements
       ConfigurationChangeListener<AttributeTypeDescriptionAttributeSyntaxCfg> {

  /**
   * The reference to the configuration for this attribute type description
   * syntax.
   */
  private AttributeTypeDescriptionAttributeSyntaxCfg currentConfig;



  /** If true strip the suggested minimum upper bound from the syntax OID. */
  private static boolean stripMinimumUpperBound;

  private ServerContext serverContext;


  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public AttributeTypeSyntax()
  {
    super();
  }

  @Override
  public void
  initializeSyntax(AttributeTypeDescriptionAttributeSyntaxCfg configuration, ServerContext serverContext)
      throws ConfigException, InitializationException, DirectoryException
  {
    this.serverContext = serverContext;

    // This syntax is one of the Directory Server's core syntaxes and therefore
    // it may be instantiated at times without a configuration entry.  If that
    // is the case, then we'll exit now before doing anything that could require
    // access to that entry.
    if (configuration == null)
    {
      return;
    }

    currentConfig = configuration;
    currentConfig.addAttributeTypeDescriptionChangeListener(this);
    stripMinimumUpperBound = configuration.isStripSyntaxMinUpperBound();
    serverContext.getSchema().updateSchemaOption(STRIP_UPPER_BOUND_FOR_ATTRIBUTE_TYPE, stripMinimumUpperBound);
  }

  @Override
  public Syntax getSDKSyntax(Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_ATTRIBUTE_TYPE_OID);
  }

  @Override
  public String getName()
  {
    return SYNTAX_ATTRIBUTE_TYPE_NAME;
  }

  @Override
  public String getOID()
  {
    return SYNTAX_ATTRIBUTE_TYPE_OID;
  }

  @Override
  public String getDescription()
  {
    return SYNTAX_ATTRIBUTE_TYPE_DESCRIPTION;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
              AttributeTypeDescriptionAttributeSyntaxCfg configuration)
  {
    ConfigChangeResult ccr = new ConfigChangeResult();
    currentConfig = configuration;
    stripMinimumUpperBound = configuration.isStripSyntaxMinUpperBound();
    try
    {
      serverContext.getSchema().updateSchemaOption(STRIP_UPPER_BOUND_FOR_ATTRIBUTE_TYPE, stripMinimumUpperBound);
    }
    catch (DirectoryException e)
    {
      ccr.setResultCode(e.getResultCode());
      ccr.addMessage(e.getMessageObject());
    }
    return ccr;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      AttributeTypeDescriptionAttributeSyntaxCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // The configuration will always be acceptable.
    return true;
  }

  /**
   * Boolean that indicates that the minimum upper bound value should be
   * stripped from the Attribute Type Syntax Description.
   *
   * @return True if the minimum upper bound value should be stripped.
   */
  public static boolean isStripSyntaxMinimumUpperBound() {
    return stripMinimumUpperBound;
  }
}
