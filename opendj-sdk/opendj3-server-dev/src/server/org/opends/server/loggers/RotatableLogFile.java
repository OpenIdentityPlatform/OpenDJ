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
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.util.Calendar;

/**
 * Represents a file that can be rotated based on size or on time.
 */
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
