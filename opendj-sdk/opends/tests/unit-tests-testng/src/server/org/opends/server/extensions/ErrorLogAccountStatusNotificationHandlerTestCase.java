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
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.messages.Message;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.
       ErrorLogAccountStatusNotificationHandlerCfgDefn;
import org.opends.server.admin.std.server.
       ErrorLogAccountStatusNotificationHandlerCfg;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationProperty;
import org.opends.server.types.AccountStatusNotificationType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;

import static org.testng.Assert.*;

import static org.opends.server.types.AccountStatusNotificationType.*;
import static org.opends.server.types.AccountStatusNotificationProperty.*;



/**
 * A set of test cases for the error log account status notification handler.
 */
public class ErrorLogAccountStatusNotificationHandlerTestCase
       extends ExtensionsTestCase
{
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
  }



  /**
   * Retrieves a set of invalid configuration entries that should cause the
   * notification handler initialization to fail.
   *
   * @return  A set of invalid configuration entries that should cause the
   *          notification handler initialization to fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Error Log Handler,cn=Account Status Notification Handlers," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-account-status-notification-handler",
         "objectClass: ds-cfg-error-log-account-status-notification-handler",
         "cn: Error Log Handler",
         "ds-cfg-java-class: org.opends." +
              "server.extensions.ErrorLogAccountStatusNotificationHandler",
         "ds-cfg-enabled: true",
         "",
         "dn: cn=Error Log Handler,cn=Account Status Notification Handlers," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-account-status-notification-handler",
         "objectClass: ds-cfg-error-log-account-status-notification-handler",
         "cn: Error Log Handler",
         "ds-cfg-java-class: org.opends." +
              "server.extensions.ErrorLogAccountStatusNotificationHandler",
         "ds-cfg-enabled: true",
         "ds-cfg-account-status-notification-type: invalid",
         "",
         "dn: cn=Error Log Handler,cn=Account Status Notification Handlers," +
              "cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-account-status-notification-handler",
         "objectClass: ds-cfg-error-log-account-status-notification-handler",
         "cn: Error Log Handler",
         "ds-cfg-java-class: org.opends." +
              "server.extensions.ErrorLogAccountStatusNotificationHandler",
         "ds-cfg-enabled: true",
         "ds-cfg-account-status-notification-type: password-reset",
         "ds-cfg-account-status-notification-type: invalid");


    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }



  /**
   * Tests to ensure that the notification handler initialization fails with an
   * invalid configuration.
   *
   * @param  e  The configuration entry to use to initialize the account status
   *            notificaton handler.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInalidConfigs(Entry e)
         throws Exception
  {
    DN parentDN =
            DN.decode("cn=Account Status Notification Handlers,cn=config");
    ConfigEntry parentEntry = DirectoryServer.getConfigEntry(parentDN);
    ConfigEntry configEntry = new ConfigEntry(e, parentEntry);

    ErrorLogAccountStatusNotificationHandler handler =
         new ErrorLogAccountStatusNotificationHandler();
    ErrorLogAccountStatusNotificationHandlerCfg configuration =
      AdminTestCaseUtils.getConfiguration(
          ErrorLogAccountStatusNotificationHandlerCfgDefn.getInstance(),
          configEntry.getEntry()
          );
    handler.initializeStatusNotificationHandler(configuration);
  }



  /**
   * Tests to ensure that the error log account status notification handler is
   * configured and enabled within the Directory Server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHandlerEnabled()
         throws Exception
  {
    String dnStr = "cn=Error Log Handler,cn=Account Status Notification " +
                        "Handlers,cn=config";
    DN handlerDN = DN.decode(dnStr);
    AccountStatusNotificationHandler handler =
         DirectoryServer.getAccountStatusNotificationHandler(handlerDN);
    assertNotNull(handler);
    assertTrue(handler instanceof ErrorLogAccountStatusNotificationHandler);
  }



  /**
   * Retrieves the set of valid notification types that may be used with an
   * account status notification handler.
   *
   * @return  The set of valid notification types that may be used with an
   *          account status notification handler.
   */
  @DataProvider(name = "notificationTypes")
  public Object[][] getNotificationTypes()
  {
    AccountStatusNotificationType[] notificationTypes =
         AccountStatusNotificationType.values();

    Object[][] typeArray = new Object[notificationTypes.length][1];
    for (int i=0; i < notificationTypes.length; i++)
    {
      typeArray[i] = new Object[] { notificationTypes[i] };
    }

    return typeArray;
  }



  /**
   * Tests the <CODE>handleStatusNotification</CODE> method.
   *
   * @param  notificationType  The account status notification type to be
   *                           tested.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "notificationTypes")
  public void testHandleStatusNotification(
                   AccountStatusNotificationType notificationType)
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password");

    String dnStr = "cn=Error Log Handler,cn=Account Status Notification " +
                        "Handlers,cn=config";
    DN handlerDN = DN.decode(dnStr);
    AccountStatusNotificationHandler handler =
         DirectoryServer.getAccountStatusNotificationHandler(handlerDN);
    assertNotNull(handler);

    Entry userEntry =
               DirectoryServer.getEntry(DN.decode("uid=test.user,o=test"));

    PasswordPolicyState pwPolicyState =
         new PasswordPolicyState(userEntry, false, false);
    PasswordPolicy policy = pwPolicyState.getPolicy();

    HashMap<AccountStatusNotificationProperty,List<String>>
         notificationProperties =
              new HashMap<AccountStatusNotificationProperty,List<String>>();

    ArrayList<String> propList = new ArrayList<String>(1);
    propList.add(policy.getConfigEntryDN().toString());
    notificationProperties.put(PASSWORD_POLICY_DN, propList);


    if (notificationType == ACCOUNT_TEMPORARILY_LOCKED)
    {
      propList = new ArrayList<String>(1);
      propList.add("300");
      notificationProperties.put(SECONDS_UNTIL_UNLOCK, propList);

      propList = new ArrayList<String>(1);
      propList.add("5 minutes");
      notificationProperties.put(TIME_UNTIL_UNLOCK, propList);

      propList = new ArrayList<String>(1);
      propList.add(new Date(System.currentTimeMillis() + 300000L).toString());
      notificationProperties.put(ACCOUNT_UNLOCK_TIME, propList);
    }
    else if (notificationType == PASSWORD_EXPIRING)
    {
      propList = new ArrayList<String>(1);
      propList.add("86400");
      notificationProperties.put(SECONDS_UNTIL_EXPIRATION, propList);

      propList = new ArrayList<String>(1);
      propList.add("1 day");
      notificationProperties.put(TIME_UNTIL_EXPIRATION, propList);

      propList = new ArrayList<String>(1);
      propList.add(new Date(System.currentTimeMillis() + 86400000L).toString());
      notificationProperties.put(PASSWORD_EXPIRATION_TIME, propList);
    }
    else if ((notificationType == PASSWORD_CHANGED) ||
             (notificationType == PASSWORD_RESET))
    {
      propList = new ArrayList<String>(1);
      propList.add("oldpassword");
      notificationProperties.put(OLD_PASSWORD, propList);

      propList = new ArrayList<String>(1);
      propList.add("newpassword");
      notificationProperties.put(NEW_PASSWORD, propList);
    }


    AccountStatusNotification notification =
         new AccountStatusNotification(notificationType, userEntry,
                                       Message.raw("Test Modification"),
                                       notificationProperties);
    handler.handleStatusNotification(notification);
  }
}

