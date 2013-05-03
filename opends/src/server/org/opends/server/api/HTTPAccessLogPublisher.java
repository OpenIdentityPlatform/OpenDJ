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
   * Logs the request info according to the common logfile format. The common
   * logfile format is as follows:
   *
   * <pre>
   * remotehost rfc931 authuser [date] "request" status bytes "useragent"
   * </pre>
   * <dl>
   * <dt>remotehost</dt>
   * <dd>Remote hostname (or IP number if DNS hostname is not available, or if
   * DNSLookup is Off.</dd>
   * <dt>rfc931</dt>
   * <dd>The remote logname of the user.</dd>
   * <dt>authuser</dt>
   * <dd>The username as which the user has authenticated himself.</dd>
   * <dt>[date]</dt>
   * <dd>Date and time of the request.</dd>
   * <dt>"request"</dt>
   * <dd>The request line exactly as it came from the client.</dd>
   * <dt>status</dt>
   * <dd>The HTTP status code returned to the client.</dd>
   * <dt>bytes</dt>
   * <dd>The content-length of the document transferred.</dd>
   * <dt>"useragent"</dt>
   * <dd>The user agent that issued the request.</dd>
   * </dl>
   * <p>
   * <b>NOTE:</b> The bytes field is not currently supported.
   * </p>
   *
   * @param requestInfo
   *          The request info to log
   */
  public void logRequestInfo(HTTPRequestInfo requestInfo)
  {
    // Do nothing
  }

}
