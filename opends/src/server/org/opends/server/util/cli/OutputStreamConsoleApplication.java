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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.util.cli;



/**
 * A console application decorator which redirects all output to the
 * underlying application's output stream.
 */
public class OutputStreamConsoleApplication extends ConsoleApplication {

  // The underlying console application.
  private final ConsoleApplication app;



  /**
   * Creates a new console application instance which redirects all
   * output to the underlying application's output stream.
   *
   * @param app
   *          The underlying application console.
   */
  public OutputStreamConsoleApplication(ConsoleApplication app) {
    super(app.getInputStream(), app.getOutputStream(), app.getOutputStream());

    this.app = app;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isInteractive() {
    return app.isInteractive();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isMenuDrivenMode() {
    return app.isMenuDrivenMode();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isQuiet() {
    return app.isQuiet();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isScriptFriendly() {
    return app.isScriptFriendly();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isVerbose() {
    return app.isVerbose();
  }

}
