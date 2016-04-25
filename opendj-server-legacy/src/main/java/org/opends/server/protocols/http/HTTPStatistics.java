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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.protocols.http;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.opends.server.api.MonitorData;
import org.opends.server.protocols.ldap.LDAPStatistics;

/**
 * Collects statistics for HTTP. This class inherits from {@link LDAPStatistics}
 * to show the administrator how the underlying LDAP internal operations are
 * performing.
 */
public class HTTPStatistics extends LDAPStatistics
{
  /**
   * Map containing the total number of requests per HTTP methods.
   * <p>
   * key: HTTP method => value: number of requests for that method.
   * </p>
   * Not using a ConcurrentMap implementation here because the keys are static.
   * The keys are static because they need to be listed in the schema which is
   * static.
   */
  private final Map<String, AtomicInteger> requestMethodsTotalCount = new HashMap<>();
  /**
   * Map containing the total execution time for the requests per HTTP methods.
   * <p>
   * key: HTTP method => value: total execution time for requests using that
   * method.
   * </p>
   * Not using a ConcurrentMap implementation here because the keys are static.
   * The keys are static because they need to be listed in the schema which is
   * static.
   */
  private final Map<String, AtomicLong> requestMethodsTotalTime = new HashMap<>();
  /**
   * Total number of requests. The total number may be different than the sum of
   * the supported HTTP methods above because clients could use unsupported HTTP
   * methods.
   */
  private final AtomicInteger requestsTotalCount = new AtomicInteger(0);

  /**
   * Constructor for this class.
   *
   * @param instanceName
   *          The name for this monitor provider instance.
   */
  public HTTPStatistics(String instanceName)
  {
    super(instanceName);

    // List the HTTP methods supported by Rest2LDAP
    final List<String> supportedHttpMethods =
        Arrays.asList("delete", "get", "patch", "post", "put");
    for (String method : supportedHttpMethods)
    {
      requestMethodsTotalCount.put(method, new AtomicInteger(0));
      requestMethodsTotalTime.put(method, new AtomicLong(0));
    }
  }

  /** {@inheritDoc} */
  @Override
  public void clearStatistics()
  {
    this.requestMethodsTotalCount.clear();
    this.requestMethodsTotalTime.clear();
    this.requestsTotalCount.set(0);

    super.clearStatistics();
  }

  @Override
  public MonitorData getMonitorData()
  {
    final MonitorData results = super.getMonitorData();
    addAll(results, requestMethodsTotalCount, "ds-mon-http-", "-requests-total-count");
    addAll(results, requestMethodsTotalTime, "ds-mon-resident-time-http-", "-requests-total-time");
    results.add("ds-mon-http-requests-total-count", requestsTotalCount.get());
    return results;
  }

  private void addAll(final MonitorData results,
      final Map<String, ?> toOutput, String prefix, String suffix)
  {
    for (Entry<String, ?> entry : toOutput.entrySet())
    {
      final String httpMethod = entry.getKey();
      final String nb = entry.getValue().toString();
      results.add(prefix + httpMethod + suffix, nb);
    }
  }

  /**
   * Adds to the total time of an HTTP request method.
   *
   * @param httpMethod
   *          the method of the HTTP request to add to the stats
   * @param time
   *          the time to add to the total
   * @throws NullPointerException
   *           if the httpMethod is null
   */
  public void updateRequestMonitoringData(String httpMethod, long time)
      throws NullPointerException
  {
    AtomicLong nb = this.requestMethodsTotalTime.get(httpMethod.toLowerCase());
    if (nb != null)
    {
      nb.addAndGet(time);
    } // else this is an unsupported HTTP method
  }
}
