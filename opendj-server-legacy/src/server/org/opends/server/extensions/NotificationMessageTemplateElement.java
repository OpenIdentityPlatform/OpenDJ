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
 *      Portions Copyright 2014 ForgeRock AS
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

