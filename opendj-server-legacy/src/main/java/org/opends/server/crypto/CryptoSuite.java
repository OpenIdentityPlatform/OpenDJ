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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.crypto;

import net.jcip.annotations.Immutable;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.CryptoManagerException;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

import static org.opends.messages.CoreMessages.*;

/** Defines cipher transformation and hash algorithm for cryptographic related operations. */
public class CryptoSuite
{
  /** Cipher specific settings that can change at runtime. */
  @Immutable
  private static final class CipherInfo
  {
    private final String cipherTransformation;
    private final int cipherKeyLength;
    private final boolean encrypt;

    CipherInfo(String cipherTransformation, int cipherKeyLength, boolean encrypt)
    {
      this.cipherTransformation = cipherTransformation;
      this.cipherKeyLength = cipherKeyLength;
      this.encrypt = encrypt;
    }
  }

  private volatile CipherInfo cipherInfo;
  private final CryptoManager cryptoManager;

  /**
   * Declares a new CryptoSuite with provided parameters.
   * @param cryptoManager the CryptoManager to use for cryptographic operations
   * @param cipherTransformation the initial cipher transformation
   * @param cipherKeyLength the initial key length for the cipher
   * @param encrypt if the user of the crypto suite needs encryption
   */
  public CryptoSuite(CryptoManager cryptoManager, String cipherTransformation, int cipherKeyLength, boolean encrypt)
  {
    this.cryptoManager = cryptoManager;
    this.cipherInfo = new CipherInfo(cipherTransformation, cipherKeyLength, encrypt);
  }

  /**
   * Set new cipher and enable parameters for the crypto suite.
   *
   * @param cipherTransformation the new cipher transformation
   * @param cipherKeyLength the new key length
   * @param enabled true if the user of the crypto suite needs encryption
   */
  public void newParameters(String cipherTransformation, int cipherKeyLength, boolean enabled)
  {
    cipherInfo = new CipherInfo(cipherTransformation, cipherKeyLength, enabled);
  }

  /**
   * Decrypts data using the key specified in the prologue.
   *
   * @param data the cipher-text to be decrypted (contains prologue)
   * @return a byte array with the clear-text
   * @throws GeneralSecurityException if a problem occurs while decrypting the data
   * @throws CryptoManagerException if a problem occurs during cipher initialization
   */
  public byte[] decrypt(byte[] data) throws GeneralSecurityException, CryptoManagerException
  {
    return cryptoManager.decrypt(data);
  }

  /**
   * Encrypts data with the configured cipher transformation and key length.
   *
   * @param data the clear-text data to encrypt
   * @return a byte array with a prologue containing the key identifier followed by cipher-text
   * @throws GeneralSecurityException if a problem occurs while encrypting the data
   * @throws CryptoManagerException if a problem occurs during cipher initialization
   */
  public byte[] encrypt(byte[] data) throws GeneralSecurityException, CryptoManagerException
  {
    CipherInfo currentCipher = cipherInfo;
    return cryptoManager.encrypt(currentCipher.cipherTransformation, currentCipher.cipherKeyLength, data);
  }

  /**
   * Returns a {@link CipherOutputStream} for encrypting through a sequence of
   * OutputStreams.
   *
   * @param os the up-link OutputStream
   * @return a {@link CipherOutputStream} for encrypting through a sequence of
   * OutputStreams
   * @throws CryptoManagerException if a problem occurs during cipher initialization
   */
  public CipherOutputStream getCipherOutputStream(OutputStream os) throws CryptoManagerException
  {
    CipherInfo currentCipher = cipherInfo;
    return cryptoManager.getCipherOutputStream(currentCipher.cipherTransformation, currentCipher.cipherKeyLength, os);
  }

  /**
   * Returns a {@link CipherInputStream} for decrypting through a sequence of InputStreams.
   *
   * @param is the up-link InputStream
   * @return a {@link CipherInputStream} for decrypting through a sequence of InputStreams.
   * @throws CryptoManagerException if a problem occurs during cipher initialization
   */
  public CipherInputStream getCipherInputStream(InputStream is) throws CryptoManagerException
  {
    return cryptoManager.getCipherInputStream(is);
  }

  /**
   * Returns a ByteString of 6 bytes hash of the data.
   *
   * @param data a ByteSequence containing the input data to be hashed
   * @return a ByteString of 6 bytes hash of the data.
   * @throws DecodeException if digest of the data cannot be computed
   */
  public ByteString hash48(ByteSequence data) throws DecodeException
  {
    try
    {
      byte[] hash = cryptoManager.digest("SHA-1", data.toByteArray());
      return ByteString.valueOfBytes(hash, 0, 6);
    }
    catch (NoSuchAlgorithmException e)
    {
      throw DecodeException.error(ERR_CANNOT_HASH_DATA.get());
    }
  }

  /**
   * Returns whether the user of the crypto suite needs encryption.
   *
   * @return true if the user of the crypto suite needs encryption
   */
  public boolean isEncrypted()
  {
    return cipherInfo.encrypt;
  }

  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    CipherInfo currentCipher = cipherInfo;
    builder.append("CryptoSuite(cipherTransformation=");
    builder.append(currentCipher.cipherTransformation);
    builder.append(", keyLength=");
    builder.append(currentCipher.cipherKeyLength);
    builder.append(", encrypt=");
    builder.append(currentCipher.encrypt);
    builder.append(")");
    return builder.toString();
  }
}
