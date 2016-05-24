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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.quicksetup.util;

import java.io.OutputStream;
import java.io.IOException;
import java.io.PrintStream;

/** Tool for suppressing and unsuppressing output to standard output streams. */
public class StandardOutputSuppressor {

  private static Token token;

  /** Object to return to this class for unsuppressing output. */
  private static class Token {
    PrintStream out;
    PrintStream err;
  }

  /** Suppresses output to the standard output streams. */
  public static synchronized void suppress() {
    if (token == null) {
      token = new Token();
      token.out = System.out;
      token.err = System.err;
      System.out.flush();
      System.err.flush();
      System.setOut(new PrintStream(new NullOutputStream()));
      System.setErr(new PrintStream(new NullOutputStream()));
    } else {
      throw new IllegalStateException("Standard streams currently suppressed");
    }
  }

  /**
   * Unsuppresses the standard output streams.  Following a call to this
   * method System.out and System.err will point to the descriptor prior
   * to calling <code>suppress()</code>.
   */
  public static synchronized void unsuppress() {
    if (token != null) {
      System.setOut(token.out);
      System.setErr(token.err);
      token = null;
    } else {
      throw new IllegalStateException(
              "Standard streams not currently suppressed");
    }
  }

  /**
   * Checks whether this class has suppressed standard out streams.
   * @return boolean where true indicates output is suppressed
   */
  public static boolean isSuppressed() {
    return token != null;
  }

  /** PrintWriter for suppressing stream. */
  private static class NullOutputStream extends OutputStream {

    @Override
    public void write(int b) throws IOException {
      // do nothing;
    }
  }
}
