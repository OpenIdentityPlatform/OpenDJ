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
 * Copyright 2010 ForgeRock AS
 *
 * BSD-compatible md5 password crypt
 * Ported to Java from C based on crypt-md5.c by Poul-Henning Kamp,
 * which was distributed with the following notice:
 * ----------------------------------------------------------------------------
 * "THE BEER-WARE LICENSE" (Revision 42):
 * <phk@login.dknet.dk> wrote this file.  As long as you retain this notice you
 * can do whatever you want with this stuff. If we meet some day, and you think
 * this stuff is worth it, you can buy me a beer in return.   Poul-Henning Kamp
 * ----------------------------------------------------------------------------
 */
package org.opends.server.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;

/**
 * BSD MD5 Crypt algorithm, ported from C.
 *
 * @author ludo
 */
public final class BSDMD5Crypt {

  private final static String magic = "$1$";
  private final static int saltLength = 8;
  private final static String itoa64 =
          "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  private static String intTo64(int value, int length)
  {
    StringBuilder output = new StringBuilder();

    while (--length >= 0)
    {
      output.append(itoa64.charAt((value & 0x3f)));
      value >>= 6;
    }

    return (output.toString());
  }

  /* Clear bytes, equivalent of memset */
  static private void clearBytes(byte bytes[])
  {
    for (int i = 0; i < bytes.length; i++)
    {
      bytes[i] = 0;
    }
  }

  /**
   * Encode the supplied password in BSD MD5 crypt form, using
   * a random salt.
   *
   * @param password A password to encode.
   *
   * @return An encrypted string.
   *
   * @throws NoSuchAlgorithmException If the MD5 algorithm is not supported.
   *
   */
  static public String crypt(String password)
          throws NoSuchAlgorithmException
  {
    SecureRandom randomGenerator = new SecureRandom();
    StringBuilder salt = new StringBuilder();

    /* Generate some random salt */
    while (salt.length() < saltLength)
    {
      int index = (int) (randomGenerator.nextFloat() * itoa64.length());
      salt.append(itoa64.charAt(index));
    }

    return BSDMD5Crypt.crypt(password, salt.toString());
  }

  /**
   * Encode the supplied password in BSD MD5 crypt form, using
   * provided salt.
   *
   * @param password A password to encode.
   *
   * @param salt A salt string of any size, of which only the first
   * 8 bytes will be considered.
   *
   * @return An encrypted string.
   *
   * @throws NoSuchAlgorithmException If the MD5 algorithm is not supported.
   *
   */
  static public String crypt(String password, String salt)
          throws NoSuchAlgorithmException
  {
    MessageDigest ctx, ctx1;
    byte digest1[], digest[];

    /* First skip the magic string */
    if (salt.startsWith(magic))
    {
      salt = salt.substring(magic.length());
    }

    /* Salt stops at the first $, max saltLength chars */
    int saltEnd = salt.indexOf('$');
    if (saltEnd != -1)
    {
      salt = salt.substring(0, saltEnd);
    }

    if (salt.length() > saltLength)
    {
      salt = salt.substring(0, saltLength);
    }

    ctx = MessageDigest.getInstance("MD5");

    /* The password first, since that is what is most unknown */
    ctx.update(password.getBytes());

    /* Then our magic string */
    ctx.update(magic.getBytes());

    /* Then the raw salt */
    ctx.update(salt.getBytes());

    /* Then just as many characters of the MD5(password,salt,password) */
    ctx1 = MessageDigest.getInstance("MD5");
    ctx1.update(password.getBytes());
    ctx1.update(salt.getBytes());
    ctx1.update(password.getBytes());
    digest1 = ctx1.digest();


    for (int pl = password.length(); pl > 0; pl -= 16)
    {
      ctx.update(digest1, 0, pl > 16 ? 16 : pl);
    }

    /* Don't leave anything around in vm they could use. */
    clearBytes(digest1);

    /* Then something really weird... */
    for (int i = password.length(); i != 0; i >>= 1)
    {
      if ((i & 1) != 0)
      {
        ctx.update(digest1[0]);
      } else
      {
        ctx.update(password.getBytes()[0]);
      }
    }

    /* Now make the output string */
    StringBuilder output = new StringBuilder();
    output.append(magic);
    output.append(salt);
    output.append("$");

    digest = ctx.digest();

    /*
     * and now, just to make sure things don't run too fast
     * On a 60 MHz Pentium this takes 34 msec, so you would
     * need 30 seconds to build a 1000 entry dictionary...
     */
    for (int i = 0; i < 1000; i++)
    {
      ctx1 = MessageDigest.getInstance("MD5");

      if ((i & 1) != 0)
      {
        ctx1.update(password.getBytes());
      } else
      {
        ctx1.update(digest);
      }
      if ((i % 3) != 0)
      {
        ctx1.update(salt.getBytes());
      }
      if ((i % 7) != 0)
      {
        ctx1.update(password.getBytes());
      }
      if ((i & 1) != 0)
      {
        ctx1.update(digest);
      } else
      {
        ctx1.update(password.getBytes());
      }
      digest = ctx1.digest();
    }

    int l;

    l = ((digest[0] & 0xff) << 16) | ((digest[6] & 0xff) << 8)
            | (digest[12] & 0xff);
    output.append(intTo64(l, 4));
    l = ((digest[1] & 0xff) << 16) | ((digest[7] & 0xff) << 8)
            | (digest[13] & 0xff);
    output.append(intTo64(l, 4));
    l = ((digest[2] & 0xff) << 16) | ((digest[8] & 0xff) << 8)
            | (digest[14] & 0xff);
    output.append(intTo64(l, 4));
    l = ((digest[3] & 0xff) << 16) | ((digest[9] & 0xff) << 8)
            | (digest[15] & 0xff);
    output.append(intTo64(l, 4));
    l = ((digest[4] & 0xff) << 16) | ((digest[10] & 0xff) << 8)
            | (digest[5] & 0xff);
    output.append(intTo64(l, 4));
    l = (digest[11] & 0xff);
    output.append(intTo64(l, 2));

    /* Don't leave anything around in vm they could use. */
    clearBytes(digest);
    ctx = null;
    ctx1 = null;

    return output.toString();

  }

  /**
   * Getter to the BSD MD5 magic string.
   *
   * @return the magic string for this crypt algorithm
   */
  static public String getMagicString()
  {
    return magic;
  }

  /**
   * Main test method.
   *
   * @param argv The array of test arguments
   *
   */
  static public void main(String argv[])
  {
    if ((argv.length < 1) || (argv.length > 2))
    {
      System.err.println("Usage: BSDMD5Crypt password salt");
      System.exit(1);
    }
    try
    {
      if (argv.length == 2)
      {
        System.out.println(BSDMD5Crypt.crypt(argv[0], argv[1]));
      } else
      {
        System.out.println(BSDMD5Crypt.crypt(argv[0]));
      }
    } catch (Exception e)
    {
      System.err.println(e.getMessage().toString());
      System.exit(1);
    }
    System.exit(0);
  }
}
