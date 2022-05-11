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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.opends.server.types.DirectoryException;

import static org.opends.server.extensions.ExtensionsConstants.*;

/**
 * This class defines a Directory Server password storage scheme based on the
 * PBKDF2 algorithm defined in RFC 2898.  This is a one-way digest algorithm
 * so there is no way to retrieve the original clear-text version of the
 * password from the hashed value (although this means that it is not suitable
 * for things that need the clear-text password like DIGEST-MD5).  This
 * implementation uses a configurable number of iterations.
 */
public class PBKDF2HmacSHA512PasswordStorageScheme
    extends AbstractPBKDF2PasswordStorageScheme
{
  public String getStorageSchemeName() {
    return STORAGE_SCHEME_NAME_PBKDF2_HMAC_SHA512;
  }

  public String getAuthPasswordSchemeName() {
    return AUTH_PASSWORD_SCHEME_NAME_PBKDF2_HMAC_SHA512;
  }

  String getMessageDigestAlgorithm() {
    return MESSAGE_DIGEST_ALGORITHM_PBKDF2_HMAC_SHA512;
  }

  int getDigestSize() {
    return 64;
  }

  public static String encodeOffline(byte[] passwordBytes) throws DirectoryException {
    return encodeOffline(passwordBytes,
            AUTH_PASSWORD_SCHEME_NAME_PBKDF2_HMAC_SHA512, MESSAGE_DIGEST_ALGORITHM_PBKDF2_HMAC_SHA512, 64);
  }
}
