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

package org.opends.guitools.controlpanel.datamodel;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.types.OpenDsException;

/**
 * Exception that occurs when the user ask to make a modification that is not
 * handled by ModifyEntryTask.
 * @see <CODE>org.opends.guitools.controlpanel.task.ModifyEntryTask</CODE>
 */
public class CannotRenameException extends OpenDsException
{
  private static final long serialVersionUID = 6445729932279305687L;

  /**
   * Constructor.
   * @param msg the message describing why the entry cannot be renamed.
   */
  public CannotRenameException(LocalizableMessage msg)
  {
    super(msg);
  }
}
