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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;

/**
 * This class implements a notification message template element that will
 * generate a value that is the value of a specified attribute from the target
 * user's entry.
 */
public class UserAttributeNotificationMessageTemplateElement
       extends NotificationMessageTemplateElement
{
  /** The attribute type for which to obtain the value. */
  private AttributeType attributeType;

  /**
   * Creates a new user DN notification message template element.
   *
   * @param  attributeType  The attribute type for which to obtain the value.
   */
  public UserAttributeNotificationMessageTemplateElement(AttributeType attributeType)
  {
    this.attributeType = attributeType;
  }

  @Override
  public void generateValue(LocalizableMessageBuilder buffer,
                            AccountStatusNotification notification)
  {
    Entry userEntry = notification.getUserEntry();
    for (Attribute a : userEntry.getAllAttributes(attributeType))
    {
      for (ByteString v : a)
      {
        buffer.append(v);
        return;
      }
    }
  }
}
