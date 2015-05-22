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

import org.opends.server.ServerContextBuilder;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.admin.std.server.CountryStringAttributeSyntaxCfg;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.types.DN;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the CountryStringSyntax.
 */
@RemoveOnceSDKSchemaIsUsed
@Test
public class CountryStringSyntaxTest extends AttributeSyntaxTest
{

  /** {@inheritDoc} */
  @Override
  protected AttributeSyntax getRule()
  {
    CountryStringSyntax syntax = new CountryStringSyntax();
    CountryStringAttributeSyntaxCfg cfg = new CountryStringAttributeSyntaxCfg()
    {
      @Override
      public DN dn()
      {
        return null;
      }



      @Override
      public void removeChangeListener(ConfigurationChangeListener<AttributeSyntaxCfg> listener)
      {
        // Stub.
      }



      @Override
      public boolean isEnabled()
      {
        // Stub.
        return false;
      }



      @Override
      public void addChangeListener(
          ConfigurationChangeListener<AttributeSyntaxCfg> listener)
      {
        // Stub.
      }



      @Override
      public void removeCountryStringChangeListener(
          ConfigurationChangeListener<CountryStringAttributeSyntaxCfg> listener)
      {
        // Stub.
      }



      @Override
      public boolean isStrictFormat()
      {
        return true;
      }



      @Override
      public String getJavaClass()
      {
        // Stub.
        return null;
      }



      @Override
      public Class<? extends CountryStringAttributeSyntaxCfg> configurationClass()
      {
        // Stub.
        return null;
      }



      @Override
      public void addCountryStringChangeListener(
          ConfigurationChangeListener<CountryStringAttributeSyntaxCfg> listener)
      {
        // Stub.
      }
    };

    try
    {
      Schema schema = Schema.getCoreSchema();
      ServerContext serverContext = ServerContextBuilder.aServerContext()
        .schemaNG(schema)
        .schemaUpdater(new ServerContextBuilder.MockSchemaUpdater(schema)).build();
      syntax.initializeSyntax(cfg, serverContext);
    }
    catch (ConfigException e)
    {
      // Should never happen.
      throw new RuntimeException(e);
    }

    return syntax;
  }

  /** {@inheritDoc} */
  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object [][] {
        {"DE", true},
        {"de", false},
        {"SX", true},
        {"12", false},
        {"UK", true},
        {"Xf", false},
        {"\u00D6\u00C4", false},
    };
  }

}
