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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.server.config.server.HTTPAccessLogPublisherCfg;

/**
 * This class defines the set of methods and structures that must be implemented
 * for a Directory Server HTTP access log publisher.
 *
 * @param <T>
 *          The type of HTTP access log publisher configuration handled by this
 *          log publisher implementation.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate = false,
    mayExtend = true,
    mayInvoke = false)
public abstract class HTTPAccessLogPublisher
    <T extends HTTPAccessLogPublisherCfg> implements LogPublisher<T>
{

  @Override
  public boolean isConfigurationAcceptable(T configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation. It should be overridden by HTTP access log publisher
    // implementations that wish to perform more detailed validation.
    return true;
  }

  /**
   * Logs the request info according to the configured extended log format.
   *
   * @param requestInfo
   *          The request info to log
   * @see <a href="http://www.w3.org/TR/WD-logfile.html">W3C's Extended Log File
   *      Format</a>
   * @see <a href=
   *      "http://www.microsoft.com/technet/prodtechnol/WindowsServer2003/
   *      Library/IIS/676400bc-8969-4aa7-851a-9319490a9bbb.mspx?mfr=true">
   *      Microsoft's W3C Extended Log File Format (IIS 6.0)</a>
   */
  public void logRequestInfo(HTTPRequestInfo requestInfo)
  {
    // Do nothing
  }

}
