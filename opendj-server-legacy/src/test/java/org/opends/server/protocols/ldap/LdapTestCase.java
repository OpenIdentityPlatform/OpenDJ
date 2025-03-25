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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;

import static org.mockito.Mockito.mock;
import static org.opends.server.config.ConfigConstants.ATTR_LISTEN_PORT;

import java.util.Iterator;
import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.reactive.LDAPConnectionHandler2;
import org.forgerock.opendj.server.config.meta.LDAPConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.LDAPConnectionHandlerCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.testng.annotations.Test;

/**
 * An abstract class that all types  unit test should extend.
 */
@Test(groups = { "precommit", "ldap" }, singleThreaded = true)
public abstract class LdapTestCase extends DirectoryServerTestCase
{

  /**
   * Determine whether one LDAPAttribute is equal to another.
   * The values of the attribute must be identical and in the same order.
   * @param a1 The first LDAPAttribute.
   * @param a2 The second LDAPAttribute.
   * @return true if the first LDAPAttribute is equal to the second.
   */
  static boolean testEqual(LDAPAttribute a1, LDAPAttribute a2)
  {
    return a1.getAttributeType().equals(a2.getAttributeType())
        && a1.getValues().equals(a2.getValues());
  }

  /**
   * Determine whether one list of LDAPAttribute is equal to another.
   * @param list1 The first list of LDAPAttribute.
   * @param list2 The second list of LDAPAttribute.
   * @return true if the first list of LDAPAttribute is equal to the second.
   */
  static boolean testEqual(List<LDAPAttribute> list1, List<LDAPAttribute> list2)
  {
    Iterator<LDAPAttribute> e1 = list1.iterator();
    Iterator<LDAPAttribute> e2 = list2.iterator();
    while(e1.hasNext() && e2.hasNext()) {
      LDAPAttribute o1 = e1.next();
      LDAPAttribute o2 = e2.next();
      if (o1 == null ? o2 != null : !testEqual(o1, o2))
      {
        return false;
      }
    }
    return !e1.hasNext() && !e2.hasNext();
  }

  /**
   * Test toString methods.
   * @param op The op.
   * @throws Exception If the toString method fails.
   */
  static void toString(ProtocolOp op) throws Exception
  {
    StringBuilder sb = new StringBuilder();
    op.toString(sb);
    op.toString(sb, 1);
  }

  /**
   * Generate a LDAPConnectionHandler from a entry. The listen port is
   * determined automatically, so no ATTR_LISTEN_PORT should be in the
   * entry.
   *
   * @param handlerEntry The entry to be used to configure the handle.
   * @return Returns the new LDAP connection handler.
   * @throws Exception if the handler cannot be initialized.
   */
  static LDAPConnectionHandler2 getLDAPHandlerInstance(Entry handlerEntry)
      throws Exception
  {
    long serverLdapPort = TestCaseUtils.findFreePort();
    Attribute a = Attributes.create(ATTR_LISTEN_PORT, String.valueOf(serverLdapPort));
    handlerEntry.addAttribute(a, null);
    LDAPConnectionHandlerCfg config = getConfiguration(handlerEntry);
    LDAPConnectionHandler2 handler = new LDAPConnectionHandler2();
    handler.initializeConnectionHandler(mock(ServerContext.class), config);
    return handler;
  }

  /**
   * Decode an LDAP connection handler configuration entry.
   *
   * @param handlerEntry
   *          The configuration entry.
   * @return Returns the decoded LDAP connection handler
   *         configuration.
   * @throws ConfigException
   *           If the configuration entry could not be decoded.
   */
  static LDAPConnectionHandlerCfg getConfiguration(Entry handlerEntry) throws ConfigException {
    return InitializationUtils.getConfiguration(LDAPConnectionHandlerCfgDefn.getInstance(), handlerEntry);
  }
}
