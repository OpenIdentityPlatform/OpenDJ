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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.protocols.http;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.protocols.ldap.LDAPStatistics;
import org.opends.server.types.Attribute;

/**
 * Collects statistics for HTTP. This class inherits from {@link LDAPStatistics}
 * to show the administrator how the underlying LDAP internal operations are
 * performing.
 */
public class HTTPStatistics extends LDAPStatistics
{

  /**
   * Map containing the total number of requests.
   * <p>
   * key: HTTP verb => value: number of requests for that verb.
   * </p>
   * Not using a ConcurrentMap implementation because the keys are static. The
   * keys are static because they need to be listed in the schema which is
   * static.
   */
  private Map<String, AtomicInteger> nbRequests =
      new HashMap<String, AtomicInteger>();
  /**
   * Total number of requests. The total number may be different than the sum of
   * the supported HTTP methods above because clients could use unsupported HTTP
   * verbs.
   */
  private AtomicInteger nbRequestsTotalCount = new AtomicInteger(0);

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
      nbRequests.put(method, new AtomicInteger(0));
    }
  }

  /** {@inheritDoc} */
  @Override
  public void clearStatistics()
  {
    this.nbRequests.clear();

    super.clearStatistics();
  }

  /** {@inheritDoc} */
  @Override
  public List<Attribute> getMonitorData()
  {
    // first take a snapshot of all the data as fast as possible
    final Map<String, Integer> snapshot = new HashMap<String, Integer>();
    for (Entry<String, AtomicInteger> entry : this.nbRequests.entrySet())
    {
      snapshot.put(entry.getKey(), entry.getValue().get());
    }

    // do the same with the underlying data
    final List<Attribute> results = super.getMonitorData();

    // then add the snapshot data to the monitoring data
    int total = 0;
    for (Entry<String, Integer> entry : snapshot.entrySet())
    {
      final String httpMethod = entry.getKey();
      final Integer nb = entry.getValue();
      final String number = nb.toString();
      // nb should never be null since we only allow supported HTTP methods
      total += nb;

      results.add(createAttribute("ds-mon-http-" + httpMethod
          + "-requests-total-count", number));
    }
    results.add(createAttribute("ds-mon-http-requests-total-count", Integer
        .toString(total)));

    return results;
  }

  /**
   * Adds a request to the stats using the provided HTTP method.
   *
   * @param httpMethod
   *          the method of the HTTP request to add to the stats
   * @throws NullPointerException
   *           if the httpMethod is null
   */
  public void addRequest(String httpMethod) throws NullPointerException
  {
    AtomicInteger nb = this.nbRequests.get(httpMethod.toLowerCase());
    if (nb != null)
    {
      nb.incrementAndGet();
    } // else this is an unsupported HTTP method
    // always count any requests regardless of whether the method is supported
    this.nbRequestsTotalCount.incrementAndGet();
  }
}
