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
package org.opends.server.core;



import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;

import org.opends.server.TestCaseUtils;
import org.opends.server.util.StaticUtils;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.protocols.ldap.LDAPConnectionHandler;
import org.opends.server.protocols.ldap.LDAPStatistics;
import org.opends.server.types.Control;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for operations
 */
public abstract class OperationTestCase
       extends CoreTestCase
{
  // The LDAPStatistics object associated with the LDAP connection handler.
  protected LDAPStatistics ldapStatistics;

  // The LDAPStatistics object associated with the LDAPS connection handler.
  protected LDAPStatistics ldapsStatistics;

  @BeforeMethod
  public void setUpQuiesceServer()
  {
    TestCaseUtils.quiesceServer();
  }

  /**
   * Since the PostResponse plugins are called after the response is sent
   * back to the client, a client (e.g. a test case) can get a response before
   * the PostResponse plugins have been called.  So that we can verify that the
   * PostResponse plugins were called, the tests call this method to ensure
   * that all operations in the server have been completed.
   */
  protected void ensurePostReponseHasRun()
  {
    TestCaseUtils.quiesceServer();
  }

  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    for (ConnectionHandler ch : DirectoryServer.getConnectionHandlers())
    {
      if (ch instanceof LDAPConnectionHandler)
      {
        LDAPConnectionHandler lch = (LDAPConnectionHandler) ch;
        if (lch.useSSL())
        {
          ldapsStatistics = lch.getStatTracker();
        }
        else
        {
          ldapStatistics = lch.getStatTracker();
        }
      }
    }

    assertNotNull(ldapStatistics);
    assertNotNull(ldapsStatistics);
  }



  /**
   * Creates a set of valid operation instances of this type that may be used
   * for testing the general methods defined in the Operation superclass.  Only
   * the constructors for the operation need to be used -- it does not require
   * any further initialization (no tests will be performed that require any
   * further processing).
   *
   * @return  A set of operation instances of this type that may be used for
   *          testing the general methods defined in the Operation superclass.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public abstract Operation[] createTestOperations()
         throws Exception;



  /**
   * Retrieves a set of valid operation instances that may be used to test
   * methods common to all operations.
   *
   * @return  A set of valid operation instances that may be used to test
   *          methods common to all operations.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "testOperations")
  public Object[][] getTestOperations()
         throws Exception
  {
    Operation[] operationArray = createTestOperations();
    Object[][]  objectArray    = new Object[operationArray.length][1];

    for (int i=0; i < operationArray.length; i++)
    {
      objectArray[i][0] = operationArray[i];
    }

    return objectArray;
  }



  /**
   * Tests the <CODE>getOperationType</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetOperationType(Operation operation)
  {
    assertNotNull(operation.getOperationType());
  }



  /**
   * Tests the <CODE>getCommonLogElements</CODE> method for the provided
   * operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetCommonLogElements(Operation operation)
  {
    assertNotNull(operation.getCommonLogElements());
  }



  /**
   * Tests the <CODE>getRequestLogElements</CODE> method for the provided
   * operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetRequestLogElements(Operation operation)
  {
    assertNotNull(operation.getRequestLogElements());
  }



  /**
   * Tests the <CODE>getClientConnection</CODE> method for the provided
   * operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetClientConnection(Operation operation)
  {
    assertNotNull(operation.getClientConnection());
  }



  /**
   * Tests the <CODE>getConnectionID</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetConnectionID(Operation operation)
  {
    operation.getConnectionID();
  }



  /**
   * Tests the <CODE>getOperationID</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetOperationID(Operation operation)
  {
    operation.getOperationID();
  }



  /**
   * Tests the <CODE>getMessageID</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetMessageID(Operation operation)
  {
    operation.getMessageID();
  }



  /**
   * Tests the <CODE>getRequestControls</CODE> method for the provided
   * operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetRequestControls(Operation operation)
  {
    operation.getRequestControls();
  }



  /**
   * Tests the <CODE>getResponseControls</CODE> method for the provided
   * operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetResponseControls(Operation operation)
  {
    operation.getResponseControls();
  }



  /**
   * Tests the <CODE>getResultCode</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetResultCode(Operation operation)
  {
    assertNotNull(operation.getResultCode());
  }



  /**
   * Tests the <CODE>getErrorMessage</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetErrorMessage(Operation operation)
  {
    assertNotNull(operation.getErrorMessage());
  }



  /**
   * Tests the <CODE>getAdditionalLogMessage</CODE> method for the provided
   * operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetAdditionalLogMessage(Operation operation)
  {
    assertNotNull(operation.getAdditionalLogMessage());
  }



  /**
   * Tests the <CODE>getMatchedDN</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetMatchedDN(Operation operation)
  {
    operation.getMatchedDN();
  }



  /**
   * Tests the <CODE>getReferralURLs</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetReferralURLs(Operation operation)
  {
    operation.getReferralURLs();
  }



  /**
   * Tests the <CODE>isInternalOperation</CODE> method for the provided \
   * operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testIsInternalOperation(Operation operation)
  {
    operation.isInternalOperation();
  }



  /**
   * Tests the <CODE>isSynchronizationOperation</CODE> method for the provided
   * operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testIsSynchronizationOperation(Operation operation)
  {
    operation.isSynchronizationOperation();
  }



  /**
   * Tests the <CODE>getAuthorizationDN</CODE> method for the provided
   * operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetAuthorizationDN(Operation operation)
  {
    assertNotNull(operation.getAuthorizationDN());
  }



  /**
   * Tests the <CODE>getMatchedDN</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetAttachments(Operation operation)
  {
    assertNotNull(operation.getAttachments());
  }



  /**
   * Tests the <CODE>getCancelRequest</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetCancelRequest(Operation operation)
  {
    operation.getCancelRequest();
  }



  /**
   * Tests the <CODE>getCancelResult</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testGetCancelResult(Operation operation)
  {
    operation.getCancelResult();
  }



  /**
   * Tests the <CODE>toString</CODE> method for the provided operation.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testToString(Operation operation)
  {
    assertNotNull(operation.toString());
  }



  /**
   * Tests the <CODE>addResponseControl</CODE> and
   * <CODE>removeResponseControl</CODE> methods.
   *
   * @param  operation  The operation to test.
   */
  @Test(dataProvider = "testOperations")
  public void testAddAndRemoveResponseControl(Operation operation)
  {
    Control c = new Control("1.2.3.4", false);
    operation.addResponseControl(c);
    operation.removeResponseControl(c);

    if (operation.getResponseControls() != null)
    {
      assertFalse(operation.getResponseControls().contains(c));
    }
  }

  /**
   * Checking ldapStatistics.getModifyResponses() too soon can lead to
   * problems since we can see the response before the statistic is
   * incremented, so we're a little more forgiving.
   */
  protected void waitForModifyResponsesStat(long expectedValue)
  {
    for (int i = 0; i < 10; i++) {
      if (ldapStatistics.getModifyResponses() == expectedValue) {
        return;
      }
      TestCaseUtils.sleep(100);
    }
    assertEquals(ldapStatistics.getModifyResponses(), expectedValue);
  }

  /**
   * Checking ldapStatistics.getAddResponses() too soon can lead to
   * problems since we can see the response before the statistic is
   * incremented, so we're a little more forgiving.
   */
  protected void waitForAddResponsesStat(long expectedValue)
  {
    for (int i = 0; i < 10; i++) {
      if (ldapStatistics.getAddResponses() == expectedValue) {
        return;
      }
      TestCaseUtils.sleep(100);
    }
    assertEquals(ldapStatistics.getAddResponses(), expectedValue);
  }
}

