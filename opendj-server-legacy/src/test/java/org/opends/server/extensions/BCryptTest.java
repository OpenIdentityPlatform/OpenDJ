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
 * Copyright (c) 2006 Damien Miller <djm@mindrot.org>
 * Portions Copyright 2016 ForgeRock AS.
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 */

package org.opends.server.extensions;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class BCryptTest extends ExtensionsTestCase {

    /**
     * Retrieves a set of passwords, salt and expected hashed passwords for tests.
     */
    @DataProvider(name = "PasswordsAndHash")
    public Object[][] getPasswordsAndHash() {
        return new Object[][]{
                { "", "$2a$06$DCq7YPn5Rq63x1Lad4cll.",
                        "$2a$06$DCq7YPn5Rq63x1Lad4cll.TV4S6ytwfsfvkgY8jIucDrjc8deX1s." },
                { "", "$2a$08$HqWuK6/Ng6sg9gQzbLrgb.",
                        "$2a$08$HqWuK6/Ng6sg9gQzbLrgb.Tl.ZHfXLhvt/SgVyWhQqgqcZ7ZuUtye" },
                { "", "$2a$10$k1wbIrmNyFAPwPVPSVa/ze",
                        "$2a$10$k1wbIrmNyFAPwPVPSVa/zecw2BCEnBwVS2GbrmgzxFUOqW9dk4TCW" },
                { "", "$2a$12$k42ZFHFWqBp3vWli.nIn8u",
                        "$2a$12$k42ZFHFWqBp3vWli.nIn8uYyIkbvYRvodzbfbK18SSsY.CsIQPlxO" },
                { "a", "$2a$06$m0CrhHm10qJ3lXRY.5zDGO",
                        "$2a$06$m0CrhHm10qJ3lXRY.5zDGO3rS2KdeeWLuGmsfGlMfOxih58VYVfxe" },
                { "a", "$2a$08$cfcvVd2aQ8CMvoMpP2EBfe",
                        "$2a$08$cfcvVd2aQ8CMvoMpP2EBfeodLEkkFJ9umNEfPD18.hUF62qqlC/V." },
                { "a", "$2a$10$k87L/MF28Q673VKh8/cPi.",
                        "$2a$10$k87L/MF28Q673VKh8/cPi.SUl7MU/rWuSiIDDFayrKk/1tBsSQu4u" },
                { "a", "$2a$12$8NJH3LsPrANStV6XtBakCe",
                        "$2a$12$8NJH3LsPrANStV6XtBakCez0cKHXVxmvxIlcz785vxAIZrihHZpeS" },
                { "abc", "$2a$06$If6bvum7DFjUnE9p2uDeDu",
                        "$2a$06$If6bvum7DFjUnE9p2uDeDu0YHzrHM6tf.iqN8.yx.jNN1ILEf7h0i" },
                { "abc", "$2a$08$Ro0CUfOqk6cXEKf3dyaM7O",
                        "$2a$08$Ro0CUfOqk6cXEKf3dyaM7OhSCvnwM9s4wIX9JeLapehKK5YdLxKcm" },
                { "abc", "$2a$10$WvvTPHKwdBJ3uk0Z37EMR.",
                        "$2a$10$WvvTPHKwdBJ3uk0Z37EMR.hLA2W6N9AEBhEgrAOljy2Ae5MtaSIUi" },
                { "abc", "$2a$12$EXRkfkdmXn2gzds2SSitu.",
                        "$2a$12$EXRkfkdmXn2gzds2SSitu.MW9.gAVqa9eLS1//RYtYCmB1eLHg.9q" },
                { "abcdefghijklmnopqrstuvwxyz", "$2a$06$.rCVZVOThsIa97pEDOxvGu",
                        "$2a$06$.rCVZVOThsIa97pEDOxvGuRRgzG64bvtJ0938xuqzv18d3ZpQhstC" },
                { "abcdefghijklmnopqrstuvwxyz", "$2a$08$aTsUwsyowQuzRrDqFflhge",
                        "$2a$08$aTsUwsyowQuzRrDqFflhgekJ8d9/7Z3GV3UcgvzQW3J5zMyrTvlz." },
                { "abcdefghijklmnopqrstuvwxyz", "$2a$10$fVH8e28OQRj9tqiDXs1e1u",
                        "$2a$10$fVH8e28OQRj9tqiDXs1e1uxpsjN0c7II7YPKXua2NAKYvM6iQk7dq" },
                { "abcdefghijklmnopqrstuvwxyz", "$2a$12$D4G5f18o7aMMfwasBL7Gpu",
                        "$2a$12$D4G5f18o7aMMfwasBL7GpuQWuP3pkrZrOAnqP.bmezbMng.QwJ/pG" },
                { "~!@#$%^&*()      ~!@#$%^&*()PNBFRD", "$2a$06$fPIsBO8qRqkjj273rfaOI.",
                        "$2a$06$fPIsBO8qRqkjj273rfaOI.HtSV9jLDpTbZn782DC6/t7qT67P6FfO" },
                { "~!@#$%^&*()      ~!@#$%^&*()PNBFRD", "$2a$08$Eq2r4G/76Wv39MzSX262hu",
                        "$2a$08$Eq2r4G/76Wv39MzSX262huzPz612MZiYHVUJe/OcOql2jo4.9UxTW" },
                { "~!@#$%^&*()      ~!@#$%^&*()PNBFRD", "$2a$10$LgfYWkbzEvQ4JakH7rOvHe",
                        "$2a$10$LgfYWkbzEvQ4JakH7rOvHe0y8pHKF9OaFgwUZ2q7W2FFZmZzJYlfS" },
                { "~!@#$%^&*()      ~!@#$%^&*()PNBFRD", "$2a$12$WApznUOJfkEGSmYRfnkrPO",
                        "$2a$12$WApznUOJfkEGSmYRfnkrPOr466oFDCaj4b6HY3EXGvfxm43seyhgC" }
        };
    }

    /**
     * Retrieves a set of passwords for tests.
     */
    @DataProvider(name = "Passwords")
    public Object[][] getPasswords() {
        return new Object[][]{
                {""},
                {"a"},
                {"abc"},
                {"abcdefghijklmnopqrstuvwxyz"},
                {"~!@#$%^&*()      ~!@#$%^&*()PNBFRD"}
        };
    }

    /**
     * Retrieves a pair of password and hashed passwords that don't match
     */
    @DataProvider(name = "BadPasswords")
    public Object[][] getBadPasswords() {
        return new Object[][]{
                { "", "$2a$06$m0CrhHm10qJ3lXRY.5zDGO3rS2KdeeWLuGmsfGlMfOxih58VYVfxe" },
                { "a", "$2a$06$If6bvum7DFjUnE9p2uDeDu0YHzrHM6tf.iqN8.yx.jNN1ILEf7h0i" },
                { "abc", "$2a$06$.rCVZVOThsIa97pEDOxvGuRRgzG64bvtJ0938xuqzv18d3ZpQhstC" },
                { "abcdefghijklmnopqrstuvwxyz", "$2a$06$fPIsBO8qRqkjj273rfaOI.HtSV9jLDpTbZn782DC6/t7qT67P6FfO" },
                { "~!@#$%^&*()      ~!@#$%^&*()PNBFRD", "$2a$06$DCq7YPn5Rq63x1Lad4cll.TV4S6ytwfsfvkgY8jIucDrjc8deX1s." }
        };
    }


    /**
     * Test for BCrypt.hashpw(String, String).
     *
     * @param plain The plain text password.
     * @param salt  The salt to use when hashing the password.
     * @param expected The expected hashed password.
     *
     */
    @Test(dataProvider = "PasswordsAndHash")
    public void hashpw(String plain, String salt, String expected) {
        String hashed = BCrypt.hashpw(plain, salt);
        assertEquals(hashed, expected);
    }

    /**
     * Test for the BCrypt.hashpw(byte[], String) method.
     *
     * @param plain The plain text password.
     * @param salt  The salt to use when hashing the password.
     * @param expected The expected hashed password.
     *
     */
    @Test(dataProvider = "PasswordsAndHash")
    public void hashpwBytes(String plain, String salt, String expected) {
        String hashed = BCrypt.hashpw(plain.getBytes(), salt);
        assertEquals(hashed, expected);
    }

    /**
     * Test for the BCrypt.genSalt(int) method, with a cost varying from 4 to 12.
     *
     * @param plain The plain text password.
     *
     */
    @Test(dataProvider = "Passwords")
    public void genSaltInt(String plain) {
        for (int i = 4; i <= 12; i++){
            String salt = BCrypt.gensalt(i);
            String hashed1 = BCrypt.hashpw(plain, salt);
            String hashed2 = BCrypt.hashpw(plain, hashed1);
            assertEquals(hashed1, hashed2);
        }
    }

    /**
     * Tests the BCrypt.genSalt method with default cost
     *
     * @param plain The plain text password.
     *
     */
    @Test(dataProvider = "Passwords")
    public void genSalt(String plain) {
        String salt = BCrypt.gensalt();
        String hashed1 = BCrypt.hashpw(plain, salt);
        String hashed2 = BCrypt.hashpw(plain, hashed1);
        assertEquals(hashed1, hashed2);
    }

    /**
     * Test for the BCrypt.checkpw(String, String) method.
     *
     * @param plain The plain text password.
     * @param salt  The salt to use when hashing the password.
     * @param expected The expected hashed password.
     *
     */
    @Test(dataProvider = "PasswordsAndHash")
    public void checkPw(String plain, String salt, String expected) {
        assertTrue(BCrypt.checkpw(plain, expected));
    }

    /**
     * Test for the BCrypt.checkpw(String, String) method, expecting failures.
     *
     * @param plain The plain text password.
     * @param hashedValue A hashed password that doesn't match the plain text password.
     *
     */
    @Test(dataProvider = "BadPasswords")
    public void checkPw_Failure(String plain, String hashedValue) {
        assertFalse(BCrypt.checkpw(plain, hashedValue));
    }

    /**
     *     Test for correct hashing of non-US-ASCII passwords.
     */
    @Test
    public void testInternationalChars() {
        String pw1 = "\u2605\u2605\u2605\u2605\u2605\u2605\u2605\u2605";
        String pw2 = "????????";

        String h1 = BCrypt.hashpw(pw1, BCrypt.gensalt());
        assertFalse(BCrypt.checkpw(pw2, h1));

        String h2 = BCrypt.hashpw(pw2, BCrypt.gensalt());
        assertFalse(BCrypt.checkpw(pw1, h2));
    }
}
