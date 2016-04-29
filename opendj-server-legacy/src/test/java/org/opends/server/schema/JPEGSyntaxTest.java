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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.util.Base64;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Test the JPEGSyntax. */
@RemoveOnceSDKSchemaIsUsed
@Test
public class JPEGSyntaxTest extends BinaryAttributeSyntaxTest
{
  @Override
  protected AttributeSyntax<?> getRule()
  {
    return new JPEGSyntax();
  }

  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
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
      try
      {
        return new Object [][] {
          {ByteString.wrap(Base64.decode(jfif_image)), true},
        };
      }
      catch (Exception e)
      {
        return new Object[][] {};
      }
  }
}
