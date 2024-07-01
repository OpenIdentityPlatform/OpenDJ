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

package org.opends.quicksetup.util;

import java.io.BufferedReader;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;

/** This class is used to read an input stream and process ouput. */
public abstract class OutputReader {

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Called whenever new input is read from the reader.
   * @param line String representing new input
   */
  public abstract void processLine(String line);

  /**
   * The protected constructor.
   *
   * @param reader  the BufferedReader of the stop process.
   */
  public OutputReader(final BufferedReader reader) {
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          String line;
          while (null != (line = reader.readLine())) {
            processLine(line);
          }
        } catch (Throwable t) {
          logger.info(LocalizableMessage.raw("error reading output"), t);
        }
      }
    });
    t.start();
  }
}
