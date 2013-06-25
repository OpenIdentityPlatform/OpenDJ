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



import java.util.List;

import org.opends.messages.MessageBuilder;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationProperty;



/**
 * This class implements a notification message template element that will
 * generate a value that is the value of a specified notification property.
 */
public class NotificationPropertyNotificationMessageTemplateElement
       extends NotificationMessageTemplateElement
{
  // The account status notification property for which to obtain the value.
  private AccountStatusNotificationProperty property;



  /**
   * Creates a new notification property notification message template element.
   *
   * @param  property  The notification property for which to obtain the value.
   */
  public NotificationPropertyNotificationMessageTemplateElement(
              AccountStatusNotificationProperty property)
  {
    this.property = property;
  }



  /**
   * {@inheritDoc}
   */
  public void generateValue(MessageBuilder buffer,
                            AccountStatusNotification notification)
  {
    List<String> propertyValues =
         notification.getNotificationProperty(property);
    if ((propertyValues != null) && (! propertyValues.isEmpty()))
    {
      buffer.append(propertyValues.get(0));
    }
  }
}

