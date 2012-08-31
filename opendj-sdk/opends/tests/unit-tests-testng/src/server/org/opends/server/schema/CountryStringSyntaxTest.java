/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2012 Forgerock AS
 *      Portions Copyright 2012 Manuel Gaupp
 */
package org.opends.server.schema;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.admin.std.server.CountryStringAttributeSyntaxCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DN;

import org.testng.annotations.DataProvider;

/**
 * Test the CountryStringSyntax.
 */
public class CountryStringSyntaxTest extends AttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  protected AttributeSyntax getRule()
  {
    CountryStringSyntax syntax = new CountryStringSyntax();
    CountryStringAttributeSyntaxCfg cfg = new CountryStringAttributeSyntaxCfg()
    {
      public DN dn()
      {
        return null;
      }



      public void removeChangeListener(ConfigurationChangeListener<AttributeSyntaxCfg> listener)
      {
        // Stub.
      }



      public boolean isEnabled()
      {
        // Stub.
        return false;
      }



      public void addChangeListener(
          ConfigurationChangeListener<AttributeSyntaxCfg> listener)
      {
        // Stub.
      }



      public void removeCountryStringChangeListener(
          ConfigurationChangeListener<CountryStringAttributeSyntaxCfg> listener)
      {
        // Stub.
      }



      public boolean isStrictFormat()
      {
        return true;
      }



      public String getJavaClass()
      {
        // Stub.
        return null;
      }



      public Class<? extends CountryStringAttributeSyntaxCfg> configurationClass()
      {
        // Stub.
        return null;
      }



      public void addCountryStringChangeListener(
          ConfigurationChangeListener<CountryStringAttributeSyntaxCfg> listener)
      {
        // Stub.
      }
    };

    try
    {
      syntax.initializeSyntax(cfg);
    }
    catch (ConfigException e)
    {
      // Should never happen.
      throw new RuntimeException(e);
    }

    return syntax;
  }

  /**
   * {@inheritDoc}
   */
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
