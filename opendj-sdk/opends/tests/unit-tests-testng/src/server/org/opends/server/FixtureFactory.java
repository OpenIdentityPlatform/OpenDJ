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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.server;

/**
 * A factory interface for controlling construction and finalization of
 * fixtures.
 *
 * @param <T>
 *          The type of fixture managed by this factory.
 */
public interface FixtureFactory<T> {
  /**
   * Create and initialize the fixture instance.
   *
   * @return The fixture instance.
   * @throws Exception
   *           If the fixture instance could not be initialized
   *           successfully.
   */
  public T setUp() throws Exception;

  /**
   * Tear down the fixture instance, releasing any resources in the
   * process.
   *
   * @throws Exception
   *           If the fixture instance could not be finalized.
   */
  public void tearDown() throws Exception;
}
