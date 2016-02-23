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
package org.opends.server;

import org.testng.TestNG;
import static org.opends.server.TestCaseUtils.originalSystemErr;

/**
 * This class wraps TestNG so that we can force the process to exit if there
 * is an uncaught exception (e.g. OutOfMemoryError).
 */
public class SuiteRunner {
  public static void main(String[] args) {
    try {
      TestNG.main(args);
    } catch (Throwable e) {
      originalSystemErr.println("TestNG.main threw an expected exception:");
      e.printStackTrace(originalSystemErr);
      System.exit(1);
    }
  }
}
