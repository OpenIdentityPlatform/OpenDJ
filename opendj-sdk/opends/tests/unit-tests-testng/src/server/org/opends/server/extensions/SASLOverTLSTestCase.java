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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.extensions;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Random;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.testng.Assert;

/**
 * This class tests SASL confidentiality/integrity over TLS (SSL). It
 * generates binary data larger than the TLS buffer size to make sure
 * that the data is processed correctly.
 *
 */
public class SASLOverTLSTestCase extends ExtensionsTestCase {

  private static int KB = 1024;
  private static final String factory = "com.sun.jndi.ldap.LdapCtxFactory";

  //Password policy
  private static final String pwdPolicy = "Temp PWD Policy";
  private static final String pwdPolicyDN =
                     "cn=" + pwdPolicy + ",cn=Password Policies,cn=config";

  //Keystore/truststore paths
  private String keyStorePath =
         DirectoryServer.getInstanceRoot() + File.separator + "config" +
         File.separator + "client.keystore";
  private String trustStorePath =
          DirectoryServer.getInstanceRoot() + File.separator +  "config" +
          File.separator + "client.truststore";

  //DNS
  private static String testUserDN = "cn=test.User, o=test";
  private static final String digestDN = "dn:"+ testUserDN;
  private static String dirMgr = "cn=Directory Manager";

  //Auth methods
  private static String simple = "simple";
  private static String digest =  "DIGEST-MD5";

  //Test QOS
  private static String confidentiality = "auth-conf";
  private static String integrity = "auth-int";

  //Go from 8KB to 64KB.
  @DataProvider(name = "kiloBytes")
   public Object[][] kiloBytes() {
     return new Object[][] {
          {8},
          {16},
          {24},
          {32},
          {64}
     };
   }

  @BeforeClass
  public void setup() throws Exception {
    TestCaseUtils.startServer();
    TestCaseUtils.dsconfig(
            "create-password-policy",
            "--policy-name", pwdPolicy,
            "--set", "password-attribute:userPassword",
            "--set", "default-password-storage-scheme: Clear"
            );
    TestCaseUtils.dsconfig(
            "set-sasl-mechanism-handler-prop",
            "--handler-name", "DIGEST-MD5",
            "--set", "quality-of-protection:" + "confidentiality",
            "--set", "server-fqdn:localhost");
    keyStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                          "config" + File.separator + "client.keystore";
    trustStorePath = DirectoryServer.getInstanceRoot() + File.separator +
                            "config" + File.separator + "client.truststore";
    System.setProperty("javax.net.ssl.keyStore",keyStorePath);
    System.setProperty("javax.net.ssl.keyStorePassword", "password");
    System.setProperty("javax.net.ssl.trustStore", trustStorePath);
    System.setProperty("javax.net.ssl.trustStorePassword", "password");
    addTestEntry();
  }

  @AfterClass(alwaysRun = true)
  public void tearDown() throws Exception {
    deleteTestEntry();
    TestCaseUtils.dsconfig(
        "delete-password-policy",
        "--policy-name", pwdPolicy
    );
    TestCaseUtils.dsconfig(
        "set-sasl-mechanism-handler-prop",
        "--handler-name", "DIGEST-MD5",
        "--reset", "server-fqdn",
        "--reset", "quality-of-protection");
  }

  /**
   * Test DIGEST-MD5 integrity over TLS.
   *
   * @throws NamingException If there was an JNDi naming error.
   * @throws IOException If there was an IO error occurs.
   */
  @Test(dataProvider = "kiloBytes")
  public void sslIntegrity(int size)throws NamingException, IOException {
    TestCaseUtils.dsconfig(
        "set-sasl-mechanism-handler-prop",
        "--handler-name", "DIGEST-MD5",
        "--set", "quality-of-protection:" + "integrity");
    sslTest(size, integrity);
  }

  /**
   * Test DIGEST-MD5 confidentiality over TLS.
   *
   * @throws NamingException If there was an JNDi naming error.
   * @throws IOException If there was an IO error occurs.
   */
  @Test(dataProvider = "kiloBytes")
  public void sslConfidentiality(int size)throws NamingException, IOException {
    TestCaseUtils.dsconfig(
        "set-sasl-mechanism-handler-prop",
        "--handler-name", "DIGEST-MD5",
        "--set", "quality-of-protection:" + "confidentiality");
    sslTest(size, confidentiality);
  }

 /**
  * Generate the test attributes, replace it in the entry, then read it
  * back to make sure it is the same as the original.
  *
  * @param size The number of KBs to generate in the random bytes.
  * @param qop The quality of protection.
  *
  * @throws NamingException If a JNDI naming error occurs.
  * @throws IOException If there was an IO error.
  */
  private void
  sslTest(int size, String qop) throws NamingException, IOException {
    Hashtable<String, String> env = new Hashtable<String, String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, factory);
    String url = "ldaps://localhost:" + TestCaseUtils.getServerLdapsPort();
    env.put(Context.PROVIDER_URL, url);
    env.put(Context.SECURITY_AUTHENTICATION,  digest);
    env.put(Context.SECURITY_PRINCIPAL, digestDN);
    env.put(Context.SECURITY_CREDENTIALS, "password");
    env.put("java.naming.ldap.attributes.binary", "jpegPhoto");
    env.put("javax.security.sasl.qop", qop);
    LdapContext ctx = new InitialLdapContext(env, null);
    byte[] jpegBytes = getRandomBytes(size);
    ModificationItem[] mods = new ModificationItem[1];
    Attribute jpegPhoto = new BasicAttribute("jpegPhoto", jpegBytes);
    mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, jpegPhoto);
    ctx.modifyAttributes(testUserDN, mods);
    Attributes testAttributes = ctx.getAttributes(testUserDN);
    Attribute jpegPhoto1 = testAttributes.get("jpegPhoto");
    byte[] jpegBytes1 = (byte[]) jpegPhoto1.get();
    Assert.assertTrue(Arrays.equals(jpegBytes, jpegBytes1));
    ctx.close();
  }

  /**
   * This test was originally testing DIGEST-MD5 confidentiality over StartTLS,
   * but JNDI had problems doing DIGEST-MD5 over StartTLS so the auth method was
   * changed to simple.
   *
   * @throws NamingException If there was an JNDi naming error.
   * @throws IOException If there was an IO error.
   */
  @Test(dataProvider = "kiloBytes")
  public void StartTLS(int size) throws NamingException, IOException {
    Hashtable<String, String> env = new Hashtable<String, String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, factory);
    String url = "ldap://localhost:" + TestCaseUtils.getServerLdapPort();
    env.put(Context.PROVIDER_URL, url);
    env.put("java.naming.ldap.attributes.binary", "jpegPhoto");
    LdapContext ctx = new InitialLdapContext(env, null);
    StartTlsResponse tls =
      (StartTlsResponse) ctx.extendedOperation(new StartTlsRequest());
    tls.setHostnameVerifier(new SampleVerifier());
    tls.negotiate();
    ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION,  simple);
    ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, testUserDN);
    ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, "password");
    byte[] jpegBytes = getRandomBytes(size);
    ModificationItem[] mods = new ModificationItem[1];
    Attribute jpegPhoto = new BasicAttribute("jpegPhoto", jpegBytes);
    mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, jpegPhoto);
    ctx.modifyAttributes(testUserDN, mods);
    Attributes testAttributes = ctx.getAttributes(testUserDN);
    Attribute jpegPhoto1 = testAttributes.get("jpegPhoto");
    byte[] jpegBytes1 = (byte[]) jpegPhoto1.get();
    Assert.assertTrue(Arrays.equals(jpegBytes, jpegBytes1));
    ctx.close();
  }

  /**
   * Add the entry we will use. It has it's own password
   * policy that uses clear a storage scheme.
   *
   * @throws NamingException If the entry cannot be added.
   */
  private void addTestEntry() throws NamingException {
    Attribute objectClass = new BasicAttribute("objectclass");
    objectClass.add("top");
    objectClass.add("person");
    objectClass.add("organizationalPerson");
    objectClass.add("inetOrgPerson");
    Attribute pwdPolicy =
                  new BasicAttribute("ds-pwp-password-policy-dn",pwdPolicyDN);
    Attribute cn = new BasicAttribute("cn", "test");
    cn.add("test.User");
    Attribute sn = new BasicAttribute("sn","User");
    Attributes entryAttrs = new BasicAttributes();
    entryAttrs.put(objectClass);
    entryAttrs.put(cn);
    entryAttrs.put(sn);
    entryAttrs.put(pwdPolicy);
    Hashtable<String, String> env = new Hashtable<String, String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, factory);
    String url = "ldaps://localhost:" + TestCaseUtils.getServerLdapsPort();
    env.put(Context.PROVIDER_URL, url);
    env.put(Context.SECURITY_PRINCIPAL, dirMgr);
    env.put(Context.SECURITY_CREDENTIALS, "password");
    env.put(Context.SECURITY_AUTHENTICATION, simple);
    DirContext ctx = new InitialDirContext(env);
    ctx.createSubcontext(testUserDN, entryAttrs);
    ModificationItem[] mods = new ModificationItem[1];
    Attribute pwd = new BasicAttribute("userPassword", "password");
    mods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE, pwd);
    ctx.modifyAttributes(testUserDN, mods);
    ctx.close();
  }

  /**
   * Get a byte buffer with a random set of bytes.
   *
   * @param kbs The number of KB (kilo-bytes) to generate.
   * @return Byte array of random bytes.
   */
  private static byte[] getRandomBytes(int kbs) {
    byte[] randomBytes = new byte[kbs * KB];
    Random r = new Random(0);
    for (int i = 0; i < randomBytes.length; i++) {
      randomBytes[i] = (byte) r.nextInt();
    }
    return randomBytes;
  }

/**
 * Delete the test entry.
 *
 * @throws NamingException If the entry cannot be deleted.
 */
  private void deleteTestEntry() throws NamingException {
    Hashtable<String, String> env = new Hashtable<String, String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, factory);
    String url = "ldaps://localhost:" + TestCaseUtils.getServerLdapsPort();
    env.put(Context.PROVIDER_URL, url);
    env.put(Context.SECURITY_PRINCIPAL, dirMgr);
    env.put(Context.SECURITY_CREDENTIALS, "password");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    DirContext ctx = new InitialDirContext(env);
    ctx.destroySubcontext(testUserDN);
    ctx.close();
  }

  /**
   * Verifier class so JNDI startTLS will work with "localhost" host name.
   * Returns trues, accepting any host name.
   */
  class SampleVerifier implements HostnameVerifier {
    public boolean verify(String hostname, SSLSession session) {
      return true;
    }
  }
}
