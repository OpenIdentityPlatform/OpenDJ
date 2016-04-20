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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.opendj.server.config.server.
       AccountStatusNotificationHandlerCfg;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.types.AccountStatusNotification;

import static org.testng.Assert.*;
import static org.opends.server.types.AccountStatusNotificationProperty.*;

/**
 * This class implements a simple account status notification handler that may
 * be used to ensure that all notifications are generated properly.
 */
public class TestAccountStatusNotificationHandler
extends AccountStatusNotificationHandler<AccountStatusNotificationHandlerCfg>
{
  /** The number of notifications that this handler has processed. */
  private static AtomicInteger notificationsProcessed = new AtomicInteger(0);

  /** Creates a new instance of this test account status notification handler. */
  public TestAccountStatusNotificationHandler()
  {
    // No implementation is required.
  }

  @Override
  public void initializeStatusNotificationHandler(
                   AccountStatusNotificationHandlerCfg configuration)
  {
    // No implementation is required.
  }

  @Override
  public void handleStatusNotification(AccountStatusNotification notification)
  {
    notificationsProcessed.incrementAndGet();

    assertNotNull(notification);
    assertNotNull(notification.getNotificationType());
    assertNotNull(notification.getUserDN());
    assertNotNull(notification.getUserEntry());
    assertNotNull(notification.getMessage());
    assertNotNull(notification.getNotificationProperties());
    assertNotNull(
         notification.getNotificationProperties().get(PASSWORD_POLICY_DN));

    switch (notification.getNotificationType())
    {
      case ACCOUNT_TEMPORARILY_LOCKED:
        assertNotNull(
             notification.getNotificationProperties().get(ACCOUNT_UNLOCK_TIME));
        assertNotNull(
             notification.getNotificationProperties().get(
                  SECONDS_UNTIL_UNLOCK));
        assertNotNull(
             notification.getNotificationProperties().get(TIME_UNTIL_UNLOCK));
        break;

      case PASSWORD_EXPIRING:
        assertNotNull(
             notification.getNotificationProperties().get(
                  PASSWORD_EXPIRATION_TIME));
        assertNotNull(
             notification.getNotificationProperties().get(
                  SECONDS_UNTIL_EXPIRATION));
        assertNotNull(
             notification.getNotificationProperties().get(
                  TIME_UNTIL_EXPIRATION));
        break;

      case PASSWORD_CHANGED:
      case PASSWORD_RESET:
        // Note that the old password may not always be available, so we
        // can't check for it.
        assertNotNull(
             notification.getNotificationProperties().get(NEW_PASSWORD));
        break;
    }
  }

  /**
   * Retrieves the number of account status notifications that this handler has
   * processed.
   *
   * @return  The number of account status notifications that this handler has
   *          processed.
   */
  public static int getNotificationsProcessed()
  {
    return notificationsProcessed.get();
  }

  /**
   * Resets the counter used to keep track fo the number of account status
   * notfications that this handler has processed.
   */
  public static void resetCount()
  {
    notificationsProcessed.set(0);
  }
}
