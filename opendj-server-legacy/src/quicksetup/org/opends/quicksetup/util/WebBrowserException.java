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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */

package org.opends.quicksetup.util;

import org.opends.server.types.OpenDsException;
import org.forgerock.i18n.LocalizableMessage;

/**
 * This class is the exception that we get when we try to launch the user web
 * browser and we fail.  The exception is generated in WebBrowserLauncher.
 *
 */
public class WebBrowserException extends OpenDsException {

  private static final long serialVersionUID = 4283835325192567244L;

  private String url;

  /**
   * Constructor of the WebBrowserException.
   * @param url the url that we were trying to display.
   * @param msg the error message.
   * @param rootCause the root cause.
   */
  public WebBrowserException(String url, LocalizableMessage msg, Throwable rootCause)
  {
    super(msg, rootCause);
    this.url = url;
  }

  /**
   * Returns the url that we were trying to display when the exception occurred.
   * @return the url that we were trying to display when the exception occurred.
   */
  public String getUrl()
  {
    return this.url;
  }
}
