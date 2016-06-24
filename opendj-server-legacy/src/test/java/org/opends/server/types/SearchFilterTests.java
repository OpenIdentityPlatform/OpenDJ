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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2014 Manuel Gaupp
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.types;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.Base64;
import org.opends.server.util.StaticUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.Arrays.*;

import static org.testng.Assert.*;

/**
 * Tests for the org.opends.server.types.SearchFilter class
 *
 * This class covers the SearchFilter class fairly well.  The main gaps are
 * with extensible match, attribute options, and there is a lot of code
 * that is not reachable because it's in exception handling code that
 * is not exercisable externally.
 */
@SuppressWarnings("javadoc")
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

  /** These are valid filters. */
  @DataProvider
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

            {"(&(sn=test))", "(sn=test)"},
            {"(|(sn=test))", "(sn=test)"},
            {"(&(objectclass=person)(|(sn=test)))", "(&(objectClass=person)(sn=test))"},
    };
  }

  @Test(dataProvider = "paramsCreateFilterFromStringValidFilters")
  public void testCreateFilterFromStringValidFilters(
          String originalFilter,
          String expectedToStringFilter
  ) throws DirectoryException {
    runRecreateFilterTest(originalFilter, expectedToStringFilter);
  }

  private void runRecreateFilterTest(String originalFilter, String expectedToStringFilter) throws DirectoryException {
    String regenerated = SearchFilter.createFilterFromString(originalFilter).toString();
    assertEquals(regenerated, expectedToStringFilter,
        "original=" + originalFilter + ", expected=" + expectedToStringFilter);
  }

  /** These are valid filters. */
  @DataProvider(name = "escapeSequenceFilters")
  public Object[][] escapeSequenceFilters() {
    final char[] CHAR_NIBBLES = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                                 'a', 'b', 'c', 'd', 'e', 'f',
                                 'A', 'B', 'C', 'D', 'E', 'F'};

    final byte[] BYTE_NIBBLES = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                                 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                                 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};

    List<String[]> allParameters = new ArrayList<>();
    for (int i = 0; i < CHAR_NIBBLES.length; i++) {
      char highNibble = CHAR_NIBBLES[i];
      byte highByteNibble = BYTE_NIBBLES[i];
      for (int j = 0; j < CHAR_NIBBLES.length; j++) {
        char lowNibble = CHAR_NIBBLES[j];
        byte lowByteNibble = BYTE_NIBBLES[j];
        String inputChar = "\\" + highNibble + lowNibble;
        byte byteValue = (byte)((highByteNibble << 4) | lowByteNibble);
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

    return allParameters.toArray(new String[][]{});
  }


  /** These are filters with invalid escape sequences. */
  @DataProvider
  public Object[][] invalidEscapeSequenceFilters() {
    final char[] VALID_NIBBLES = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                                 'a', 'b', 'c', 'd', 'e', 'f',
                                 'A', 'B', 'C', 'D', 'E', 'F'};

    final char[] INVALID_NIBBBLES = {'g', 'z', 'G', 'Z', '-', '=', '+', '\00', ')',
                                     'n', 't', '\\'};

    List<String> invalidEscapeSequences = new ArrayList<>();

    for (char validNibble : VALID_NIBBLES)
    {
      for (char invalidNibble : INVALID_NIBBBLES)
      {
        invalidEscapeSequences.add("\\" + validNibble + invalidNibble);
        invalidEscapeSequences.add("\\" + invalidNibble + validNibble);
      }
      // Also do a test case where we only have one character in the escape sequence.
      invalidEscapeSequences.add("\\" + validNibble);
    }

    List<String[]> allParameters = new ArrayList<>();
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

    return allParameters.toArray(new String[][]{});
  }


  /**
   * @return a value that can be used in an LDAP filter.
   */
  private String getFilterValueForChar(byte value) {
    if (((value & 0x7F) != value)  // Not 7-bit clean
        || value <= 0x1F           // Below the printable character range
        || value == 0x28           // Open parenthesis
        || value == 0x29           // Close parenthesis
        || value == 0x2A           // Asterisk
        || value == 0x5C           // Backslash
        || value == 0x7F)          // Delete character
    {
      return "\\" + StaticUtils.byteToHex(value);
    }
    return String.valueOf((char) value);
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

  /** Invalid filters that are detected. */
  @DataProvider
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

  /**
   * This is more or less the same as what's above, but it's for invalid
   * filters that are not currently detected by the parser.  To turn these
   * on, remove them from the broken group.  As the code is modified to handle
   * these cases, please add these test cases to the
   * paramsCreateFilterFromStringInvalidFilters DataProvider.
   */
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

  @DataProvider
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


  private static String makeSimpleLdif(String givenname, String sn) {
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


  private List<String> getEntriesExcluding(List<String> matchedEntries) {
    List<String> unmatched = new ArrayList<>(ALL_ENTRIES_LDIF);
    unmatched.removeAll(matchedEntries);
    return unmatched;
  }


  private static class FilterDescription {
    private SearchFilter searchFilter;

    private List<String> matchedEntriesLdif;
    private List<String> unmatchedEntriesLdif;

    private FilterType filterType;
    private LinkedHashSet<SearchFilter> filterComponents = new LinkedHashSet<>();
    private SearchFilter notComponent;
    private ByteString assertionValue;
    private AttributeType attributeType;
    private ByteString subInitialElement;
    private List<ByteString> subAnyElements = new ArrayList<>();
    private ByteString subFinalElement;
    private String matchingRuleId;
    private boolean dnAttributes;

    public void validateFilterFields() throws AssertionError {
      assertEquals(searchFilter.getFilterType(), filterType, errorMsg("filterTypes"));
      assertEquals(searchFilter.getFilterComponents(), filterComponents, errorMsg("filterComponents"));
      assertEquals(searchFilter.getNotComponent(), notComponent, "notComponent");
      assertEquals(searchFilter.getAssertionValue(), assertionValue, "assertionValue");
      assertEquals(searchFilter.getAttributeType(), attributeType, errorMsg("attributeType"));
      assertEquals(searchFilter.getSubInitialElement(), subInitialElement, errorMsg("subInitial"));
      assertEquals(searchFilter.getSubAnyElements(), subAnyElements, errorMsg("subAny"));
      assertEquals(searchFilter.getSubFinalElement(), subFinalElement, errorMsg("subFinal"));
      assertEquals(searchFilter.getMatchingRuleID(), matchingRuleId, errorMsg("matchingRuleId"));
      assertEquals(searchFilter.getDNAttributes(), dnAttributes, errorMsg("dnAttributes"));
    }

    private String errorMsg(String message) {
      return "Filter differs from what is expected '" + message + "' differ.\n" + toString();
    }

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


    @Override
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


  private FilterDescription assertionFilterDescription(FilterType filterType,
                                                       String attributeType,
                                                       String attributeValue,
                                                       String... matchedEntries) {
    FilterDescription description = new FilterDescription();

    description.filterType = filterType;
    description.attributeType = DirectoryServer.getSchema().getAttributeType(attributeType);
    description.assertionValue = ByteString.valueOfUtf8(attributeValue);

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

    description.matchedEntriesLdif = asList(matchedEntries);
    description.unmatchedEntriesLdif = getEntriesExcluding(description.matchedEntriesLdif);

    return description;
  }


  private FilterDescription equalityFilterDescription(String attributeType,
                                                      String attributeValue,
                                                      String... matchedEntries) {
    return assertionFilterDescription(FilterType.EQUALITY, attributeType, attributeValue, matchedEntries);
  }


  private FilterDescription lessEqualFilterDescription(String attributeType,
                                                       String attributeValue,
                                                       String... matchedEntries) {
    return assertionFilterDescription(FilterType.LESS_OR_EQUAL, attributeType, attributeValue, matchedEntries);
  }


  private FilterDescription greaterEqualFilterDescription(String attributeType,
                                                          String attributeValue,
                                                          String... matchedEntries) {
    return assertionFilterDescription(FilterType.GREATER_OR_EQUAL, attributeType, attributeValue, matchedEntries);
  }


  private FilterDescription approximateFilterDescription(String attributeType,
                                                         String attributeValue,
                                                         String... matchedEntries) {
    return assertionFilterDescription(FilterType.APPROXIMATE_MATCH, attributeType, attributeValue, matchedEntries);
  }


  private FilterDescription substringFilterDescription(String attributeType,
                                                       String subInitial,
                                                       List<String> subAny,
                                                       String subFinal,
                                                       List<String> matchedEntries) {
    FilterDescription description = new FilterDescription();

    description.filterType = FilterType.SUBSTRING;
    description.attributeType = DirectoryServer.getSchema().getAttributeType(attributeType);

    description.subInitialElement = ByteString.valueOfUtf8(subInitial);
    description.subAnyElements = new ArrayList<>();
    if (subAny != null)
    {
      for (String s : subAny)
      {
        description.subAnyElements.add(ByteString.valueOfUtf8(s));
      }
    }
    description.subFinalElement = ByteString.valueOfUtf8(subFinal);

    description.searchFilter = SearchFilter.createSubstringFilter(description.attributeType,
            description.subInitialElement,
            description.subAnyElements,
            description.subFinalElement);


    description.matchedEntriesLdif = matchedEntries;
    description.unmatchedEntriesLdif = getEntriesExcluding(matchedEntries);

    return description;
  }


  private List<FilterDescription> getNotFilters(List<FilterDescription> filters) {
    List<FilterDescription> notFilters = new ArrayList<>();

    for (FilterDescription filter: filters) {
      notFilters.add(filter.negate());
    }

    return notFilters;
  }


  private FilterDescription getAndFilter(List<FilterDescription> filters) {
    FilterDescription andFilter = new FilterDescription();

    List<String> matchedEntries = new ArrayList<>(ALL_ENTRIES_LDIF);
    List<SearchFilter> filterComponents = new ArrayList<>();

    for (FilterDescription filter: filters) {
      matchedEntries.retainAll(filter.matchedEntriesLdif);
      filterComponents.add(filter.searchFilter);
    }

    andFilter.searchFilter = SearchFilter.createANDFilter(filterComponents);
    andFilter.filterComponents = new LinkedHashSet<>(filterComponents);

    andFilter.filterType = FilterType.AND;

    andFilter.matchedEntriesLdif = matchedEntries;
    andFilter.unmatchedEntriesLdif = getEntriesExcluding(matchedEntries);

    return andFilter;
  }


  private List<FilterDescription> getAndFilters(List<FilterDescription> filters) {
    List<FilterDescription> andFilters = new ArrayList<>();

    for (FilterDescription first: filters) {
      for (FilterDescription second: filters) {
        andFilters.add(getAndFilter(asList(first, second)));
      }
    }

    return andFilters;
  }


  private FilterDescription getOrFilter(List<FilterDescription> filters) {
    FilterDescription orFilter = new FilterDescription();

    List<String> unmatchedEntries = new ArrayList<>(ALL_ENTRIES_LDIF);
    List<SearchFilter> filterComponents = new ArrayList<>();

    for (FilterDescription filter: filters) {
      unmatchedEntries.retainAll(filter.unmatchedEntriesLdif);
      filterComponents.add(filter.searchFilter);
    }

    orFilter.searchFilter = SearchFilter.createORFilter(filterComponents);
    orFilter.filterComponents = new LinkedHashSet<>(filterComponents);

    orFilter.filterType = FilterType.OR;

    // Since we're not using Sets, we've whittled down unmatched entries from
    // the full set instead of adding to matchedEntries, which would lead
    // to duplicates.
    orFilter.unmatchedEntriesLdif = unmatchedEntries;
    orFilter.matchedEntriesLdif = getEntriesExcluding(unmatchedEntries);

    return orFilter;
  }


  private List<FilterDescription> getOrFilters(List<FilterDescription> filters) {
    List<FilterDescription> orFilters = new ArrayList<>();

    for (FilterDescription first: filters) {
      for (FilterDescription second: filters) {
        orFilters.add(getOrFilter(asList(first, second)));
      }
    }

    return orFilters;
  }


  private List<FilterDescription> getEqualityFilters() throws Exception {
    return asList(
        equalityFilterDescription("sn", "Smith", JANE_SMITH_LDIF, JOE_SMITH_LDIF),
        equalityFilterDescription("givenname", "Jane", JANE_SMITH_LDIF, JANE_AUSTIN_LDIF));
  }


  private List<FilterDescription> getApproximateFilters() throws Exception {
    return asList(approximateFilterDescription("sn", "Smythe", JANE_SMITH_LDIF, JOE_SMITH_LDIF));
  }


  private List<FilterDescription> getSubstringFilters() throws Exception {
    return asList(substringFilterDescription(
            "sn",
            "S", asList("i"), "th", // S*i*th
            asList(JANE_SMITH_LDIF, JOE_SMITH_LDIF)));
  }


  private List<FilterDescription> getInequalityFilters() throws Exception {
    return asList(
        lessEqualFilterDescription("sn", "Aus"),
        greaterEqualFilterDescription("sn", "Aus",
            JANE_AUSTIN_LDIF, JOE_AUSTIN_LDIF, JANE_SMITH_LDIF, JOE_SMITH_LDIF),
        lessEqualFilterDescription("sn", "Smi",
            JANE_AUSTIN_LDIF, JOE_AUSTIN_LDIF),
        greaterEqualFilterDescription("sn", "Smi",
            JANE_SMITH_LDIF, JOE_SMITH_LDIF),
        lessEqualFilterDescription("sn", "Smith",
            JANE_AUSTIN_LDIF, JOE_AUSTIN_LDIF, JANE_SMITH_LDIF, JOE_SMITH_LDIF),
        greaterEqualFilterDescription("sn", "Smith",
            JANE_SMITH_LDIF, JOE_SMITH_LDIF));
  }


  /**
   * Updates to this should also be made in getMinimalFilterDescriptionList.
   * @see #getMinimalFilterDescriptionList
   */
  private List<FilterDescription> getFilterDescriptionList() throws Exception {
    List<FilterDescription> baseDescriptions = new ArrayList<>();

    baseDescriptions.addAll(getEqualityFilters());
    baseDescriptions.addAll(getInequalityFilters());
    baseDescriptions.addAll(getApproximateFilters());
    baseDescriptions.addAll(getSubstringFilters());
    baseDescriptions.addAll(getNotFilters(baseDescriptions));

    List<FilterDescription> allDescriptions = new ArrayList<>();

    allDescriptions.addAll(getAndFilters(baseDescriptions));
    allDescriptions.addAll(getOrFilters(baseDescriptions));
    allDescriptions.addAll(baseDescriptions);

    return allDescriptions;
  }


  protected List<FilterDescription> getMinimalFilterDescriptionList() throws Exception {
    List<FilterDescription> baseDescriptions = new ArrayList<>();
    List<FilterDescription> allDescriptions = new ArrayList<>();

    baseDescriptions.addAll(getEqualityFilters().subList(0, 1));
    baseDescriptions.addAll(getInequalityFilters().subList(0, 2));
    baseDescriptions.addAll(getSubstringFilters().subList(0, 1));
    baseDescriptions.addAll(getNotFilters(baseDescriptions).subList(0, 1));

    allDescriptions.addAll(baseDescriptions);
    allDescriptions.addAll(getAndFilters(baseDescriptions).subList(0, 2));
    allDescriptions.addAll(getOrFilters(baseDescriptions).subList(0, 2));

    return allDescriptions;
  }



  @DataProvider
  public Object[][] filterDescriptions() throws Exception {
    List<FilterDescription> allDescriptions = getFilterDescriptionList();

    // Now convert to [][]
    FilterDescription[][] descriptionArray = new FilterDescription[allDescriptions.size()][];
    for (int i = 0; i < allDescriptions.size(); i++) {
      descriptionArray[i] = new FilterDescription[]{ allDescriptions.get(i) };
    }

    return descriptionArray;
  }


  @Test(dataProvider = "filterDescriptions")
  public void testFilterConstruction(FilterDescription description) throws Exception {
    description.validateFilterFields();

    for (String ldif: description.matchedEntriesLdif) {
      Entry entry = TestCaseUtils.entryFromLdifString(ldif);
      assertTrue(description.searchFilter.matchesEntry(entry),
          "Expected to match entry. " + description + " " + entry);
    }

    for (String ldif: description.unmatchedEntriesLdif) {
      Entry entry = TestCaseUtils.entryFromLdifString(ldif);
      assertFalse(description.searchFilter.matchesEntry(entry),
          "Should not have matched entry. " + description + " " + entry);
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
          {"(&(sn=Smith))", "(sn=Smith)", true, true},
          {"(|(sn=Smith))", "(sn=Smith)", true, true},

          // These are reordered, so they are equivalent, but their String representations will differ
          {"(&(sn=Smith)(sn<=Aus))", "(&(sn<=Aus)(sn=Smith))", true, false},
          {"(|(sn=Smith)(sn<=Aus))", "(|(sn<=Aus)(sn=Smith))", true, false},

          // These should be case insensitive
          {"(SN=Smith)", "(sn=Smith)", true, true},
          {"(SN=S*th)", "(sn=S*th)", true, true},

          // We no longer normalize assertion values,
          // so these filters are not equal
          {"(sn=smith)", "(sn=Smith)", false, false},

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


          // This was initially to demonstrates bug 705.
          // But we reverted back to old behavior,
          // because we no longer normalize assertion values.
          {"(sn=s*t*h)", "(sn=S*T*H)", false, false},

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


  @DataProvider
  public Object[][] equalsTest() throws Exception {
    return TEST_EQUALS_PARAMS;
  }


  @Test(dataProvider = "equalsTest")
  public void testEquals(String stringFilter1, String stringFilter2, boolean expectEquals, boolean expectStringEquals)
      throws Exception {
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


  /**
   * Dataprovider for testing different normalization for value and assertion.
   */
  @DataProvider
  public Object[][] differentNormalization() throws ParseException
  {
    final String BASE64_CERT_VALUE =
      "MIICpTCCAg6gAwIBAgIJALeoA6I3ZC/cMA0GCSqGSIb3DQEBBQUAMFYxCzAJBgNV" +
      "BAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRpb25lMRwwGgYDVQQLExNQcm9kdWN0IERl" +
      "dmVsb3BtZW50MRQwEgYDVQQDEwtCYWJzIEplbnNlbjAeFw0xMjA1MDIxNjM0MzVa" +
      "Fw0xMjEyMjExNjM0MzVaMFYxCzAJBgNVBAYTAlVTMRMwEQYDVQQHEwpDdXBlcnRp" +
      "b25lMRwwGgYDVQQLExNQcm9kdWN0IERldmVsb3BtZW50MRQwEgYDVQQDEwtCYWJz" +
      "IEplbnNlbjCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEApysa0c9qc8FB8gIJ" +
      "8zAb1pbJ4HzC7iRlVGhRJjFORkGhyvU4P5o2wL0iz/uko6rL9/pFhIlIMbwbV8sm" +
      "mKeNUPitwiKOjoFDmtimcZ4bx5UTAYLbbHMpEdwSpMC5iF2UioM7qdiwpAfZBd6Z" +
      "69vqNxuUJ6tP+hxtr/aSgMH2i8ECAwEAAaN7MHkwCQYDVR0TBAIwADAsBglghkgB" +
      "hvhCAQ0EHxYdT3BlblNTTCBHZW5lcmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYE" +
      "FLlZD3aKDa8jdhzoByOFMAJDs2osMB8GA1UdIwQYMBaAFLlZD3aKDa8jdhzoByOF" +
      "MAJDs2osMA0GCSqGSIb3DQEBBQUAA4GBAE5vccY8Ydd7by2bbwiDKgQqVyoKrkUg" +
      "6CD0WRmc2pBeYX2z94/PWO5L3Fx+eIZh2wTxScF+FdRWJzLbUaBuClrxuy0Y5ifj" +
      "axuJ8LFNbZtsp1ldW3i84+F5+SYT+xI67ZcoAtwx/VFVI9s5I/Gkmu9f9nxjPpK7" +
      "1AIUXiE3Qcck";
    final String CERT_EXACT_ASSERTION =
      "{ serialNumber 13233831500277100508, issuer rdnSequence:\""+
      "CN=Babs Jensen,OU=Product Development,L=Cupertione,C=US\" }";
    final String LDIF_ENTRY = TestCaseUtils.makeLdif(
          "dn: cn=John Smith,dc=example,dc=com",
          "objectclass: inetorgperson",
          "cn: John Smith",
          "sn: Smith",
          "userCertificate;binary:: "+BASE64_CERT_VALUE
          );
    StringBuilder builder = new StringBuilder();
    RawFilter.valueToFilterString(builder,ByteString.wrap(Base64.decode(BASE64_CERT_VALUE)));
    final String CERTIFICATE_ENCODED = builder.toString();

    return new Object[][]{
      { LDIF_ENTRY, "userCertificate=" + CERT_EXACT_ASSERTION, true },
      { LDIF_ENTRY, "userCertificate=" + CERTIFICATE_ENCODED, true },
    };
  }

  @Test(dataProvider = "differentNormalization")
  public void testDifferentNormalization(String ldifEntry, String filterStr,
                                         boolean expectMatch) throws Exception
  {
    Entry entry = TestCaseUtils.entryFromLdifString(ldifEntry);
    boolean matches = SearchFilter.createFilterFromString(filterStr).matchesEntry(entry);
    Assert.assertEquals(matches, expectMatch, "Filter=" + filterStr + "\nEntry=" + entry);
  }
}
