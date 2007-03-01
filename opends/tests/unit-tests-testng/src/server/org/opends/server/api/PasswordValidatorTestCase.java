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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.net.Socket;
import java.util.ArrayList;
import java.util.Set;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.extensions.TestPasswordValidator;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.tools.LDAPPasswordModify;
import org.opends.server.types.Attribute;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for password validators.
 */
public class PasswordValidatorTestCase
       extends APITestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Gets simple test coverage for the default
   * PasswordValidator.finalizePasswordValidator method.
   */
  @Test()
  public void testFinalizePasswordValidator()
  {
    TestPasswordValidator.getInstance().finalizePasswordValidator();
  }



  /**
   * Performs a test to ensure that the password validation will be successful
   * under the base conditions for the password modify extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSuccessfulValidationPasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    assertEquals(TestPasswordValidator.getLastNewPassword().value(),
                 new ASN1OctetString("newPassword").value());
    assertFalse(TestPasswordValidator.getLastCurrentPasswords().isEmpty());
  }



  /**
   * Performs a test to ensure that the password validation will fail if the
   * test validator is configured to make it fail for the password modify
   * extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailedValidationPasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    TestPasswordValidator.setNextReturnValue(false);
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };

    int returnCode = LDAPPasswordModify.mainPasswordModify(args, false, null,
                                                           null);
    assertFalse(returnCode == 0);

    assertEquals(TestPasswordValidator.getLastNewPassword().value(),
                 new ASN1OctetString("newPassword").value());
    assertFalse(TestPasswordValidator.getLastCurrentPasswords().isEmpty());

    TestPasswordValidator.setNextReturnValue(true);
  }



  /**
   * Performs a test to make sure that the clear-text password will not be
   * provided if the user has a non-reversible scheme and does not provide the
   * current password for a password modify extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCurrentPasswordNotAvailablePasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertTrue(currentPasswords.isEmpty());
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a non-reversible scheme but provides the current password
   * for a password modify extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCurrentPasswordAvailablePasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next().value(),
                 new ASN1OctetString("password").value());
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a reversible scheme and does not provide the current
   * password for a password modify extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStoredPasswordAvailablePasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next().value(),
                 new ASN1OctetString("password").value());
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a reversible scheme and also provides the current password
   * for a password modify extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStoredAndCurrentPasswordAvailablePasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next().value(),
                 new ASN1OctetString("password").value());
  }



  /**
   * Performs a test to ensure that the password validation will be successful
   * under the base conditions for the modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSuccessfulValidationModify()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(3000);

    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
               new ASN1OctetString("uid=test.user,o=test"),
                                3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("newPassword"));
    LDAPAttribute attr = new LDAPAttribute("userPassword", values);
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  new ASN1OctetString("uid=test.user,o=test"), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertEquals(modifyResponse.getResultCode(), 0);

    assertEquals(TestPasswordValidator.getLastNewPassword().value(),
                 new ASN1OctetString("newPassword").value());
    assertTrue(TestPasswordValidator.getLastCurrentPasswords().isEmpty());
  }



  /**
   * Performs a test to ensure that the password validation will fail if the
   * test validator is configured to make it fail for the modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testFailedValidationModify()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(3000);

    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
               new ASN1OctetString("uid=test.user,o=test"),
                                3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("newPassword"));
    LDAPAttribute attr = new LDAPAttribute("userPassword", values);
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    TestPasswordValidator.setNextReturnValue(false);
    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  new ASN1OctetString("uid=test.user,o=test"), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertFalse(modifyResponse.getResultCode() == 0);

    assertEquals(TestPasswordValidator.getLastNewPassword().value(),
                 new ASN1OctetString("newPassword").value());
    assertTrue(TestPasswordValidator.getLastCurrentPasswords().isEmpty());

    TestPasswordValidator.setNextReturnValue(true);
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a non-reversible scheme but provides the current password
   * for a modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCurrentPasswordAvailableModify()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(3000);

    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
               new ASN1OctetString("uid=test.user,o=test"),
                                3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("password"));
    LDAPAttribute attr = new LDAPAttribute("userPassword", values);
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("newPassword"));
    attr = new LDAPAttribute("userPassword", values);
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  new ASN1OctetString("uid=test.user,o=test"), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertEquals(modifyResponse.getResultCode(), 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next().value(),
                 new ASN1OctetString("password").value());
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a reversible scheme and does not provide the current
   * password for a modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStoredPasswordAvailableModify()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "ds-privilege-name: bypass-acl",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(3000);

    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
               new ASN1OctetString("uid=test.user,o=test"),
                                3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("newPassword"));
    LDAPAttribute attr = new LDAPAttribute("userPassword", values);
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  new ASN1OctetString("uid=test.user,o=test"), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertEquals(modifyResponse.getResultCode(), 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next().value(),
                 new ASN1OctetString("password").value());
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a reversible scheme and also provides the current password
   * for a modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testStoredAndCurrentPasswordAvailableModify()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

    TestCaseUtils.initializeTestBackend(true);

    Entry userEntry = TestCaseUtils.makeEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "ds-privilege-name: bypass-acl",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");


    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation =
         conn.processAdd(userEntry.getDN(), userEntry.getObjectClasses(),
                         userEntry.getUserAttributes(),
                         userEntry.getOperationalAttributes());
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);


    Socket s = new Socket("127.0.0.1", (int) TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);
    r.setIOTimeout(3000);

    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
               new ASN1OctetString("uid=test.user,o=test"),
                                3, new ASN1OctetString("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    ArrayList<LDAPModification> mods = new ArrayList<LDAPModification>();
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("password"));
    LDAPAttribute attr = new LDAPAttribute("userPassword", values);
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    values = new ArrayList<ASN1OctetString>();
    values.add(new ASN1OctetString("newPassword"));
    attr = new LDAPAttribute("userPassword", values);
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  new ASN1OctetString("uid=test.user,o=test"), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeElement(message.encode());

    message = LDAPMessage.decode(r.readElement().decodeAsSequence());
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertEquals(modifyResponse.getResultCode(), 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next().value(),
                 new ASN1OctetString("password").value());
  }
}

