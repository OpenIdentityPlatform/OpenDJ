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
 *      Copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.sync.filters;



import java.io.Closeable;

import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;



/**
 * A change record filter. Transforms may modify the content of the provided
 * change record.
 */
public interface Filter extends Closeable
{
  /**
   * Release any resources associated with this filter. This method is called
   * during application termination.
   */
  @Override
  void close();



  /**
   * Transforms the provided add request.
   *
   * @param context
   *          Additional information associated with the change record.
   * @param change
   *          The add request to be filtered or modified.
   * @return The result of the filter.
   */
  FilterResult filterChangeRecord(ChangeRecordContext context, AddRequest change);



  /**
   * Transforms the provided delete request.
   *
   * @param context
   *          Additional information associated with the change record.
   * @param change
   *          The delete request to be filtered or modified.
   * @return The result of the filter.
   */
  FilterResult filterChangeRecord(ChangeRecordContext context,
      DeleteRequest change);



  /**
   * Transforms the provided modify DN request.
   *
   * @param context
   *          Additional information associated with the change record.
   * @param change
   *          The modify DN request to be filtered or modified.
   * @return The result of the filter.
   */
  FilterResult filterChangeRecord(ChangeRecordContext context,
      ModifyDNRequest change);



  /**
   * Transforms the provided modify request.
   *
   * @param context
   *          Additional information associated with the change record.
   * @param change
   *          The modify request to be filtered or modified.
   * @return The result of the filter.
   */
  FilterResult filterChangeRecord(ChangeRecordContext context,
      ModifyRequest change);
}
