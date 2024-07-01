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
 * Portions Copyright 2014 ForgeRock AS.
 */
package org.opends.server.extensions;



import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.server.types.AccountStatusNotification;



/**
 * This class defines the base class for elements that may be used to generate
 * an account status notification message.
 */
public abstract class NotificationMessageTemplateElement
{
  /**
   * Generates a value for this template element using the information contained
   * in the provided account status notification and appends it to the given
   * buffer.
   *
   * @param  buffer        The buffer to which the generated value should be
   *                       appended.
   * @param  notification  The account status notification to process.
   */
  public abstract void generateValue(LocalizableMessageBuilder buffer,
                                     AccountStatusNotification notification);
}

