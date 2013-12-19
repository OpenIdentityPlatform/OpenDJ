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
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2010-2013 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.je;

import java.io.UnsupportedEncodingException;

import com.sleepycat.je.DatabaseEntry;

import static org.opends.server.util.StaticUtils.*;

/**
 * Useful to create ReplicationServer keys from sequence numbers.
 */
public class ReplicationDraftCNKey extends DatabaseEntry
{
  private static final long serialVersionUID = 1L;

  /**
   * Creates a ReplicationDraftCNKey that can start anywhere in the DB.
   */
  public ReplicationDraftCNKey()
  {
    super();
  }

  /**
   * Creates a new ReplicationKey from the given change number.
   *
   * @param changeNumber
   *          The change number to use.
   */
  public ReplicationDraftCNKey(long changeNumber)
  {
    try
    {
      // Should it use StaticUtils.getBytes() to increase performances?
      setData(String.format("%016d", changeNumber).getBytes("UTF-8"));
    }
    catch (UnsupportedEncodingException e)
    {
      // Should never happens, UTF-8 is always supported
      // TODO : add better logging
    }
  }

  /**
   * Getter for the change number associated with this key.
   *
   * @return the change number associated with this key.
   */
  public long getChangeNumber()
  {
    return Long.valueOf(decodeUTF8(getData()));
  }
}
