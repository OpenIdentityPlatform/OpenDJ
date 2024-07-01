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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.types;

import static org.opends.server.core.BackendConfigManager.NamingContextFilter.PRIVATE;
import static org.opends.server.core.BackendConfigManager.NamingContextFilter.PUBLIC;
import static org.opends.server.core.BackendConfigManager.NamingContextFilter.TOP_LEVEL;
import static org.testng.Assert.*;

import java.util.ArrayList;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** This class defines a set of tests for the org.opends.server.core.DN class. */
@SuppressWarnings("javadoc")
public class TestDN extends TypesTestCase {
  @BeforeClass
  public void setUp() throws Exception {
    TestCaseUtils.startServer();
  }

  @DataProvider(name = "namingContexts")
  public Object[][] getNamingContexts() {
    ArrayList<DN> contextList = new ArrayList<>();
    BackendConfigManager manager = DirectoryServer.getInstance().getServerContext().getBackendConfigManager();
    contextList.addAll(manager.getNamingContexts(PRIVATE));
    contextList.addAll(manager.getNamingContexts(PUBLIC));
    contextList.addAll(manager.getNamingContexts(PUBLIC, TOP_LEVEL));

    Object[][] contextArray = new Object[contextList.size()][1];
    for (int i = 0;i < contextArray.length;i++) {
      contextArray[i][0] = contextList.get(i);
    }

    return contextArray;
  }

  @Test(dataProvider = "namingContexts")
  public void testGetParentDNInSuffix(DN namingContext) throws Exception {
    BackendConfigManager backendConfigManager = TestCaseUtils.getServerContext().getBackendConfigManager();
    assertNull(backendConfigManager.getParentDNInSuffix(namingContext));

    DN childDN = namingContext.child(RDN.valueOf("ou=People"));
    assertNotNull(backendConfigManager.getParentDNInSuffix(childDN));
    assertEquals(backendConfigManager.getParentDNInSuffix(childDN), namingContext);
  }
}

