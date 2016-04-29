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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.AddOperation;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/** Test the LDAPSyntaxDescriptionSyntax. */
@RemoveOnceSDKSchemaIsUsed
@SuppressWarnings("javadoc")
public class LDAPSyntaxTest extends AttributeSyntaxTest
{
  @Override
  protected AttributeSyntax<?> getRule()
  {
    return new LDAPSyntaxDescriptionSyntax();
  }

  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
      return new Object [][] {
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-9EN ('this' 'is' 'a' 'test'))",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "(X-name 'this",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "(X-name 'this'",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "Y-name 'this')",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name 'this' 'is')",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name )",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X- ('this' 'is' 'a' 'test'))",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name ('this' 'is' 'a' 'test') X-name-a 'this' X-name-b ('this')",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name ('this' 'is' 'a' 'test') X-name-a 'this' X-name-b ('this'",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name ('this' 'is' 'a' 'test') X-name-a 'this' X-name-b ('this'))))",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name ('this' 'is' 'a' 'test') X-name-a  X-name-b ('this'))))",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name ('this' 'is' 'a' 'test') X-name-a  'X-name-b' ('this'))))",
                    false},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name ('this' 'is' 'a' 'test') X-name-a 'this' X-name-b ('this'))",
                    true},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name ('this' 'is' 'a' 'test') X-name-a 'this' X-name-b ( 'this' ))",
                    true},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name ('this' 'is' 'a' 'test') X-name-a 'this' X-name-b ('this' 'that'))",
                    true},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name ('this' 'is' 'a' 'test') X-name-a 'this' X-name-b ('this' 'that') )",
                    true},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-a-_eN_- ('this' 'is' 'a' 'test'))",
                    true},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name ('this'))",
                    true},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name 'this')",
                    true},
              {"( 2.5.4.3 DESC 'full syntax description' " +
                    "X-name 'this' X-name-a 'test')",
                    true},
              {"( 2.5.4.3 DESC 'full syntax description' )", true},
              {"   (    2.5.4.3    DESC  ' syntax description'    )", true},
              {"( 2.5.4.3 DESC 'Test syntax' X-SCHEMA-FILE '00-core.ldif' )", true},
              {"( 2.5.4.3 DESC 'Test X-SUBST Extensions' X-SUBST '1.3.6.1.4.1.1466.115.121.1.15' )", true},
    {"( 2.5.4.3 DESC 'Test X-SUBST Extensions' X-SUBST '1.3.6.1.4.1.1466.115.121.1.15' X-SCHEMA-FILE '00-core.ldif' )",
                  true},
    {"( 2.5.4.3 DESC 'Test X-SUBST Extensions' X-SCHEMA-FILE '00-core.ldif' X-SUBST '1.3.6.1.4.1.1466.115.121.1.15' )",
                  true},
              {"( 2.5.4.3 DESC 'Test X-PATTERN Extensions' X-PATTERN '[0-9]+' )", true},
              {"( 2.5.4.3 DESC 'Test X-PATTERN Extensions' X-PATTERN '[0-9]+' X-SCHEMA-FILE '00-core.ldif' )", true},
              {"( 2.5.4.3 DESC 'Test X-ENUM Extensions' X-ENUM ( 'black' 'white' ) )", true},
              {"( 2.5.4.3 DESC 'Test X-ENUM Extensions' X-ENUM ( 'white' 'white' ) )", false},
              {"( 2.5.4.3 DESC 'Test X-ENUM Extensions' X-ENUM )", false},
      { "( 2.5.4.3 DESC 'Test X-ENUM Extensions' X-ENUM ( 'black' 'white' ) X-SCHEMA-FILE '00-core.ldif' )", true },
              {"( 2.5.4.3 DESC syntax description )", false},
              {"($%^*&!@ DESC 'syntax description' )", false},
              {"(temp-oid DESC 'syntax description' )", true},
              {"2.5.4.3 DESC 'syntax description' )", false},
              {"(2.5.4.3 DESC 'syntax description' ", false},
      };
  }



  /**
   * Tests whether an implemented syntax can't be substituted by another.
   */
  @Test
  public void testSubstitutionSyntaxForInvalidSubstitution() throws Exception
  {
    try
    {
      //Test if we can substitute a directory string syntax by itself.
      int resultCode = TestCaseUtils.applyModifications(true,
        "dn: cn=schema",
        "changetype: modify",
        "add: ldapsyntaxes",
        "ldapsyntaxes: ( 1.3.6.1.4.1.1466.115.121.1.15 " +
        "DESC 'Replacing DirectorySyntax'   " +
        " X-SUBST '1.3.6.1.4.1.1466.115.121.1.15' )");

      //This is not expected to happen
      assertThat(resultCode).isNotEqualTo(0);

      //Test if we can substitute a directory string syntax by an undefined.
      resultCode = TestCaseUtils.applyModifications(true,
        "dn: cn=schema",
        "changetype: modify",
        "add: ldapsyntaxes",
        "ldapsyntaxes: ( 1.3.6.1.4.1.1466.115.121.1.15 " +
        "DESC 'Replacing DirectorySyntax'   " +
        " X-SUBST '1.1.1' )");

      //This is not expected to happen
      assertThat(resultCode).isNotEqualTo(0);


      //Test if we can substitute a core syntax with a user-defined syntax
      addSubtitutionSyntax();
      //Replace the IA5Stringsyntax with the custom syntax we just created.
      resultCode = TestCaseUtils.applyModifications(true,
        "dn: cn=schema",
        "changetype: modify",
        "add: ldapsyntaxes",
        "ldapsyntaxes: ( 1.3.6.1.4.1.1466.115.121.1.26 " +
        "DESC 'Replacing DirectorySyntax'   " +
        " X-SUBST '9.9.9' )");

      //This is not expected to happen
      assertThat(resultCode).isNotEqualTo(0);
    }
    finally
    {
      deleteSubstitutionSyntax();
    }
  }



  /**
   * Tests whether both the virtual and the newly added real substitution syntax are available when
   * a search is made for ldapsyntaxes attribute.
   */
  @Test
  public void testSubstitutionSyntaxSearch() throws Exception
  {
    try
    {
      addSubtitutionSyntax();

      SearchRequest request = newSearchRequest("cn=schema", SearchScope.WHOLE_SUBTREE, "objectclass=ldapsubentry")
          .addAttribute("ldapsyntaxes");
      InternalSearchOperation searchOperation = getRootConnection().processSearch(request);

      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      assertThat(entries).isNotEmpty();
      SearchResultEntry e = entries.get(0);
      assertNotNull(e);
      Attribute attr = e.getAttribute("ldapsyntaxes").get(0);

      //There are other ways of doing it but we will extract the OID
      //from the attribute values and then check to see if our
      //OID is found in the result set or not.
      List<String> syntaxList = new ArrayList<>();
      for (ByteString attrValue : attr)
      {
        //parse the OIDs.
        syntaxList.add(getOIDFromLdapSyntax(attrValue.toString()));
      }

      //Check if we find our OID.
      assertThat(syntaxList).contains("9.9.9");
      assertThat(syntaxList).contains(SchemaConstants.SYNTAX_DIRECTORY_STRING_OID);
      assertThat(syntaxList).contains(SchemaConstants.SYNTAX_IA5_STRING_OID);
    }
    finally
    {
      deleteSubstitutionSyntax();
    }
  }



   /**
   * Tests whether it is possible to add values after an unimplemented syntax has been substituted
   * by DirectoryString syntax.
   */
  @Test
   public void testSubsitutionSyntaxAddValues() throws Exception
   {
     try
     {
       addSubtitutionSyntax();
       //Add an attribute with undefined syntax.
       int  resultCode = TestCaseUtils.applyModifications(true,
          "dn: cn=schema",
          "changetype: modify",
          "add: attributetypes",
          "attributetypes: ( test-oid NAME 'test-attr' SYNTAX 9.9.9 )",
          "-",
          "add: objectclasses",
          "objectclasses: ( oc-oid NAME 'testOC' SUP top AUXILIARY MUST test-attr)"
        );
      assertThat(resultCode).isEqualTo(0);
      TestCaseUtils.initializeTestBackend(true);

      TestCaseUtils.addEntry(
      "dn: cn=syntax-test,o=test",
      "objectclass: person",
      "objectclass: testOC",
      "cn: syntax-test",
      "sn: xyz",
      "test-attr: test value for unimplemented syntax");
     }
     finally
     {
       int resultCode = TestCaseUtils.applyModifications(true,
         "dn: cn=schema",
         "changetype: modify",
         "delete: objectclasses",
         "objectclasses: ( oc-oid NAME 'testOC' SUP top AUXILIARY MUST test-attr)",
         "-",
         "delete: attributetypes",
         "attributetypes: ( test-oid NAME 'test-attr' SYNTAX 9.9.9 )"
       );
       assertThat(resultCode).isEqualTo(0);

       deleteSubstitutionSyntax();
     }
   }



  /**
    * Tests whether it is possible to add values after a regex syntax
    * has been added.
    *
    * @throws java.lang.Exception
    */
  @Test
  public void testRegexSyntaxAddValues() throws Exception
  {
    try
    {
      addRegexSyntax();
      TestCaseUtils.initializeTestBackend(true);

      //This addition should fail because it doesn't match the pattern.
      Entry entry = TestCaseUtils.makeEntry(
      "dn: cn=syntax-test,o=test",
      "objectclass: person",
      "objectclass: testOC",
      "cn: syntax-test",
      "sn: xyz",
      "test-attr-regex: invalid regex");
      AddOperation addOperation = getRootConnection().processAdd(entry);
    assertEquals(addOperation.getResultCode(),
            ResultCode.INVALID_ATTRIBUTE_SYNTAX);

      //This addition should go through.
      TestCaseUtils.addEntry(
        "dn: cn=syntax-test,o=test",
        "objectclass: person",
        "objectclass: testOC",
        "cn: syntax-test",
        "sn: xyz",
        "test-attr-regex: host:0.0.0");
    }
    finally
    {
     deleteRegexSyntax();
    }
  }



  /**
   * Tests the search using regex syntax.
   *
   * @throws java.lang.Exception
   */
  @Test
  public void testRegexSyntaxSearch() throws Exception
  {
    try
    {
      addRegexSyntax();
      TestCaseUtils.initializeTestBackend(true);
      //This addition should go through.
      TestCaseUtils.addEntry(
        "dn: cn=test,o=test",
        "objectclass: person",
        "objectclass: testOC",
        "cn: test",
        "sn: xyz",
        "test-attr-regex: host:0.0.0");

      SearchRequest request =
          newSearchRequest("cn=test,o=test", SearchScope.WHOLE_SUBTREE, "test-attr-regex=host:0.0.0");
      InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      assertThat(entries).isNotEmpty();
      assertNotNull(entries.get(0));
    }
    finally
    {
      deleteRegexSyntax();
    }
  }

  /**
    * Tests whether it is possible to add values after an enum syntax
    * has been added.
    *
    * @throws java.lang.Exception
    */
  @Test
  public void testEnumSyntaxAddValues() throws Exception
  {
    try
    {
      addEnumSyntax();
      TestCaseUtils.initializeTestBackend(true);

      //This addition should fail because it doesn't match the pattern.
      Entry entry = TestCaseUtils.makeEntry(
      "dn: cn=syntax-test,o=test",
      "objectclass: person",
      "objectclass: testOC",
      "cn: syntax-test",
      "sn: xyz",
      "test-attr-enum: arbit-day");
      AddOperation addOperation = getRootConnection().processAdd(entry);
    assertEquals(addOperation.getResultCode(),
            ResultCode.INVALID_ATTRIBUTE_SYNTAX);

      //This addition should go through.
      TestCaseUtils.addEntry(
        "dn: cn=syntax-test,o=test",
        "objectclass: person",
        "objectclass: testOC",
        "cn: syntax-test",
        "sn: xyz",
        "test-attr-enum: sunday");
    }
    finally
    {
      deleteEnumSyntax();
    }
  }



  /**
   * Tests the equality-based search using enum syntax.
   */
  @Test
  public void testEnumSyntaxEqualitySearch() throws Exception
  {
    try
    {
      addEnumSyntax();
      //This addition should go through.
      TestCaseUtils.initializeTestBackend(true);
      TestCaseUtils.addEntry(
        "dn: cn=test,o=test",
        "objectclass: person",
        "objectclass: testOC",
        "cn: test",
        "sn: xyz",
        "test-attr-enum: wednesday");

      SearchRequest request = newSearchRequest("cn=test,o=test", SearchScope.WHOLE_SUBTREE, "test-attr-enum=wednesday");
      InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      assertThat(entries).isNotEmpty();
      SearchResultEntry e = entries.get(0);
      //An entry must be returned.
      assertNotNull(e);
    }
    finally
    {
      deleteEnumSyntax();
    }
  }

  /**
   * Tests the ordering-based search using enum syntax.
   */
  @Test
  public void testEnumSyntaxOrderingSearch() throws Exception
  {
    try
    {
      addEnumSyntax();
      TestCaseUtils.initializeTestBackend(true);
      //This addition should go through.
      TestCaseUtils.addEntries(
        "dn: cn=test1,o=test",
        "objectclass: person",
        "objectclass: testOC",
        "cn: test1",
        "sn: xyz",
        "test-attr-enum: sunday",
        "",
        "dn: cn=test2,o=test",
        "objectclass: person",
        "objectclass: testOC",
        "cn: test2",
        "sn: xyz",
        "test-attr-enum: monday",
        "",
        "dn: cn=test3,o=test",
        "objectclass: person",
        "objectclass: testOC",
        "cn: test3",
        "sn: xyz",
        "test-attr-enum: tuesday");

      SearchRequest request = newSearchRequest("o=test", SearchScope.WHOLE_SUBTREE, "test-attr-enum>=tuesday");
      InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      assertThat(entries).as("expected one entry to be returned").isNotEmpty();
      assertThat((Object) entries.get(0).getName()).isEqualTo(DN.valueOf("cn=test1,o=test"));
    }
    finally
    {
      deleteEnumSyntax();
    }
  }



  /** Parses the OID from the syntax defitions. */
  private String getOIDFromLdapSyntax(String valueStr)
  {
    int pos    = 0;
    int length = valueStr.length();

    while (pos < length && valueStr.charAt(pos) == ' ')
    {
      pos++;
    }
    // The next character must be an open parenthesis.  If it is not, then that
    // is an error.
    assertEquals(valueStr.charAt(pos++), '(');

    // Skip over any spaces immediately following the opening parenthesis.
    while (pos < length && valueStr.charAt(pos) == ' ')
    {
      pos++;
    }
    int oidStartPos = pos;

    while (pos < length
        && valueStr.charAt(pos) != ' '
        && valueStr.charAt(pos) != ')')
    {
      pos++;
    }
    return valueStr.substring(oidStartPos, pos);
  }


  /** Adds a substitutionsyntax to the schema. */
  private void addSubtitutionSyntax() throws Exception
  {
    //Add the substitution syntax for an unimplemented syntax.
    int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "add: ldapsyntaxes",
    "ldapsyntaxes: ( 9.9.9 DESC 'Unimplemented Syntax'   " +
    " X-SUBST '1.3.6.1.4.1.1466.115.121.1.15' )");
    assertThat(resultCode).isEqualTo(0);
  }

  /** Deletes the substitutionSyntax from the schema. */
  private void deleteSubstitutionSyntax() throws Exception
  {
    //delete the substitution syntax.
    int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "delete: ldapsyntaxes",
    "ldapsyntaxes: ( 9.9.9 DESC 'Unimplemented Syntax'   " +
    " X-SUBST '1.3.6.1.4.1.1466.115.121.1.15' )");
    assertThat(resultCode).isEqualTo(0);
  }

  /** Adds a regex syntax to the schema. */
  private void addRegexSyntax() throws Exception
  {
    //Add the substitution syntax for an unimplemented syntax.
    int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "add: ldapsyntaxes",
    "ldapSyntaxes: ( 1.1.1 DESC 'Host and Port in the format of HOST:PORT'  " +
            "X-PATTERN '^[a-z-A-Z]+:[0-9.]+\\d$' )");
    assertThat(resultCode).isEqualTo(0);

    resultCode = TestCaseUtils.applyModifications(true,
          "dn: cn=schema",
          "changetype: modify",
          "add: attributetypes",
          "attributetypes: ( test-attr-oid NAME 'test-attr-regex' SYNTAX 1.1.1 )",
          "-",
          "add: objectclasses",
          "objectclasses: ( oc-oid NAME 'testOC' SUP top AUXILIARY MUST test-attr-regex)"
        );
    assertThat(resultCode).isEqualTo(0);
  }

  /** Deletes the regex syntax from the schema. */
  private void deleteRegexSyntax() throws Exception
  {
    //delete the substitution syntax.
    int resultCode = TestCaseUtils.applyModifications(true,
      "dn: cn=schema",
      "changetype: modify",
      "delete: objectclasses",
      "objectclasses: ( oc-oid NAME 'testOC' SUP top AUXILIARY MUST test-attr-regex)",
      "-",
      "delete: attributetypes",
      "attributetypes: ( test-attr-oid NAME 'test-attr-regex' SYNTAX 1.1.1 )"
    );
    assertThat(resultCode).isEqualTo(0);

    resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "delete: ldapsyntaxes",
    "ldapSyntaxes: ( 1.1.1 DESC 'Host and Port in the format of HOST:PORT'  " +
            "X-PATTERN '^[a-z-A-Z]+:[0-9.]+\\d$' )");
    assertThat(resultCode).isEqualTo(0);
  }

  /** Adds an enum syntax to the schema. */
  private void addEnumSyntax() throws Exception
  {
    //Add the enum syntax.
    int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "add: ldapsyntaxes",
    "ldapSyntaxes: ( 3.3.3  DESC 'Day Of The Week'  " +
            "X-ENUM  ( 'monday' 'tuesday'   'wednesday'  'thursday'  'friday'  'saturday' 'sunday') )");
    assertThat(resultCode).isEqualTo(0);

    resultCode = TestCaseUtils.applyModifications(true,
          "dn: cn=schema",
          "changetype: modify",
          "add: attributetypes",
          "attributetypes: ( test-attr-oid NAME 'test-attr-enum' SYNTAX 3.3.3 )",
          "-",
          "add: objectclasses",
          "objectclasses: ( oc-oid NAME 'testOC' SUP top AUXILIARY MUST test-attr-enum)"
        );
    assertThat(resultCode).isEqualTo(0);
  }

  /** Deletes the enum syntax from the schema. */
  private void deleteEnumSyntax() throws Exception
  {
    int resultCode = TestCaseUtils.applyModifications(true,
      "dn: cn=schema",
      "changetype: modify",
      "delete: objectclasses",
      "objectclasses: ( oc-oid NAME 'testOC' SUP top AUXILIARY MUST test-attr-enum)",
      "-",
      "delete: attributetypes",
      "attributetypes: ( test-attr-oid NAME 'test-attr-enum' SYNTAX 3.3.3 )"
    );
    assertThat(resultCode).isEqualTo(0);

    resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "delete: ldapsyntaxes",
    "ldapSyntaxes: ( 3.3.3  DESC 'Day Of The Week'  " +
            "X-ENUM  ( 'monday' 'tuesday'   'wednesday'  'thursday'  'friday'  'saturday' 'sunday') )");
    assertThat(resultCode).isEqualTo(0);
  }
}
