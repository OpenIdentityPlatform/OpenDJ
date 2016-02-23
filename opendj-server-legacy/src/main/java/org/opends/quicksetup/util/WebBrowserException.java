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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
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
