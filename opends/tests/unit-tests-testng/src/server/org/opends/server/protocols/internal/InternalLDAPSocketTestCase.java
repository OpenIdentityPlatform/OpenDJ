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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.internal;


import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.ldap.*;
import org.opends.server.tools.LDAPReader;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.*;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * This class provides a number of tests to cover the internal LDAP socket
 * implementation.
 */
public class InternalLDAPSocketTestCase
       extends InternalTestCase
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
   * Tests the ability to perform an add operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);
    assertFalse(DirectoryServer.entryExists(DN.decode("o=test")));

    InternalLDAPSocket socket = new InternalLDAPSocket();
    LDAPReader reader = new LDAPReader(socket);
    LDAPWriter writer = new LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    writer.writeMessage(message);

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);


    ArrayList<RawAttribute> attrList = new ArrayList<RawAttribute>();
    attrList.add(RawAttribute.create("objectClass", "organization"));
    attrList.add(RawAttribute.create("o", "test"));

    AddRequestProtocolOp addRequest =
         new AddRequestProtocolOp(ByteString.valueOf("o=test"), attrList);
    writer.writeMessage(new LDAPMessage(2, addRequest));

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getAddResponseProtocolOp().getResultCode(),
                 LDAPResultCode.SUCCESS);
    assertTrue(DirectoryServer.entryExists(DN.decode("o=test")));

    reader.close();
    writer.close();
    socket.close();
  }



  /**
   * Tests the ability to perform an add operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddOperationThroughJNDI()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);
    assertFalse(DirectoryServer.entryExists(DN.decode("o=test")));


    Hashtable<String,String> env = new Hashtable<String,String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");
    env.put("com.sun.jndi.ldap.connect.pool.debug", "fine");

    DirContext context = new InitialDirContext(env);

    Attributes attributes = new BasicAttributes(true);

    Attribute objectClass = new BasicAttribute("objectClass");
    objectClass.add("top");
    objectClass.add("organization");
    attributes.put(objectClass);

    Attribute o = new BasicAttribute("o");
    o.add("test");
    attributes.put(o);

    context.createSubcontext("o=test", attributes);
    assertTrue(DirectoryServer.entryExists(DN.decode("o=test")));

    context.close();
  }



  /**
   * Tests the ability to perform a compare operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.decode("o=test")));

    InternalLDAPSocket socket = new InternalLDAPSocket();
    LDAPReader reader = new LDAPReader(socket);
    LDAPWriter writer = new LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    writer.writeMessage(message);

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);


    CompareRequestProtocolOp compareRequest =
         new CompareRequestProtocolOp(ByteString.valueOf("o=test"), "o",
                                      ByteString.valueOf("test"));
    writer.writeMessage(new LDAPMessage(2, compareRequest));

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getCompareResponseProtocolOp().getResultCode(),
                 LDAPResultCode.COMPARE_TRUE);

    reader.close();
    writer.close();
    socket.close();
  }



  /**
   * Tests the ability to perform a compare operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCompareOperationThroughJNDI()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.decode("o=test")));


    Hashtable<String,String> env = new Hashtable<String,String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");

    DirContext context = new InitialDirContext(env);

    SearchControls poorlyNamedSearchControls = new SearchControls();
    poorlyNamedSearchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
    poorlyNamedSearchControls.setReturningAttributes(new String[0]);

    NamingEnumeration results = context.search("o=test", "(o=test)",
                                               poorlyNamedSearchControls);
    assertTrue(results.hasMoreElements());
    assertNotNull(results.nextElement());
    assertFalse(results.hasMoreElements());

    context.close();
  }



  /**
   * Tests the ability to perform a delete operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.decode("o=test")));

    InternalLDAPSocket socket = new InternalLDAPSocket();
    LDAPReader reader = new LDAPReader(socket);
    LDAPWriter writer = new LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    writer.writeMessage(message);

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);


    DeleteRequestProtocolOp deleteRequest =
         new DeleteRequestProtocolOp(ByteString.valueOf("o=test"));
    writer.writeMessage(new LDAPMessage(2, deleteRequest));

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getDeleteResponseProtocolOp().getResultCode(),
                 LDAPResultCode.SUCCESS);
    assertFalse(DirectoryServer.entryExists(DN.decode("o=test")));

    reader.close();
    writer.close();
    socket.close();
  }



  /**
   * Tests the ability to perform a delete operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDeleteOperationThroughJNDI()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.decode("o=test")));


    Hashtable<String,String> env = new Hashtable<String,String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");

    DirContext context = new InitialDirContext(env);

    context.destroySubcontext("o=test");
    assertFalse(DirectoryServer.entryExists(DN.decode("o=test")));

    context.close();
  }



  /**
   * Tests the ability to perform an extended operation over the internal LDAP
   * socket using the "Who Am I?" request.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testExtendedOperation()
         throws Exception
  {
    InternalLDAPSocket socket = new InternalLDAPSocket();
    LDAPReader reader = new LDAPReader(socket);
    LDAPWriter writer = new LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    writer.writeMessage(message);

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);


    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_WHO_AM_I_REQUEST);
    writer.writeMessage(new LDAPMessage(2, extendedRequest));

    message = reader.readMessage();
    assertNotNull(message);

    ExtendedResponseProtocolOp extendedResponse =
         message.getExtendedResponseProtocolOp();
    assertEquals(extendedResponse.getResultCode(), LDAPResultCode.SUCCESS);
    assertTrue(extendedResponse.getValue().toString().equalsIgnoreCase(
                    "dn:cn=Directory Manager,cn=Root DNs,cn=config"));

    reader.close();
    writer.close();
    socket.close();
  }



  /**
   * Tests the ability to perform a modify operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.decode("o=test")));

    InternalLDAPSocket socket = new InternalLDAPSocket();
    LDAPReader reader = new LDAPReader(socket);
    LDAPWriter writer = new LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    writer.writeMessage(message);

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);


    ArrayList<RawModification> mods = new ArrayList<RawModification>();
    mods.add(RawModification.create(ModificationType.REPLACE, "description",
                                    "foo"));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(ByteString.valueOf("o=test"), mods);
    writer.writeMessage(new LDAPMessage(2, modifyRequest));

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getModifyResponseProtocolOp().getResultCode(),
                 LDAPResultCode.SUCCESS);

    reader.close();
    writer.close();
    socket.close();
  }



  /**
   * Tests the ability to perform a modify operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyOperationThroughJNDI()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.decode("o=test")));


    Hashtable<String,String> env = new Hashtable<String,String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");

    DirContext context = new InitialDirContext(env);

    ModificationItem[] mods =
    {
      new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                           new BasicAttribute("description", "foo"))
    };

    context.modifyAttributes("o=test", mods);
    context.close();
  }



  /**
   * Tests the ability to perform a modify DN operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People");

    assertTrue(DirectoryServer.entryExists(DN.decode("ou=People,o=test")));
    assertFalse(DirectoryServer.entryExists(DN.decode("ou=Users,o=test")));

    InternalLDAPSocket socket = new InternalLDAPSocket();
    LDAPReader reader = new LDAPReader(socket);
    LDAPWriter writer = new LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    writer.writeMessage(message);

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);


    ModifyDNRequestProtocolOp modifyDNRequest =
         new ModifyDNRequestProtocolOp(ByteString.valueOf("ou=People,o=test"),
                                       ByteString.valueOf("ou=Users"), true);
    writer.writeMessage(new LDAPMessage(2, modifyDNRequest));

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getModifyDNResponseProtocolOp().getResultCode(),
                 LDAPResultCode.SUCCESS);

    assertFalse(DirectoryServer.entryExists(DN.decode("ou=People,o=test")));
    assertTrue(DirectoryServer.entryExists(DN.decode("ou=Users,o=test")));

    reader.close();
    writer.close();
    socket.close();
  }



  /**
   * Tests the ability to perform a modify DN operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNOperationThroughJNDI()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People");

    assertTrue(DirectoryServer.entryExists(DN.decode("ou=People,o=test")));
    assertFalse(DirectoryServer.entryExists(DN.decode("ou=Users,o=test")));


    Hashtable<String,String> env = new Hashtable<String,String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");

    DirContext context = new InitialDirContext(env);

    context.rename("ou=People,o=test", "ou=Users,o=test");

    assertFalse(DirectoryServer.entryExists(DN.decode("ou=People,o=test")));
    assertTrue(DirectoryServer.entryExists(DN.decode("ou=Users,o=test")));

    context.close();
  }



  /**
   * Tests the ability to perform a search operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSearchOperation()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.decode("o=test")));

    InternalLDAPSocket socket = new InternalLDAPSocket();
    LDAPReader reader = new LDAPReader(socket);
    LDAPWriter writer = new LDAPWriter(socket);

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(ByteString.valueOf("cn=Directory Manager"),
                                   3, ByteString.valueOf("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    writer.writeMessage(message);

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);


    SearchRequestProtocolOp searchRequest =
         new SearchRequestProtocolOp(ByteString.valueOf("o=test"),
                                     SearchScope.BASE_OBJECT,
                                     DereferencePolicy.NEVER_DEREF_ALIASES,
                                     0, 0, false,
                                     LDAPFilter.decode("(objectClass=*)"),
                                     new LinkedHashSet<String>());
    writer.writeMessage(new LDAPMessage(2, searchRequest));

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getSearchResultEntryProtocolOp().getDN(),
                 DN.decode("o=test"));

    message = reader.readMessage();
    assertNotNull(message);
    assertEquals(message.getSearchResultDoneProtocolOp().getResultCode(),
                 LDAPResultCode.SUCCESS);

    reader.close();
    writer.close();
    socket.close();
  }



  /**
   * Tests the ability to perform a searcj operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSearchOperationThroughJNDI()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.decode("o=test")));


    Hashtable<String,String> env = new Hashtable<String,String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");

    DirContext context = new InitialDirContext(env);

    SearchControls poorlyNamedSearchControls = new SearchControls();
    poorlyNamedSearchControls.setSearchScope(SearchControls.OBJECT_SCOPE);

    NamingEnumeration results = context.search("o=test", "(objectClass=*)",
                                               poorlyNamedSearchControls);
    assertTrue(results.hasMoreElements());
    assertNotNull(results.nextElement());
    assertFalse(results.hasMoreElements());

    context.close();
  }
}

