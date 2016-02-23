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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

/**
 * Enumeration used for storing type of attribute modification
 * in the value of the replication historical information.
 *
 * Example of ds-sync-hist values:
 * ds-sync-hist: attrName1:changeNumber1:repl:newReplacingValue
 * ds-sync-hist: attrName1:changeNumber2:del:deletedValue
 * ds-sync-hist: attrName3:changeNumber3:add:newAddedvalue
 * ds-sync-hist: attrName3:changeNumber4:attrDel
 */
public enum HistAttrModificationKey
{
  /** The key for attribute value deletion. */
  DEL("del"),
  /** The key for attribute deletion. */
  ATTRDEL("attrDel"),
  /** The key for attribute replace. */
  REPL("repl"),
  /** The key for attribute value addition. */
  ADD("add");

  /** The string representation of this key. */
  private String key;

  /**
   * Creates a new HistKey type with the provided key string.
   *
   * @param histkey The key string
   */
  private HistAttrModificationKey(String histkey)
  {
    this.key = histkey;
  }

  /**
   * Get a key from the String representation.
   *
   * @param histkey the String to decode
   * @return the key from the enum type
   */
  public static HistAttrModificationKey decodeKey(String histkey)
  {
    for (HistAttrModificationKey histKey : values())
    {
      if (histKey.toString().equals(histkey))
      {
        return histKey;
      }
    }
    return null;
  }

  /**
   * Retrieves the human-readable name for this HistKey.
   *
   * @return  The human-readable name for this HistKey.
   */
  public String getKey()
  {
    return key;
  }

  /**
   * Retrieves a string representation of this HistKey.
   *
   * @return  A string representation of this HistKey.
   */
  @Override
  public String toString()
  {
    return key;
  }
}
