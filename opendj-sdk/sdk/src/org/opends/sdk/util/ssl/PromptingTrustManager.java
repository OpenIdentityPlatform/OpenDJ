package org.opends.sdk.util.ssl;

import static org.opends.messages.UtilityMessages.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.opends.messages.Message;
import org.opends.sdk.util.Validator;
import org.opends.server.util.cli.*;

/**
 * Created by IntelliJ IDEA.
 * User: boli
 * Date: Oct 16, 2009
 * Time: 10:38:50 AM
 * To change this template use File | Settings | File Templates.
 */
public class PromptingTrustManager implements X509TrustManager
{
  static private final Logger LOG =
      Logger.getLogger(PromptingTrustManager.class.getName());
  static private final String DEFAULT_PATH =
      System.getProperty("user.home") + File.separator + ".opends" +
          File.separator + "keystore";
  static private final char[] DEFAULT_PASSWORD = "OpenDS".toCharArray();

  /**
   * Enumeration description server certificate trust option.
   */
  private enum TrustOption
  {
    UNTRUSTED(1, INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_NO.get()),
    SESSION(2,INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_SESSION.get()),
    PERMAMENT(3,INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION_ALWAYS.get()),
    CERTIFICATE_DETAILS(4,
        INFO_LDAP_CONN_PROMPT_SECURITY_CERTIFICATE_DETAILS.get());

    private Integer choice;

    private Message msg;

    /**
     * Private constructor.
     *
     * @param i
     *          the menu return value.
     * @param msg
     *          the message message.
     */
    private TrustOption(int i, Message msg)
    {
      choice = i;
      this.msg = msg;
    }

    /**
     * Returns the choice number.
     *
     * @return the attribute name.
     */
    public Integer getChoice()
    {
      return choice;
    }

    /**
     * Return the menu message.
     *
     * @return the menu message.
     */
    public Message getMenuMessage()
    {
      return msg;
    }
  }

  private final KeyStore inMemoryTrustStore;
  private final KeyStore onDiskTrustStore;

  private final X509TrustManager inMemoryTrustManager;
  private final X509TrustManager onDiskTrustManager;

  private final X509TrustManager nestedTrustManager;
  private final ConsoleApplication app;

  public PromptingTrustManager(ConsoleApplication app,
                               X509TrustManager sourceTrustManager)
      throws KeyStoreException, IOException, NoSuchAlgorithmException,
      CertificateException
  {
    this(app, DEFAULT_PATH, sourceTrustManager);
  }
  public PromptingTrustManager(ConsoleApplication app, String acceptedStorePath,
                               X509TrustManager sourceTrustManager)
      throws KeyStoreException, IOException, NoSuchAlgorithmException,
      CertificateException
  {
    Validator.ensureNotNull(app, acceptedStorePath);
    this.app = app;
    this.nestedTrustManager = sourceTrustManager;
    inMemoryTrustStore =
        KeyStore.getInstance(KeyStore.getDefaultType());
    onDiskTrustStore =
        KeyStore.getInstance(KeyStore.getDefaultType());

    File onDiskTrustStorePath = new File(acceptedStorePath);
    inMemoryTrustStore.load(null, null);
    if(!onDiskTrustStorePath.exists())
    {
      onDiskTrustStore.load(null, null);
    }
    else
    {
      FileInputStream fos = new FileInputStream(onDiskTrustStorePath);
      onDiskTrustStore.load(fos, DEFAULT_PASSWORD);
    }
    TrustManagerFactory tmf = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());

    tmf.init(inMemoryTrustStore);
    X509TrustManager x509tm = null;
    for(TrustManager tm : tmf.getTrustManagers())
    {
      if(tm instanceof X509TrustManager)
      {
        x509tm = (X509TrustManager)tm;
        break;
      }
    }
    if(x509tm == null)
    {
      throw new NoSuchAlgorithmException();
    }
    this.inMemoryTrustManager = x509tm;

    tmf.init(onDiskTrustStore);
    x509tm = null;
    for(TrustManager tm : tmf.getTrustManagers())
    {
      if(tm instanceof X509TrustManager)
      {
        x509tm = (X509TrustManager)tm;
        break;
      }
    }
    if(x509tm == null)
    {
      throw new NoSuchAlgorithmException();
    }
    this.onDiskTrustManager = x509tm;
  }

  public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
      throws CertificateException
  {
    try
    {
      inMemoryTrustManager.checkClientTrusted(x509Certificates, s);
    }
    catch(Exception ce1)
    {
      try
      {
        onDiskTrustManager.checkClientTrusted(x509Certificates, s);
      }
      catch(Exception ce2)
      {
        if(nestedTrustManager != null)
        {
          try
          {
          nestedTrustManager.checkClientTrusted(x509Certificates, s);
          }
          catch(Exception ce3)
          {
            checkManuallyTrusted(x509Certificates, ce3);
          }
        }
        else
        {
          checkManuallyTrusted(x509Certificates, ce1);
        }
      }
    }
  }

  public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
      throws CertificateException
  {
    try
    {
      inMemoryTrustManager.checkServerTrusted(x509Certificates, s);
    }
    catch(Exception ce1)
    {
      try
      {
        onDiskTrustManager.checkServerTrusted(x509Certificates, s);
      }
      catch(Exception ce2)
      {
        if(nestedTrustManager != null)
        {
          try
          {
          nestedTrustManager.checkServerTrusted(x509Certificates, s);
          }
          catch(Exception ce3)
          {
            checkManuallyTrusted(x509Certificates, ce3);
          }
        }
        else
        {
          checkManuallyTrusted(x509Certificates, ce1);
        }
      }
    }
  }

  public X509Certificate[] getAcceptedIssuers() {
    if(nestedTrustManager != null)
    {
      return nestedTrustManager.getAcceptedIssuers();
    }
    return new X509Certificate[0];
  }

  /**
   * Indicate if the certificate chain can be trusted.
   *
   * @param chain The certificate chain to validate
   * certificate.
   */
  private void checkManuallyTrusted(X509Certificate[] chain,
                                    Exception exception)
      throws CertificateException
  {
    app.println();
    app.println(INFO_LDAP_CONN_PROMPT_SECURITY_SERVER_CERTIFICATE.get());
    app.println();
    for (int i = 0; i < chain.length; i++)
    {
      // Certificate DN
      app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_USER_DN.get(
          chain[i].getSubjectDN().toString()));

      // certificate validity
      app.println(
          INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_VALIDITY.get(
              chain[i].getNotBefore().toString(),
              chain[i].getNotAfter().toString()));

      // certificate Issuer
      app.println(
          INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE_ISSUER.get(
              chain[i].getIssuerDN().toString()));

      if (i+1 <chain.length)
      {
        app.println();
        app.println();
      }
    }
    MenuBuilder<Integer> builder = new MenuBuilder<Integer>(app);
    builder.setPrompt(INFO_LDAP_CONN_PROMPT_SECURITY_TRUST_OPTION.get());

    TrustOption defaultTrustMethod = TrustOption.SESSION ;
    for (TrustOption t : TrustOption.values())
    {
      int i = builder.addNumberedOption(t.getMenuMessage(), MenuResult
          .success(t.getChoice()));
      if (t.equals(defaultTrustMethod))
      {
        builder.setDefault(
            INFO_LDAP_CONN_PROMPT_SECURITY_PROTOCOL_DEFAULT_CHOICE
                .get(new Integer(i)), MenuResult.success(t.getChoice()));
      }
    }

    app.println();
    app.println();

    Menu<Integer> menu = builder.toMenu();
    while (true)
    {
      try
      {
        MenuResult<Integer> result = menu.run();
        if (result.isSuccess())
        {
          if (result.getValue().equals(TrustOption.UNTRUSTED.getChoice()))
          {
            if(exception instanceof CertificateException)
            {
              throw (CertificateException)exception;
            }
            else
            {
              throw new CertificateException(exception);
            }
          }

          if ((result.getValue().equals(TrustOption.CERTIFICATE_DETAILS
              .getChoice())))
          {
            for (X509Certificate aChain : chain) {
              app.println();
              app.println(INFO_LDAP_CONN_SECURITY_SERVER_CERTIFICATE
                  .get(aChain.toString()));
            }
            continue;
          }

          // Update the trust manager with the new certificate
          acceptCertificate(chain,
              result.getValue().equals(TrustOption.PERMAMENT.getChoice()));
          break;
        }
        else
        {
          // Should never happen.
          throw new RuntimeException();
        }
      }
      catch (CLIException cliE)
      {
        throw new RuntimeException(cliE);
      }
    }
  }

  /**
   * This method is called when the user accepted a certificate.
   * @param chain the certificate chain accepted by the user.
   * certificate.
   */
  public void acceptCertificate(X509Certificate[] chain, boolean permanent)
  {
    if(permanent)
    {
      LOG.log(Level.INFO, "Permanently accepting certificate chain to " +
          "truststore");
    }
    else
    {
      LOG.log(Level.INFO, "Accepting certificate chain for this session");
    }

    for (X509Certificate aChain : chain) {
      try
      {
        String alias = aChain.getSubjectDN().getName();
        inMemoryTrustStore.setCertificateEntry(alias, aChain);
        if(permanent)
        {
          onDiskTrustStore.setCertificateEntry(alias, aChain);
        }
      }
      catch(Exception e)
      {
        LOG.log(Level.WARNING, "Error setting certificate to store: " + e +
            "\nCert: " + aChain.toString());
      }
    }

    if(permanent)
    {
      try
      {
        File truststoreFile = new File(DEFAULT_PATH);
        if (!truststoreFile.exists())
        {
          createFile(truststoreFile);
        }
        FileOutputStream fos = new FileOutputStream(truststoreFile);
        onDiskTrustStore.store(fos, DEFAULT_PASSWORD);
        fos.close();
      }
      catch(Exception e)
      {
        LOG.log(Level.WARNING, "Error saving store to disk: " + e);
      }
    }
  }

  private boolean createFile(File f) throws IOException {
    boolean success = false;
    if (f != null) {
      File parent = f.getParentFile();
      if (!parent.exists()) {
        parent.mkdirs();
      }
      success = f.createNewFile();
    }
    return success;
  }
}
