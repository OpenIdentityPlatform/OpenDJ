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
package org.opends.server.extensions;



/**
 * This class defines a number of constants that may be used by Directory Server
 * extensions.
 */
public class ExtensionsConstants
{
  /**
   * The authentication password scheme name for use with passwords encoded in a
   * salted MD5 representation.
   */
  public static final String AUTH_PASSWORD_SCHEME_NAME_SALTED_MD5 = "MD5";



  /**
   * The authentication password scheme name for use with passwords encoded in a
   * salted SHA-1 representation.
   */
  public static final String AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_1 = "SHA1";



  /**
   * The authentication password scheme name for use with passwords encoded in a
   * salted SHA-256 representation.
   */
  public static final String AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_256 =
       "SHA256";



  /**
   * The authentication password scheme name for use with passwords encoded in a
   * salted SHA-384 representation.
   */
  public static final String AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_384 =
       "SHA384";



  /**
   * The authentication password scheme name for use with passwords encoded in a
   * salted SHA-512 representation.
   */
  public static final String AUTH_PASSWORD_SCHEME_NAME_SALTED_SHA_512 =
       "SHA512";



  /**
   * The name of the message digest algorithm that should be used to generate
   * MD5 hashes.
   */
  public static final String MESSAGE_DIGEST_ALGORITHM_MD5 = "MD5";



  /**
   * The name of the message digest algorithm that should be used to generate
   * SHA-1 hashes.
   */
  public static final String MESSAGE_DIGEST_ALGORITHM_SHA_1 = "SHA-1";



  /**
   * The name of the message digest algorithm that should be used to generate
   * 256-bit SHA-2 hashes.
   */
  public static final String MESSAGE_DIGEST_ALGORITHM_SHA_256 = "SHA-256";



  /**
   * The name of the message digest algorithm that should be used to generate
   * 384-bit SHA-2 hashes.
   */
  public static final String MESSAGE_DIGEST_ALGORITHM_SHA_384 = "SHA-384";



  /**
   * The name of the message digest algorithm that should be used to generate
   * 512-bit SHA-2 hashes.
   */
  public static final String MESSAGE_DIGEST_ALGORITHM_SHA_512 = "SHA-512";



  /**
   * The cipher transformation that should be used when performing 3DES
   * encryption/decription.
   */
  public static final String CIPHER_TRANSFORMATION_3DES =
       "DESede/CFB/NoPadding";



  /**
   * The cipher transformation that should be used when performing AES
   * encryption/decription.
   */
  public static final String CIPHER_TRANSFORMATION_AES = "AES/CFB/NoPadding";



  /**
   * The cipher transformation that should be used when performing blowfish
   * encryption/decription.
   */
  public static final String CIPHER_TRANSFORMATION_BLOWFISH =
       "Blowfish/CFB/NoPadding";



  /**
   * The cipher transformation that should be used when performing RC4
   * encryption/decription.
   *
   * TODO: https://opends.dev.java.net/issues/show_bug.cgi?id=2471
   */
  public static final String CIPHER_TRANSFORMATION_RC4 = "RC4/NONE/NoPadding";



  /**
   * The key size (in bits) that should be used for the encryption key when
   * using the 3DES cipher.
   */
  public static final int KEY_SIZE_3DES = 168;



  /**
   * The key size (in bits) that should be used for the encryption key when
   * using the AES cipher.
   * TODO: https://opends.dev.java.net/issues/show_bug.cgi?id=2475
   */
  public static final int KEY_SIZE_AES = 128;



  /**
   * The key size (in bits) that should be used for the encryption key when
   * using the Blowfish cipher.
   * TODO: https://opends.dev.java.net/issues/show_bug.cgi?id=2475
   */
  public static final int KEY_SIZE_BLOWFISH = 128;



  /**
   * The key size (in bits) that should be used for the encryption key when
   * using the RC4 cipher.
   * TODO: https://opends.dev.java.net/issues/show_bug.cgi?id=2475
   */
  public static final int KEY_SIZE_RC4 = 128;



  /**
   * The password storage scheme name that will be used for passwords that are
   * stored in 3DES-encrypted form.
   */
  public static final String STORAGE_SCHEME_NAME_3DES = "3DES";



  /**
   * The password storage scheme name that will be used for passwords that are
   * stored in AES-encrypted form.
   */
  public static final String STORAGE_SCHEME_NAME_AES = "AES";



  /**
   * The password storage scheme name that will be used for passwords that are
   * stored in base64-encoded form (virtually no protection, but the value is
   * reversible).
   */
  public static final String STORAGE_SCHEME_NAME_BASE64 = "BASE64";



  /**
   * The password storage scheme name that will be used for passwords that are
   * stored in Blowfish-encrypted form.
   */
  public static final String STORAGE_SCHEME_NAME_BLOWFISH = "BLOWFISH";



  /**
   * The password storage scheme name that will be used for passwords that are
   * not encoded or obscured in any way.
   */
  public static final String STORAGE_SCHEME_NAME_CLEAR = "CLEAR";



  /**
   * The password storage scheme name that will be used for passwords stored in
   * an MD5 representation.
   */
  public static final String STORAGE_SCHEME_NAME_MD5 = "MD5";



  /**
   * The password storage scheme name that will be used for passwords that are
   * stored in RC4-encrypted form.
   */
  public static final String STORAGE_SCHEME_NAME_RC4 = "RC4";



  /**
   * The password storage scheme name that will be used for passwords stored in
   * a salted MD5 representation.
   */
  public static final String STORAGE_SCHEME_NAME_SALTED_MD5 = "SMD5";



  /**
   * The password storage scheme name that will be used for passwords stored in
   * a SHA-1 representation.
   */
  public static final String STORAGE_SCHEME_NAME_SHA_1 = "SHA";



  /**
   * The password storage scheme name that will be used for passwords stored in
   * a salted SHA-1 representation.
   */
  public static final String STORAGE_SCHEME_NAME_SALTED_SHA_1 = "SSHA";



  /**
   * The password storage scheme name that will be used for passwords stored in
   * a salted SHA-256 representation.
   */
  public static final String STORAGE_SCHEME_NAME_SALTED_SHA_256 = "SSHA256";



  /**
   * The password storage scheme name that will be used for passwords stored in
   * a salted SHA-384 representation.
   */
  public static final String STORAGE_SCHEME_NAME_SALTED_SHA_384 = "SSHA384";



  /**
   * The password storage scheme name that will be used for passwords stored in
   * a salted SHA-512 representation.
   */
  public static final String STORAGE_SCHEME_NAME_SALTED_SHA_512 = "SSHA512";



  /**
   * The password storage scheme name that will be used for passwords stored in
   * a UNIX crypt representation.
   */
  public static final String STORAGE_SCHEME_NAME_CRYPT = "CRYPT";



  /**
   * The string that will appear before the name of the password storage scheme
   * in an encoded password.
   */
  public static final String STORAGE_SCHEME_PREFIX = "{";



  /**
   * The string that will appear after the name of the password storage scheme
   * in an encoded password.
   */
  public static final String STORAGE_SCHEME_SUFFIX = "}";



  /**
   * The ASN.1 element type that will be used to encode the userIdentity
   * component in a password modify extended request.
   */
  public static final byte TYPE_PASSWORD_MODIFY_USER_ID = (byte) 0x80;



  /**
   * The ASN.1 element type that will be used to encode the oldPasswd component
   * in a password modify extended request.
   */
  public static final byte TYPE_PASSWORD_MODIFY_OLD_PASSWORD = (byte) 0x81;



  /**
   * The ASN.1 element type that will be used to encode the newPasswd component
   * in a password modify extended request.
   */
  public static final byte TYPE_PASSWORD_MODIFY_NEW_PASSWORD = (byte) 0x82;



  /**
   * The ASN.1 element type that will be used to encode the genPasswd component
   * in a password modify extended response.
   */
  public static final byte TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD =
       (byte) 0x80;
}

