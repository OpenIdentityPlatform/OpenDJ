/*
 *  CDDL HEADER START
 * 
 *  The contents of this file are subject to the terms of the
 *  Common Development and Distribution License, Version 1.0 only
 *  (the "License").  You may not use this file except in compliance
 *  with the License.
 * 
 *  You can obtain a copy of the license at
 *  trunk/opends/resource/legal-notices/OpenDS.LICENSE
 *  or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 *  See the License for the specific language governing permissions
 *  and limitations under the License.
 * 
 *  When distributing Covered Code, include this CDDL HEADER in each
 *  file and include the License file at
 *  trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 *  add the following below this CDDL HEADER, with the fields enclosed
 *  by brackets "[]" replaced with your own identifying information:
 *       Portions Copyright [yyyy] [name of copyright owner]
 * 
 *  CDDL HEADER END
 * 
 * 
 *       Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.testqa.monitoringclient;

import java.util.Properties;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author florian
 */
public class DatasBufferTest {
  
  private static MonitoringClient client;
  private DatasBuffer datas;

    public DatasBufferTest() {
    }

  @BeforeClass
  public static void setUpClass() throws Exception {
    client = new MonitoringClient (MonitoringClient.argumentParser(
            new String[] {"-h","havmann","-p","1389","-w","toto123"}));
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    client = null;
  }

    @Before
    public void setUp() {
      datas = new DatasBuffer(client);
    }

    @After
    public void tearDown() {
      datas = null;
    }

  /**
   * Test of addAttributeToMonitor method, of class DatasBuffer.
   */
  @Test
  public void testAddAttributeToMonitor() {
    Properties params = new Properties();
    params.setProperty("name", "missing-changes");
    params.setProperty("baseDN","cn=monitor");
    params.setProperty("filter", "(&#038;(missing-changes=*)(cn=Direct LDAP " +
            "Server dc=example,dc=com*))");
    assertTrue(datas.addAttributeToMonitor(params.getProperty("name"), params));
  }

}