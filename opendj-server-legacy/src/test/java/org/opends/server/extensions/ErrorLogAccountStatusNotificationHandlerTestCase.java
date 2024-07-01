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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.ErrorLogAccountStatusNotificationHandlerCfgDefn;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.types.Entry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationProperty;
import org.opends.server.types.AccountStatusNotificationType;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.InitializationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.types.AccountStatusNotificationProperty.*;
import static org.opends.server.types.AccountStatusNotificationType.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

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
  @BeforeClass
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
   * @param  configEntry  The configuration entry to use to initialize the account status
   *            notificaton handler.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInvalidConfigs(Entry configEntry)
         throws Exception
  {
    InitializationUtils.initializeStatusNotificationHandler(
        new ErrorLogAccountStatusNotificationHandler(),
        configEntry,
        ErrorLogAccountStatusNotificationHandlerCfgDefn.getInstance());
  }



  /**
   * Tests to ensure that the error log account status notification handler is
   * configured and enabled within the Directory Server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testHandlerEnabled()
         throws Exception
  {
    String dnStr = "cn=Error Log Handler,cn=Account Status Notification " +
                        "Handlers,cn=config";
    DN handlerDN = DN.valueOf(dnStr);
    AccountStatusNotificationHandler<?> handler =
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
    DN handlerDN = DN.valueOf(dnStr);
    AccountStatusNotificationHandler<?> handler =
         DirectoryServer.getAccountStatusNotificationHandler(handlerDN);
    assertNotNull(handler);

    Entry userEntry = DirectoryServer.getEntry(DN.valueOf("uid=test.user,o=test"));
    HashMap<AccountStatusNotificationProperty, List<String>> notificationProperties = new HashMap<>();

    PasswordPolicy policy = (PasswordPolicy) AuthenticationPolicy.forUser(userEntry, false);
    notificationProperties.put(PASSWORD_POLICY_DN, newArrayList(policy.getDN().toString()));

    if (notificationType == ACCOUNT_TEMPORARILY_LOCKED)
    {
      notificationProperties.put(SECONDS_UNTIL_UNLOCK, newArrayList("300"));
      notificationProperties.put(TIME_UNTIL_UNLOCK, newArrayList("5 minutes"));

      Date date = new Date(System.currentTimeMillis() + 300000L);
      notificationProperties.put(ACCOUNT_UNLOCK_TIME, newArrayList(date.toString()));
    }
    else if (notificationType == PASSWORD_EXPIRING)
    {
      notificationProperties.put(SECONDS_UNTIL_EXPIRATION, newArrayList("86400"));
      notificationProperties.put(TIME_UNTIL_EXPIRATION, newArrayList("1 day"));

      Date date = new Date(System.currentTimeMillis() + 86400000L);
      notificationProperties.put(PASSWORD_EXPIRATION_TIME, newArrayList(date.toString()));
    }
    else if (notificationType == PASSWORD_CHANGED ||
             notificationType == PASSWORD_RESET)
    {
      notificationProperties.put(OLD_PASSWORD, newArrayList("oldpassword"));
      notificationProperties.put(NEW_PASSWORD,  newArrayList("newpassword"));
    }


    AccountStatusNotification notification =
         new AccountStatusNotification(notificationType, userEntry,
                                       LocalizableMessage.raw("Test Modification"),
                                       notificationProperties);
    handler.handleStatusNotification(notification);
  }
}
