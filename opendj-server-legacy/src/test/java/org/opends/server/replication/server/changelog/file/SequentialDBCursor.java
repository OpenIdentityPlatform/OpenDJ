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
package org.opends.server.replication.server.changelog.file;

import java.util.List;

import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.util.CollectionUtils;

@SuppressWarnings("javadoc")
class SequentialDBCursor implements DBCursor<UpdateMsg>
{
  private final List<UpdateMsg> msgs;
  private UpdateMsg current;

  /**
   * A cursor built from a list of update messages.
   * <p>
   * This cursor provides a java.sql.ResultSet-like API to be consistent with the
   * {@code DBCursor} API : it is positioned before the first requested record
   * and needs to be moved forward by calling {@link DBCursor#next()}.
   */
  public SequentialDBCursor(UpdateMsg... msgs)
  {
    this.msgs = CollectionUtils.newArrayList(msgs);
  }

  public void add(UpdateMsg msg)
  {
    this.msgs.add(msg);
  }

  @Override
  public UpdateMsg getRecord()
  {
    return current;
  }

  @Override
  public boolean next()
  {
    if (!msgs.isEmpty())
    {
      current = msgs.remove(0);
      return current != null;
    }
    current = null;
    return false;
  }

  @Override
  public void close()
  {
    // nothing to do
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "(currentRecord=" + current + " nextMessages=" + msgs + ")";
  }
}
