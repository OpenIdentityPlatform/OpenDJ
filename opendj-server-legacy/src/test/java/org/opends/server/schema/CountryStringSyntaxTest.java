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
 * Portions Copyright 2012 Manuel Gaupp
 */
package org.opends.server.schema;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.ServerContextBuilder;
import org.opends.server.api.AttributeSyntax;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.AttributeSyntaxCfg;
import org.forgerock.opendj.server.config.server.CountryStringAttributeSyntaxCfg;
import org.opends.server.core.ServerContext;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Test the CountryStringSyntax. */
@RemoveOnceSDKSchemaIsUsed
@Test
public class CountryStringSyntaxTest extends AttributeSyntaxTest
{

  @Override
  protected AttributeSyntax<?> getRule() throws Exception
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

    ServerContext serverContext = ServerContextBuilder.aServerContext()
        .schema(new org.opends.server.types.Schema(Schema.getCoreSchema()))
        .build();
    syntax.initializeSyntax(cfg, serverContext);
    return syntax;
  }

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
