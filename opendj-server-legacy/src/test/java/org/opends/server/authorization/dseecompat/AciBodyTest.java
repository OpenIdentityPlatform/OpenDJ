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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("javadoc")
public class AciBodyTest extends DirectoryServerTestCase
{

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startFakeServer();
  }

  @AfterClass
  public void tearDown() throws DirectoryException
  {
    TestCaseUtils.shutdownFakeServer();
  }

  @DataProvider(name = "validAcis")
  public Object[][] getValidAcis()
  {
    return new Object[][] {
      { "(version 3.0; acl \"Anonymous extended operation access\"; deny(all) userdn=\"ldap:///anyone\";)" },
      { "(version 3.0; acl \"Anonymous extended operation access\"; allow(read) userdn=\"ldap:///anyone\";)" },
      { "(version 3.0; acl \"Self entry modification\"; allow(write) userdn=\"ldap:///self\";)" },
      { "(version 3.0; acl \"Self entry read\"; allow(read,search,compare) userdn=\"ldap:///self\";)" },
      { "(version 3.0; acl \"Anonymous read access\"; allow(read,search,compare) userdn=\"ldap:///anyone\";)" },
      { "(version 3.0; acl \"Anonymous control access\"; allow(read) userdn=\"ldap:///anyone\";)" },
      { "(version 3.0; acl \"Authenticated users control access\"; allow(read) userdn=\"ldap:///all\";)" }, };
  }

  @Test(dataProvider = "validAcis")
  public void decodeValidAci(String aci) throws Exception
  {
    AciBody aciBody = AciBody.decode(aci);
    assertThat(aciBody.toString()).isEqualTo(aci);
    assertThat(aciBody.getPermBindRulePairs()).hasSize(1);
  }
}
