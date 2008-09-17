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

import java.util.Date;
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
public class ErrorTest {
  
  private Error error;
  private Date date;

    public ErrorTest() {
    }

  @BeforeClass
  public static void setUpClass() throws Exception {
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
  }

    @Before
    public void setUp() {
      date = new Date();
      error = new Error("Protocol", "Attribute", "Message", date);
    }

    @After
    public void tearDown() {
      date = null;
      error = null;
    }

  /**
   * Test of getDate method, of class Error.
   */
  @Test
  public void testGetDate() {
    assertNotSame(date, error.getDate());
    assertEquals(date, error.getDate());
  }

  /**
   * Test of getProtocol method, of class Error.
   */
  @Test
  public void testGetProtocol() {
    assertEquals("Protocol", error.getProtocol());
  }

  /**
   * Test of getAttribute method, of class Error.
   */
  @Test
  public void testGetAttribute() {
    assertEquals("Attribute", error.getAttribute());
  }

  /**
   * Test of getMessage method, of class Error.
   */
  @Test
  public void testGetMessage() {
    assertEquals("Message", error.getMessage());
  }

}