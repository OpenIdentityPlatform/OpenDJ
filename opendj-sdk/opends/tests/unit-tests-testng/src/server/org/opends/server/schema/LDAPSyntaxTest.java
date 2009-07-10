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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Test the LDAPSyntaxDescriptionSyntax.
 */
public class LDAPSyntaxTest extends AttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  protected AttributeSyntax getRule()
  {
    return new LDAPSyntaxDescriptionSyntax();
  }

  /**
   * {@inheritDoc}
   */
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
   @Test()
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
        assertFalse(resultCode==0);

        //Test if we can substitute a directory string syntax by an undefined.
        resultCode = TestCaseUtils.applyModifications(true,
          "dn: cn=schema",
          "changetype: modify",
          "add: ldapsyntaxes",
          "ldapsyntaxes: ( 1.3.6.1.4.1.1466.115.121.1.15 " +
          "DESC 'Replacing DirectorySyntax'   " +
          " X-SUBST '1.1.1' )");

        //This is not expected to happen
        assertFalse(resultCode==0);


        //Test if we can substitute a core syntax with a user-defined
        //syntax
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
        assertFalse(resultCode==0);
     }
     finally
     {
      deleteSubstitutionSyntax();
     }
   }



   /**
    * Tests whether both the virtual and the newly added real substitution
    * sytanx are available when a search is made for ldapsyntaxes attribute.
    *
    * @throws java.lang.Exception
    */
   @Test()
   public void testSubstitutionSyntaxSearch() throws Exception
   {
     try
     {
      addSubtitutionSyntax();
      InternalClientConnection conn =
      InternalClientConnection.getRootConnection();
      LinkedHashSet<String> attrList = new LinkedHashSet<String>();
      attrList.add("ldapsyntaxes");

      InternalSearchOperation searchOperation =
           new InternalSearchOperation(
                conn,
                InternalClientConnection.nextOperationID(),
                InternalClientConnection.nextMessageID(),
                null,
                ByteString.valueOf("cn=schema"),
                SearchScope.WHOLE_SUBTREE,
                DereferencePolicy.NEVER_DEREF_ALIASES,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                false,
                LDAPFilter.decode("objectclass=ldapsubentry"),
                attrList, null);

      searchOperation.run();
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      SearchResultEntry e = entries.get(0);
      //An entry must be returned.
      assertNotNull(e);
      Attribute attr = e.getAttribute("ldapsyntaxes").get(0);
      Iterator<AttributeValue> iter = attr.iterator();
      
      //There are other ways of doing it but we will extract the OID
      //from the attribute values and then check to see if our 
      //OID is found in the result set or not.
      List<String> syntaxList = new ArrayList<String>();
      while(iter.hasNext())
      {
        AttributeValue val = iter.next();
        //parse the OIDs.
        syntaxList.add(getOIDFromLdapSyntax(val.toString()));
      }
 
      assertTrue(syntaxList.size() ==
              DirectoryServer.getAttributeSyntaxSet().size() ) ;
      //Check if we find our OID.    
      assertTrue(syntaxList.contains("9.9.9"));
      //DirectoryString.
      assertTrue(syntaxList.contains("1.3.6.1.4.1.1466.115.121.1.15"));
      //IA5String.
      assertTrue(syntaxList.contains("1.3.6.1.4.1.1466.115.121.1.26"));
     }
     finally
     {
       deleteSubstitutionSyntax();
     }
   }



   /**
    * Tests whether it is possible to add values after an umimplemented syntax
    * has been subsitutited by DirectoryString syntax.
    *
    * @throws java.lang.Exception
    */
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
      assertTrue(resultCode == 0);
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
       deleteSubstitutionSyntax();
     }
   }


  //Parses the OID from the syntax defitions.
  private String getOIDFromLdapSyntax(String valueStr)
  {
    int pos    = 0;
    int length = valueStr.length();

    while ((pos < length) && (valueStr.charAt(pos) == ' '))
    {
      pos++;
    }
    // The next character must be an open parenthesis.  If it is not, then that
    // is an error.
    char c = valueStr.charAt(pos++);

    // Skip over any spaces immediately following the opening parenthesis.
    while ((pos < length) && ((c = valueStr.charAt(pos)) == ' '))
    {
      pos++;
    }
    int oidStartPos = pos;

    boolean lastWasPeriod = false;
    while ((pos < length) && ((c = valueStr.charAt(pos)) != ' ')
          && (c = valueStr.charAt(pos)) != ')')
    {
      if (c == '.')
      {
        lastWasPeriod = true;
      }
      else
      {
        lastWasPeriod = false;
      }
      pos++;
    }
    return valueStr.substring(oidStartPos, pos);
  }


  //Adds a substitutionsyntax to the schema.
  private void addSubtitutionSyntax() throws Exception
  {
    //Add the substitution syntax for an unimplemented syntax.
    int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "add: ldapsyntaxes",
    "ldapsyntaxes: ( 9.9.9 DESC 'Unimplemented Syntax'   " +
    " X-SUBST '1.3.6.1.4.1.1466.115.121.1.15' )");

    assertTrue(resultCode==0);
  }



  //Deletes the substitutionSyntax from the schema.
  private void deleteSubstitutionSyntax() throws Exception
  {
    //delete the substitution syntax.
    int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "delete: ldapsyntaxes",
    "ldapsyntaxes: ( 9.9.9 DESC 'Unimplemented Syntax'   " +
    " X-SUBST '1.3.6.1.4.1.1466.115.121.1.15' )");

    assertTrue(resultCode==0);
  }
}
