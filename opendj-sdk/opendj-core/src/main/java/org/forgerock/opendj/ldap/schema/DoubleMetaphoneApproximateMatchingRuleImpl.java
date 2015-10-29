/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

/**
 * This class defines an approximate matching rule based on the Double Metaphone
 * algorithm. The Metaphone and Double Metaphone algorithms were originally
 * devised by Lawrence Philips (published in the December 1990 issue of
 * <I>Computer Language</I> and the <A
 * HREF="http://www.cuj.com/documents/s=8038/cuj0006philips/">June 2000 issue of
 * <I>C/C++ Users Journal</I></A>, respectively), and this version of the
 * algorithm is based on a version modified by Kevin Atkinson to include
 * bugfixes and additional functionality (source is available <A
 * HREF="http://aspell.net/metaphone/dmetaph.cpp">here</A> and additional
 * Metaphone and Double Metaphone information is available at <A
 * HREF="http://aspell.net/metaphone/">http://aspell.net/ metaphone/</A>). This
 * implementation is largely the same as the one provided by Kevin Atkinson, but
 * it has been re-written for better readability, for more efficiency, to get
 * rid of checks for conditions that can't possibly happen, and to get rid of
 * redundant checks that aren't needed. It has also been updated to always only
 * generate a single value rather than one or possibly two values.
 */
final class DoubleMetaphoneApproximateMatchingRuleImpl extends AbstractApproximateMatchingRuleImpl {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    DoubleMetaphoneApproximateMatchingRuleImpl() {
      super(AMR_DOUBLE_METAPHONE_NAME);
    }

    /** {@inheritDoc} */
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) {
        String valueString = value.toString();
        final int length = valueString.length();
        if (length == 0) {
            // The value is empty, so it is already normalized.
            return ByteString.empty();
        }

        final int last = length - 1;

        // Pad the value to allow for checks to go past the end of the value.
        valueString = valueString.toUpperCase() + "     ";

        // The metaphone value that is being constructed.
        final StringBuilder metaphone = new StringBuilder(4);

        // Skip over GN, KN, PN, WR, and PS at the beginning of a word.
        int pos = 0;
        String substring = valueString.substring(0, 2);
        if (substring.equals("GN") || substring.equals("KN") || substring.equals("PN")
                || substring.equals("WR") || substring.equals("PS")) {
            pos++;
        } else if (valueString.charAt(0) == 'X') {
            // 'X' at the beginning of a word will sound like Z, but Z will
            // always be mapped to S.
            metaphone.append("S");
            pos++;
        }

        // Loop until we have at least four metaphone characters or have
        // reached the end of the string.
        while (metaphone.length() < 4 && pos < length) {
            // Check the character at the current position against various targets.
            char posMinusFour;
            char posMinusThree;
            char posMinusTwo;
            char posMinusOne;
            char posPlusOne;
            char posPlusTwo;
            switch (valueString.charAt(pos)) {
            case 'A':
            case 'E':
            case 'I':
            case 'O':
            case 'U':
            case 'Y':
                // All initial vowels map to 'A'. All others will be ignored.
                if (pos == 0) {
                    metaphone.append("A");
                }

                pos++;
                break;

            case 'B':
                // B and BB will be mapped to P, with the exception of "MB" as
                // in "crumb", but that will be handled elsewhere.
                metaphone.append("P");

                if (valueString.charAt(++pos) == 'B') {
                    pos++;
                }

                break;

            case 'C':
                // Check for various Germanic sequences, which will be mapped to 'K'.
                // This basically includes all occurrences of "ACH" where
                // the preceding character is not a vowel and the following
                // character is neither an 'E' nor an 'I' except in "BACHER" and
                // "MACHER".
                if (pos > 1
                        && !isVowel(posMinusTwo = valueString.charAt(pos - 2))
                        && hasSubstring(valueString, pos - 1, "ACH")
                        && (posPlusTwo = valueString.charAt(pos + 2)) != 'I'
                        && (posPlusTwo != 'E'
                            || (valueString.charAt(pos + 3) == 'R'
                                && (posMinusTwo == 'B' || posMinusTwo == 'M')))) {
                    metaphone.append("K");
                    pos += 2;
                    break;
                }

                // Check for a special case of "caesar", which will be mapped to 'S'.
                if (pos == 0 && hasSubstring(valueString, pos + 1, "AESAR")) {
                    metaphone.append("S");
                    pos += 2;
                    break;
                }

                // CH can be treated in lots of different ways.
                posPlusOne = valueString.charAt(pos + 1);
                if (posPlusOne == 'H') {
                    // Check for "chia" as in "chianti" and map to 'K'.
                    if (hasSubstring(valueString, pos + 2, "IA")) {
                        metaphone.append("K");
                        pos += 2;
                        break;
                    }

                    // Check for "chae" as in "michael" and map to 'K'.
                    if (hasSubstring(valueString, pos + 2, "AE")) {
                        metaphone.append("K");
                        pos += 2;
                        break;
                    }

                    // Check for a Greek root at the beginning of the value like
                    // chemistry or chorus and map to 'K'.
                    if (pos == 0
                            && !hasSubstring(valueString, 2, "ORE")
                            && (hasSubstring(valueString, 2, "ARAC")
                                    || hasSubstring(valueString, 2, "ARIS")
                                    || hasSubstring(valueString, 2, "OR")
                                    || hasSubstring(valueString, 2, "YM")
                                    || hasSubstring(valueString, 2, "IA") || hasSubstring(
                                        valueString, 2, "EM"))) {
                        metaphone.append("K");
                        pos += 2;
                        break;
                    }

                    // Check for "CH" values that produce a "KH" sound that will
                    // be mapped to 'K'.
                    if (isGermanic(valueString)
                            || hasSubstring(valueString, pos - 2, "ORCHES")
                            || hasSubstring(valueString, pos - 2, "ARCHIT")
                            || hasSubstring(valueString, pos - 2, "ORCHID")
                            || (posPlusTwo = valueString.charAt(pos + 2)) == 'T'
                            || posPlusTwo == 'S'
                            || (pos == 0 || (posMinusOne = valueString.charAt(pos - 1)) == 'A'
                                    || posMinusOne == 'O' || posMinusOne == 'U' || posMinusOne == 'E')
                            && (posPlusTwo == 'L' || posPlusTwo == 'R' || posPlusTwo == 'N'
                                    || posPlusTwo == 'M' || posPlusTwo == 'B' || posPlusTwo == 'H'
                                    || posPlusTwo == 'F' || posPlusTwo == 'V' || posPlusTwo == 'W')) {
                        metaphone.append("K");
                        pos += 2;
                        break;
                    }

                    // All other "CH" values.
                    if (pos > 0) {
                        if (hasSubstring(valueString, 0, "MC")) {
                            metaphone.append("K");
                        } else {
                            metaphone.append("X");
                        }
                    } else {
                        metaphone.append("X");
                    }

                    pos += 2;
                    break;
                }

                // Check for "CZ" as in "czerny" but not "wicz" and map to 'S'.
                if (posPlusOne == 'Z' && !hasSubstring(valueString, pos - 2, "WI")) {
                    metaphone.append("S");
                    pos += 2;
                    break;
                }

                // Check for "CIA" as in "focaccia" and map to 'X'.
                if (posPlusOne == 'I' && valueString.charAt(pos + 2) == 'A') {
                    metaphone.append("X");
                    pos += 3;
                    break;
                }

                // Check for a double C but not in values that start with "McC"
                if (posPlusOne == 'C' && !(pos == 1 && valueString.charAt(0) == 'M')) {
                    posPlusTwo = valueString.charAt(pos + 2);
                    if ((posPlusTwo == 'I' || posPlusTwo == 'E' || posPlusTwo == 'H')
                            && !(posPlusTwo == 'H' && valueString.charAt(pos + 3) == 'U')) {
                        if ((pos == 1 && valueString.charAt(pos - 1) == 'A')
                                || hasSubstring(valueString, pos - 1, "UCCEE")
                                || hasSubstring(valueString, pos - 1, "UCCES")) {
                            // Values like "accident", "accede", and "succeed".
                            metaphone.append("K");
                            pos += 2;
                            break;
                        } else {
                            // Values like "bacci" or "bertucci".
                            metaphone.append("X");
                            pos += 3;
                            break;
                        }
                    } else {
                        // This is Pierce's Rule, whatever that means.
                        metaphone.append("K");
                        pos += 2;
                        break;
                    }
                }

                // Check for CK, CG, or CQ and map to 'K'. Check for CI, CE, and
                // CY and map to "S".
                posPlusOne = valueString.charAt(pos + 1);
                if (posPlusOne == 'K' || posPlusOne == 'G' || posPlusOne == 'Q') {
                    metaphone.append("K");
                    pos += 2;
                    break;
                }

                // Check for CI, CE, or CY and map to 'S'.
                if (posPlusOne == 'I' || posPlusOne == 'E' || posPlusOne == 'Y') {
                    metaphone.append("S");
                    pos += 2;
                    break;
                }

                // All other cases of "C" will be mapped to 'K'. However, the
                // number of positions that we skip ahead may vary. If there is
                // a value that consists of two words like "mac caffrey", then
                // skip ahead three. For the character combinations of "CK" and
                // "CQ", then skip ahead two. For the character combinations of
                // "CC" except "CCE" and "CCI", then skip ahead two. For all
                // other cases, skip ahead one.
                metaphone.append("K");
                switch (valueString.charAt(pos + 1)) {
                case ' ':
                    switch (valueString.charAt(pos + 2)) {
                    case 'C':
                    case 'Q':
                    case 'G':
                        pos += 3;
                        break;
                    default:
                        pos++;
                        break;
                    }
                    break;

                case 'K':
                case 'Q':
                    pos += 2;
                    break;

                case 'C':
                    switch (valueString.charAt(pos + 2)) {
                    case 'E':
                    case 'I':
                        pos++;
                        break;
                    default:
                        pos += 2;
                        break;
                    }
                    break;
                default:
                    pos++;
                }
                break;

            case 'D':
                // DG will be mapped to either 'J' (in cases like edge) or 'TK'
                // (in cases like Edgar).
                posPlusOne = valueString.charAt(pos + 1);
                if (posPlusOne == 'G') {
                    posPlusTwo = valueString.charAt(pos + 2);
                    if (posPlusTwo == 'I' || posPlusTwo == 'E' || posPlusTwo == 'Y') {
                        metaphone.append("J");
                        pos += 3;
                    } else {
                        metaphone.append("TK");
                        pos += 2;
                    }
                    break;
                }

                // DT and DD will be mapped to 'T'.
                if (posPlusOne == 'T' || posPlusOne == 'D') {
                    metaphone.append("T");
                    pos += 2;
                    break;
                }

                // All other cases will be mapped to 'T'.
                metaphone.append("T");
                pos++;
                break;

            case 'F':
                // F always maps to F. If there is a double F, then skip the second one.
                metaphone.append("F");
                pos++;
                if (valueString.charAt(pos) == 'F') {
                    pos++;
                }
                break;

            case 'G':
                posPlusOne = valueString.charAt(pos + 1);
                if (posPlusOne == 'H') {
                    // A "GH" that is not preceded by a vowel will be mapped to 'K'.
                    if (pos > 0 && !isVowel(valueString.charAt(pos - 1))) {
                        metaphone.append("K");
                        pos += 2;
                        break;
                    }

                    if (pos == 0) {
                        if (valueString.charAt(pos + 2) == 'I') {
                            // Words like ghislane or ghiradelli
                            metaphone.append("J");
                        } else {
                            metaphone.append("K");
                        }

                        pos += 2;
                        break;
                    }

                    // A refined version of Parker's Rule.
                    if (pos > 1
                            && ((posMinusTwo = valueString.charAt(pos - 2)) == 'B'
                                    || posMinusTwo == 'H' || posMinusTwo == 'D')
                            || pos > 2
                            && ((posMinusThree = valueString.charAt(pos - 3)) == 'B'
                                    || posMinusThree == 'H' || posMinusThree == 'D')
                            || pos > 3
                            && ((posMinusFour = valueString.charAt(pos - 4)) == 'B' || posMinusFour == 'H')) {
                        pos += 2;
                        break;
                    } else {
                        if (pos > 2
                                && valueString.charAt(pos - 1) == 'U'
                                && ((posMinusThree = valueString.charAt(pos - 3)) == 'C'
                                        || posMinusThree == 'G' || posMinusThree == 'L'
                                        || posMinusThree == 'R' || posMinusThree == 'T')) {
                            // Words like laugh, McLaughlin, cough, rough are mapped to 'F'.
                            metaphone.append("F");
                        } else if (pos > 0 && valueString.charAt(pos - 1) != 'I') {
                            metaphone.append("K");
                        }

                        pos += 2;
                        break;
                    }
                }

                if (posPlusOne == 'N') {
                    if (pos == 1 && isVowel(valueString.charAt(0)) && !isSlavoGermanic(valueString)) {
                        metaphone.append("KN");
                        pos += 2;
                        break;
                    } else {
                        if (!hasSubstring(valueString, pos + 2, "EY")
                                && !isSlavoGermanic(valueString)) {
                            metaphone.append("N");
                        } else {
                            metaphone.append("KN");
                        }

                        pos += 2;
                        break;
                    }
                }

                // GLI as in tagliaro will be mapped to "KL".
                if (posPlusOne == 'L' && valueString.charAt(pos + 2) == 'I') {
                    metaphone.append("KL");
                    pos += 2;
                    break;
                }

                // Forms of GY, GE, and GI at the beginning of a word will map to 'K'.
                if (pos == 0
                        && (posPlusOne == 'Y'
                                || (substring = valueString.substring(pos + 1, pos + 3)).equals("ES")
                                || substring.equals("EP")
                                || substring.equals("EB") || substring.equals("EL")
                                || substring.equals("EY") || substring.equals("IB")
                                || substring.equals("IL") || substring.equals("IN")
                                || substring.equals("IE") || substring.equals("EI")
                                || substring.equals("ER"))) {
                    metaphone.append("K");
                    pos += 2;
                    break;
                }

                // Some occurrences of GER and GY in a word will be mapped to 'K'.
                posPlusTwo = valueString.charAt(pos + 2);
                if (((posPlusOne == 'E' && posPlusTwo == 'R') || posPlusOne == 'Y')
                        && (posMinusOne = valueString.charAt(pos - 1)) != 'E' && posMinusOne != 'I'
                        && !hasSubstring(valueString, 0, "DANGER")
                        && !hasSubstring(valueString, 0, "RANGER")
                        && !hasSubstring(valueString, 0, "MANGER")
                        && !hasSubstring(valueString, pos - 1, "RGY")
                        && !hasSubstring(valueString, pos - 1, "OGY")) {
                    metaphone.append("K");
                    pos += 2;
                    break;
                }

                // Check for Italian uses like 'biaggi" and map to 'J'.
                if (posPlusOne == 'E' || posPlusOne == 'I' || posPlusOne == 'Y'
                        || hasSubstring(valueString, pos - 1, "AGGI")
                        || hasSubstring(valueString, pos - 1, "OGGI")) {
                    // Germanic uses will be mapped to 'K'.
                    if (isGermanic(valueString) || hasSubstring(valueString, pos + 1, "ET")) {
                        metaphone.append("K");
                    } else {
                        metaphone.append("J");
                    }

                    pos += 2;
                    break;
                }

                // All other cases will be mapped to 'K'. If there is a double
                // G, then skip two. Otherwise, just skip one.
                metaphone.append("K");
                pos++;

                if (posPlusOne == 'G') {
                    pos++;
                }

                break;

            case 'H':
                // The letter 'H' will only be processed if it is immediately
                // followed by a vowel and is either the start of the word or
                // preceded by a vowel.
                if (isVowel(valueString.charAt(pos + 1))
                        && (pos == 0 || isVowel(valueString.charAt(pos - 1)))) {
                    metaphone.append("H");
                    pos++;
                }

                pos++;
                break;

            case 'J':
                // Take care of obvious Spanish uses that should map to 'H'.
                if (hasSubstring(valueString, 0, "SAN ")) {
                    metaphone.append("H");
                    pos++;
                    break;
                }

                if (hasSubstring(valueString, pos, "JOSE")) {
                    if (pos == 0 && valueString.charAt(pos + 4) == ' ') {
                        metaphone.append("H");
                    } else {
                        metaphone.append("J");
                    }

                    pos++;
                    break;
                }

                // All other cases will be mapped to 'J'.
                metaphone.append("J");

                if (valueString.charAt(pos + 1) == 'J') {
                    pos++;
                }

                pos++;
                break;

            case 'K':
                // 'K' will always be mapped to 'K'. KK will be treated like K.
                metaphone.append("K");

                if (valueString.charAt(pos + 1) == 'K') {
                    pos++;
                }

                pos++;
                break;

            case 'L':
                // 'L' will always be mapped to 'L'. LL will be treated like L,
                // even for potential Spanish uses.
                metaphone.append("L");

                if (valueString.charAt(pos + 1) == 'L') {
                    pos++;
                }

                pos++;
                break;

            case 'M':
                // 'M' will always be mapped to 'M'. MM will be treated like M.
                // UMB in cases like "dumb" and "thumb" will be treated like M.
                metaphone.append("M");

                if (valueString.charAt(pos + 1) == 'M') {
                    pos++;
                } else if (hasSubstring(valueString, pos - 1, "UMB")
                        && (pos + 1 == last || hasSubstring(valueString, pos + 2, "ER"))) {
                    pos++;
                }

                pos++;
                break;

            case 'N':
                // 'N' will always be mapped to 'N'. NN will be treated like N.
                metaphone.append("N");

                if (valueString.charAt(pos + 1) == 'N') {
                    pos++;
                }

                pos++;
                break;

            case 'P':
                // PH will be mapped to 'F'.
                posPlusOne = valueString.charAt(pos + 1);
                if (posPlusOne == 'H') {
                    metaphone.append("F");
                    pos += 2;
                    break;
                }

                // All other cases will be mapped to 'P', with PP and PB being
                // treated like P.
                metaphone.append("P");

                if (posPlusOne == 'P' || posPlusOne == 'B') {
                    pos++;
                }

                pos++;
                break;

            case 'Q':
                // 'Q' will always be mapped to 'K'. QQ will be treated like Q.
                metaphone.append("K");

                if (valueString.charAt(pos + 1) == 'Q') {
                    pos++;
                }

                pos++;
                break;

            case 'R':
                // Ignore R at the end of French words.
                if (pos == last && !isSlavoGermanic(valueString)
                        && hasSubstring(valueString, pos - 2, "IE")
                        && !hasSubstring(valueString, pos - 4, "ME")
                        && !hasSubstring(valueString, pos - 4, "MA")) {
                    pos++;
                    break;
                }

                // All other cases will be mapped to 'R', with RR treated like R.
                metaphone.append("R");

                if (valueString.charAt(pos + 1) == 'R') {
                    pos++;
                }

                pos++;
                break;

            case 'S':
                // Special cases like isle and carlysle will be silent.
                if (hasSubstring(valueString, pos - 1, "ISL")
                        || hasSubstring(valueString, pos - 1, "YSL")) {
                    pos++;
                    break;
                }

                // Special case of sugar mapped to 'X'.
                if (hasSubstring(valueString, pos + 1, "UGAR")) {
                    metaphone.append("X");
                    pos++;
                    break;
                }

                // SH is generally mapped to 'X', but not in Germanic cases.
                posPlusOne = valueString.charAt(pos + 1);
                if (posPlusOne == 'H') {
                    if (hasSubstring(valueString, pos + 1, "HEIM")
                            || hasSubstring(valueString, pos + 1, "HOEK")
                            || hasSubstring(valueString, pos + 1, "HOLM")
                            || hasSubstring(valueString, pos + 1, "HOLZ")) {
                        metaphone.append("S");
                    } else {
                        metaphone.append("X");
                    }

                    pos += 2;
                    break;
                }

                // Italian and Armenian cases will map to "S".
                if (hasSubstring(valueString, pos + 1, "IO")
                        || hasSubstring(valueString, pos + 1, "IA")) {
                    metaphone.append("S");
                    pos += 3;
                    break;
                }

                // SZ should be mapped to 'S'.
                if (posPlusOne == 'Z') {
                    metaphone.append("S");
                    pos += 2;
                    break;
                }

                // Various combinations at the beginning of words will be mapped to 'S'.
                if (pos == 0
                        && (posPlusOne == 'M' || posPlusOne == 'N' || posPlusOne == 'L' || posPlusOne == 'W')) {
                    metaphone.append("S");
                    pos++;
                    break;
                }

                // SC should be mapped to either SK, X, or S.
                if (posPlusOne == 'C') {
                    posPlusTwo = valueString.charAt(pos + 2);
                    if (posPlusTwo == 'H') {
                        if (hasSubstring(valueString, pos + 3, "OO")
                                || hasSubstring(valueString, pos + 3, "UY")
                                || hasSubstring(valueString, pos + 3, "ED")
                                || hasSubstring(valueString, pos + 3, "EM")) {
                            metaphone.append("SK");
                        } else {
                            metaphone.append("X");
                        }

                        pos += 3;
                        break;
                    }

                    if (posPlusTwo == 'I' || posPlusTwo == 'E' || posPlusTwo == 'Y') {
                        metaphone.append("S");
                        pos += 3;
                        break;
                    }

                    metaphone.append("SK");
                    pos += 3;
                    break;
                }

                // Ignore a trailing S in French words. All others will be
                // mapped to 'S'.
                if (!(pos == last && (hasSubstring(valueString, pos - 2, "AI") || hasSubstring(
                        valueString, pos - 2, "OI")))) {
                    metaphone.append("S");
                }

                if (posPlusOne == 'S' || posPlusOne == 'Z') {
                    pos++;
                }

                pos++;
                break;

            case 'T':
                // "TION", "TIA", and "TCH" will be mapped to 'X'.
                if (hasSubstring(valueString, pos, "TION") || hasSubstring(valueString, pos, "TIA")
                        || hasSubstring(valueString, pos, "TCH")) {
                    metaphone.append("X");
                    pos += 3;
                    break;
                }

                // TH or TTH will be mapped to either T (for Germanic cases) or
                // 0 (zero) for the rest.
                posPlusOne = valueString.charAt(pos + 1);
                if (posPlusOne == 'H'
                        || (posPlusOne == 'T' && valueString.charAt(pos + 2) == 'H')) {
                    if (isGermanic(valueString) || hasSubstring(valueString, pos + 2, "OM")
                            || hasSubstring(valueString, pos + 2, "AM")) {
                        metaphone.append("T");
                    } else {
                        metaphone.append("0");
                    }

                    pos += 2;
                    break;
                }

                // All other cases will map to T, with TT and TD being treated like T.
                metaphone.append("T");

                if (posPlusOne == 'T' || posPlusOne == 'D') {
                    pos++;
                }

                pos++;
                break;

            case 'V':
                // 'V' will always be mapped to 'F', with VV treated like V.
                metaphone.append("F");

                if (valueString.charAt(pos + 1) == 'V') {
                    pos++;
                }

                pos++;
                break;

            case 'W':
                // WR should always map to R.
                posPlusOne = valueString.charAt(pos + 1);
                if (posPlusOne == 'R') {
                    metaphone.append("R");
                    pos += 2;
                    break;
                }

                // W[AEIOUYH] at the beginning of the word should be mapped to A.
                if (pos == 0 && (isVowel(posPlusOne) || posPlusOne == 'H')) {
                    metaphone.append("A");

                    // FIXME -- This isn't in the algorithm as written. Should it be?
                    pos += 2;
                    break;
                }

                // A Polish value like WICZ or WITZ should be mapped to TS.
                if (hasSubstring(valueString, pos + 1, "WICZ")
                        || hasSubstring(valueString, pos + 1, "WITZ")) {
                    metaphone.append("TS");
                    pos += 4;
                    break;
                }

                // Otherwise, we'll just skip it.
                pos++;
                break;

            case 'X':
                // X maps to KS except at the end of French words.
                if (!(pos == last && (hasSubstring(valueString, pos - 3, "IAU")
                        || hasSubstring(valueString, pos - 3, "EAU")
                        || hasSubstring(valueString, pos - 2, "AU") || hasSubstring(valueString,
                            pos - 2, "OU")))) {
                    metaphone.append("KS");
                }

                posPlusOne = valueString.charAt(pos + 1);
                if (posPlusOne == 'C' || posPlusOne == 'X') {
                    pos++;
                }

                pos++;
                break;

            case 'Z':
                // Chinese usages like zhao will map to J.
                posPlusOne = valueString.charAt(pos + 1);
                if (posPlusOne == 'H') {
                    metaphone.append("J");
                    pos += 2;
                    break;
                }

                // All other cases map to "S". ZZ will be treated like Z.
                metaphone.append("S");

                if (posPlusOne == 'Z') {
                    pos++;
                }

                pos++;
                break;

            case '\u00C7': // C with a cedilla
                // This will always be mapped to 'S'.
                metaphone.append("S");
                pos++;
                break;

            case '\u00D1': // N with a tilde
                // This will always be mapped to 'N'.
                metaphone.append("N");
                pos++;
                break;

            default:
                // We don't have any special treatment for this character, so
                // skip it.
                pos++;
                break;
            }
        }

        return ByteString.valueOfUtf8(metaphone);
    }

    /**
     * Indicates whether the provided value has the given substring at the
     * specified position.
     *
     * @param value
     *            The value containing the range for which to make the
     *            determination.
     * @param start
     *            The position in the value at which to start the comparison.
     * @param substring
     *            The substring to compare against the specified value range.
     * @return <CODE>true</CODE> if the specified portion of the value matches
     *         the given substring, or <CODE>false</CODE> if it does not.
     */
    private boolean hasSubstring(final String value, final int start, final String substring) {
        try {
            // This can happen since a lot of the rules "look behind" and
            // rightfully don't check if it's the first character
            if (start < 0) {
                return false;
            }

            final int end = start + substring.length();

            // value isn't big enough to do the comparison
            if (end > value.length()) {
                return false;
            }

            for (int i = 0, pos = start; pos < end; i++, pos++) {
                if (value.charAt(pos) != substring.charAt(i)) {
                    return false;
                }
            }

            return true;
        } catch (final Exception e) {
            logger.debug(LocalizableMessage.raw(
                "Unable to check that '%s' has substring '%s' at position %d: %s", value, substring, start, e));
            return false;
        }
    }

    /**
     * Indicates whether the provided string appears Germanic (starts with
     * "VAN ", "VON ", or "SCH").
     *
     * @param s
     *            The string for which to make the determination.
     * @return <CODE>true</CODE> if the provided string appears Germanic, or
     *         <CODE>false</CODE> if not.
     */
    private boolean isGermanic(final String s) {
        return s.startsWith("VAN ") || s.startsWith("VON ") || s.startsWith("SCH");
    }

    /**
     * Indicates whether the provided string appears to be Slavo-Germanic.
     *
     * @param s
     *            The string for which to make the determination.
     * @return <CODE>true</CODE> if the provided string appears to be
     *         Slavo-Germanic, or <CODE>false</CODE> if not.
     */
    private boolean isSlavoGermanic(final String s) {
        return s.contains("W") || s.contains("K") || s.contains("CZ") || s.contains("WITZ");
    }

    /**
     * Indicates whether the provided character is a vowel (including "Y").
     *
     * @param c
     *            The character for which to make the determination.
     * @return <CODE>true</CODE> if the provided character is a vowel, or
     *         <CODE>false</CODE> if not.
     */
    private boolean isVowel(final char c) {
        switch (c) {
        case 'A':
        case 'E':
        case 'I':
        case 'O':
        case 'U':
        case 'Y':
            return true;

        default:
            return false;
        }
    }

    @Override
    public String keyToHumanReadableString(ByteSequence key) {
        return key.toString();
    }
}
