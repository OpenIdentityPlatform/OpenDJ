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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */


package org.opends.server.protocols.ldap;

import org.opends.server.types.Entry;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.ldap.LDAPConstants.
     OP_TYPE_SEARCH_RESULT_ENTRY;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;

import java.util.ArrayList;

public class TestSearchResultEntryProtocolOp extends LdapTestCase
{
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @DataProvider(name = "entries")
  public Object[][] createData() throws Exception
  {
    return new Object[][] {
         {
              TestCaseUtils.makeEntry(
                   "dn: cn=This is an extremely long relative distinguished " +
                        "name that does not fit in two line of eighty " +
                        "columns each.  It is intended to exercise the LDIF " +
                        "line wrapping code.,o=airius",
                   "userpassword: {SHA}O3HSv1MusyL4kTjP+HKI5uxuNoM=",
                   "objectclass: top",
                   "objectclass: person",
                   "objectclass: organizationalPerson",
                   "objectclass: inetOrgPerson",
                   "uid: rogasawara",
                   "mail: rogasawara@airius.co.jp",
                   "givenname;lang-ja:: 44Ot44OJ44OL44O8",
                   "sn;lang-ja:: 5bCP56yg5Y6f",
                   "cn;lang-ja:: 5bCP56yg5Y6fIOODreODieODi+ODvA==",
                   "title;lang-ja:: 5Za25qWt6YOoIOmDqOmVtw==",
                   "preferredlanguage: ja",
                   "givenname:: 44Ot44OJ44OL44O8",
                   "sn:: 5bCP56yg5Y6f",
                   "cn:: 5bCP56yg5Y6fIOODreODieODi+ODvA==",
                   "title:: 5Za25qWt6YOoIOmDqOmVtw==",
                   "givenname;lang-ja;phonetic:: 44KN44Gp44Gr44O8",
                   "sn;lang-ja;phonetic:: 44GK44GM44GV44KP44KJ",
                   "cn;lang-ja;phonetic:: " +
                        "44GK44GM44GV44KP44KJIOOCjeOBqeOBq+ODvA==",
                   "title;lang-ja;phonetic:: ",
                   "createtimestamp: 20060915161843Z",
                   "modifytimestamp: 20060915161843Z",
                   "description:: LyoKI" +
                        "CogQ0RETCBIRUFERVIgU1RBUlQKICoKICogVGhlIGNvbnRlbnRz" +
                        "IG9mIHRoaXMgZmlsZSBhcmUgc3ViamVjdCB0byB0aGUgdGVybXM" +
                        "gb2YgdGhlCiAqIENvbW1vbiBEZXZlbG9wbWVudCBhbmQgRGlzdH" +
                        "JpYnV0aW9uIExpY2Vuc2UsIFZlcnNpb24gMS4wIG9ubHkKICogK" +
                        "HRoZSAiTGljZW5zZSIpLiAgWW91IG1heSBub3QgdXNlIHRoaXMg" +
                        "ZmlsZSBleGNlcHQgaW4gY29tcGxpYW5jZQogKiB3aXRoIHRoZSB" +
                        "MaWNlbnNlLgogKgogKiBZb3UgY2FuIG9idGFpbiBhIGNvcHkgb2" +
                        "YgdGhlIGxpY2Vuc2UgYXQKICogdHJ1bmsvb3BlbmRzL3Jlc291c" +
                        "mNlL2xlZ2FsLW5vdGljZXMvT3BlbkRTLkxJQ0VOU0UKICogb3Ig" +
                        "aHR0cHM6Ly9PcGVuRFMuZGV2LmphdmEubmV0L09wZW5EUy5MSUN" +
                        "FTlNFLgogKiBTZWUgdGhlIExpY2Vuc2UgZm9yIHRoZSBzcGVjaW" +
                        "ZpYyBsYW5ndWFnZSBnb3Zlcm5pbmcgcGVybWlzc2lvbnMKICogY" +
                        "W5kIGxpbWl0YXRpb25zIHVuZGVyIHRoZSBMaWNlbnNlLgogKgog" +
                        "KiBXaGVuIGRpc3RyaWJ1dGluZyBDb3ZlcmVkIENvZGUsIGluY2x" +
                        "1ZGUgdGhpcyBDRERMIEhFQURFUiBpbiBlYWNoCiAqIGZpbGUgYW" +
                        "5kIGluY2x1ZGUgdGhlIExpY2Vuc2UgZmlsZSBhdAogKiB0cnVua" +
                        "y9vcGVuZHMvcmVzb3VyY2UvbGVnYWwtbm90aWNlcy9PcGVuRFMu" +
                        "TElDRU5TRS4gIElmIGFwcGxpY2FibGUsCiAqIGFkZCB0aGUgZm9" +
                        "sbG93aW5nIGJlbG93IHRoaXMgQ0RETCBIRUFERVIsIHdpdGggdG" +
                        "hlIGZpZWxkcyBlbmNsb3NlZAogKiBieSBicmFja2V0cyAiW10iI" +
                        "HJlcGxhY2VkIHdpdGggeW91ciBvd24gaWRlbnRpZnlpbmcgKiBp" +
                        "bmZvcm1hdGlvbjoKICogICAgICBQb3J0aW9ucyBDb3B5cmlnaHQ" +
                        "gW3l5eXldIFtuYW1lIG9mIGNvcHlyaWdodCBvd25lcl0KICoKIC" +
                        "ogQ0RETCBIRUFERVIgRU5ECiAqCiAqCiAqICAgICAgUG9ydGlvb" +
                        "nMgQ29weXJpZ2h0IDIwMDYgU3VuIE1pY3Jvc3lzdGVtcywgSW5j" +
                        "LgogKi8K"
              )
         }
    };
  }


  /**
   * Test that going from SearchResultEntry to SearchResultEntryProtocolOp
   * and back again preserves the entry contents.
   *
   * @param from The entry to undergo the transformation.
   * @throws Exception On failure.
   */
  @Test(dataProvider = "entries")
  public void testEntryTransformation(Entry from) throws Exception
  {
    SearchResultEntryProtocolOp protocolOp =
         new SearchResultEntryProtocolOp(new SearchResultEntry(from));
    Entry to = protocolOp.toSearchResultEntry();

    // FIXME Issue 660: Need to provide Entry.equals(Object)
//    assertEquals(to, from);
  }



  /**
   * Test that going from Entry to SearchResultEntryProtocolOp to LDIF and
   * back to Entry preserves the entry contents.
   *
   * @param from The entry to undergo the transformation.
   * @throws Exception On failure.
   */
  @Test(dataProvider = "entries")
  public void testToLdif(Entry from) throws Exception
  {
    int wrapColumn = 80;
    SearchResultEntryProtocolOp protocolOp =
         new SearchResultEntryProtocolOp(new SearchResultEntry(from));
    StringBuilder builder = new StringBuilder();
    protocolOp.toLDIF(builder, wrapColumn);
    Entry to = TestCaseUtils.entryFromLdifString(builder.toString());

    // FIXME Issue 660: Need to provide Entry.equals(Object)
//    assertEquals(to, from);
  }

  @Test(dataProvider = "entries")
  public void testToLdifWrapping(Entry from) throws Exception
  {
    int wrapColumn = 78;
    SearchResultEntryProtocolOp protocolOp =
         new SearchResultEntryProtocolOp(new SearchResultEntry(from));
    StringBuilder builder = new StringBuilder();
    protocolOp.toLDIF(builder, wrapColumn);

    // Test that lines were correctly wrapped.
    String[] lines = builder.toString().split("[\r\n]+");
    for (String line : lines)
    {
      if (line.length() > wrapColumn)
      {
        fail("LDIF line length " + line.length() +
             " exceeds wrap column " + wrapColumn + " : " + line);
      }
    }

    Entry to = TestCaseUtils.entryFromLdifString(builder.toString());

    // FIXME Issue 660: Need to provide Entry.equals(Object)
//    assertEquals(to, from);
  }

  @Test(dataProvider = "entries")
  public void testToString(Entry entry) throws Exception
  {
    SearchResultEntryProtocolOp protocolOp =
         new SearchResultEntryProtocolOp(new SearchResultEntry(entry));
    StringBuilder sb = new StringBuilder();
    protocolOp.toString();
    protocolOp.toString(sb, 1);
  }

  @Test(dataProvider = "entries")
  public void testEncodeDecode(Entry entry) throws Exception
  {
    SearchResultEntryProtocolOp protocolOp =
         new SearchResultEntryProtocolOp(new SearchResultEntry(entry));

    // Encode to ASN1.
    ASN1Element element = protocolOp.encode();

    // Decode to a new protocol op.
    SearchResultEntryProtocolOp decodedProtocolOp =
         (SearchResultEntryProtocolOp)ProtocolOp.decode(element);

    assertEquals(decodedProtocolOp.getDN(), protocolOp.getDN());
    assertTrue(testEqual(decodedProtocolOp.getAttributes(),
                         protocolOp.getAttributes()));
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testInvalidSequence() throws Exception
  {
    ProtocolOp.decode(new ASN1Integer(OP_TYPE_SEARCH_RESULT_ENTRY, 0));
  }

  @Test (dataProvider = "entries", expectedExceptions = LDAPException.class)
  public void testTooManyElements(Entry entry) throws Exception
  {
    SearchResultEntryProtocolOp protocolOp =
         new SearchResultEntryProtocolOp(new SearchResultEntry(entry));
    ASN1Element element = protocolOp.encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.add(new ASN1Boolean(true));
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_RESULT_ENTRY, elements));
  }

  @Test (dataProvider = "entries", expectedExceptions = LDAPException.class)
  public void testTooFewElements(Entry entry) throws Exception
  {
    SearchResultEntryProtocolOp protocolOp =
         new SearchResultEntryProtocolOp(new SearchResultEntry(entry));
    ASN1Element element = protocolOp.encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.remove(0);
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_RESULT_ENTRY, elements));
  }

  @Test (dataProvider = "entries", expectedExceptions = LDAPException.class)
  public void testInvalidElement1(Entry entry) throws Exception
  {
    SearchResultEntryProtocolOp protocolOp =
         new SearchResultEntryProtocolOp(new SearchResultEntry(entry));
    ASN1Element element = protocolOp.encode();
    ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
    elements.set(1, new ASN1OctetString("cn"));
    ProtocolOp.decode(new ASN1Sequence(OP_TYPE_SEARCH_RESULT_ENTRY, elements));
  }

}
