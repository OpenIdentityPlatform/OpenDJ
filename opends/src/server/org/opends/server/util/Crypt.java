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
/*
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

/*      Copyright (c) 1984,1988 AT&T */
/*        All Rights Reserved   */
package org.opends.server.util;



/**
 * UNIX Crypt cipher, ported from the Sun OpenSolaris project.
 * */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class Crypt
{

  /* LINTLIBRARY */
  /*
   * This program implements the Proposed Federal Information Processing Data
   * Encryption Standard. See Federal Register, March 17, 1975 (40FR12134)
   */

  /*
   * Initial permutation,
   */
  private static final byte IP[]     =
                       { 58, 50, 42, 34, 26, 18, 10, 2, 60, 52, 44, 36, 28, 20,
      12, 4, 62, 54, 46, 38, 30, 22, 14, 6, 64, 56, 48, 40, 32, 24, 16, 8, 57,
      49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3, 61, 53, 45, 37,
      29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7, };

  /*
   * Final permutation, FP = IP^(-1)
   */
  private static final byte FP[]     =
                       { 40, 8, 48, 16, 56, 24, 64, 32, 39, 7, 47, 15, 55, 23,
      63, 31, 38, 6, 46, 14, 54, 22, 62, 30, 37, 5, 45, 13, 53, 21, 61, 29, 36,
      4, 44, 12, 52, 20, 60, 28, 35, 3, 43, 11, 51, 19, 59, 27, 34, 2, 42, 10,
      50, 18, 58, 26, 33, 1, 41, 9, 49, 17, 57, 25, };

  /*
   * Permuted-choice 1 from the key bits to yield C and D. Note that bits
   * 8,16... are left out: They are intended for a parity check.
   */
  private static final byte PC1_C[]  =
                       { 57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18,
      10, 2, 59, 51, 43, 35, 27, 19, 11, 3, 60, 52, 44, 36, };

  private static final byte PC1_D[]  =
                       { 63, 55, 47, 39, 31, 23, 15, 7, 62, 54, 46, 38, 30, 22,
      14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 28, 20, 12, 4, };

  /*
   * Sequence of shifts used for the key schedule.
   */
  private static final byte shifts[] =
                       { 1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1, };

  /*
   * Permuted-choice 2, to pick out the bits from the CD array that generate the
   * key schedule.
   */
  private static final int  PC2_C[]  =
                       { 14, 17, 11, 24, 1, 5, 3, 28, 15, 6, 21, 10, 23, 19,
      12, 4, 26, 8, 16, 7, 27, 20, 13, 2, };

  private static final byte PC2_D[]  =
                       { 41, 52, 31, 37, 47, 55, 30, 40, 51, 45, 33, 48, 44,
      49, 39, 56, 34, 53, 46, 42, 50, 36, 29, 32, };

  /**
   * Container for many variables altered throughout the encryption process.
   * */
  private static class SubCrypt
  {
    /*
     * The C and D arrays used to calculate the key schedule.
     */
    int _C[]      = new int[28];

    int _D[]      = new int[28];

    /*
     * The key schedule. Generated from the key.
     */
    int _KS[][]   = new int[16][48];

    /*
     * The E bit-selection table.
     */
    int _E[]      = new int[48];

    /*
     * The current block, divided into 2 halves.
     */
    int _L[]      = new int[32];

    int _R[]      = new int[32];

    int _tempL[]  = new int[32];

    int _f[]      = new int[32];

    /*
     * The combination of the key and the input, before selection.
     */
    int _preS[]   = new int[48];

    /*
     * Temps for crypt
     */
    int _ablock[] = new int[66];

    int _iobuf[]  = new int[16];
  }

  private final SubCrypt _crypt;

  /**
   * Constructor.
   */
  public Crypt() {
    _crypt = new SubCrypt();

    for (int i = 0; i < _crypt._E.length; i++)
      _crypt._E[i] = e[i];
  }


  /**
   * Sets up the key schedule from the key.
   */
  private void setkey(int[] key)
  {
    int i, j, k;
    int t;
    SubCrypt _c = _crypt;

    /*
     * if (_c == null) { _cryptinit(); _c = __crypt; }
     */
    /*
     * First, generate C and D by permuting the key. The low order bit of each
     * 8-bit char is not used, so C and D are only 28 bits apiece.
     */
    for (i = 0; i < 28; i++)
    {
      _c._C[i] = key[PC1_C[i] - 1];
      _c._D[i] = key[PC1_D[i] - 1];
    }
    /*
     * To generate Ki, rotate C and D according to schedule and pick up a
     * permutation using PC2.
     */
    for (i = 0; i < 16; i++)
    {
      /*
       * rotate.
       */
      for (k = 0; k < shifts[i]; k++)
      {
        t = _c._C[0];
        for (j = 0; j < 28 - 1; j++)
          _c._C[j] = _c._C[j + 1];
        _c._C[27] = t;
        t = _c._D[0];
        for (j = 0; j < 28 - 1; j++)
          _c._D[j] = _c._D[j + 1];
        _c._D[27] = t;
      }
      /*
       * get Ki. Note C and D are concatenated.
       */
      for (j = 0; j < 24; j++)
      {
        _c._KS[i][j] = _c._C[PC2_C[j] - 1];
        _c._KS[i][j + 24] = _c._D[PC2_D[j] - 28 - 1];
      }
    }
  }

  /*
   * The E bit-selection table.
   */
  private static final byte e[]   =
                    { 32, 1, 2, 3, 4, 5, 4, 5, 6, 7, 8, 9, 8, 9, 10, 11, 12,
      13, 12, 13, 14, 15, 16, 17, 16, 17, 18, 19, 20, 21, 20, 21, 22, 23, 24,
      25, 24, 25, 26, 27, 28, 29, 28, 29, 30, 31, 32, 1, };

  /*
   * The 8 selection functions. For some reason, they give a 0-origin index,
   * unlike everything else.
   */
  private static final int  S[][] =
    {
      { 14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7, 0, 15, 7, 4, 14,
      2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8, 4, 1, 14, 8, 13, 6, 2, 11, 15, 12,
      9, 7, 3, 10, 5, 0, 15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13 },

      {
      15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10, 3, 13, 4, 7, 15, 2,
      8, 14, 12, 0, 1, 10, 6, 9, 11, 5, 0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12,
      6, 9, 3, 2, 15, 13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9 },

      {
      10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8, 13, 7, 0, 9, 3, 4,
      6, 10, 2, 8, 5, 14, 12, 11, 15, 1, 13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2,
      12, 5, 10, 14, 7, 1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12 },

      {
      7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15, 13, 8, 11, 5, 6,
      15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9, 10, 6, 9, 0, 12, 11, 7, 13, 15, 1,
      3, 14, 5, 2, 8, 4, 3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14 },

      {
      2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9, 14, 11, 2, 12, 4,
      7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6, 4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12,
      5, 6, 3, 0, 14, 11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3 },

      {
      12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11, 10, 15, 4, 2, 7,
      12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8, 9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4,
      10, 1, 13, 11, 6, 4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13 },

      {
      4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1, 13, 0, 11, 7, 4, 9,
      1, 10, 14, 3, 5, 12, 2, 15, 8, 6, 1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6,
      8, 0, 5, 9, 2, 6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12 },

      {
      13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7, 1, 15, 13, 8, 10,
      3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2, 7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10,
      13, 15, 3, 5, 8, 2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11 }
    };

  /*
   * P is a permutation on the selected combination of the current L and key.
   */
  private static final int  P[]   =
                    { 16, 7, 20, 21, 29, 12, 28, 17, 1, 15, 23, 26, 5, 18, 31,
      10, 2, 8, 24, 14, 32, 27, 3, 9, 19, 13, 30, 6, 22, 11, 4, 25, };

  /**
   * Encrypts a block in place.
   */
  private final void encrypt(int block[], int edflag)
  {
    int i, ii;
    int t, j, k;
    SubCrypt _c = _crypt;

    int a = 0;

    /*
     * First, permute the bits in the input
     */
    for (j = 0; j < 64; j++)
    {
      a = IP[j] - 1;
      int b = block[a];
      if (j <= 31)
        _c._L[j] = b;
      else
        _c._R[j - 32] = b;

    }
    /*
     * Perform an encryption operation 16 times.
     */
    for (ii = 0; ii < 16; ii++)
    {
      /*
       * Set direction
       */
      if (edflag != 0)
      {
        i = 15 - ii;
      }
      else
      {
        i = ii;
      }
      /*
       * Save the R array, which will be the new L.
       */
      for (j = 0; j < 32; j++)
        _c._tempL[j] = _c._R[j];
      /*
       * Expand R to 48 bits using the E selector; exclusive-or with the current
       * key bits.
       */
      for (j = 0; j < 48; j++)
        _c._preS[j] = _c._R[_c._E[j] - 1] ^ _c._KS[i][j];
      /*
       * The pre-select bits are now considered in 8 groups of 6 bits each. The
       * 8 selection functions map these 6-bit quantities into 4-bit quantities
       * and the results permuted to make an f(R, K). The indexing into the
       * selection functions is peculiar; it could be simplified by rewriting
       * the tables.
       */
      for (j = 0; j < 8; j++)
      {
        t = 6 * j;
        k = S[j][(_c._preS[t + 0] << 5) + (_c._preS[t + 1] << 3)
            + (_c._preS[t + 2] << 2) + (_c._preS[t + 3] << 1)
            + (_c._preS[t + 4] << 0) + (_c._preS[t + 5] << 4)];
        t = 4 * j;
        _c._f[t + 0] = (k >> 3) & 01;
        _c._f[t + 1] = (k >> 2) & 01;
        _c._f[t + 2] = (k >> 1) & 01;
        _c._f[t + 3] = (k >> 0) & 01;
      }
      /*
       * The new R is L ^ f(R, K). The f here has to be permuted first, though.
       */
      for (j = 0; j < 32; j++)
        _c._R[j] = _c._L[j] ^ _c._f[P[j] - 1];
      /*
       * Finally, the new L (the original R) is copied back.
       */
      for (j = 0; j < 32; j++)
        _c._L[j] = _c._tempL[j];
    }
    /*
     * The output L and R are reversed.
     */
    for (j = 0; j < 32; j++)
    {
      t = _c._L[j];
      _c._L[j] = _c._R[j];
      _c._R[j] = t;
    }
    /*
     * The final output gets the inverse permutation of the very original.
     */
    for (j = 0; j < 64; j++)
    {
      int iv = FP[j] - 1;
      a = (iv <= 31) ? _c._L[iv] : _c._R[iv - 32];
      block[j] = a;
    }
  }

  private Object digestLock = new Object();

  /**
   * Encode the supplied password in unix crypt form with the provided
   * salt.
   *
   * @param pw A password to encode.
   * @param salt A salt array of any size, of which only the first
   * 2 bytes will be considered.
   * @return A trimmed array
   *
   * */
  public byte[] crypt(byte[] pw, byte[] salt)
  {
    int[] r;
    synchronized (digestLock)
    {
      r = _crypt(pw, salt);
    }

    //TODO: crypt always returns same size array?  So don't mess
    // around calculating the number of zeros at the end.

    // The _crypt algorithm pads the
    // result block with zeros; we need to
    // copy the array into a byte string,
    // but without these zeros.
    int zeroCount = 0;
    for (int i = r.length - 1; i >= 0; --i)
    {
      if (r[i] == 0)
      {
        ++zeroCount;
      }
      else
      {
        // Zeros can only occur at the end
        // of the block.
        break;
      }
    }
    byte[] b = new byte[r.length - zeroCount];

    // Convert to byte
    for (int i = 0; i < b.length; ++i)
    {
      b[i] = (byte) r[i];
    }
    return b;
  }

  private int[] _crypt(byte[] pw, byte[] salt)
  {
    int i, j, c, n;
    int temp;
    SubCrypt _c = _crypt;

    for (i = 0; i < 66; i++)
      _c._ablock[i] = 0;
    for (i = 0, n = 0; n < pw.length && i < 64; n++)
    {
      c = pw[n];
      for (j = 0; j < 7; j++, i++)
        _c._ablock[i] = (c >> (6 - j)) & 01;
      i++;
    }

    setkey(_c._ablock);

    for (i = 0; i < 66; i++)
      _c._ablock[i] = 0;

    for (i = 0; i < 48; i++)
      _c._E[i] = e[i];

    for (i = 0; i < 2; i++)
    {
      c = salt[i];
      _c._iobuf[i] = c;
      if (c > 'Z') c -= 6;
      if (c > '9') c -= 7;
      c -= '.';
      for (j = 0; j < 6; j++)
      {
        if (((c >> j) & 01) != 0)
        {
          temp = _c._E[6 * i + j];
          _c._E[6 * i + j] = _c._E[6 * i + j + 24];
          _c._E[6 * i + j + 24] = temp;
        }
      }
    }

    for (i = 0; i < 25; i++)
      encrypt(_c._ablock, 0);

    for (i = 0; i < 11; i++)
    {
      c = 0;
      for (j = 0; j < 6; j++)
      {
        c <<= 1;
        c |= _c._ablock[6 * i + j];
      }
      c += '.';
      if (c > '9') c += 7;
      if (c > 'Z') c += 6;
      _c._iobuf[i + 2] = c;
    }
    _c._iobuf[i + 2] = 0;
    if (_c._iobuf[1] == 0) _c._iobuf[1] = _c._iobuf[0];
    return (_c._iobuf);
  }
}

