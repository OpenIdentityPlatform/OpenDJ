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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPrivateCrtKeySpec;

/** Re-encodes the fixture's RSA private key as RSA-PSS while retaining its certificate. */
public final class ReencodeAdminSecretKey
{
  public static void main(String[] args) throws Exception
  {
    final String alias = "admin-cert";
    final Path storePath = Path.of(args[0]);
    final char[] pin = Files.readString(Path.of(args[1])).stripTrailing().toCharArray();
    final KeyStore store = KeyStore.getInstance("JKS");
    try (InputStream input = Files.newInputStream(storePath))
    {
      store.load(input, pin);
    }

    final PSSParameterSpec params;
    if (args.length > 2)
    {
      final String digest = args[2];
      params = new PSSParameterSpec(digest, "MGF1", new MGF1ParameterSpec(digest),
          Integer.parseInt(args[3]), PSSParameterSpec.TRAILER_FIELD_BC);
    }
    else
    {
      params = null;
    }

    final RSAPrivateCrtKey rsa = (RSAPrivateCrtKey) store.getKey(alias, pin);
    final RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(rsa.getModulus(), rsa.getPublicExponent(),
        rsa.getPrivateExponent(), rsa.getPrimeP(), rsa.getPrimeQ(), rsa.getPrimeExponentP(),
        rsa.getPrimeExponentQ(), rsa.getCrtCoefficient(), params);
    final PrivateKey pss = KeyFactory.getInstance("RSASSA-PSS").generatePrivate(spec);
    store.setKeyEntry(alias, pss, pin, store.getCertificateChain(alias));
    try (OutputStream output = Files.newOutputStream(storePath))
    {
      store.store(output, pin);
    }
  }
}
