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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.guitools.replicationcli;

/**
 * This class is used to store the information provided by the user to
 * perform the operations required before doing an initialization using
 * import-ldif of the binary copy.  It is required because when we
 * are in interactive mode the ReplicationCliArgumentParser is not enough.
 *
 */
class PreExternalInitializationUserData extends MonoServerReplicationUserData
{
  private boolean onlyLocal;

  /**
   * Whether the operation must be applied only on the local server or not.
   * @return <CODE>true</CODE> if the operation must be applied only on the
   * local server and <CODE>false</CODE> otherwise.
   */
  public boolean isOnlyLocal()
  {
    return onlyLocal;
  }

  /**
   * Sets whether the operation must be applied only on the local server or not.
   * @param onlyLocal whether the operation must be applied only on the local
   * server or not.
   */
  public void setOnlyLocal(boolean onlyLocal)
  {
    this.onlyLocal = onlyLocal;
  }

}
