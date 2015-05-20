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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.file;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;

@SuppressWarnings("javadoc")
class FakeUpdateMsg extends UpdateMsg
{
  private final int t;

  FakeUpdateMsg(int t)
  {
    super(new CSN(t, t, t), new byte[1]);
    this.t = t;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "UpdateMsg(" + t + ")";
  }
}
