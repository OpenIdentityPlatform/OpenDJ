/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.util;

import java.io.OutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Tool for suppressing and unsuppressing output to standard
 * output streams.
 */
public class StandardOutputSuppressor {

  static private Token token = null;

  /**
   * Object to return to this class for
   * unsupressing output.
   */
  static private class Token {
    PrintStream out;
    PrintStream err;
  }

  /**
   * Suppresses output to the standard output
   * streams.
   */
  static synchronized public void suppress() {
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
   * Unsupresses the standard output streams.  Following a call to this
   * method System.out and System.err will point to the descriptor prior
   * to calling <code>suppress()</code>.
   */
  static synchronized public void unsuppress() {
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
   * Checks whether or not this class has suppressed standard out streams.
   * @return boolean where true indicates output is suppressed
   */
  static public boolean isSuppressed() {
    return token != null;
  }

  /**
   * PrintWriter for suppressing stream.
   */
  static private class NullOutputStream extends OutputStream {

    /**
     * {@inheritDoc}
     */
    public void write(int b) throws IOException {
      // do nothing;
    }

  }


}
