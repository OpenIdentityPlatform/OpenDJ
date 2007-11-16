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
package org.opends.server.util;



import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Enumeration;



/**
 * This class provides an interface for generating self-signed certificates and
 * certificate signing requests, and for importing, exporting, and deleting
 * certificates from a key store.  It supports JKS, PKCS11, and PKCS12 key store
 * types.
 * <BR><BR>
 * Note that for some operations, particularly those that require updating the
 * contents of a key store (including generating certificates and/or certificate
 * signing  requests, importing certificates, or removing certificates), this
 * class relies on the keytool utility provided with Sun's implementation of the
 * Java runtime  environment.  It will perform the associated operations by
 * invoking the appropriate command.  It is possible that the keytool command
 * will not exist in all Java runtime environments, especially those not created
 * by Sun.  In those cases, it will not be possible to invoke operations that
 * require altering the contents of the key store.  Therefore, it is strongly
 * recommended that any code that may want to make use of this facility should
 * first call {@code mayUseCertificateManager} and if it returns {@code false}
 * the caller should gracefully degrade and suggest that the user perform the
 * operation manually.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class CertificateManager
{
  /**
   * The path to the keytool command, which will be required to perform
   * operations that modify the contents of a key store.
   */
  public static final String KEYTOOL_COMMAND;



  /**
   * The key store type value that should be used for the "JKS" key store.
   */
  public static final String KEY_STORE_TYPE_JKS = "JKS";



  /**
   * The key store type value that should be used for the "PKCS11" key store.
   */
  public static final String KEY_STORE_TYPE_PKCS11 = "PKCS11";



  /**
   * The key store type value that should be used for the "PKCS12" key store.
   */
  public static final String KEY_STORE_TYPE_PKCS12 = "PKCS12";



  /**
   * The key store path value that must be used in conjunction with the PKCS11
   * key store type.
   */
  public static final String KEY_STORE_PATH_PKCS11 = "NONE";



  // The parsed key store backing this certificate manager.
  private KeyStore keyStore;

  // The password that should be used to interact with the key store.
  private String keyStorePIN;

  // The path to the key store that we should be using.
  private String keyStorePath;

  // The name of the key store type we are using.
  private String keyStoreType;



  static
  {
    String keytoolCommand = null;

    try
    {
      String cmd = System.getProperty("java.home") + File.separator + "bin" +
                   File.separator + "keytool";
      File cmdFile = new File(cmd);
      if (cmdFile.exists())
      {
        keytoolCommand = cmdFile.getAbsolutePath();
      }
      else
      {
        cmd = cmd + ".exe";
        cmdFile = new File(cmd);
        if (cmdFile.exists())
        {
          keytoolCommand = cmdFile.getAbsolutePath();
        }
        else
        {
          keytoolCommand = null;
        }
      }
    }
    catch (Exception e)
    {
      keytoolCommand = null;
    }

    KEYTOOL_COMMAND = SetupUtils.getScriptPath(keytoolCommand);
  }



  /**
   * Indicates whether it is possible to use this certificate manager code to
   * perform operations which may alter the contents of a key store.
   *
   * @return  {@code true} if it appears that the keytool utility is available
   *          and may be used to execute commands that may alter the contents of
   *          a key store, or {@code false} if not.
   */
  public static boolean mayUseCertificateManager()
  {
    return (KEYTOOL_COMMAND != null);
  }



  /**
   * Creates a new certificate manager instance with the provided information.
   *
   * @param  keyStorePath  The path to the key store file, or "NONE" if the key
   *                       store type is "PKCS11".  For the other key store
   *                       types, the file does not need to exist if a new
   *                       self-signed certificate or certificate signing
   *                       request is to be generated, although the directory
   *                       containing the file must exist.  The key store file
   *                       must exist if import or export operations are to be
   *                       performed.
   * @param  keyStoreType  The key store type to use.  It should be one of
   *                       {@code KEY_STORE_TYPE_JKS},
   *                       {@code KEY_STORE_TYPE_PKCS11}, or
   *                       {@code KEY_STORE_TYPE_PKCS12}.
   * @param  keyStorePIN   The PIN required to access the key store.  It must
   *                       not be {@code null}.
   *
   * @throws  IllegalArgumentException  If any of the provided arguments is
   *                                    invalid.
   *
   * @throws  NullPointerException  If any of the provided arguments is
   *                                {@code null}.
   *
   * @throws  UnsupportedOperationException  If it is not possible to use the
   *                                         certificate manager on the
   *                                         underlying platform.
   */
  public CertificateManager(String keyStorePath, String keyStoreType,
                            String keyStorePIN)
         throws IllegalArgumentException, NullPointerException,
                UnsupportedOperationException
  {
    if ((keyStorePath == null) || (keyStorePath.length() == 0))
    {
      throw new NullPointerException("keyStorePath");
    }
    else if ((keyStoreType == null) || (keyStoreType.length() == 0))
    {
      throw new NullPointerException("keyStoreType");
    }
    else if ((keyStorePIN == null) || (keyStorePIN.length() == 0))
    {
      throw new NullPointerException("keyStorePIN");
    }


    if (keyStoreType.equals(KEY_STORE_TYPE_PKCS11))
    {
      if (! keyStorePath.equals(KEY_STORE_PATH_PKCS11))
      {
        // FIXME -- Make this an internationalizeable string.
        throw new IllegalArgumentException("Invalid key store path for " +
                                           "PKCS11 keystore -- it must be " +
                                           KEY_STORE_PATH_PKCS11);
      }
    }
    else if (keyStoreType.equals(KEY_STORE_TYPE_JKS) ||
             keyStoreType.equals(KEY_STORE_TYPE_PKCS12))
    {
      File keyStoreFile = new File(keyStorePath);
      if (keyStoreFile.exists())
      {
        if (! keyStoreFile.isFile())
        {
          // FIXME -- Make this an internationalizeable string.
          throw new IllegalArgumentException("Key store path " + keyStorePath +
                                             " exists but is not a file.");
        }
      }
      else
      {
        File keyStoreDirectory = keyStoreFile.getParentFile();
        if ((keyStoreDirectory == null) || (! keyStoreDirectory.exists()) ||
            (! keyStoreDirectory.isDirectory()))
        {
          // FIXME -- Make this an internationalizeable string.
          throw new IllegalArgumentException("Parent directory for key " +
                         "store path " + keyStorePath + " does not exist or " +
                         "is not a directory.");
        }
      }
    }
    else
    {
      // FIXME -- Make this an internationalizeable string.
      throw new IllegalArgumentException("Invalid key store type -- it must " +
                  "be one of " + KEY_STORE_TYPE_JKS + ", " +
                  KEY_STORE_TYPE_PKCS11 + ", or " + KEY_STORE_TYPE_PKCS12);
    }


    this.keyStorePath = keyStorePath;
    this.keyStoreType = keyStoreType;
    this.keyStorePIN  = keyStorePIN;

    keyStore = null;
  }



  /**
   * Indicates whether the provided alias is in use in the key store.
   *
   * @param  alias  The alias for which to make the determination.  It must not
   *                be {@code null} or empty.
   *
   * @return  {@code true} if the key store exist and already contains a
   *          certificate with the given alias, or {@code false} if not.
   *
   * @throws  KeyStoreException  If a problem occurs while attempting to
   *                             interact with the key store.
   *
   * @throws  NullPointerException  If the provided alias is {@code null} or a
   *                                zero-length string.
   */
  public boolean aliasInUse(String alias)
         throws KeyStoreException, NullPointerException
  {
    if ((alias == null) || (alias.length() == 0))
    {
      throw new NullPointerException("alias");
    }


    KeyStore keyStore = getKeyStore();
    if (keyStore == null)
    {
      return false;
    }

    return keyStore.containsAlias(alias);
  }



  /**
   * Retrieves the aliases of the certificates in the specified key store.
   *
   * @return  The aliases of the certificates in the specified key store, or
   *          {@code null} if the key store does not exist.
   *
   * @throws  KeyStoreException  If a problem occurs while attempting to
   *                             interact with the key store.
   */
  public String[] getCertificateAliases()
         throws KeyStoreException
  {
    KeyStore keyStore = getKeyStore();
    if (keyStore == null)
    {
      return null;
    }

    Enumeration<String> aliasEnumeration = keyStore.aliases();
    if (aliasEnumeration == null)
    {
      return new String[0];
    }

    ArrayList<String> aliasList = new ArrayList<String>();
    while (aliasEnumeration.hasMoreElements())
    {
      aliasList.add(aliasEnumeration.nextElement());
    }


    String[] aliases = new String[aliasList.size()];
    return aliasList.toArray(aliases);
  }



  /**
   * Retrieves the certificate with the specified alias from the key store.
   *
   * @param  alias  The alias of the certificate to retrieve.  It must not be
   *                {@code null} or empty.
   *
   * @return  The requested certificate, or {@code null} if the specified
   *          certificate does not exist.
   *
   * @throws  KeyStoreException  If a problem occurs while interacting with the
   *                             key store, or the key store does not exist.
   *
   * @throws  NullPointerException  If the provided alias is {@code null} or a
   *                                zero-length string.
   */
  public Certificate getCertificate(String alias)
         throws KeyStoreException, NullPointerException
  {
    if ((alias == null) || (alias.length() == 0))
    {
      throw new NullPointerException("alias");
    }

    KeyStore keyStore = getKeyStore();
    if (keyStore == null)
    {
      // FIXME -- Make this an internationalizeable string.
      throw new KeyStoreException("The key store does not exist.");
    }

    return keyStore.getCertificate(alias);
  }



  /**
   * Generates a self-signed certificate using the provided information.
   *
   * @param  alias      The nickname to use for the certificate in the key
   *                    store.  For the server certificate, it should generally
   *                    be "server-cert".  It must not be {@code null} or empty.
   * @param  subjectDN  The subject DN to use for the certificate.  It must not
   *                    be {@code null} or empty.
   * @param  validity   The length of time in days that the certificate should
   *                    be valid, starting from the time the certificate is
   *                    generated.  It must be a positive integer value.
   *
   * @throws  IllegalArgumentException  If the validity is not positive.
   *
   * @throws  KeyStoreException  If a problem occurs while actually attempting
   *                             to generate the certificate in the key store.
   *
   * @throws  NullPointerException  If either the alias or subject DN is null or
   *                                a zero-length string.
   *
   * @throws  UnsupportedOperationException  If it is not possible to use the
   *                                         keytool utility to alter the
   *                                         contents of the key store.
   */
  public void generateSelfSignedCertificate(String alias, String subjectDN,
                                            int validity)
         throws KeyStoreException, IllegalArgumentException,
                NullPointerException, UnsupportedOperationException
  {
    if ((alias == null) || (alias.length() == 0))
    {
      throw new NullPointerException("alias");
    }
    else if ((subjectDN == null) || (subjectDN.length() == 0))
    {
      throw new NullPointerException("subjectDN");
    }
    else if (validity <= 0)
    {
      // FIXME -- Make this an internationalizeable string.
      throw new IllegalArgumentException("The validity must be positive.");
    }

    if (KEYTOOL_COMMAND == null)
    {
      // FIXME -- Make this an internationalizeable string.
      throw new UnsupportedOperationException("The certificate manager may " +
                     "not be used to alter the contents of key stores on " +
                     "this system.");
    }

    if (aliasInUse(alias))
    {
      // FIXME -- Make this an internationalizeable string.
      throw new IllegalArgumentException("A certificate with alias " + alias +
                                         " already exists in the key store.");
    }


    // Clear the reference to the key store, since it will be altered by
    // invoking the KeyTool command.
    keyStore = null;


    // First, we need to run with the "-genkey" command to create the private
    // key.
    String[] commandElements =
    {
      KEYTOOL_COMMAND,
      "-genkey",
      "-alias", alias,
      "-dname", subjectDN,
      "-keyalg", "rsa",
      "-keystore", keyStorePath,
      "-storetype", keyStoreType
    };
    runKeyTool(commandElements, keyStorePIN, keyStorePIN, true);

    // Next, we need to run with the "-selfcert" command to self-sign the
    // certificate.
    commandElements = new String[]
    {
      KEYTOOL_COMMAND,
      "-selfcert",
      "-alias", alias,
      "-validity", String.valueOf(validity),
      "-keystore", keyStorePath,
      "-storetype", keyStoreType
    };
    runKeyTool(commandElements, keyStorePIN, keyStorePIN, true);
  }



  /**
   * Generates a certificate signing request (CSR) using the provided
   * information.
   *
   * @param  alias      The nickname to use for the certificate in the key
   *                    store.  For the server certificate, it should generally
   *                    be "server-cert".  It must not be {@code null} or empty.
   * @param  subjectDN  The subject DN to use for the certificate.  It must not
   *                    be {@code null} or empty.
   *
   * @return  The file containing the generated certificate signing request.
   *
   * @throws  KeyStoreException  If a problem occurs while actually attempting
   *                             to generate the private key in the key store or
   *                             generate the certificate signing request based
   *                             on that key.
   *
   * @throws  IOException  If a problem occurs while attempting to create the
   *                       file to which the certificate signing request will be
   *                       written.
   *
   * @throws  NullPointerException  If either the alias or subject DN is null or
   *                                a zero-length string.
   *
   * @throws  UnsupportedOperationException  If it is not possible to use the
   *                                         keytool utility to alter the
   *                                         contents of the key store.
   */
  public File generateCertificateSigningRequest(String alias, String subjectDN)
         throws KeyStoreException, IOException, NullPointerException,
                UnsupportedOperationException
  {
    if ((alias == null) || (alias.length() == 0))
    {
      throw new NullPointerException("alias");
    }
    else if ((subjectDN == null) || (subjectDN.length() == 0))
    {
      throw new NullPointerException("subjectDN");
    }

    if (KEYTOOL_COMMAND == null)
    {
      // FIXME -- Make this an internationalizeable string.
      throw new UnsupportedOperationException("The certificate manager may " +
                     "not be used to alter the contents of key stores on " +
                     "this system.");
    }

    if (aliasInUse(alias))
    {
      // FIXME -- Make this an internationalizeable string.
      throw new IllegalArgumentException("A certificate with alias " + alias +
                                         " already exists in the key store.");
    }


    // Clear the reference to the key store, since it will be altered by
    // invoking the KeyTool command.
    keyStore = null;


    // First, we need to run with the "-genkey" command to create the private
    // key.
    String[] commandElements =
    {
      KEYTOOL_COMMAND,
      "-genkey",
      "-alias", alias,
      "-dname", subjectDN,
      "-keyalg", "rsa",
      "-keystore", keyStorePath,
      "-storetype", keyStoreType
    };
    runKeyTool(commandElements, keyStorePIN, keyStorePIN, true);

    // Next, we need to run with the "-certreq" command to generate the
    // certificate signing request.
    File csrFile = File.createTempFile("CertificateManager-", ".csr");
    commandElements = new String[]
    {
      KEYTOOL_COMMAND,
      "-certreq",
      "-alias", alias,
      "-file", csrFile.getAbsolutePath(),
      "-keystore", keyStorePath,
      "-storetype", keyStoreType
    };
    runKeyTool(commandElements, keyStorePIN, keyStorePIN, true);

    return csrFile;
  }



  /**
   * Adds the provided certificate to the key store.  This may be used to
   * associate an externally-signed certificate with an existing private key
   * with the given alias.
   *
   * @param  alias            The alias to use for the certificate.  It must not
   *                          be {@code null} or empty.
   * @param  certificateFile  The file containing the encoded certificate.  It
   *                          must not be {@code null}, and the file must exist.
   *
   * @throws  IllegalArgumentException  If the provided certificate file does
   *                                    not exist.
   *
   * @throws  KeyStoreException  If a problem occurs while interacting with the
   *                             key store.
   *
   * @throws  NullPointerException  If the provided alias is {@code null} or a
   *                                zero-length string, or the certificate file
   *                                is {@code null}.
   *
   * @throws  UnsupportedOperationException  If it is not possible to use the
   *                                         keytool utility to alter the
   *                                         contents of the key store.
   */
  public void addCertificate(String alias, File certificateFile)
         throws IllegalArgumentException, KeyStoreException,
                NullPointerException, UnsupportedOperationException
  {
    if ((alias == null) || (alias.length() == 0))
    {
      throw new NullPointerException("alias");
    }

    if (certificateFile == null)
    {
      throw new NullPointerException("certificateFile");
    }
    else if ((! certificateFile.exists()) ||
             (! certificateFile.isFile()))
    {
      // FIXME -- Make this an internationalizeable string.
      throw new IllegalArgumentException("Certificate file " +
                                         certificateFile.getAbsolutePath() +
                                         " does not exist or is not a file.");
    }

    if (KEYTOOL_COMMAND == null)
    {
      // FIXME -- Make this an internationalizeable string.
      throw new UnsupportedOperationException("The certificate manager may " +
                     "not be used to alter the contents of key stores on " +
                     "this system.");
    }


    // Clear the reference to the key store, since it will be altered by
    // invoking the KeyTool command.
    keyStore = null;


    String[] commandElements =
    {
      KEYTOOL_COMMAND,
      "-import",
      "-noprompt",
      "-alias", alias,
      "-file", certificateFile.getAbsolutePath(),
      "-keystore", keyStorePath,
      "-storetype", keyStoreType
    };
    runKeyTool(commandElements, keyStorePIN, keyStorePIN, true);
  }


  /**
   * Removes the specified certificate from the key store.
   *
   * @param  alias  The alias to use for the certificate to remove.  It must not
   *                be {@code null} or an empty string, and it must exist in
   *                the key store.
   *
   * @throws  IllegalArgumentException  If the specified certificate does not
   *                                    exist in the key store.
   *
   * @throws  KeyStoreException  If a problem occurs while interacting with the
   *                             key store.
   *
   * @throws  NullPointerException  If the provided alias is {@code null} or a
   *                                zero-length string, or the certificate file
   *                                is {@code null}.
   *
   * @throws  UnsupportedOperationException  If it is not possible to use the
   *                                         keytool utility to alter the
   *                                         contents of the key store.
   */
  public void removeCertificate(String alias)
         throws IllegalArgumentException, KeyStoreException,
                NullPointerException, UnsupportedOperationException
  {
    if ((alias == null) || (alias.length() == 0))
    {
      throw new NullPointerException("alias");
    }

    if (KEYTOOL_COMMAND == null)
    {
      // FIXME -- Make this an internationalizeable string.
      throw new UnsupportedOperationException("The certificate manager may " +
                     "not be used to alter the contents of key stores on " +
                     "this system.");
    }

    if (! aliasInUse(alias))
    {
      // FIXME -- Make this an internationalizeable string.
      throw new IllegalArgumentException("There is no certificate with alias " +
                                         alias + " in the key store.");
    }


    // Clear the reference to the key store, since it will be altered by
    // invoking the KeyTool command.
    keyStore = null;


    String[] commandElements =
    {
      KEYTOOL_COMMAND,
      "-delete",
      "-alias", alias,
      "-keystore", keyStorePath,
      "-storetype", keyStoreType
    };
    runKeyTool(commandElements, keyStorePIN, keyStorePIN, true);
  }



  /**
   * Attempts to run the keytool utility with the provided arguments.
   *
   * @param  commandElements   The command and arguments to execute.  The first
   *                           element of the array must be the command, and the
   *                           remaining elements must be the arguments.
   * @param  keyStorePassword  The password of the key store.
   * @param  storePassword     The password of the certificate.
   * @param  outputAcceptable  Indicates whether it is acceptable for the
   *                           command to generate output, as long as the exit
   *                           code is zero.  Some commands (like "keytool
   *                           -import") may generate output even on successful
   *                           completion.  If the command generates output and
   *                           this is {@code false}, then an exception will
   *                           be thrown.
   *
   * @throws  KeyStoreException  If a problem occurs while attempting to invoke
   *                             the keytool utility, if it does not exit with
   *                             the expected exit code, or if any unexpected
   *                             output is generated while running the tool.
   */
  private void runKeyTool(String[] commandElements, String keyStorePassword,
      String storePassword, boolean outputAcceptable)
          throws KeyStoreException
  {
    String lineSeparator = System.getProperty("line.separator");
    if (lineSeparator == null)
    {
      lineSeparator = "\n";
    }
    boolean keyStoreDefined;
    File keyStoreFile = new File(keyStorePath);
    keyStoreDefined = (keyStoreFile.exists() && (keyStoreFile.length() > 0)) ||
      KEY_STORE_TYPE_PKCS11.equals(keyStoreType);

    boolean isNewKeyStorePassword = !keyStoreDefined &&
      ("-genkey".equalsIgnoreCase(commandElements[1]) ||
      "-import".equalsIgnoreCase(commandElements[1]));

    boolean isNewStorePassword =
      "-genkey".equalsIgnoreCase(commandElements[1]);

    boolean askForStorePassword =
      !"-import".equalsIgnoreCase(commandElements[1]);

    try
    {
      ProcessBuilder processBuilder = new ProcessBuilder(commandElements);
      processBuilder.redirectErrorStream(true);

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      Process process = processBuilder.start();
      InputStream inputStream = process.getInputStream();
      OutputStream out = process.getOutputStream();
      out.write(keyStorePassword.getBytes()) ;
      out.write(lineSeparator.getBytes()) ;
      out.flush() ;
      // With Java6 and above, keytool asks for the password twice.
      if (!isJDK15() && isNewKeyStorePassword)
      {
         out.write(keyStorePassword.getBytes()) ;
         out.write(lineSeparator.getBytes()) ;
         out.flush() ;
      }

      if (askForStorePassword)
      {
        out.write(storePassword.getBytes()) ;
        out.write(lineSeparator.getBytes()) ;
        out.flush() ;

        // With Java6 and above, keytool asks for the password twice!
        if (!isJDK15() && isNewStorePassword)
        {
          out.write(storePassword.getBytes()) ;
          out.write(lineSeparator.getBytes()) ;
          out.flush() ;
        }
      }

      while (true)
      {
        int bytesRead = inputStream.read(buffer);
        if (bytesRead < 0)
        {
          break;
        }
        else if (bytesRead > 0)
        {
          output.write(buffer, 0, bytesRead);
        }
      }
      process.waitFor();
      int exitValue = process.exitValue();
      byte[] outputBytes = output.toByteArray();
      if (exitValue != 0)
      {
        // FIXME -- Make this an internationalizeable string.
        StringBuilder message = new StringBuilder();
        message.append("Unexpected exit code of ");
        message.append(exitValue);
        message.append(" returned from the keytool utility.");

        if ((outputBytes != null) && (outputBytes.length > 0))
        {
          message.append("  The generated output was:  '");
          message.append(new String(outputBytes));
          message.append("'.");
        }

        throw new KeyStoreException(message.toString());
      }
      else if ((! outputAcceptable) && (outputBytes != null) &&
               (outputBytes.length > 0))
      {
        // FIXME -- Make this an internationalizeable string.
        StringBuilder message = new StringBuilder();
        message.append("Unexpected output generated by the keytool " +
                       "utility:  '");
        message.append(new String(outputBytes));
        message.append("'.");

        throw new KeyStoreException(message.toString());
      }
    }
    catch (KeyStoreException kse)
    {
      throw kse;
    }
    catch (Exception e)
    {
      // FIXME -- Make this an internationalizeable string.
      throw new KeyStoreException("Could not invoke the KeyTool.run method:  " +
                                  e, e);
    }
  }



  /**
   * Retrieves a handle to the key store.
   *
   * @return  The handle to the key store, or {@code null} if the key store
   *          doesn't exist.
   *
   * @throws  KeyStoreException  If a problem occurs while trying to open the
   *                             key store.
   */
  private KeyStore getKeyStore()
          throws KeyStoreException
  {
    if (keyStore != null)
    {
      return keyStore;
    }

    // For JKS and PKCS12 key stores, we should make sure the file exists, and
    // we'll need an input stream that we can use to read it.  For PKCS11 key
    // stores there won't be a file and the input stream should be null.
    FileInputStream keyStoreInputStream = null;
    if (keyStoreType.equals(KEY_STORE_TYPE_JKS) ||
        keyStoreType.equals(KEY_STORE_TYPE_PKCS12))
    {
      File keyStoreFile = new File(keyStorePath);
      if (! keyStoreFile.exists())
      {
        return null;
      }

      try
      {
        keyStoreInputStream = new FileInputStream(keyStoreFile);
      }
      catch (Exception e)
      {
        throw new KeyStoreException(String.valueOf(e), e);
      }
    }


    KeyStore keyStore = KeyStore.getInstance(keyStoreType);
    try
    {
      keyStore.load(keyStoreInputStream, keyStorePIN.toCharArray());
      return this.keyStore = keyStore;
    }
    catch (Exception e)
    {
      throw new KeyStoreException(String.valueOf(e), e);
    }
    finally
    {
      if (keyStoreInputStream != null)
      {
        try
        {
          keyStoreInputStream.close();
        }
        catch (Throwable t)
        {
        }
      }
    }
  }

  /**
   * Returns whether we are running JDK 1.5 or not.
   * @return <CODE>true</CODE> if we are running JDK 1.5 and <CODE>false</CODE>
   * otherwise.
   */
  private boolean isJDK15()
  {
    boolean isJDK15 = false;
    try
    {
      String javaRelease = System.getProperty ("java.version");
      isJDK15 = javaRelease.startsWith("1.5");
    }
    catch (Throwable t)
    {
      System.err.println("Cannot get the java version: " + t);
    }
    return isJDK15;
  }
}


