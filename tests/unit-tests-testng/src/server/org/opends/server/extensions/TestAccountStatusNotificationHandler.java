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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.admin.std.server.
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
  // The number of notifications that this handler has processed.
  private static AtomicInteger notificationsProcessed = new AtomicInteger(0);



  /**
   * Creates a new instance of this test account status notification handler.
   */
  public TestAccountStatusNotificationHandler()
  {
    // No implementation is required.
  }



  /**
   * {@inheritDoc}
   */
  public void initializeStatusNotificationHandler(
                   AccountStatusNotificationHandlerCfg configuration)
  {
    // No implementation is required.
  }



  /**
   * {@inheritDoc}
   */
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

