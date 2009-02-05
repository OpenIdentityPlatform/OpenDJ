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
 *      Copyright  2008 Sun Microsystems, Inc.
 */

package org.opends.server.protocols.ldap;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.tools.*;
import org.opends.server.types.*;
import org.testng.annotations.*;
import static org.testng.Assert.*;

/*
 * This class defines a set of testcases for testing the ;binary transfer
 * option functionality across different protocol versions of LDAP.
 */
public class LDAPBinaryOptionTestCase extends LdapTestCase {
  // Exported LDIF file.
  File ldif = null;
  //LDIFExportConfig used for exporting entries.
  LDIFExportConfig exportConfig = null;
  //LDIFImportConfig used for importing entries.
  LDIFImportConfig importConfig = null;
  //Test Backend.
  Backend backend = null;

  //Constant value of userCertificate attribute.
  private static final String CERT=
      ": MIIB5TCCAU6gAwIBAgIERloIajANBgkqhkiG9" +
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
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
  }



  /**
   * Test to verify an ADD of the binary attributes  using a V3
   * protocol.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void binaryAttributeAddV3() throws Exception
  {
    //Add a binary attribute both with or without the binary option.
    String filePath = TestCaseUtils.createTempFile(
      "dn: uid=user.1,o=test",
      "objectClass: inetOrgPerson",
      "uid: user.1",
      "sn: 1",
      "cn: user 1",
      "userCertificate"+CERT
      );
    String[] args = new String []
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-a",
      "-f", filePath
    };
    int err = LDAPModify.mainModify(args, false, null,null);
    assertEquals(err,0);

    //ADD with ;binary option.
    filePath = TestCaseUtils.createTempFile(
      "dn: uid=user.2,o=test",
      "objectClass: inetOrgPerson",
      "uid: user.2",
      "sn: 2",
      "cn: user 2",
      "userCertificate;binary"+CERT
      );
    args = new String []
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-a",
      "-f", filePath
    };
    err = LDAPModify.mainModify(args, false, null,null);
    assertEquals(err,0);
  }



  /**
   * Test to verify an ADD of a non-binary attribute with the ;binary
   * transfer option using a V3 protocol.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
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
      "-a",
      "-f", filePath,
    };
    int err = LDAPModify.mainModify(args, false, null,null);
    assertFalse(err==0);
  }




  /**
   * Test to verify a SEARCH using the ;binary transfer option using a V3
   * protocol.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dependsOnMethods = {"org.opends.server.protocols.ldap."+
  "LDAPBinaryOptionTestCase.binaryAttributeAddV3"})
  public void binaryAttributeSearchV3() throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf("o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(uid=user.1)"),
              null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    SearchResultEntry e = entries.get(0);
    assertNotNull(e);
    List<Attribute> attrs = e.getAttribute("usercertificate");
    Attribute a = attrs.get(0);
    assertNotNull(a);
    assertTrue(a.getOptions().contains("binary"));

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
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    LinkedHashSet<String> attrs = new LinkedHashSet<String>();
    attrs.add("cn;binary");
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(
              conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              new ArrayList<Control>(),
              ByteString.valueOf("o=test"),
              SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES,
              Integer.MAX_VALUE,
              Integer.MAX_VALUE,
              false,
              LDAPFilter.decode("(uid=user.1)"),
              attrs, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    List<SearchResultEntry> entries = searchOperation.getSearchEntries();
    SearchResultEntry e = entries.get(0);
    assertNotNull(e);
    List<Attribute> list = e.getAttributes();
    assertEquals(list.size(), 0);
    }



  /**
   * Test to verify an ADD and SEARCH using the ;binary transfer option using a
   * V2 protocol.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void binaryOptionUsingV2() throws Exception
  {
    //Construct a V2 connection.
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    org.opends.server.tools.LDAPWriter w = new org.opends.server.tools.LDAPWriter(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    ByteString.valueOf("cn=Directory Manager"), 2,
                    ByteString.valueOf("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeMessage(message);

      message = r.readMessage();
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);
      ArrayList<RawAttribute> addAttrs = new ArrayList<RawAttribute>();
      addAttrs.add(RawAttribute.create("objectClass", "inetOrgPerson"));
      addAttrs.add(RawAttribute.create("uid", "user.7"));
      addAttrs.add(RawAttribute.create("cn", "user 7"));
      addAttrs.add(RawAttribute.create("sn", "sn#1"));
      addAttrs.add(RawAttribute.create("sn;x-foo", "sn#2"));
      addAttrs.add(RawAttribute.create("sn;lang-fr", "sn#3"));
      addAttrs.add(RawAttribute.create("userCertificate;binary", CERT));

      AddRequestProtocolOp addRequest =
           new AddRequestProtocolOp(ByteString.valueOf("uid=user.7,o=test"),
                                    addAttrs);
      message = new LDAPMessage(2, addRequest);
      w.writeMessage(message);

      message = r.readMessage();
      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertEquals(addResponse.getResultCode(),0);

      //Create a SEARCH request to search for this added entry.
      LinkedHashSet<String> attrs = new LinkedHashSet<String>();
      //Request only the interesting attributes.
      attrs.add("sn");
      attrs.add("userCertificate;binary");
      SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(ByteString.valueOf("o=test"),
                                     SearchScope.WHOLE_SUBTREE,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false,
                                     LDAPFilter.decode("(uid=user.7)"),
                                     attrs);
      message = new LDAPMessage(2, searchRequest);
      w.writeMessage(message);

      SearchResultEntryProtocolOp searchResultEntry = null;
      SearchResultDoneProtocolOp searchResultDone = null;
      while (searchResultDone == null && message != null)
      {
        message = r.readMessage();
        switch (message.getProtocolOpType())
        {
          case LDAPConstants.OP_TYPE_SEARCH_RESULT_ENTRY:
            searchResultEntry = message.getSearchResultEntryProtocolOp();
            break;
          case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
            searchResultDone = message.getSearchResultDoneProtocolOp();
            assertEquals(searchResultDone.getResultCode(),
                         LDAPResultCode.SUCCESS);
            break;
        }
      }
      assertNotNull(searchResultEntry);
      boolean certWithNoOption = false;
      boolean snWithMultiVal = false;
      for(LDAPAttribute a:searchResultEntry.getAttributes())
      {
        //Shouldn't be userCertificate;binary.
        if(a.getAttributeType().equalsIgnoreCase("userCertificate"))
          certWithNoOption=true;
        else if(a.getAttributeType().equalsIgnoreCase("sn"))
        {
          List<ByteString> lVal = a.getValues();
          for(ByteString v:lVal)
          {
            String val = v.toString();
            if(val.equals("sn#1") || val.equals("sn#2")
                    || val.equals("sn#3"))
            {
              snWithMultiVal = true;
            }
            else //All the values should match.
              snWithMultiVal =  false;
          }

        }
      }
      assertTrue(snWithMultiVal && certWithNoOption);
    }
    finally
    {
      try
      {
        r.close();
      } catch (Exception e) {}
      try
      {
        w.close();
      } catch (Exception e) {}
      try
      {
        s.close();
      } catch (Exception e) {}
    }

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
    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
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
    assert(ldif.exists());
    importLDIF();
    assertTrue(containsBinary());
    //Remove the binary option and re-import it.
    FileReader reader = new FileReader(ldif);
    BufferedReader buf = new BufferedReader(reader);
    StringBuilder builder = new StringBuilder();
    String userCert = "userCertificate;binary";
    String line = null;
    while((line=buf.readLine())!=null)
    {
      if(line.startsWith(userCert))
      {

        builder.append("userCertificate:");
        builder.append(line.substring(userCert.length()+1, line.length()));
      }
      else
        builder.append(line);
      builder.append("\n");
    }
    buf.close();
    ldif.delete();
    ldif = new File(TestCaseUtils.createTempFile(builder.toString()));
    importLDIF();
    //Re-export it.
    exportBackend();
    assertTrue(containsBinary());
    ldif.delete();
  }



  /**
   * Test to verify a MODIFY using the ;binary transfer option using V3
   * protocol.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
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
      "-a",
      "-f", filePath,
    };
    int err = LDAPModify.mainModify(args, false, null,null);
    assertEquals(err,0);

    filePath = TestCaseUtils.createTempFile(
     "dn: uid=user.4,o=test",
     "changetype: modify",
     "add: usercertificate;binary",
     "userCertificate;binary" + CERT);
    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-f", filePath,
    };
    err = LDAPModify.mainModify(args, false, null,null);
    assertEquals(err,0);

  }



  /**
   * Utility method to verify if the LDIF file contains binary option.
   * @return  {@code true} if binary option is found in the LDIF
   *           , or {@code false} if not.
   * @throws  Exception  If an unexpected problem occurs.
   */
  private boolean containsBinary() throws Exception
  {
    FileReader reader = new FileReader(ldif);
    BufferedReader buf = new BufferedReader(reader);
    String line = null;
    boolean found=false;
    while((line=buf.readLine())!=null)
    {
      if(line.startsWith("userCertificate;binary"))
        found = true;
    }
    buf.close();
    return found;
  }



  /**
   * Utility method to export the database into an LDIF file.
   * @throws  Exception  If an unexpected problem occurs.
   */
  private void exportBackend() throws Exception
  {
     //Initialize necessary instance variables.
    if(ldif==null)
      ldif = File.createTempFile("LDAPBinaryOptionTestCase", ".ldif");
    exportConfig = new LDIFExportConfig(ldif.getAbsolutePath(),
                              ExistingFileBehavior.OVERWRITE);
    backend = DirectoryServer.getBackend("test");
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
    backend = DirectoryServer.getBackend("test");
    backend.importLDIF(importConfig);
  }
}
