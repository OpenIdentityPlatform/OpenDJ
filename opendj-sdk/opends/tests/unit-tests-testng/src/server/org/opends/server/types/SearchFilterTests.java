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
package org.opends.server.types;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.util.StaticUtils;
import org.opends.server.types.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.Assert;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;

import static java.util.Arrays.asList;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

/**
 * Tests for the org.opends.server.types.SearchFilter class
 *
 * This class covers the SearchFilter class fairly well.  The main gaps are
 * with extensible match, attribute options, and there is a lot of code
 * that is not reachable because it's in exception handling code that
 * is not exercisable externally.
   */
public class SearchFilterTests extends DirectoryServerTestCase {

  @BeforeClass
  public void setupClass() throws Exception {
    TestCaseUtils.startServer();
  }

  ////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////
  //
  // createFilterFromString
  //
  ////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////

  // -------------------------------------------------------------------------
  //
  // Test valid filters.
  //
  // -------------------------------------------------------------------------

  // These are valid filters.
  @DataProvider(name = "paramsCreateFilterFromStringValidFilters")
  public Object[][] paramsCreateFilterFromStringValidFilters() {
    return new Object[][]{
            {"(&)", "(&)"},
            {"(|)", "(|)"},
            {"(sn=test)", "(sn=test)"},
            {"(sn=*)", "(sn=*)"},
            {"(sn=)", "(sn=)"},
            {"(sn=*test*)", "(sn=*test*)"},

            {"(!(sn=test))", "(!(sn=test))"},
            {"(|(sn=test)(sn=test2))", "(|(sn=test)(sn=test2))"},

            {"(&(sn=test))", "(&(sn=test))"},
            {"(|(sn=test))", "(|(sn=test))"},
    };
  }

  @Test(dataProvider = "paramsCreateFilterFromStringValidFilters")
  public void testCreateFilterFromStringValidFilters(
          String originalFilter,
          String expectedToStringFilter
  ) throws DirectoryException {
    runRecreateFilterTest(originalFilter, expectedToStringFilter);
  }

  private void runRecreateFilterTest(
          String originalFilter,
          String expectedToStringFilter
  ) throws DirectoryException {
    String regenerated = SearchFilter.createFilterFromString(originalFilter).toString();
    Assert.assertEquals(regenerated, expectedToStringFilter, "original=" + originalFilter + ", expected=" + expectedToStringFilter);
  }

  // These are valid filters.
  @DataProvider(name = "escapeSequenceFilters")
  public Object[][] escapeSequenceFilters() {
    final char[] CHAR_NIBBLES = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                                 'a', 'b', 'c', 'd', 'e', 'f',
                                 'A', 'B', 'C', 'D', 'E', 'F'};

    final byte[] BYTE_NIBBLES = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                                 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                                 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};

    List<String[]> allParameters = new ArrayList<String[]>();
    for (int i = 0; i < CHAR_NIBBLES.length; i++) {
      char highNibble = CHAR_NIBBLES[i];
      byte highByteNibble = BYTE_NIBBLES[i];
      for (int j = 0; j < CHAR_NIBBLES.length; j++) {
        char lowNibble = CHAR_NIBBLES[j];
        byte lowByteNibble = BYTE_NIBBLES[j];
        String inputChar = "\\" + highNibble + lowNibble;
        byte byteValue = (byte)((((int)highByteNibble) << 4) | lowByteNibble);
        String outputChar = getFilterValueForChar(byteValue);

        // Exact match
        String inputFilter = "(sn=" + inputChar + ")";
        String outputFilter = "(sn=" + outputChar + ")";
        allParameters.add(new String[]{inputFilter, outputFilter});

        // Substring
        inputFilter = "(sn=" + inputChar + "*" + inputChar + "*" + inputChar + ")";
        outputFilter = "(sn=" + outputChar + "*" + outputChar + "*" + outputChar + ")";
        allParameters.add(new String[]{inputFilter, outputFilter});

        // <=
        inputFilter = "(sn<=" + inputChar + ")";
        outputFilter = "(sn<=" + outputChar + ")";
        allParameters.add(new String[]{inputFilter, outputFilter});

        // >=
        inputFilter = "(sn>=" + inputChar + ")";
        outputFilter = "(sn>=" + outputChar + ")";
        allParameters.add(new String[]{inputFilter, outputFilter});

        // =~
        inputFilter = "(sn>=" + inputChar + ")";
        outputFilter = "(sn>=" + outputChar + ")";
        allParameters.add(new String[]{inputFilter, outputFilter});

        // =~
        inputFilter = "(sn:caseExactMatch:=" + inputChar + ")";
        outputFilter = "(sn:caseExactMatch:=" + outputChar + ")";
        allParameters.add(new String[]{inputFilter, outputFilter});
      }
    }

    return (Object[][]) allParameters.toArray(new String[][]{});
  }


  // These are filters with invalid escape sequences.
  @DataProvider(name = "invalidEscapeSequenceFilters")
  public Object[][] invalidEscapeSequenceFilters() {
    final char[] VALID_NIBBLES = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                                 'a', 'b', 'c', 'd', 'e', 'f',
                                 'A', 'B', 'C', 'D', 'E', 'F'};

    final char[] INVALID_NIBBBLES = {'g', 'z', 'G', 'Z', '-', '=', '+', '\00', ')',
                                     'n', 't', '\\'};

    List<String> invalidEscapeSequences = new ArrayList<String>();

    for (int i = 0; i < VALID_NIBBLES.length; i++) {
      char validNibble = VALID_NIBBLES[i];
      for (int j = 0; j < INVALID_NIBBBLES.length; j++) {
        char invalidNibble = INVALID_NIBBBLES[j];

        invalidEscapeSequences.add("\\" + validNibble + invalidNibble);
        invalidEscapeSequences.add("\\" + invalidNibble + validNibble);
      }
      // Also do a test case where we only have one character in the escape sequence.
      invalidEscapeSequences.add("\\" + validNibble);
    }

    List<String[]> allParameters = new ArrayList<String[]>();
    for (String invalidEscape : invalidEscapeSequences) {
      // Exact match
      allParameters.add(new String[]{"(sn=" + invalidEscape + ")"});
      allParameters.add(new String[]{"(sn=" + invalidEscape});

      // Substring
      allParameters.add(new String[]{"(sn=" + invalidEscape + "*" + invalidEscape + "*" + invalidEscape + ")"});
      allParameters.add(new String[]{"(sn=" + invalidEscape + "*" + invalidEscape + "*" + invalidEscape});

      // <=
      allParameters.add(new String[]{"(sn<=" + invalidEscape + ")"});
      allParameters.add(new String[]{"(sn<=" + invalidEscape});

      // >=
      allParameters.add(new String[]{"(sn>=" + invalidEscape + ")"});
      allParameters.add(new String[]{"(sn>=" + invalidEscape});

      // =~
      allParameters.add(new String[]{"(sn>=" + invalidEscape + ")"});
      allParameters.add(new String[]{"(sn>=" + invalidEscape});

      // =~
      allParameters.add(new String[]{"(sn:caseExactMatch:=" + invalidEscape + ")"});
      allParameters.add(new String[]{"(sn:caseExactMatch:=" + invalidEscape});
    }

    return (Object[][]) allParameters.toArray(new String[][]{});
  }


  /**
   * @return a value that can be used in an LDAP filter.
   */
  private String getFilterValueForChar(byte value) {
    if (((value & 0x7F) != value) ||  // Not 7-bit clean
         (value <= 0x1F) ||           // Below the printable character range
         (value == 0x28) ||           // Open parenthesis
         (value == 0x29) ||           // Close parenthesis
         (value == 0x2A) ||           // Asterisk
         (value == 0x5C) ||           // Backslash
         (value == 0x7F))             // Delete character
    {
      return "\\" + StaticUtils.byteToHex(value);
    } else {
      return "" + ((char)value);
    }
  }

  @Test(dataProvider = "escapeSequenceFilters")
  public void testRecreateFilterWithEscape(
          String originalFilter,
          String expectedToStringFilter
  ) throws DirectoryException {
    runRecreateFilterTest(originalFilter, expectedToStringFilter);
  }

  @Test(dataProvider = "invalidEscapeSequenceFilters",
        expectedExceptions = DirectoryException.class)
  public void testFilterWithInvalidEscape(
          String filterWithInvalidEscape)
          throws DirectoryException {
    // This should fail with a parse error.
    SearchFilter.createFilterFromString(filterWithInvalidEscape);
  }


  // -------------------------------------------------------------------------
  //
  // Test invalid filters.
  //
  // -------------------------------------------------------------------------

  //
  // Invalid filters that are detected.
  //

  @DataProvider(name = "invalidFilters")
  public Object[][] invalidFilters() {
    return new Object[][]{
            {null},
            {"(cn)"},
            {"()"},
            {"("},
            {"(&(sn=test)"},
            {"(|(sn=test)"},
            {"(!(sn=test)"},
            {"(&(sn=test)))"},
            {"(|(sn=test)))"},
            {"(!(sn=test)))"},
            {"(sn=\\A)"},
            {"(sn=\\1H)"},
            {"(sn=\\H1)"},
            {"(!(sn=test)(cn=test))"},
            {"(!)"},
            {"(:dn:=Sally)"}
    };
  }

  @Test(dataProvider = "invalidFilters",
        expectedExceptions = DirectoryException.class)
  public void testCreateFilterFromStringInvalidFilters(String invalidFilter)
          throws DirectoryException {
    SearchFilter.createFilterFromString(invalidFilter).toString();
  }

  //
  // This is more or less the same as what's above, but it's for invalid
  // filters that are not currently detected by the parser.  To turn these
  // on, remove them from the broken group.  As the code is modified to handle
  // these cases, please add these test cases to the
  // paramsCreateFilterFromStringInvalidFilters DataProvider.
  //

  @DataProvider(name = "uncaughtInvalidFilters")
  public Object[][] paramsCreateFilterFromStringUncaughtInvalidFilters() {
    return new Object[][]{
            {"(cn=**)"},
            {"( sn = test )"},
            {"&(cn=*)"},
            {"(!(sn=test)(sn=test2))"},
            {"(objectclass=**)"},
    };
  }

  @Test(dataProvider = "uncaughtInvalidFilters",
        expectedExceptions = DirectoryException.class,
        // FIXME:  These currently aren't detected
        enabled = false)
  public void testCreateFilterFromStringUncaughtInvalidFilters(String invalidFilter)
          throws DirectoryException {
    SearchFilter.createFilterFromString(invalidFilter).toString();
  }


  ////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////
  //
  // matches
  //
  ////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////

  private static final String JOHN_SMITH_LDIF = TestCaseUtils.makeLdif(
          "dn: cn=John Smith,dc=example,dc=com",
          "objectclass: inetorgperson",
          "cn: John Smith",
          "cn;lang-en: Jonathan Smith",
          "sn: Smith",
          "givenname: John",
          "internationaliSDNNumber: 12345",
          "displayName: *",
          "title: tattoos",
          "labeledUri: http://opends.org/john"
          );

  @DataProvider(name = "matchesParams")
  public Object[][] matchesParams() {
    return new Object[][]{
            {JOHN_SMITH_LDIF, "(objectclass=inetorgperson)", true},
            {JOHN_SMITH_LDIF, "(objectclass=iNetOrgPeRsOn)", true},
            {JOHN_SMITH_LDIF, "(objectclass=*)", true},
            {JOHN_SMITH_LDIF, "(objectclass=person)", false},

            {JOHN_SMITH_LDIF, "(cn=John Smith)", true},
            {JOHN_SMITH_LDIF, "(cn=Jonathan Smith)", true},
            {JOHN_SMITH_LDIF, "(cn=JOHN SmITh)", true},
            {JOHN_SMITH_LDIF, "(cn=*)", true},
            {JOHN_SMITH_LDIF, "(cn=*John Smith*)", true},
            {JOHN_SMITH_LDIF, "(cn=*Jo*ith*)", true},
            {JOHN_SMITH_LDIF, "(cn=*Jo*i*th*)", true},
            {JOHN_SMITH_LDIF, "(cn=*Joh*ohn*)", false},  // this shouldn't match
            {JOHN_SMITH_LDIF, "(internationaliSDNNumber=*23*34*)", false},  // this shouldn't match

            {JOHN_SMITH_LDIF, "(cn=*o*n*)", true},
            {JOHN_SMITH_LDIF, "(cn=*n*o*)", false},

            // attribute options
            {JOHN_SMITH_LDIF, "(cn;lang-en=Jonathan Smith)", true},
            {JOHN_SMITH_LDIF, "(cn;lang-EN=Jonathan Smith)", true},
            {JOHN_SMITH_LDIF, "(cn;lang-en=Jonathan Smithe)", false},
            {JOHN_SMITH_LDIF, "(cn;lang-fr=Jonathan Smith)", false},
            {JOHN_SMITH_LDIF, "(cn;lang-en=*jon*an*)", true},
            {JOHN_SMITH_LDIF, "(cn;lAnG-En=*jon*an*)", true},

            // attribute subtypes.  Enable this once 593 is fixed.
            {JOHN_SMITH_LDIF, "(name=John Smith)", true},
            {JOHN_SMITH_LDIF, "(name=*Smith*)", true},
            {JOHN_SMITH_LDIF, "(name;lang-en=Jonathan Smith)", true},
            {JOHN_SMITH_LDIF, "(name;lang-EN=Jonathan Smith)", true},
            {JOHN_SMITH_LDIF, "(name;lang-en=*Jonathan*)", true},

            // Enable this once
//            {JOHN_SMITH_LDIF, "(cn=*Jo**i*th*)", true},

            {JOHN_SMITH_LDIF, "(cn=\\4Aohn*)", true}, // \4A = J
            {JOHN_SMITH_LDIF, "(|(cn=Jane Smith)(cn=John Smith))", true},

            {JOHN_SMITH_LDIF, "(title~=tattoos)", true},
            {JOHN_SMITH_LDIF, "(title~=tattos)", true},

            {JOHN_SMITH_LDIF, "(labeledUri=http://opends.org/john)", true},
            {JOHN_SMITH_LDIF, "(labeledUri=http://opends.org/JOHN)", false},
            {JOHN_SMITH_LDIF, "(labeledUri=http://*/john)", true},
            {JOHN_SMITH_LDIF, "(labeledUri=http://*/JOHN)", false},

            {JOHN_SMITH_LDIF, "(cn>=John Smith)", true},
            {JOHN_SMITH_LDIF, "(cn>=J)", true},
            {JOHN_SMITH_LDIF, "(cn<=J)", false},

            {JOHN_SMITH_LDIF, "(cn=Jane Smith)", false},

            {JOHN_SMITH_LDIF, "(displayName=\\2A)", true}, // \2A = *

            // 2.5.4.4 is Smith
            {JOHN_SMITH_LDIF, "(2.5.4.4=Smith)", true},

            {JOHN_SMITH_LDIF, "(sn:caseExactMatch:=Smith)", true},
            {JOHN_SMITH_LDIF, "(sn:caseExactMatch:=smith)", false},

            // Test cases for 730
            {JOHN_SMITH_LDIF, "(internationaliSDNNumber=*12*45*)", true},
            {JOHN_SMITH_LDIF, "(internationaliSDNNumber=*45*12*)", false},

            // TODO: open a bug for all of these.
//            {JOHN_SMITH_LDIF, "(:caseExactMatch:=Smith)", true},
//            {JOHN_SMITH_LDIF, "(:caseExactMatch:=NotSmith)", false},

            // Look at 4515 for some more examples.  Ask Neil.
//            {JOHN_SMITH_LDIF, "(:dn:caseExactMatch:=example)", true},
//            {JOHN_SMITH_LDIF, "(:dn:caseExactMatch:=notexample)", false},
    };
  }

  @Test(dataProvider = "matchesParams")
  public void testMatches(String ldifEntry, String filterStr, boolean expectMatch) throws Exception {
    runMatchTest(ldifEntry, filterStr, expectMatch);
  }

  private void runMatchTest(String ldifEntry, String filterStr, boolean expectMatch) throws Exception {
    Entry entry = TestCaseUtils.entryFromLdifString(ldifEntry);

    runSingleMatchTest(entry, filterStr, expectMatch);
    runSingleMatchTest(entry, "(|" + filterStr + ")", expectMatch);
    runSingleMatchTest(entry, "(&" + filterStr + ")", expectMatch);
    runSingleMatchTest(entry, "(!" + filterStr + ")", !expectMatch);
  }

  private void runSingleMatchTest(Entry entry, String filterStr, boolean expectMatch) throws Exception {
    final SearchFilter filter = SearchFilter.createFilterFromString(filterStr);
    boolean matches = filter.matchesEntry(entry);
    Assert.assertEquals(matches, expectMatch, "Filter=" + filter + "\nEntry=" + entry);
  }

  ////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////
  //
  // Filter construction
  //
  ////////////////////////////////////////////////////////////////////////////
  ////////////////////////////////////////////////////////////////////////////


  /**
   *
   */
  private static final String makeSimpleLdif(String givenname, String sn) {
    String cn = givenname + " " + sn;
    return TestCaseUtils.makeLdif(
          "dn: cn=" + cn + ",dc=example,dc=com",
          "objectclass: inetorgperson",
          "cn: " + cn,
          "sn: " + sn,
          "givenname: " + givenname
          );
  }

  private static final String JANE_SMITH_LDIF = makeSimpleLdif("Jane", "Smith");
  private static final String JANE_AUSTIN_LDIF = makeSimpleLdif("Jane", "Austin");
  private static final String JOE_SMITH_LDIF = makeSimpleLdif("Joe", "Smith");
  private static final String JOE_AUSTIN_LDIF = makeSimpleLdif("Joe", "Austin");

  private static final List<String> ALL_ENTRIES_LDIF =
          Collections.unmodifiableList(asList(JANE_SMITH_LDIF,
                                              JANE_AUSTIN_LDIF,
                                              JOE_SMITH_LDIF,
                                              JOE_AUSTIN_LDIF));


  /**
   *
   */
  private List<String> getEntriesExcluding(List<String> matchedEntries) {
    List<String> unmatched = new ArrayList<String>(ALL_ENTRIES_LDIF);
    unmatched.removeAll(matchedEntries);
    return unmatched;
  }


  /**
   *
   */
  private static class FilterDescription {
    private SearchFilter searchFilter;

    private List<String> matchedEntriesLdif;
    private List<String> unmatchedEntriesLdif;

    private FilterType filterType;
    private LinkedHashSet<SearchFilter> filterComponents = new LinkedHashSet<SearchFilter>();
    private SearchFilter notComponent;
    private AttributeValue assertionValue;
    private AttributeType attributeType;
    private ByteString subInitialElement;
    private List<ByteString> subAnyElements = new ArrayList<ByteString>();
    private ByteString subFinalElement;
    private String matchingRuleId;
    private boolean dnAttributes;


    /**
     *
     */
    public void validateFilterFields() throws AssertionError {
      if (!searchFilter.getFilterType().equals(filterType)) {
        throwUnequalError("filterTypes");
      }

      if (!searchFilter.getFilterComponents().equals(filterComponents)) {
        throwUnequalError("filterComponents");
      }

      if (!objectsAreEqual(searchFilter.getNotComponent(), notComponent)) {
        throwUnequalError("notComponent");
      }

      if (!objectsAreEqual(searchFilter.getAssertionValue(), assertionValue)) {
        throwUnequalError("assertionValue");
      }

      if (!objectsAreEqual(searchFilter.getAttributeType(), attributeType)) {
        throwUnequalError("attributeType");
      }

      if (!objectsAreEqual(searchFilter.getSubInitialElement(), subInitialElement)) {
        throwUnequalError("subInitial");
      }

      if (!objectsAreEqual(searchFilter.getSubAnyElements(), subAnyElements)) {
        throwUnequalError("subAny");
      }

      if (!objectsAreEqual(searchFilter.getSubFinalElement(), subFinalElement)) {
        throwUnequalError("subFinal");
      }

      if (!objectsAreEqual(searchFilter.getMatchingRuleID(), matchingRuleId)) {
        throwUnequalError("matchingRuleId");
      }

      if (searchFilter.getDNAttributes() != dnAttributes) {
        throwUnequalError("dnAttributes");
      }
    }


    /**
     *
     */
    private void throwUnequalError(String message) throws AssertionError {
      throw new AssertionError("Filter differs from what is expected '" + message + "' differ.\n" + toString());
    }


    /**
     *
     */
    @Override
    public String toString() {
      return "FilterDescription: \n" +
              "\tsearchFilter=" + searchFilter + "\n" +
              "\tfilterType = " + filterType + "\n" +
              "\tfilterComponents = " + filterComponents + "\n" +
              "\tnotComponent = " + notComponent + "\n" +
              "\tassertionValue = " + assertionValue + "\n" +
              "\tattributeType = " + attributeType + "\n" +
              "\tsubInitialElement = " + subInitialElement + "\n" +
              "\tsubAnyElements = " + subAnyElements + "\n" +
              "\tsubFinalElement = " + subFinalElement + "\n" +
              "\tmatchingRuleId = " + dnAttributes + "\n";
    }


    /**
     *
     */
    private FilterDescription negate() {
      FilterDescription negation = new FilterDescription();
      negation.searchFilter = SearchFilter.createNOTFilter(searchFilter);

      // Flip-flop these
      negation.matchedEntriesLdif = unmatchedEntriesLdif;
      negation.unmatchedEntriesLdif = matchedEntriesLdif;

      negation.filterType = FilterType.NOT;
      negation.notComponent = searchFilter;

      return negation;
    }


    /**
     *
     */
    public FilterDescription clone() {
      FilterDescription that = new FilterDescription();

      that.searchFilter = this.searchFilter;
      that.matchedEntriesLdif = this.matchedEntriesLdif;
      that.unmatchedEntriesLdif = this.unmatchedEntriesLdif;
      that.filterType = this.filterType;
      that.filterComponents = this.filterComponents;
      that.notComponent = this.notComponent;
      that.assertionValue = this.assertionValue;
      that.attributeType = this.attributeType;
      that.subInitialElement = this.subInitialElement;
      that.subAnyElements = this.subAnyElements;
      that.subFinalElement = this.subFinalElement;
      that.matchingRuleId = this.matchingRuleId;
      that.dnAttributes = this.dnAttributes;

      return that;
    }
  }


  /**
   *
   */
  private FilterDescription assertionFilterDescription(FilterType filterType,
                                                       String attributeType,
                                                       String attributeValue,
                                                       List<String> matchedEntries) {
    FilterDescription description = new FilterDescription();

    description.filterType = filterType;
    description.attributeType = DirectoryServer.getAttributeType(attributeType);
    description.assertionValue = new AttributeValue(description.attributeType, attributeValue);

    if (filterType == FilterType.EQUALITY) {
      description.searchFilter = SearchFilter.createEqualityFilter(description.attributeType,
                                                                   description.assertionValue);
    } else if (filterType == FilterType.LESS_OR_EQUAL) {
      description.searchFilter = SearchFilter.createLessOrEqualFilter(description.attributeType,
                                                                      description.assertionValue);
    } else if (filterType == FilterType.GREATER_OR_EQUAL) {
      description.searchFilter = SearchFilter.createGreaterOrEqualFilter(description.attributeType,
                                                                         description.assertionValue);
    } else if (filterType == FilterType.APPROXIMATE_MATCH) {
      description.searchFilter = SearchFilter.createApproximateFilter(description.attributeType,
                                                                      description.assertionValue);
    } else {
      fail(filterType + " is not handled.");
    }

    description.matchedEntriesLdif = matchedEntries;
    description.unmatchedEntriesLdif = getEntriesExcluding(matchedEntries);

    return description;
  }


  /**
   *
   */
  private FilterDescription equalityFilterDescription(String attributeType,
                                                      String attributeValue,
                                                      List<String> matchedEntries) {
    return assertionFilterDescription(FilterType.EQUALITY, attributeType, attributeValue, matchedEntries);
  }


  /**
   *
   */
  private FilterDescription lessEqualFilterDescription(String attributeType,
                                                       String attributeValue,
                                                       List<String> matchedEntries) {
    return assertionFilterDescription(FilterType.LESS_OR_EQUAL, attributeType, attributeValue, matchedEntries);
  }


  /**
   *
   */
  private FilterDescription greaterEqualFilterDescription(String attributeType,
                                                          String attributeValue,
                                                          List<String> matchedEntries) {
    return assertionFilterDescription(FilterType.GREATER_OR_EQUAL, attributeType, attributeValue, matchedEntries);
  }


  /**
   *
   */
  private FilterDescription approximateFilterDescription(String attributeType,
                                                         String attributeValue,
                                                         List<String> matchedEntries) {
    return assertionFilterDescription(FilterType.APPROXIMATE_MATCH, attributeType, attributeValue, matchedEntries);
  }


  /**
   *
   */
  private FilterDescription substringFilterDescription(String attributeType,
                                                       String subInitial,
                                                       List<String> subAny,
                                                       String subFinal,
                                                       List<String> matchedEntries) {
    FilterDescription description = new FilterDescription();

    description.filterType = FilterType.SUBSTRING;
    description.attributeType = DirectoryServer.getAttributeType(attributeType);

    description.subInitialElement = new ASN1OctetString(subInitial);
    description.subAnyElements = new ArrayList<ByteString>();
    for (int i = 0; (subAny != null) && (i < subAny.size()); i++) {
      String s = subAny.get(i);
      description.subAnyElements.add(new ASN1OctetString(s));
    }
    description.subFinalElement = new ASN1OctetString(subFinal);

    description.searchFilter = SearchFilter.createSubstringFilter(description.attributeType,
            description.subInitialElement,
            description.subAnyElements,
            description.subFinalElement);


    description.matchedEntriesLdif = matchedEntries;
    description.unmatchedEntriesLdif = getEntriesExcluding(matchedEntries);

    return description;
  }


  /**
   *
   */
  private List<FilterDescription> getNotFilters(List<FilterDescription> filters) {
    List<FilterDescription> notFilters = new ArrayList<FilterDescription>();

    for (FilterDescription filter: filters) {
      notFilters.add(filter.negate());
    }

    return notFilters;
  }


  /**
   *
   */
  private FilterDescription getAndFilter(List<FilterDescription> filters) {
    FilterDescription andFilter = new FilterDescription();

    List<String> matchedEntries = new ArrayList<String>(ALL_ENTRIES_LDIF);
    List<SearchFilter> filterComponents = new ArrayList<SearchFilter>();

    for (FilterDescription filter: filters) {
      matchedEntries.retainAll(filter.matchedEntriesLdif);
      filterComponents.add(filter.searchFilter);
    }

    andFilter.searchFilter = SearchFilter.createANDFilter(filterComponents);
    andFilter.filterComponents = new LinkedHashSet<SearchFilter>(filterComponents);

    andFilter.filterType = FilterType.AND;

    andFilter.matchedEntriesLdif = matchedEntries;
    andFilter.unmatchedEntriesLdif = getEntriesExcluding(matchedEntries);

    return andFilter;
  }


  /**
   *
   */
  private List<FilterDescription> getAndFilters(List<FilterDescription> filters) {
    List<FilterDescription> andFilters = new ArrayList<FilterDescription>();

    for (FilterDescription first: filters) {
      for (FilterDescription second: filters) {
        andFilters.add(getAndFilter(asList(first, second)));
      }
    }

    return andFilters;
  }


  /**
   *
   */
  private FilterDescription getOrFilter(List<FilterDescription> filters) {
    FilterDescription orFilter = new FilterDescription();

    List<String> unmatchedEntries = new ArrayList<String>(ALL_ENTRIES_LDIF);
    List<SearchFilter> filterComponents = new ArrayList<SearchFilter>();

    for (FilterDescription filter: filters) {
      unmatchedEntries.retainAll(filter.unmatchedEntriesLdif);
      filterComponents.add(filter.searchFilter);
    }

    orFilter.searchFilter = SearchFilter.createORFilter(filterComponents);
    orFilter.filterComponents = new LinkedHashSet<SearchFilter>(filterComponents);

    orFilter.filterType = FilterType.OR;

    // Since we're not using Sets, we've whittled down unmatched entries from
    // the full set instead of adding to matchedEntries, which would lead
    // to duplicates.
    orFilter.unmatchedEntriesLdif = unmatchedEntries;
    orFilter.matchedEntriesLdif = getEntriesExcluding(unmatchedEntries);

    return orFilter;
  }


  /**
   *
   */
  private List<FilterDescription> getOrFilters(List<FilterDescription> filters) {
    List<FilterDescription> orFilters = new ArrayList<FilterDescription>();

    for (FilterDescription first: filters) {
      for (FilterDescription second: filters) {
        orFilters.add(getOrFilter(asList(first, second)));
      }
    }

    return orFilters;
  }


  /**
   *
   */
  private List<FilterDescription> getEqualityFilters() throws Exception {
    List<FilterDescription> descriptions = new ArrayList<FilterDescription>();

    descriptions.add(equalityFilterDescription("sn", "Smith",
            asList(JANE_SMITH_LDIF, JOE_SMITH_LDIF)));

    descriptions.add(equalityFilterDescription("givenname", "Jane",
            asList(JANE_SMITH_LDIF, JANE_AUSTIN_LDIF)));

    return descriptions;
  }


  /**
   *
   */
  private List<FilterDescription> getApproximateFilters() throws Exception {
    List<FilterDescription> descriptions = new ArrayList<FilterDescription>();

    descriptions.add(approximateFilterDescription("sn", "Smythe",
            asList(JANE_SMITH_LDIF, JOE_SMITH_LDIF)));

    return descriptions;
  }


  /**
   *
   */
  private List<FilterDescription> getSubstringFilters() throws Exception {
    List<FilterDescription> descriptions = new ArrayList<FilterDescription>();

    descriptions.add(substringFilterDescription(
            "sn",
            "S", asList("i"), "th", // S*i*th
            asList(JANE_SMITH_LDIF, JOE_SMITH_LDIF)));

    return descriptions;
  }


  /**
   *
   */
  private List<FilterDescription> getInequalityFilters() throws Exception {
    List<FilterDescription> descriptions = new ArrayList<FilterDescription>();

    descriptions.add(lessEqualFilterDescription("sn", "Aus",
            (List<String>)(new ArrayList<String>())));

    descriptions.add(greaterEqualFilterDescription("sn", "Aus",
            asList(JANE_AUSTIN_LDIF, JOE_AUSTIN_LDIF,
                   JANE_SMITH_LDIF, JOE_SMITH_LDIF)));


    descriptions.add(lessEqualFilterDescription("sn", "Smi",
            asList(JANE_AUSTIN_LDIF, JOE_AUSTIN_LDIF)));

    descriptions.add(greaterEqualFilterDescription("sn", "Smi",
            asList(JANE_SMITH_LDIF, JOE_SMITH_LDIF)));


    descriptions.add(lessEqualFilterDescription("sn", "Smith",
            asList(JANE_AUSTIN_LDIF, JOE_AUSTIN_LDIF,
                   JANE_SMITH_LDIF, JOE_SMITH_LDIF)));

    descriptions.add(greaterEqualFilterDescription("sn", "Smith",
            asList(JANE_SMITH_LDIF, JOE_SMITH_LDIF)));


    return descriptions;
  }


  /**
   * Updates to this should also be made in getMinimalFilterDescriptionList.
   * @see #getMinimalFilterDescriptionList
   */
  private List<FilterDescription> getFilterDescriptionList() throws Exception {
    List<FilterDescription> baseDescriptions = new ArrayList<FilterDescription>();

    baseDescriptions.addAll(getEqualityFilters());
    baseDescriptions.addAll(getInequalityFilters());
    baseDescriptions.addAll(getApproximateFilters());
    baseDescriptions.addAll(getSubstringFilters());
    baseDescriptions.addAll(getNotFilters(baseDescriptions));

    List<FilterDescription> allDescriptions = new ArrayList<FilterDescription>();

    allDescriptions.addAll(getAndFilters(baseDescriptions));
    allDescriptions.addAll(getOrFilters(baseDescriptions));
    allDescriptions.addAll(baseDescriptions);

    return allDescriptions;
  }


  /**
   *
   */
  protected List<FilterDescription> getMinimalFilterDescriptionList() throws Exception {
    List<FilterDescription> baseDescriptions = new ArrayList<FilterDescription>();
    List<FilterDescription> allDescriptions = new ArrayList<FilterDescription>();

    baseDescriptions.addAll(getEqualityFilters().subList(0, 1));
    baseDescriptions.addAll(getInequalityFilters().subList(0, 2));
    baseDescriptions.addAll(getSubstringFilters().subList(0, 1));
    baseDescriptions.addAll(getNotFilters(baseDescriptions).subList(0, 1));

    allDescriptions.addAll(baseDescriptions);
    allDescriptions.addAll(getAndFilters(baseDescriptions).subList(0, 2));
    allDescriptions.addAll(getOrFilters(baseDescriptions).subList(0, 2));

    return allDescriptions;
  }



  /**
   *
   */
  @DataProvider(name = "filterDescriptions")
  public Object[][] getFilterDescriptions() throws Exception {
    List<FilterDescription> allDescriptions = getFilterDescriptionList();

    // Now convert to [][]
    FilterDescription[][] descriptionArray = new FilterDescription[allDescriptions.size()][];
    for (int i = 0; i < allDescriptions.size(); i++) {
      FilterDescription description = allDescriptions.get(i);
      descriptionArray[i] = new FilterDescription[]{description};
    }

    return descriptionArray;
  }


  @Test(dataProvider = "filterDescriptions")
  public void testFilterConstruction(FilterDescription description) throws Exception {
    description.validateFilterFields();

    for (String ldif: description.matchedEntriesLdif) {
      Entry entry = TestCaseUtils.entryFromLdifString(ldif);
      if (!description.searchFilter.matchesEntry(entry)) {
        fail("Expected to match entry. " + description + entry);
      }
    }

    for (String ldif: description.unmatchedEntriesLdif) {
      Entry entry = TestCaseUtils.entryFromLdifString(ldif);
      if (description.searchFilter.matchesEntry(entry)) {
        fail("Should not have matched entry. " + description + entry);
      }
    }
  }

  // TODO: test more on extensible match and attribute options
  // TODO: test that we fail when creating filters without specifying all of the parameters
  // TODO: we need to test attribute options!
  // TODO: test the audio attribute since it's octetStringMatch
  // TODO: test the homePhone attribute since   EQUALITY telephoneNumberMatch SUBSTR telephoneNumberSubstringsMatch
  // TODO: test labeledURI since it's  caseExactMatch SUBSTR caseExactSubstringsMatch
  // TODO: test mail since it's EQUALITY caseIgnoreIA5Match SUBSTR caseIgnoreIA5SubstringsMatch
  // TODO: test secretary since it's distinguishedNameMatch
  // TODO: test x500UniqueIdentifier since it's bitStringMatch


  private static final Object[][] TEST_EQUALS_PARAMS = new Object[][]{
          // These have duplicates, and their String representation should even reflect that.
          {"(&(sn=Smith))", "(&(sn=Smith)(sn=Smith))", true, true},
          {"(|(sn=Smith))", "(|(sn=Smith)(sn=Smith))", true, true},

          // These are reordered, so they are equivalent, but their String representations will differ
          {"(&(sn=Smith)(sn<=Aus))", "(&(sn<=Aus)(sn=Smith))", true, false},
          {"(|(sn=Smith)(sn<=Aus))", "(|(sn<=Aus)(sn=Smith))", true, false},

          // These should be case insensitive
          {"(SN=Smith)", "(sn=Smith)", true, true},
          {"(sn=smith)", "(sn=Smith)", true, false},
          {"(SN=S*th)", "(sn=S*th)", true, true},

          {"(sn:caseExactMatch:=Smith)", "(sn:caseExactMatch:=Smith)", true, true},

          // This demonstrates bug 704.
          {"(sn:caseExactMatch:=Smith)", "(sn:caseExactMatch:=smith)", false, false},

          // Ensure that ":dn:" is treated in a case-insensitive manner.
          {"(:dn:caseExactMatch:=example)", "(:DN:caseExactMatch:=example)", true, true}, // ? String not match

          // 2.5.4.4 is 'sn'
          {"(2.5.4.4=Smith)", "(2.5.4.4=Smith)", true, true},
          {"(2.5.4.4=Smith)", "(sn=Smith)", true, true},

          {"(sn;lang-en=Smith)", "(sn;lang-en=Smith)", true, true},

          // This demonstrates bug 706
          {"(sn;lang-en=Smith)", "(sn=Smith)", false, false},


          // This demonstrates bug 705.
          {"(sn=s*t*h)", "(sn=S*T*H)", true, false},

          // These should be case sensitive
          {"(labeledURI=http://opends.org)", "(labeledURI=http://OpenDS.org)", false, false},
          {"(labeledURI=http://opends*)", "(labeledURI=http://OpenDS*)", false, false},

          // These are WYSIWIG
          {"(sn=*)", "(sn=*)", true, true},
          {"(sn=S*)", "(sn=S*th)", false, false},
          {"(sn=*S)", "(sn=S*th)", false, false},
          {"(sn=S*t)", "(sn=S*th)", false, false},
          {"(sn=*i*t*)", "(sn=*i*t*)", true, true},
          {"(sn=*t*i*)", "(sn=*i*t*)", false, false},  // Test case for 695
          {"(sn=S*i*t)", "(sn=S*th)", false, false},
          {"(sn=Smith)", "(sn=Smith)", true, true},
          {"(sn=Smith)", "(sn<=Aus)", false, false},
          {"(sn=Smith)", "(sn>=Aus)", false, false},
          {"(sn=Smith)", "(sn=S*i*th)", false, false},
          {"(sn=Smith)", "(!(sn=Smith))", false, false},
          {"(sn=Smith)", "(&(sn=Smith)(sn<=Aus))", false, false},
          {"(sn=Smith)", "(|(sn=Smith)(sn<=Aus))", false, false},
          {"(sn<=Aus)", "(sn<=Aus)", true, true},
          {"(sn<=Aus)", "(sn>=Aus)", false, false},
          {"(sn<=Aus)", "(sn=S*i*th)", false, false},
          {"(sn<=Aus)", "(!(sn=Smith))", false, false},
          {"(sn<=Aus)", "(&(sn=Smith)(sn=Smith))", false, false},
          {"(sn<=Aus)", "(&(sn=Smith)(sn<=Aus))", false, false},
          {"(sn<=Aus)", "(|(sn=Smith)(sn=Smith))", false, false},
          {"(sn<=Aus)", "(|(sn=Smith)(sn<=Aus))", false, false},
          {"(sn>=Aus)", "(sn>=Aus)", true, true},
          {"(sn>=Aus)", "(sn=S*i*th)", false, false},
          {"(sn>=Aus)", "(!(sn=Smith))", false, false},
          {"(sn>=Aus)", "(&(sn=Smith)(sn=Smith))", false, false},
          {"(sn>=Aus)", "(&(sn=Smith)(sn<=Aus))", false, false},
          {"(sn>=Aus)", "(|(sn=Smith)(sn=Smith))", false, false},
          {"(sn>=Aus)", "(|(sn=Smith)(sn<=Aus))", false, false},
          {"(sn=S*i*th)", "(sn=S*i*th)", true, true},
          {"(sn=S*i*th)", "(!(sn=Smith))", false, false},
          {"(sn=S*i*th)", "(&(sn=Smith)(sn=Smith))", false, false},
          {"(sn=S*i*th)", "(&(sn=Smith)(sn<=Aus))", false, false},
          {"(sn=S*i*th)", "(|(sn=Smith)(sn=Smith))", false, false},
          {"(sn=S*i*th)", "(|(sn=Smith)(sn<=Aus))", false, false},
          {"(!(sn=Smith))", "(!(sn=Smith))", true, true},
          {"(!(sn=Smith))", "(&(sn=Smith)(sn=Smith))", false, false},
          {"(!(sn=Smith))", "(&(sn=Smith)(sn<=Aus))", false, false},
          {"(!(sn=Smith))", "(|(sn=Smith)(sn=Smith))", false, false},
          {"(!(sn=Smith))", "(|(sn=Smith)(sn<=Aus))", false, false},
          {"(&(sn=Smith)(sn=Smith))", "(&(sn=Smith)(sn=Smith))", true, true},
          {"(&(sn=Smith)(sn=Smith))", "(&(sn=Smith)(sn<=Aus))", false, false},
          {"(&(sn=Smith)(sn=Smith))", "(|(sn=Smith)(sn=Smith))", false, false},
          {"(&(sn=Smith)(sn=Smith))", "(|(sn=Smith)(sn<=Aus))", false, false},
          {"(&(sn=Smith)(sn<=Aus))", "(&(sn=Smith)(sn<=Aus))", true, true},
          {"(&(sn=Smith)(sn<=Aus))", "(|(sn=Smith)(sn=Smith))", false, false},
          {"(&(sn=Smith)(sn<=Aus))", "(|(sn=Smith)(sn<=Aus))", false, false},
          {"(|(sn=Smith)(sn=Smith))", "(|(sn=Smith)(sn=Smith))", true, true},
          {"(|(sn=Smith)(sn=Smith))", "(|(sn=Smith)(sn<=Aus))", false, false},
          {"(|(sn=Smith)(sn<=Aus))", "(|(sn=Smith)(sn<=Aus))", true, true},
          {"(&(sn=Smith)(sn<=Aus))", "(&(sn=Smith)(sn>=Aus))", false, false},
          {"(|(sn=Smith)(sn<=Aus))", "(|(sn=Smith)(sn>=Aus))", false, false},


          // Test cases for issue #1245
          {"(cn=*bowen*)", "(cn=*bowen*)", true, true},
          {"(cn=*bowen*)", "(sn=*bowen*)", false, false}
  };


  /**
   *
   */
  @DataProvider(name = "equalsTest")
  public Object[][] getEqualsTests() throws Exception {
    return TEST_EQUALS_PARAMS;
  }


  /**
   *
   */
  @Test(dataProvider = "equalsTest")
  public void testEquals(String stringFilter1, String stringFilter2, boolean expectEquals, boolean expectStringEquals) throws Exception {
    SearchFilter filter1 = SearchFilter.createFilterFromString(stringFilter1);
    SearchFilter filter2 = SearchFilter.createFilterFromString(stringFilter2);

    boolean actualEquals = filter1.equals(filter2);
    assertEquals(actualEquals, expectEquals,
                 "Expected " + filter1 + (expectEquals ? " == " : " != ") + filter2);

    // Test symmetry
    actualEquals = filter2.equals(filter1);
    assertEquals(actualEquals, expectEquals,
                 "Expected " + filter1 + (expectEquals ? " == " : " != ") + filter2);

    if (expectEquals) {
      assertEquals(filter1.hashCode(), filter2.hashCode(),
                   "Hash codes differ for " + filter1 + " and " + filter2);
    }

    // Test toString
    actualEquals = filter2.toString().equals(filter1.toString());
    assertEquals(actualEquals, expectStringEquals,
                 "Expected " + filter1 + (expectStringEquals ? " == " : " != ") + filter2);
  }
}

