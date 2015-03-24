/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;



import java.util.List;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.forgerock.opendj.ldap.ByteString;
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
  public UserAttributeNotificationMessageTemplateElement(AttributeType
                                                              attributeType)
  {
    this.attributeType = attributeType;
  }



  /** {@inheritDoc} */
  public void generateValue(LocalizableMessageBuilder buffer,
                            AccountStatusNotification notification)
  {
    Entry userEntry = notification.getUserEntry();

    List<Attribute> attrList = userEntry.getAttribute(attributeType);
    if (attrList != null)
    {
      for (Attribute a : attrList)
      {
        for (ByteString v : a)
        {
          buffer.append(v);
          return;
        }
      }
    }
  }
}

