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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2010-2011 ForgeRock AS.
 */
package org.opends.server.replication.server;

import java.io.UnsupportedEncodingException;

import com.sleepycat.je.DatabaseEntry;

/**
 * Superclass of DatabaseEntry.
 * Useful to create ReplicationServer keys from sequence numbers.
 */
public class ReplicationDraftCNKey extends DatabaseEntry
{
  private static final long serialVersionUID = 1L;

  /**
   * Creates a new ReplicationKey from the given draft ChangeNumber.
   * @param draftCN The draft change number to use.
   */
  public ReplicationDraftCNKey(int draftCN)
  {
    try
    {
      String s = String.valueOf(draftCN);
      int a = 16-s.length();
      String sscn = "0000000000000000".substring(0, a) + s;
      this.setData(sscn.getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e)
    {
      // Should never happens, UTF-8 is always supported
      // TODO : add better logging
    }
  }

  /**
   * Getter for the draft change number associated with this key.
   * @return the draft change number associated with this key.
   */
  public int getDraftCN()
  {
    String s = new String(this.getData());
    int i = Integer.valueOf(s);
    return i;
  }
}
