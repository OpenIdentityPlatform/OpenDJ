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
 *      Copyright 2012 ForgeRock AS
 */
package org.opends.server.schema;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.admin.std.server.JPEGAttributeSyntaxCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.util.Base64;

import org.testng.annotations.DataProvider;

/**
 * Test the JPEGSyntax.
 */
public class JPEGSyntaxTest extends BinaryAttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  protected AttributeSyntax<?> getRule()
  {
    JPEGSyntax syntax = new JPEGSyntax();
    JPEGAttributeSyntaxCfg cfg = new JPEGAttributeSyntaxCfg()
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



      public void removeJPEGChangeListener(
          ConfigurationChangeListener<JPEGAttributeSyntaxCfg> listener)
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



      public Class<? extends JPEGAttributeSyntaxCfg> configurationClass()
      {
        // Stub.
        return null;
      }



      public void addJPEGChangeListener(
          ConfigurationChangeListener<JPEGAttributeSyntaxCfg> listener)
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
	  String non_image =
          "AAECAwQFBgcICQ==";
	  String jfif_image =
          "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDACMYGh4aFiMeHB4nJSMpNFc4NDAwNGpM" +
          "UD9Xfm+EgnxveneLnMipi5S9lnd6ru2wvc7V4OLgh6f1//PZ/8jb4Nf/2wBDASUn" +
          "JzQuNGY4OGbXj3qP19fX19fX19fX19fX19fX19fX19fX19fX19fX19fX19fX19fX" +
          "19fX19fX19fX19fX19f/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEA" +
          "AAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIh" +
          "MUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6" +
          "Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZ" +
          "mqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx" +
          "8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREA" +
          "AgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAV" +
          "YnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hp" +
          "anN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPE" +
          "xcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwCC" +
          "iiiuM9g//9k=";
	  String exif_image =
          "/9j/4QD2RXhpZgAATU0AKgAAAAgACQESAAMAAAABAAEAAAEaAAUAAAABAAAAegEb" +
          "AAUAAAABAAAAggEoAAMAAAABAAIAAAExAAIAAAAQAAAAigEyAAIAAAAUAAAAmgE8" +
          "AAIAAAAOAAAArgITAAMAAAABAAEAAIdpAAQAAAABAAAAvAAAAAAASAAAAAEAAABI" +
          "AAAAAQAAUXVpY2tUaW1lIDcuNy4xADIwMTI6MDg6MjAgMTI6MTE6MTIATWFjIE9T" +
          "IFggMTAuOAAAApAAAAcAAAAEMDIyMJADAAIAAAAUAAAA2gAAAAAyMDEyOjA4OjIw" +
          "IDEyOjEwOjA2AP/+AAxBcHBsZU1hcmsK/9sAQwAjGBoeGhYjHhweJyUjKTRXODQw" +
          "MDRqTFA/V35vhIJ8b3p3i5zIqYuUvZZ3eq7tsL3O1eDi4Ien9f/z2f/I2+DX/9sA" +
          "QwElJyc0LjRmODhm1496j9fX19fX19fX19fX19fX19fX19fX19fX19fX19fX19fX" +
          "19fX19fX19fX19fX19fX19fX/8AAEQgAAQABAwEiAAIRAQMRAf/EAB8AAAEFAQEB" +
          "AQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAE" +
          "EQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2" +
          "Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SV" +
          "lpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn" +
          "6Onq8fLz9PX29/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//E" +
          "ALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXETIjKBCBRCkaGxwQkj" +
          "M1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2Rl" +
          "ZmdoaWpzdHV2d3h5eoKDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5" +
          "usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/aAAwDAQACEQMR" +
          "AD8AgooorjPYP//Z";
	  String png_image =
          "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAMAAAAoyzS7AAAABlBMVEX/JgAAAAAP" +
          "IsinAAAACklEQVQImWNgAAAAAgAB9HFkpgAAAABJRU5ErkJggg==";

      try
      {
        return new Object [][] {
          {ByteString.wrap(Base64.decode(non_image)), false},
          {ByteString.wrap(Base64.decode(jfif_image)), true},
          {ByteString.wrap(Base64.decode(exif_image)), true},
          {ByteString.wrap(Base64.decode(png_image)), false}
        };
	  }
      catch (Exception e)
      {
        return new Object[][] {};
      }
  }

}
