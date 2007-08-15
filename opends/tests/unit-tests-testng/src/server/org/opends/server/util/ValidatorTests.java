/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.util;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import static org.testng.Assert.*;
import org.opends.messages.Message;

/**
 * Tests for the Validator class.
 */
public class ValidatorTests {
  private static final Object NON_NULL = new Object();

  //////////////////////////////////////////////////////////////////////////////
  //
  //  POSITIVE TESTS
  //
  //////////////////////////////////////////////////////////////////////////////

  @Test
  public void testEnsureNotNull() {
    boolean returnValue = Validator.ensureNotNull(NON_NULL);
    assertTrue(returnValue);  // must always return true
  }

  @Test
  public void testEnsureNotNull2() {
    boolean returnValue = Validator.ensureNotNull(NON_NULL, NON_NULL);
    assertTrue(returnValue);  // must always return true
  }

  @Test
  public void testEnsureNotNull3() {
    boolean returnValue = Validator.ensureNotNull(NON_NULL, NON_NULL, NON_NULL);
    assertTrue(returnValue);  // must always return true
  }

  @Test
  public void testEnsureNotNull4() {
    boolean returnValue = Validator.ensureNotNull(NON_NULL, NON_NULL, NON_NULL, NON_NULL);
    assertTrue(returnValue);  // must always return true
  }

  @Test
  public void testEnsureTrue() {
    boolean returnValue = Validator.ensureTrue(true);
    assertTrue(returnValue);  // must always return true
  }

  @Test
  public void testEnsureTrueWithMessage() {
    boolean returnValue = Validator.ensureTrue(true, Message.raw("some message"));
    assertTrue(returnValue);  // must always return true
  }

  //////////////////////////////////////////////////////////////////////////////
  //
  //  NEGATIVE TESTS
  //
  //////////////////////////////////////////////////////////////////////////////

  @Test(expectedExceptions = {NullPointerException.class, AssertionError.class})
  public void testEnsureNotNullWithNull() {
    Validator.ensureNotNull(null);  // Should throw
  }


  @Test(expectedExceptions = {NullPointerException.class, AssertionError.class},
        dataProvider = "dataEnsureNotNull2WithNull")
  public void testEnsureNotNull2WithNull(Object param1, Object param2) {
    Validator.ensureNotNull(param1, param2);  // Should throw
  }

  @DataProvider(name = "dataEnsureNotNull2WithNull")
  public Object[][] dataEnsureNotNull2WithNull() {
    return new Object[][]{
            {null, NON_NULL},
            {NON_NULL, null}};
  }


  @Test(expectedExceptions = {NullPointerException.class, AssertionError.class},
        dataProvider = "dataEnsureNotNull3WithNull")
  public void testEnsureNotNull3WithNull(Object param1, Object param2, Object param3) {
    Validator.ensureNotNull(param1, param2, param3);  // Should throw
  }

  @DataProvider(name = "dataEnsureNotNull3WithNull")
  public Object[][] dataEnsureNotNull3WithNull() {
    return new Object[][]{
            {null, NON_NULL, NON_NULL},
            {NON_NULL, null, NON_NULL},
            {NON_NULL, NON_NULL, null}};
  }


  @Test(expectedExceptions = {NullPointerException.class, AssertionError.class},
        dataProvider = "dataEnsureNotNull4WithNull")
  public void testEnsureNotNull4WithNull(Object param1, Object param2, Object param3, Object param4) {
    Validator.ensureNotNull(param1, param2, param3, param4);  // Should throw
  }

  @DataProvider(name = "dataEnsureNotNull4WithNull")
  public Object[][] dataEnsureNotNull4WithNull() {
    return new Object[][]{
            {null, NON_NULL, NON_NULL, NON_NULL},
            {NON_NULL, null, NON_NULL, NON_NULL},
            {NON_NULL, NON_NULL, null, NON_NULL},
            {NON_NULL, NON_NULL, NON_NULL, null}};
  }


  @Test(expectedExceptions = {RuntimeException.class, AssertionError.class})
  public void testEnsureTrueWithFalse() {
    Validator.ensureTrue(false);
  }


  @Test(expectedExceptions = {RuntimeException.class, AssertionError.class})
  public void testEnsureTrueWithMessageWithFalse() {
    Validator.ensureTrue(false, Message.raw("some message"));
  }


  @Test
  public void testMessageContents() {
    Validator.resetErrorCount();
    Message myMessage = Message.raw("some test message");
    String thisMethod = ValidatorTests.class.getName() + "." + "testMessageContents(ValidatorTests.java:";
    try {
      Validator.ensureTrue(false, myMessage);
    } catch (Throwable e) {
      String caughtMessage = e.getMessage();
      assertTrue(caughtMessage.indexOf(myMessage.toString()) >= 0);
      assertTrue(caughtMessage.indexOf(thisMethod) >= 0);

      assertEquals(Validator.getErrorCount(), 1);
      Validator.resetErrorCount();
      assertEquals(Validator.getErrorCount(), 0);
    }
  }
}
