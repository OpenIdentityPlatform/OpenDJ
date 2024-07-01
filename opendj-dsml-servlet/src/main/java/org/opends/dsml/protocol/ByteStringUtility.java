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
 * Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.dsml.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.w3c.dom.Element;

/**
 * A utility class to assist in converting DsmlValues (in Objects) into
 * the required ByteStrings, and back again.
 */
class ByteStringUtility
{
  /**
   * Returns a ByteString from a DsmlValue Object.
   *
   * @param obj
   *           the DsmlValue object.
   * @return a new ByteString object with the value, or null if val was null,
   *         or if it could not be converted.
   * @throws IOException if any problems occurred retrieving an anyURI value.
   */
  public static ByteString convertValue(Object obj) throws IOException
  {
    if (obj == null)
    {
      return null;
    }
    else if (obj instanceof String)
    {
      return ByteString.valueOfUtf8((String) obj);
    }
    else if (obj instanceof byte[])
    {
      return ByteString.wrap((byte[]) obj);
    }
    else if (obj instanceof URI)
    {
      // read raw content and return as a byte[].
      try (InputStream is = ((URI) obj).toURL().openStream())
      {
        ByteStringBuilder bsb = new ByteStringBuilder();
        while (bsb.appendBytes(is, 2048) != -1)
        {
          // do nothing
        }
        return bsb.toByteString();
      }
    }
    else if (obj instanceof Element)
    {
      Element element = (Element) obj;
      return ByteString.valueOfUtf8(element.getTextContent());
    }
    return null;
  }

  /**
   * Returns a DsmlValue (Object) from an LDAP ByteString. The conversion is
   * simplistic - try and convert it to UTF-8 and if that fails return a byte[].
   *
   * @param bs the ByteString returned from LDAP.
   * @return a String or a byte[].
   */
  public static Object convertByteString(ByteString bs)
  {
    try
    {
      return new String(bs.toCharArray());
    }
    catch (Exception e)
    {
      return bs.toByteArray();
    }
  }
}
