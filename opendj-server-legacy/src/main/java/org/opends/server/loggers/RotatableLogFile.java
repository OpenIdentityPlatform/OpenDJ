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
 * Copyright 2014 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.util.Calendar;

/** Represents a file that can be rotated based on size or on time. */
public interface RotatableLogFile
{
  /**
   * Retrieves the number of bytes written to the file.
   *
   * @return The number of bytes written to the file.
   */
  long getBytesWritten();

  /**
   * Retrieves the last time the file was rotated. If a file rotation never
   * occurred, this value will be the time the server started.
   *
   * @return The last time file rotation occurred.
   */
  Calendar getLastRotationTime();
}
