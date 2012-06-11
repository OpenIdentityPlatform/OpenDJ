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
 *      Copyright 2011-2012 profiq s.r.o.
 *      Portions copyright 2011 ForgeRock AS.
 */
package org.opends.server.plugins;



import static org.opends.messages.PluginMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.util.StaticUtils.bytesToHexNoSpace;
import static org.opends.server.util.StaticUtils.toLowerCase;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.PluginCfgDefn;
import org.opends.server.admin.std.meta.SambaPasswordPluginCfgDefn.*;
import org.opends.server.admin.std.server.SambaPasswordPluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.ConfigException;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.extensions.PasswordModifyExtendedOperation;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationExtendedOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;



/**
 * The Samba password synchronization plugin implementation class.
 * <p>
 * This plugin synchronizes the userPassword attribute with the Samba password
 * attribute(s) for all entries containing the specified Samba object class.
 * <p>
 * It handles clear-text userPassword modify operations and password modify
 * extended operations. It does not cover the case of using pre-encoded
 * password.
 */
public final class SambaPasswordPlugin extends
    DirectoryServerPlugin<SambaPasswordPluginCfg> implements
    ConfigurationChangeListener<SambaPasswordPluginCfg>
{

  /**
   * The implementation of this algorithm has been derived from BouncyCastle.org
   * whose license can be found at http://www.bouncycastle.org/licence.html:
   * <p>
   * Copyright (c) 2000 - 2011 The Legion Of The Bouncy Castle
   * (http://www.bouncycastle.org)
   * <p>
   * Permission is hereby granted, free of charge, to any person obtaining a
   * copy of this software and associated documentation files (the "Software"),
   * to deal in the Software without restriction, including without limitation
   * the rights to use, copy, modify, merge, publish, distribute, sublicense,
   * and/or sell copies of the Software, and to permit persons to whom the
   * Software is furnished to do so, subject to the following conditions:
   * <p>
   * The above copyright notice and this permission notice shall be included in
   * all copies or substantial portions of the Software.
   * <p>
   * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
   * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
   * DEALINGS IN THE SOFTWARE.
   */
  static final class MD4MessageDigest extends MessageDigest
  {
    // Class is package private for testing.
    private final byte[] xBuf = new byte[4];
    private int xBufOff;
    private long byteCount;

    private static final int DIGEST_LENGTH = 16;
    private int H1, H2, H3, H4; // IV's
    private final int[] X = new int[16];
    private int xOff;

    //
    // round 1 left rotates
    //
    private static final int S11 = 3;
    private static final int S12 = 7;
    private static final int S13 = 11;
    private static final int S14 = 19;

    //
    // round 2 left rotates
    //
    private static final int S21 = 3;
    private static final int S22 = 5;
    private static final int S23 = 9;
    private static final int S24 = 13;

    //
    // round 3 left rotates
    //
    private static final int S31 = 3;
    private static final int S32 = 9;
    private static final int S33 = 11;
    private static final int S34 = 15;



    /**
     * Creates a new MD4 message digest algorithm.
     */
    MD4MessageDigest()
    {
      super("MD4");
      engineReset();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] engineDigest()
    {
      final byte[] digestBytes = new byte[DIGEST_LENGTH];
      finish();
      unpackWord(H1, digestBytes, 0);
      unpackWord(H2, digestBytes, 4);
      unpackWord(H3, digestBytes, 8);
      unpackWord(H4, digestBytes, 12);
      engineReset();
      return digestBytes;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void engineReset()
    {
      byteCount = 0;
      xBufOff = 0;
      for (int i = 0; i < xBuf.length; i++)
      {
        xBuf[i] = 0;
      }

      H1 = 0x67452301;
      H2 = 0xefcdab89;
      H3 = 0x98badcfe;
      H4 = 0x10325476;
      xOff = 0;
      for (int i = 0; i != X.length; i++)
      {
        X[i] = 0;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void engineUpdate(final byte input)
    {
      xBuf[xBufOff++] = input;
      if (xBufOff == xBuf.length)
      {
        processWord(xBuf, 0);
        xBufOff = 0;
      }
      byteCount++;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void engineUpdate(final byte[] in, int inOff, int len)
    {
      //
      // fill the current word
      //
      while ((xBufOff != 0) && (len > 0))
      {
        update(in[inOff]);

        inOff++;
        len--;
      }

      //
      // process whole words.
      //
      while (len > xBuf.length)
      {
        processWord(in, inOff);

        inOff += xBuf.length;
        len -= xBuf.length;
        byteCount += xBuf.length;
      }

      //
      // load in the remainder.
      //
      while (len > 0)
      {
        update(in[inOff]);

        inOff++;
        len--;
      }
    }



    /*
     * F, G, H and I are the basic MD4 functions.
     */
    private int F(final int u, final int v, final int w)
    {
      return (u & v) | (~u & w);
    }



    private void finish()
    {
      final long bitLength = (byteCount << 3);

      //
      // add the pad bytes.
      //
      engineUpdate((byte) 128);
      while (xBufOff != 0)
      {
        engineUpdate((byte) 0);
      }
      processLength(bitLength);
      processBlock();
    }



    private int G(final int u, final int v, final int w)
    {
      return (u & v) | (u & w) | (v & w);
    }



    private int H(final int u, final int v, final int w)
    {
      return u ^ v ^ w;
    }



    private void processBlock()
    {
      int a = H1;
      int b = H2;
      int c = H3;
      int d = H4;

      //
      // Round 1 - F cycle, 16 times.
      //
      a = rotateLeft(a + F(b, c, d) + X[0], S11);
      d = rotateLeft(d + F(a, b, c) + X[1], S12);
      c = rotateLeft(c + F(d, a, b) + X[2], S13);
      b = rotateLeft(b + F(c, d, a) + X[3], S14);
      a = rotateLeft(a + F(b, c, d) + X[4], S11);
      d = rotateLeft(d + F(a, b, c) + X[5], S12);
      c = rotateLeft(c + F(d, a, b) + X[6], S13);
      b = rotateLeft(b + F(c, d, a) + X[7], S14);
      a = rotateLeft(a + F(b, c, d) + X[8], S11);
      d = rotateLeft(d + F(a, b, c) + X[9], S12);
      c = rotateLeft(c + F(d, a, b) + X[10], S13);
      b = rotateLeft(b + F(c, d, a) + X[11], S14);
      a = rotateLeft(a + F(b, c, d) + X[12], S11);
      d = rotateLeft(d + F(a, b, c) + X[13], S12);
      c = rotateLeft(c + F(d, a, b) + X[14], S13);
      b = rotateLeft(b + F(c, d, a) + X[15], S14);

      //
      // Round 2 - G cycle, 16 times.
      //
      a = rotateLeft(a + G(b, c, d) + X[0] + 0x5a827999, S21);
      d = rotateLeft(d + G(a, b, c) + X[4] + 0x5a827999, S22);
      c = rotateLeft(c + G(d, a, b) + X[8] + 0x5a827999, S23);
      b = rotateLeft(b + G(c, d, a) + X[12] + 0x5a827999, S24);
      a = rotateLeft(a + G(b, c, d) + X[1] + 0x5a827999, S21);
      d = rotateLeft(d + G(a, b, c) + X[5] + 0x5a827999, S22);
      c = rotateLeft(c + G(d, a, b) + X[9] + 0x5a827999, S23);
      b = rotateLeft(b + G(c, d, a) + X[13] + 0x5a827999, S24);
      a = rotateLeft(a + G(b, c, d) + X[2] + 0x5a827999, S21);
      d = rotateLeft(d + G(a, b, c) + X[6] + 0x5a827999, S22);
      c = rotateLeft(c + G(d, a, b) + X[10] + 0x5a827999, S23);
      b = rotateLeft(b + G(c, d, a) + X[14] + 0x5a827999, S24);
      a = rotateLeft(a + G(b, c, d) + X[3] + 0x5a827999, S21);
      d = rotateLeft(d + G(a, b, c) + X[7] + 0x5a827999, S22);
      c = rotateLeft(c + G(d, a, b) + X[11] + 0x5a827999, S23);
      b = rotateLeft(b + G(c, d, a) + X[15] + 0x5a827999, S24);

      //
      // Round 3 - H cycle, 16 times.
      //
      a = rotateLeft(a + H(b, c, d) + X[0] + 0x6ed9eba1, S31);
      d = rotateLeft(d + H(a, b, c) + X[8] + 0x6ed9eba1, S32);
      c = rotateLeft(c + H(d, a, b) + X[4] + 0x6ed9eba1, S33);
      b = rotateLeft(b + H(c, d, a) + X[12] + 0x6ed9eba1, S34);
      a = rotateLeft(a + H(b, c, d) + X[2] + 0x6ed9eba1, S31);
      d = rotateLeft(d + H(a, b, c) + X[10] + 0x6ed9eba1, S32);
      c = rotateLeft(c + H(d, a, b) + X[6] + 0x6ed9eba1, S33);
      b = rotateLeft(b + H(c, d, a) + X[14] + 0x6ed9eba1, S34);
      a = rotateLeft(a + H(b, c, d) + X[1] + 0x6ed9eba1, S31);
      d = rotateLeft(d + H(a, b, c) + X[9] + 0x6ed9eba1, S32);
      c = rotateLeft(c + H(d, a, b) + X[5] + 0x6ed9eba1, S33);
      b = rotateLeft(b + H(c, d, a) + X[13] + 0x6ed9eba1, S34);
      a = rotateLeft(a + H(b, c, d) + X[3] + 0x6ed9eba1, S31);
      d = rotateLeft(d + H(a, b, c) + X[11] + 0x6ed9eba1, S32);
      c = rotateLeft(c + H(d, a, b) + X[7] + 0x6ed9eba1, S33);
      b = rotateLeft(b + H(c, d, a) + X[15] + 0x6ed9eba1, S34);

      H1 += a;
      H2 += b;
      H3 += c;
      H4 += d;

      //
      // reset the offset and clean out the word buffer.
      //
      xOff = 0;
      for (int i = 0; i != X.length; i++)
      {
        X[i] = 0;
      }
    }



    private void processLength(final long bitLength)
    {
      if (xOff > 14)
      {
        processBlock();
      }

      X[14] = (int) (bitLength & 0xffffffff);
      X[15] = (int) (bitLength >>> 32);
    }



    private void processWord(final byte[] in, final int inOff)
    {
      X[xOff++] = (in[inOff] & 0xff) | ((in[inOff + 1] & 0xff) << 8)
          | ((in[inOff + 2] & 0xff) << 16) | ((in[inOff + 3] & 0xff) << 24);

      if (xOff == 16)
      {
        processBlock();
      }
    }



    /*
     * rotate int x left n bits.
     */
    private int rotateLeft(final int x, final int n)
    {
      return (x << n) | (x >>> (32 - n));
    }



    private void unpackWord(final int word, final byte[] out, final int outOff)
    {
      out[outOff] = (byte) word;
      out[outOff + 1] = (byte) (word >>> 8);
      out[outOff + 2] = (byte) (word >>> 16);
      out[outOff + 3] = (byte) (word >>> 24);
    }
  }



  /**
   * Plugin configuration object.
   */
  private SambaPasswordPluginCfg config;

  // The name of the Samba LanMan password attribute.
  private static final String SAMBA_LM_PASSWORD_ATTRIBUTE_NAME =
    "sambaLMPassword";

  // The name of the Samba NT password attribute.
  private static final String SAMBA_NT_PASSWORD_ATTRIBUTE_NAME =
    "sambaNTPassword";

  // The name of the Samba account object class.
  private static final String SAMBA_SAM_ACCOUNT_OC_NAME = "sambaSAMAccount";

  // The name of the Samba last password change attribute.
  private static final String SAMBA_PWD_LAST_SET_NAME = "sambaPwdLastSet";

  /**
   * Debug tracer object to log debugging information.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  /**
   * Password Modify Extended Operation OID.
   */
  private static final String PWMOD_EXTOP_OID = "1.3.6.1.4.1.4203.1.11.1";

  /**
   * Magic string to be used as salt.
   */
  private static final String MAGIC_STR = "KGS!@#$%";

  // Default timestamp provider implementation.
  private static final TimeStampProvider DEFAULT_TIMESTAMP_PROVIDER =
  new TimeStampProvider()
  {
    public long getCurrentTime()
    {
      return System.currentTimeMillis() / 1000L;
    }
  };

  // Use the default implementation of the timestamp provider... by default.
  private TimeStampProvider timeStampProvider = DEFAULT_TIMESTAMP_PROVIDER;


  /**
   * Add the parity to the 56-bit key converting it to 64-bit key.
   *
   * @param key56
   *          56-bit key.
   * @return 64-bit key.
   */
  private static byte[] addParity(final byte[] key56)
  {
    final byte[] key64 = new byte[8];
    final int[] key7 = new int[7];
    final int[] key8 = new int[8];

    for (int i = 0; i < 7; i++)
    {
      key7[i] = key56[i] & 0xFF;
    }

    key8[0] = key7[0];
    key8[1] = ((key7[0] << 7) & 0xFF | (key7[1] >> 1));
    key8[2] = ((key7[1] << 6) & 0xFF | (key7[2] >> 2));
    key8[3] = ((key7[2] << 5) & 0xFF | (key7[3] >> 3));
    key8[4] = ((key7[3] << 4) & 0xFF | (key7[4] >> 4));
    key8[5] = ((key7[4] << 3) & 0xFF | (key7[5] >> 5));
    key8[6] = ((key7[5] << 2) & 0xFF | (key7[6] >> 6));
    key8[7] = (key7[6] << 1);

    for (int i = 0; i < 8; i++)
    {
      key64[i] = (byte) (setOddParity(key8[i]));
    }

    return key64;

  }



  /**
   * Create a LanMan hash from a clear-text password.
   *
   * @param password
   *          Clear-text password.
   * @return Hex string version of the hash based on the clear-text password.
   * @throws UnsupportedEncodingException
   *           if the <code>US-ASCII</code> coding is not available.
   * @throws NoSuchAlgorithmException
   *           if the algorithm does not exist for the used provider.
   * @throws InvalidKeyException
   *           if the key is inappropriate to initialize the cipher.
   * @throws NoSuchPaddingException
   *           if the padding scheme is not available.
   * @throws IllegalBlockSizeException
   *           if this encryption algorithm is unable to process the input data
   *           provided
   * @throws BadPaddingException
   *           if this cipher is in decryption mode, and (un)padding has been
   *           requested, but the decrypted data is not bounded by the
   *           appropriate padding bytes
   */
  private static String lmHash(final String password)
      throws UnsupportedEncodingException, NoSuchAlgorithmException,
      InvalidKeyException, NoSuchPaddingException, IllegalBlockSizeException,
      BadPaddingException
  {
    // Password has to be OEM encoded and in upper case
    final byte[] oemPass = password.toUpperCase().getBytes("US-ASCII");

    // It shouldn't be longer then 14 bytes
    int length = 14;
    if (oemPass.length < length)
    {
      length = oemPass.length;
    }

    // The password should be divided into two 7-byte keys
    final byte[] key1 = new byte[7];
    final byte[] key2 = new byte[7];
    if (length <= 7)
    {
      System.arraycopy(oemPass, 0, key1, 0, length);
    }
    else
    {
      System.arraycopy(oemPass, 0, key1, 0, 7);
      System.arraycopy(oemPass, 7, key2, 0, length - 7);
    }

    // We create two DES keys using key1 and key2 to on the magic string
    final SecretKey lowKey = new SecretKeySpec(addParity(key1), "DES");
    final SecretKey highKey = new SecretKeySpec(addParity(key2), "DES");
    final Cipher des = Cipher.getInstance("DES/ECB/NoPadding");
    des.init(Cipher.ENCRYPT_MODE, lowKey);
    final byte[] lowHash = des.doFinal(MAGIC_STR.getBytes());
    des.init(Cipher.ENCRYPT_MODE, highKey);
    final byte[] highHash = des.doFinal(MAGIC_STR.getBytes());

    // We finally merge hashes and return them to the client

    final byte[] lmHash = new byte[16];
    System.arraycopy(lowHash, 0, lmHash, 0, 8);
    System.arraycopy(highHash, 0, lmHash, 8, 8);
    return toLowerCase(bytesToHexNoSpace(lmHash));
  }



  /**
   * Creates a NTLM hash from a clear-text password.
   *
   * @param password
   *          Clear text password.
   * @return Returns a NTLM hash.
   * @throws NoSuchProviderException
   *           if the BouncyCastle provider does not load
   * @throws NoSuchAlgorithmException
   *           if the MD4 algorithm is not found
   * @throws UnsupportedEncodingException
   *           if the encoding <code>UnicodeLittleUnmarked</code> is not
   *           supported.
   */
  private static String ntHash(final String password)
      throws NoSuchProviderException, UnsupportedEncodingException,
      NoSuchAlgorithmException
  {
    final byte[] unicodePassword = password.getBytes("UnicodeLittleUnmarked");
    final MessageDigest md4 = new MD4MessageDigest();
    return toLowerCase(bytesToHexNoSpace(md4.digest(unicodePassword)));
  }



  /**
   * Set the parity bit for an integer.
   *
   * @param integer
   *          to add the parity bit for.
   * @return integer with the parity bit set.
   */
  private static int setOddParity(final int parity)
  {
    final boolean hasEvenBits = ((parity >>> 7) ^ (parity >>> 6)
                               ^ (parity >>> 5) ^ (parity >>> 4)
                               ^ (parity >>> 3) ^ (parity >>> 2)
                               ^ (parity >>> 1) & 0x01) == 0;
    if (hasEvenBits)
    {
      return parity | 0x01;
    }
    else
    {
      return parity & 0xFE;
    }
  }



  /**
   * Default constructor.
   */
  public SambaPasswordPlugin()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      final SambaPasswordPluginCfg newConfig)
  {

    // No validation required and no restart required.
    config = newConfig;

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PostOperation doPostOperation(
      final PostOperationExtendedOperation extendedOperation)
  {
    /*
     * If the operation is not Password Modify Extended Operation then skip this
     * operation.
     */
    if (!extendedOperation.getRequestOID().equals(PWMOD_EXTOP_OID))
    {
      return PluginResult.PostOperation.continueOperationProcessing();
    }

    /*
     * If the operation has not been successful then ignore the operation.
     */
    if (extendedOperation.getResultCode() != ResultCode.SUCCESS)
    {
      return PluginResult.PostOperation.continueOperationProcessing();
    }

    /*
     * Verify if the operation has been initiated by what was defined as Samba
     * administrative user. If so, we will skip this operation to avoid double
     * synchronization of Samba attributes.
     */
    final DN authDN = extendedOperation.getAuthorizationDN();
    final DN sambaAdminDN = config.getSambaAdministratorDN();
    if (sambaAdminDN != null && !sambaAdminDN.isNullDN())
    {
      if (authDN.equals(sambaAdminDN))
      {
        if (DebugLogger.debugEnabled())
        {
          TRACER.debugInfo("This operation will be skipped because"
              + " it was performed by Samba admin user: " + sambaAdminDN);
        }
        return PluginResult.PostOperation.continueOperationProcessing();
      }
    }

    // Get the name of the entry and clear passwords from the operation
    // attachments.
    final DN dn = (DN) extendedOperation
        .getAttachment(PasswordModifyExtendedOperation.AUTHZ_DN_ATTACHMENT);
    if (dn == null)
    {
      // The attachment is missing which should never happen.
      if (DebugLogger.debugEnabled())
      {
        TRACER.debugInfo("SambaPasswordPlugin: missing DN attachment");
      }
      return PluginResult.PostOperation.continueOperationProcessing();
    }

    final String password = extendedOperation.getAttachment(
        PasswordModifyExtendedOperation.CLEAR_PWD_ATTACHMENT).toString();
    if (password == null)
    {
      if (DebugLogger.debugEnabled())
      {
        TRACER.debugInfo("SambaPasswordPlugin: skipping syncing "
            + "pre-encoded password");
      }
      return PluginResult.PostOperation.continueOperationProcessing();
    }

    @SuppressWarnings("unchecked")
    final List<ByteString> encPasswords = (List<ByteString>) extendedOperation
        .getAttachment(PasswordModifyExtendedOperation.ENCODED_PWD_ATTACHMENT);

    try
    {
      // Before proceeding make sure this entry has samba object class.
      final Entry entry = DirectoryServer.getEntry(dn);
      if (!isSynchronizable(entry))
      {
        if (DebugLogger.debugEnabled())
        {
          TRACER.debugInfo("The entry is not Samba object.");
        }
        return PluginResult.PostOperation.continueOperationProcessing();
      }

      /*
       * Make an internal connection to process the password modification. It
       * will not trigger this plugin again with the pre-operation modify since
       * the password passed would be encoded hence the pre operation part would
       * skip it.
       */
      final InternalClientConnection connection = InternalClientConnection
          .getRootConnection();

      final List<Modification> modifications = getModifications(password);

      // Use an assertion control to avoid race conditions since extended
      // operation post-ops are done outside of the write lock.
      List<Control> controls = null;
      if (!encPasswords.isEmpty())
      {
        final AttributeType pwdAttribute = (AttributeType) extendedOperation
            .getAttachment(
                PasswordModifyExtendedOperation.PWD_ATTRIBUTE_ATTACHMENT);
        final LDAPFilter filter = RawFilter.createEqualityFilter(
            pwdAttribute.getNameOrOID(), encPasswords.get(0));
        final Control assertionControl = new LDAPAssertionRequestControl(true,
            filter);
        controls = Collections.singletonList(assertionControl);
      }

      final ModifyOperation modifyOperation = connection.processModify(dn,
          modifications, controls);

      if (DebugLogger.debugEnabled())
      {
        TRACER.debugInfo(modifyOperation.getResultCode().toString());
      }
    }
    catch (final DirectoryException e)
    {
      /*
       * This exception occurs if there is a problem while retrieving the entry.
       * This should never happen as we are processing the post-operation which
       * succeeded so the entry has to exist if we have reached this point.
       */
      if (DebugLogger.debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.WARNING, e);
      }
    }

    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public PluginResult.PreOperation doPreOperation(
      final PreOperationModifyOperation modifyOperation)
  {
    /*
     * If the passwords are changed in clear text they will be available with
     * the getNewPasswords() method. If they are encoded the method would return
     * null. The list of passwords should not be modified.
     */
    final List<AttributeValue> passwords = modifyOperation.getNewPasswords();

    /*
     * If the password list is not empty, we can be sure the current operation
     * is the one that applies to our case: - it's a modify operation on
     * userPassword attribute - it's replaces or adds new userPassword attribute
     * value. If it doesn't then we skip this modify operation.
     */
    if (passwords == null)
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }

    // Skip synchronization operations.
    if (modifyOperation.isSynchronizationOperation())
    {
      if (DebugLogger.debugEnabled())
      {
        TRACER.debugInfo("Synchronization operation. Skipping.");
      }
      return PluginResult.PreOperation.continueOperationProcessing();
    }

    /*
     * Verify if the operation has been initiated by the Samba administrative
     * user. If so, we will skip this operation to avoid double synchronization
     * of Samba attributes.
     */
    final DN authDN = modifyOperation.getAuthorizationDN();
    final DN sambaAdminDN = config.getSambaAdministratorDN();
    if (sambaAdminDN != null && !sambaAdminDN.isNullDN())
    {
      if (authDN.equals(sambaAdminDN))
      {
        if (DebugLogger.debugEnabled())
        {
          TRACER.debugInfo("This operation will be skipped because"
              + " it was performed by Samba admin user: " + sambaAdminDN);
        }
        return PluginResult.PreOperation.continueOperationProcessing();
      }
    }

    /*
     * Before proceeding with the modification, we have to make sure this entry
     * is indeed a Samba object.
     */
    if (!isSynchronizable(modifyOperation.getCurrentEntry()))
    {
      if (DebugLogger.debugEnabled())
      {
        TRACER.debugInfo("Skipping '" + modifyOperation.getEntryDN().toString()
            + "' because it does not have Samba object class.");
      }
      return PluginResult.PreOperation.continueOperationProcessing();
    }

    /*
     * Proceed with processing: add a new modification to the current modify
     * operation, so they could be executed at the same time.
     */
    processModification(modifyOperation, passwords);

    // Continue plugin processing.
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void initializePlugin(final Set<PluginType> pluginTypes,
      final SambaPasswordPluginCfg configuration) throws ConfigException,
      InitializationException
  {

    // Verify config parameters.
    final LinkedList<Message> messages = new LinkedList<Message>();
    if (!isConfigurationAcceptable(configuration, messages))
    {
      for (final Message m : messages)
      {
        logError(m);
      }
      throw new ConfigException(messages.poll());
    }

    // Register the configuration change listener.
    configuration.addSambaPasswordChangeListener(this);

    // Save the configuration.
    this.config = configuration;
  }



  /**
   * Verifies if the plugin configuration is acceptable.
   *
   * @param configuration
   *          The plugin configuration.
   * @param unacceptableReasons
   *          Reasons why the configuration is not acceptable.
   * @return Returns <code>true</code> for the correct configuration and
   *         <code>false</code> for the incorrect one.
   */
  public boolean isConfigurationAcceptable(
      final SambaPasswordPluginCfg configuration,
      final List<Message> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(configuration, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
      final SambaPasswordPluginCfg newConfig, final List<Message> messages)
  {
    /*
     * The plugin implements only postoperationmodify and postoperationextended
     * plugin types. The rest should be rejected.
     */

    final SortedSet<PluginCfgDefn.PluginType> pluginTypes = newConfig
        .getPluginType();
    for (final PluginCfgDefn.PluginType t : pluginTypes)
    {
      switch (t)
      {
      case PREOPERATIONMODIFY:
      case POSTOPERATIONEXTENDED:
        break;
      default:
        messages.add(ERR_PLUGIN_SAMBA_SYNC_INVALID_PLUGIN_TYPE.get(String
            .valueOf(t)));
        return false;
      }
    }

    return true;
  }



  /**
   * Creates the modifications to modify Samba password attributes. It uses
   * clear-text password and encodes it with the appropriate algorithm for it's
   * respective type (NTLM or LanMan); then it wraps it in the modifications to
   * be added to the modify operation.
   *
   * @param password
   *          New password which is to be encoded for Samba.
   * @return Returns a list of modifications, or null if a problem occurs.
   */
  private List<Modification> getModifications(final String password)
  {
    ArrayList<Modification> modifications = new ArrayList<Modification>();
    try
    {
      if (config.getPwdSyncPolicy().contains(PwdSyncPolicy.SYNC_NT_PASSWORD))
      {
        final Attribute attribute = Attributes.create(
            SAMBA_NT_PASSWORD_ATTRIBUTE_NAME, ntHash(password));
        modifications
            .add(new Modification(ModificationType.REPLACE, attribute));
      }

      if (config.getPwdSyncPolicy().contains(PwdSyncPolicy.SYNC_LM_PASSWORD))
      {
        final Attribute attribute = Attributes.create(
            SAMBA_LM_PASSWORD_ATTRIBUTE_NAME, lmHash(password));
        modifications
            .add(new Modification(ModificationType.REPLACE, attribute));
      }
      final Attribute pwdLastSet = Attributes.create(
        SAMBA_PWD_LAST_SET_NAME,
        String.valueOf(timeStampProvider.getCurrentTime()));
      modifications
            .add(new Modification(ModificationType.REPLACE, pwdLastSet));
    }
    catch (final Exception e)
    {
      ERR_PLUGIN_SAMBA_SYNC_ENCODING.get(e.getMessage());
      if (DebugLogger.debugEnabled())
      {
        TRACER.debugError(e.getMessage(), e);
      }
      modifications = null;
    }

    return modifications;
  }



  /**
   * Verify if the target entry contains pre-defined Samba object class.
   *
   * @param entry
   *          The entry being modified.
   * @return Returns true if the entry has Samba object class, otherwise returns
   *         false.
   */
  private boolean isSynchronizable(final Entry entry)
  {
    final Schema schema = DirectoryServer.getSchema();
    final ObjectClass sambaOc = schema
        .getObjectClass(toLowerCase(SAMBA_SAM_ACCOUNT_OC_NAME));
    if (sambaOc == null)
    {
      // If the object class is not defined then presumably we're not syncing.
      return false;
    }
    else
    {
      return entry.hasObjectClass(sambaOc);
    }
  }



  /**
   * Adds modifications for the configured Samba password attributes to the
   * current modify operation.
   *
   * @param modifyOperation
   *          Current modify operation which will be modified to add samba
   *          password attribute changes.
   * @param passwords
   *          List of userPassword clear-text attribute values to be hashed for
   *          Samba
   */
  private void processModification(
      final PreOperationModifyOperation modifyOperation,
      final List<AttributeValue> passwords)
  {
    // Get the last password (in case there is more then one).

    final String password = passwords.get(passwords.size() - 1).toString();

    try
    {
      // Generate the necessary modifications.

      for (final Modification modification : getModifications(password))
      {
        modifyOperation.addModification(modification);
      }

    }
    catch (final DirectoryException e)
    {
      ERR_PLUGIN_SAMBA_SYNC_MODIFICATION_PROCESSING.get(e.getMessage());
      if (DebugLogger.debugEnabled())
      {
        TRACER.debugError(e.getMessage());
      }
    }

  }

  /**
   * Timestamp provider interface. Intended primarily for testing purposes.
   */
  static interface TimeStampProvider
  {
    /**
     * Generates a custom time stamp.
     *
     * @return  A timestamp in UNIX format.
     */
    long getCurrentTime();
  }

  /**
   * Use custom timestamp provider. Intended primarily for testing purposes.
   *
   * @param timeStampProvider Provider object that implements the
   * TimeStampProvider intreface.
   */
  void setTimeStampProvider(TimeStampProvider timeStampProvider)
  {
    this.timeStampProvider = (timeStampProvider == null)
                             ? DEFAULT_TIMESTAMP_PROVIDER : timeStampProvider;
  }
}