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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

import java.io.File;

/**
 * This class implements a post rotation action that encrypts
 * the log file.
 */
public class EncryptAction implements PostRotationAction
{

  private File originalFile;
  private String newFile;
  private boolean deleteOriginal = false;
  private String encryptAlgorithm = "RSA";
  private String alias = null;

  /**
   * Create the action based on the original file, the new file after
   * encrypting and whether the original file should be deleted.
   *
   * @param  origFile        The source file to be encrypted.
   * @param  newFile         The new file to which the encrypted data should be
   *                         written.
   * @param  deleteOriginal  Indicates whether the original file should be
   *                         deleted.
   * @param  alias           The nickname of the certificate to use for the
   *                         encryption.
   * @param  encryptAlg      The encryption algorithm that should be used.
   */
  public EncryptAction(String origFile, String newFile,
      boolean deleteOriginal, String alias, String encryptAlg)
  {
    this.originalFile = new File(origFile);
    this.newFile = newFile;
    this.deleteOriginal = deleteOriginal;
    this.alias = alias;
    this.encryptAlgorithm = encryptAlg;
  }


  /**
   * The signature action that is executed. Returns true if the
   * encryption succeeded and false otherwise.
   *
   * @return  <CODE>true</CODE> if the encryption succeeded, or
   *          <CODE>false</CODE> if it did not.
   */
  public boolean execute()
  {
    // NYI.
    return true;
  }


}

