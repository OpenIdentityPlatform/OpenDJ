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
import java.security.KeyStore;
import java.util.Arrays;

import javax.net.ssl.KeyManagerFactory;

/** Checks a staged keystore using the default administration provider's loading rules. */
public final class AdminSecretValidator
{
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

      if (!store.entryInstanceOf("admin-cert", KeyStore.PrivateKeyEntry.class))
      {
        throw new IllegalArgumentException("The administration keystore has no admin-cert private key");
      }

      final KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      factory.init(store, pin);
      factory.getKeyManagers();
    }
    finally
    {
      Arrays.fill(pin, '\0');
    }
  }
}
