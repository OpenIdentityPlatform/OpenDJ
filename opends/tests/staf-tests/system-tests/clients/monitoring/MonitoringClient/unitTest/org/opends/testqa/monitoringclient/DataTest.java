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
public class DataTest {
  
  private Properties parameters;
  private Data data;

    public DataTest() {
    }

  @BeforeClass
  public static void setUpClass() throws Exception {
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
  }

    @Before
    public void setUp() {
      parameters = new Properties();
      parameters.setProperty("protocol", "LDAP");
      parameters.setProperty("baseDN", "cn=LDAP Connection Handler 0.0.0.0 " +
              "port 1389 Statistics,cn=monitor");
      parameters.setProperty("filter", "(objectclass=*)");
      data = new Data("searchRequests", parameters);
    }

    @After
    public void tearDown() {
      parameters = null;
      data = null;
    }

  /**
   * Test of reset method, of class Data.
   */
  @Test
  public void testReset() {
    data.reset();
    assertEquals("",data.getValue());
    assertTrue(data.getTimer() == 0);
  }

  /**
   * Test of getAttribute method, of class Data.
   */
  @Test
  public void testGetAttribute() {
    assertEquals("searchRequests",data.getAttribute());
  }

  /**
   * Test of getParameters method, of class Data.
   */
  @Test
  public void testGetParameters() {
    assertEquals(parameters,data.getParameters());
  }

  /**
   * Test of getProtocol method, of class Data.
   */
  @Test
  public void testGetProtocol() {
    assertEquals("LDAP",data.getProtocol());
  }

  /**
   * Test of isProtocol method, of class Data.
   */
  @Test
  public void testIsProtocol() {
    assertTrue(data.isProtocol("LDAP"));
    assertFalse(data.isProtocol("LDAP2"));
  }

  /**
   * Test of getValue method, of class Data.
   */
  @Test
  public void testGetValue() {
    data.setValue("12");
    assertEquals("12", data.getValue());
  }

  /**
   * Test of setValue method, of class Data.
   */
  @Test
  public void testSetValue() {
    data.setValue("12");
    assertEquals("12", data.getValue());
  }

  /**
   * Test of hasNullValue method, of class Data.
   */
  @Test
  public void testHasNullValue() {
    data.setValue("");
    assertTrue(data.hasEmptyValue());
    data.setValue("12");
    assertFalse(data.hasEmptyValue());
  }

  /**
   * Test of getTimer method, of class Data.
   */
  @Test
  public void testGetTimer() {
    data.setTimer(1000);
    assertEquals(1000, data.getTimer());
  }

  /**
   * Test of setTimer method, of class Data.
   */
  @Test
  public void testSetTimer() {
    data.setTimer(1000);
    assertEquals(1000, data.getTimer());
  }

}