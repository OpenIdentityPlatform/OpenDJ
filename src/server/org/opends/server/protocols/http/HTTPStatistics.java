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
import java.util.concurrent.atomic.AtomicLong;

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
   * Map containing the total number of requests per HTTP methods.
   * <p>
   * key: HTTP method => value: number of requests for that method.
   * </p>
   * Not using a ConcurrentMap implementation here because the keys are static.
   * The keys are static because they need to be listed in the schema which is
   * static.
   */
  private Map<String, AtomicInteger> requestMethodsTotalCount =
      new HashMap<String, AtomicInteger>();
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
  private Map<String, AtomicLong> requestMethodsTotalTime =
      new HashMap<String, AtomicLong>();
  /**
   * Total number of requests. The total number may be different than the sum of
   * the supported HTTP methods above because clients could use unsupported HTTP
   * methods.
   */
  private AtomicInteger requestsTotalCount = new AtomicInteger(0);

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

  /** {@inheritDoc} */
  @Override
  public List<Attribute> getMonitorData()
  {
    // first take a snapshot of all the data as fast as possible
    final int totalCount = this.requestsTotalCount.get();
    final Map<String, Integer> totalCountsSnapshot =
        new HashMap<String, Integer>();
    for (Entry<String, AtomicInteger> entry : this.requestMethodsTotalCount
        .entrySet())
    {
      totalCountsSnapshot.put(entry.getKey(), entry.getValue().get());
    }
    final Map<String, Long> totalTimesSnapshot = new HashMap<String, Long>();
    for (Entry<String, AtomicLong> entry1 : this.requestMethodsTotalTime
        .entrySet())
    {
      totalTimesSnapshot.put(entry1.getKey(), entry1.getValue().get());
    }

    // do the same with the underlying data
    final List<Attribute> results = super.getMonitorData();

    addAll(results, totalCountsSnapshot, "ds-mon-http-",
        "-requests-total-count");
    addAll(results, totalTimesSnapshot, "ds-mon-resident-time-http-",
        "-requests-total-time");
    results.add(createAttribute("ds-mon-http-requests-total-count", Integer
        .toString(totalCount)));

    return results;
  }

  private void addAll(final List<Attribute> results,
      final Map<String, ?> toOutput, String prefix, String suffix)
  {
    for (Entry<String, ?> entry : toOutput.entrySet())
    {
      final String httpMethod = entry.getKey();
      final String nb = entry.getValue().toString();
      results.add(createAttribute(prefix + httpMethod + suffix, nb));
    }
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
    AtomicInteger nb =
        this.requestMethodsTotalCount.get(httpMethod.toLowerCase());
    if (nb != null)
    {
      nb.incrementAndGet();
    } // else this is an unsupported HTTP method
    // always count any requests regardless of whether the method is supported
    this.requestsTotalCount.incrementAndGet();
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
