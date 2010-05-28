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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.util.concurrent.TimeUnit;

import org.opends.sdk.requests.Requests;
import org.opends.sdk.requests.SearchRequest;



/**
 * Tests the Heart beat connection factory.
 */
public class HeartBeatConnectionFactoryTestCase extends
    ConnectionFactoryTestCase
{
  // Use custom search request.
  SearchRequest request = Requests.newSearchRequest(
      "uid=user.0,ou=people,o=test", SearchScope.BASE_OBJECT, "objectclass=*",
      "cn");

  // The factory.
  private final HeartBeatConnectionFactory factory = new HeartBeatConnectionFactory(
      new LDAPConnectionFactory("localhost", TestCaseUtils.getLdapPort()),
      1000, TimeUnit.MILLISECONDS, request);



  @Override
  protected FutureResult<AsynchronousConnection> getAsynchronousConnection(
      final ResultHandler<AsynchronousConnection> handler) throws Exception
  {
    return factory.getAsynchronousConnection(handler);
  }



  @Override
  protected Connection getConnection() throws Exception
  {
    return factory.getConnection();
  }
}
