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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;

import javax.net.ssl.KeyManagerFactory;

/** Checks whether a staged keystore can supply the default administration TLS key. */
public final class AdminSecretValidator
{
  private static final String ADMIN_ALIAS = "admin-cert";

  public static void main(String[] args) throws Exception
  {
    final char[] pin;
    try (BufferedReader reader = new BufferedReader(new FileReader(args[1])))
    {
      final String firstLine = reader.readLine();
      if (firstLine == null)
      {
        throw new IllegalArgumentException("The administration PIN file has no first line");
      }
      pin = firstLine.toCharArray();
    }

    try
    {
      final KeyStore store = KeyStore.getInstance("JKS");
      try (InputStream input = Files.newInputStream(Path.of(args[0])))
      {
        store.load(input, pin);
      }

      if (!store.entryInstanceOf(ADMIN_ALIAS, KeyStore.PrivateKeyEntry.class))
      {
        throw new IllegalArgumentException("The administration keystore has no admin-cert private key");
      }

      final KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      factory.init(store, pin);
      factory.getKeyManagers();

      // The key manager loads every key with the same PIN. Only the configured alias
      // needs to sign, since it is the one selected for administration TLS.
      final PrivateKey key = (PrivateKey) store.getKey(ADMIN_ALIAS, pin);
      final PublicKey certificateKey = store.getCertificate(ADMIN_ALIAS).getPublicKey();
      verifySigningKey(key, certificateKey);
    }
    finally
    {
      Arrays.fill(pin, '\0');
    }
  }

  private static void verifySigningKey(PrivateKey key, PublicKey certificateKey)
      throws GeneralSecurityException
  {
    final Signature signature = signatureFor(key);
    signature.initSign(key);
    signature.update((byte) 0);
    final byte[] signed = signature.sign();

    signature.initVerify(certificateKey);
    signature.update((byte) 0);
    if (!signature.verify(signed))
    {
      throw new GeneralSecurityException("The admin-cert private key does not match its certificate");
    }
  }

  private static Signature signatureFor(PrivateKey key) throws GeneralSecurityException
  {
    switch (key.getAlgorithm())
    {
    case "RSA":
      return Signature.getInstance("SHA256withRSA");
    case "RSASSA-PSS":
      final Signature signature = Signature.getInstance("RSASSA-PSS");
      final AlgorithmParameterSpec params = ((RSAKey) key).getParams();
      signature.setParameter(params != null ? params
          : new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
      return signature;
    case "EC":
      return Signature.getInstance("SHA256withECDSA");
    case "DSA":
      return Signature.getInstance("SHA256withDSA");
    case "EdDSA":
    case "Ed25519":
    case "Ed448":
      return Signature.getInstance(key.getAlgorithm());
    default:
      throw new GeneralSecurityException("The admin-cert key does not support signing: " + key.getAlgorithm());
    }
  }
}
