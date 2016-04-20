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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.io.File;

/** This class implements a post rotation action that encrypts the log file. */
class EncryptAction implements PostRotationAction
{

  private File originalFile;
  private String newFile;
  private boolean deleteOriginal;
  private String encryptAlgorithm = "RSA";
  private String alias;

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
  EncryptAction(String origFile, String newFile,
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
  @Override
  public boolean execute()
  {
    // NYI.
    return true;
  }
}
