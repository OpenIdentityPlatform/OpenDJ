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
 *      Copyright 2013 ForgeRock AS.
 */
package org.opends.server.api;

import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.HTTPAccessLogPublisherCfg;
import org.opends.server.loggers.HTTPRequestInfo;

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

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(T configuration,
      List<Message> unacceptableReasons)
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
