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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.admin.ads.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Entry;

/**
 * Class providing some utilities to create LDAP connections using JNDI and
 * to manage entries retrieved using JNDI.
 */
public class ConnectionUtils
{
  /** Private constructor: this class cannot be instantiated. */
  private ConnectionUtils()
  {
  }

  /**
   * Returns the first attribute value in this attribute decoded as a UTF-8 string.
   *
   * @param entry
   *          the entry
   * @param attrDesc
   *          the attribute description
   * @return The first attribute value in this attribute decoded as a UTF-8 string.
   */
  public static String firstValueAsString(Entry entry, String attrDesc)
  {
    Attribute attr = entry.getAttribute(attrDesc);
    return (attr != null && !attr.isEmpty()) ? attr.firstValueAsString() : null;
  }

  /**
   * Returns all the values of this attribute decoded as UTF-8 strings, in the order they were
   * returned by the server.
   *
   * @param entry
   *          the entry
   * @param attrDesc
   *          the attribute description
   * @return all the values of this attribute decoded as UTF-8 strings, an empty list if the
   *         attribute is not present.
   */
  public static List<String> allValuesAsStrings(Entry entry, String attrDesc)
  {
    Attribute attr = entry.getAttribute(attrDesc);
    if (attr == null || attr.isEmpty())
    {
      return Collections.emptyList();
    }
    List<String> values = new ArrayList<>(attr.size());
    for (ByteString value : attr)
    {
      values.add(value.toString());
    }
    return values;
  }
}
