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
 * Copyright  2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.types.NullOutputStream.nullPrintStream;
import static org.testng.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.forgerock.opendj.ldap.Base64;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.LocalBackend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import com.forgerock.opendj.ldap.tools.LDAPModify;
import com.forgerock.opendj.ldap.tools.LDAPSearch;
import org.opends.server.tools.RemoteConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDAPException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.SearchResultEntry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * This class defines a set of test cases for testing the binary transfer option
 * functionality across different protocol versions of LDAP.
 */
public class LDAPBinaryOptionTestCase extends LdapTestCase {
  /** Exported LDIF file. */
  private File ldif;
  /** LDIFExportConfig used for exporting entries. */
  private LDIFExportConfig exportConfig;
  /** LDIFImportConfig used for importing entries. */
  private LDIFImportConfig importConfig;
  /** Test Backend. */
  private LocalBackend<?> backend;

  /** Constant value of userCertificate attribute. */
  private static final String CERT=
      "MIIB5TCCAU6gAwIBAgIERloIajANBgkqhkiG9" +
      "w0BAQUFADA3MQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRXhhbXBs" +
      "ZSBDb3JwMREwDwYDVQQDEwhKb2huIERvZTAeFw0wNzA1MjcyMjM4" +
      "MzRaFw0wNzA4MjUyMjM4MzRaMDcxCzAJBgNVBAYTAlVTMRUwEwYD" +
      "VQQKEwxFeGFtcGxlIENvcnAxETAPBgNVBAMTCEpvaG4gRG9lMIGfMA" +
      "0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCWNZB4qs1UvjYgvGvB" +
      "9udmiUi4X4DeaSm3o0p8PSwpOFxSqgWdSwKgUugZ1EJVyYoakljDFs" +
      "J0GVown+dIB24V4ozNs6wa0YotIKTV2AcySQkmzzP3e+OnE9Aa1wlB/" +
      "PVnh1CFLgk1UOoruLE10bac5HA8QiAmfNMorU26AwFTcwIDAQABMA" +
      "0GCSqGSIb3DQEBBQUAA4GBAGrzMKNbBRWn+LIfYTfqKYUc258XVbhFri" +
      "1OV0oF82vyvciYWZzyxLc52EPDsymLmcDh+CdWxy3bVkjdMg1WEtMGr" +
      "1GsxOVi/vWe+kT4tPhinnB4Fowf8zgqiUKo9/FJN26y7Fpvy1IODiBInDrKZ" +
      "RvNfqemCf7o3+Cp00OmF5ey";



  /**
   * Ensures that the Directory Server is running before executing the
   * testcases.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
  }



  /**
   * Test to verify an ADD of the binary attributes  using a V3 protocol.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void binaryAttributeAddV3() throws Exception
  {
    //Add a binary attribute both with or without the binary option.
    String filePath = TestCaseUtils.createTempFile(
      "dn: uid=user.1,o=test",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "sn: 1",
      "cn: user 1",
      "userCertificate:: "+CERT
      );
    String[] args = new String []
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-f", filePath
    };
    int err = LDAPModify.run(nullPrintStream(), nullPrintStream(), args);
    assertEquals(err,0);

    //ADD with ;binary option.
    filePath = TestCaseUtils.createTempFile(
      "dn: uid=user.2,o=test",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "sn: 2",
      "cn: user 2",
      "userCertificate;binary:: "+CERT
      );
    args = new String []
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-f", filePath
    };
    err = LDAPModify.run(nullPrintStream(), nullPrintStream(), args);
    assertEquals(err,0);
  }



  /**
   * Test to verify an ADD of a non-binary attribute with the ;binary
   * transfer option using a V3 protocol.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void nonBinaryAttributeAddV3() throws Exception
  {
    String filePath = TestCaseUtils.createTempFile(
      "dn: uid=user.3,o=test",
      "objectClass: inetOrgPerson",
      "uid: user.3",
      "sn: 3",
      "cn: user 3",
      "cn;binary: common name"
      );
    String[] args = new String []
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-f", filePath,
    };
    int err = LDAPModify.run(nullPrintStream(), nullPrintStream(), args);
    assertThat(err).isNotEqualTo(0);
  }




  /**
   * Test to verify a SEARCH using the ;binary transfer option using a V3 protocol.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dependsOnMethods = {"org.opends.server.protocols.ldap."+
  "LDAPBinaryOptionTestCase.binaryAttributeAddV3"})
  public void binaryAttributeSearchV3() throws Exception
  {
    SearchRequest request = newSearchRequest("o=test", SearchScope.WHOLE_SUBTREE, "(uid=user.1)");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    SearchResultEntry e = entries.get(0);
    assertNotNull(e);
    Attribute a = e.getAllAttributes("usercertificate").iterator().next();
    assertNotNull(a);
    assertThat(a.getAttributeDescription().getOptions()).contains("binary");
  }

  /**
   * Test to verify a SEARCH using the ;binary transfer option using a V3
   * protocol for a non-binary attribute.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dependsOnMethods = {"org.opends.server.protocols.ldap."+
  "LDAPBinaryOptionTestCase.binaryAttributeAddV3"})
  public void invalidBinaryAttributeSearchV3() throws Exception
  {
    SearchRequest request = newSearchRequest("o=test", SearchScope.WHOLE_SUBTREE, "(uid=user.1)")
        .addAttribute("cn;binary");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    SearchResultEntry e = entries.get(0);
    assertNotNull(e);
    assertThat(e.getAllAttributes()).isEmpty();
  }



  /**
   * Test to verify an ADD and SEARCH using the ;binary transfer option using a
   * V2 protocol.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void binaryOptionUsingV2() throws Exception
  {
    //Construct a V2 connection.
    try (RemoteConnection conn = new RemoteConnection("localhost", TestCaseUtils.getServerLdapPort()))
    {
      bindLdapV2(conn, "cn=Directory Manager", "password");

      AddRequest addRequest = Requests.newAddRequest("uid=user.7,o=test")
          .addAttribute("objectClass", "inetOrgPerson")
          .addAttribute("uid", "user.7")
          .addAttribute("cn", "user 7")
          .addAttribute("sn", "sn#1")
          .addAttribute("sn;x-foo", "sn#2")
          .addAttribute("sn;lang-fr", "sn#3")
          .addAttribute("userCertificate;binary", ByteString.wrap(Base64.decode(CERT).toByteArray()));
      LDAPMessage message = conn.add(addRequest);
      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertEquals(addResponse.getResultCode(),0);

      //Create a SEARCH request to search for this added entry.
      //Request only the interesting attributes.
      conn.search(Requests.newSearchRequest("o=test", WHOLE_SUBTREE, "(uid=user.7)", "sn", "userCertificate;binary"));
      List<SearchResultEntryProtocolOp> entries = conn.readEntries();
      assertThat(entries).hasSize(1);
      boolean certWithNoOption = false;
      boolean snWithMultiVal = false;
      for (LDAPAttribute a : entries.get(0).getAttributes())
      {
        //Shouldn't be userCertificate;binary.
        if ("userCertificate".equalsIgnoreCase(a.getAttributeType()))
        {
          certWithNoOption=true;
        }
        else if ("sn".equalsIgnoreCase(a.getAttributeType()))
        {
          for (ByteString v : a.getValues())
          {
            String val = v.toString();
            snWithMultiVal = val.equals("sn#1") || val.equals("sn#2") || val.equals("sn#3");
          }
        }
      }
      assertTrue(snWithMultiVal && certWithNoOption);
    }
  }

  private void bindLdapV2(RemoteConnection conn, String bindDN, String bindPwd) throws IOException, LDAPException
  {
    conn.writeMessage(new BindRequestProtocolOp(ByteString.valueOfUtf8(bindDN), 2, ByteString.valueOfUtf8(bindPwd)));

    LDAPMessage message = conn.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), LDAPResultCode.SUCCESS);
  }

  /**
   * Test to verify that the DB stores the binary option by
   * exporting the LDIF and searching for the binary keyword.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dependsOnMethods = {"org.opends.server.protocols.ldap."+
  "LDAPBinaryOptionTestCase.binaryAttributeAddV3"})
  public void exportAndValidateBinaryAttribute() throws Exception
  {
    //Ensure that the entry exists.
    String args[] = new String []
    {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-b", "o=test",
        "(uid=user.1)"
    };
    assertEquals(LDAPSearch.run(nullPrintStream(), System.err, args), 0);
    exportBackend();
    assertTrue(ldif.exists());
    assertTrue(containsBinary());
  }



  /**
   * Test to verify that the server adds the binary option by
   * importing an LDIF both with or without the binary option.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dependsOnMethods = {"org.opends.server.protocols.ldap."+
  "LDAPBinaryOptionTestCase.exportAndValidateBinaryAttribute"})
  public void ImportAndValidateBinaryAttribute() throws Exception
  {
    //This testcase uses the exported ldif in exportAndValidBinaryAttribute
    //testcase. It imports the ldif after cleaning up the database and verifies
    //that the binary option is present in the binary attributes. After that, it
    //modifies the binary option from the ldif and re-imports it.A re-export of
    //the last import should have the binary option even though it wasn't
    //there in the imported ldif.
    assertTrue(ldif.exists());
    importLDIF();
    assertTrue(containsBinary());
    //Remove the binary option and re-import it.
    StringBuilder builder = new StringBuilder();
    try (FileReader reader = new FileReader(ldif);
        BufferedReader buf = new BufferedReader(reader))
    {
      String userCert = "userCertificate;binary";
      String line = null;
      while ((line = buf.readLine()) != null)
      {
        if (line.startsWith(userCert))
        {
          builder.append("userCertificate:");
          builder.append(line, userCert.length() + 1, line.length());
        }
        else
        {
          builder.append(line);
        }
        builder.append("\n");
      }
    }
    ldif.delete();
    ldif = new File(TestCaseUtils.createTempFile(builder.toString()));
    importLDIF();
    //Re-export it.
    exportBackend();
    assertTrue(containsBinary());
    ldif.delete();
  }



  /**
   * Test to verify a MODIFY using the ;binary transfer option using V3 protocol.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void binaryModifyV3() throws Exception
  {
    String filePath = TestCaseUtils.createTempFile(
      "dn: uid=user.4,o=test",
      "objectClass: inetOrgPerson",
      "uid: user.4",
      "sn: 4",
      "cn: user 4"
      );
    String[] args = new String []
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-f", filePath,
    };
    int err = LDAPModify.run(nullPrintStream(), nullPrintStream(), args);
    assertEquals(err,0);

    filePath = TestCaseUtils.createTempFile(
     "dn: uid=user.4,o=test",
     "changetype: modify",
     "add: usercertificate;binary",
     "userCertificate;binary:: " + CERT);
    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-f", filePath,
    };
    err = LDAPModify.run(nullPrintStream(), nullPrintStream(), args);
    assertEquals(err,0);
  }



  /**
   * Utility method to verify if the LDIF file contains binary option.
   * @return  {@code true} if binary option is found in the LDIF, or {@code false} if not.
   * @throws  Exception  If an unexpected problem occurs.
   */
  private boolean containsBinary() throws Exception
  {
    try (FileReader reader = new FileReader(ldif);
        BufferedReader buf = new BufferedReader(reader))
    {
      String line = null;
      boolean found = false;
      while ((line = buf.readLine()) != null)
      {
        if (line.startsWith("userCertificate;binary"))
        {
          found = true;
        }
      }
      return found;
    }
  }



  /**
   * Utility method to export the database into an LDIF file.
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void exportBackend() throws Exception
  {
     //Initialize necessary instance variables.
    if(ldif==null)
    {
      ldif = File.createTempFile("LDAPBinaryOptionTestCase", ".ldif");
    }
    exportConfig = new LDIFExportConfig(ldif.getAbsolutePath(),
                              ExistingFileBehavior.OVERWRITE);
    backend = TestCaseUtils.getServerContext().getBackendConfigManager().getLocalBackendById("test");
    backend.exportLDIF(exportConfig);
  }



  /**
   * Utility method to import the LDIF into  the database.
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void importLDIF() throws Exception
  {
    importConfig = new LDIFImportConfig(ldif.getAbsolutePath());
    TestCaseUtils.initializeTestBackend(false);
    backend = TestCaseUtils.getServerContext().getBackendConfigManager().getLocalBackendById("test");
    backend.importLDIF(importConfig, DirectoryServer.getInstance().getServerContext());
  }
}
