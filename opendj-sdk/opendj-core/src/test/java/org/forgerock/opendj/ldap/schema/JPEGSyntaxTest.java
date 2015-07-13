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
 *      Portions copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.Schema.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.testng.Assert.*;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteString;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * JPEG syntax tests.
 */
@SuppressWarnings("javadoc")
@Test
public class JPEGSyntaxTest extends AbstractSchemaTestCase {

    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        String nonImage =
            "AAECAwQFBgcICQ==";
        String jfifImage =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDACMYGh4aFiMeHB4nJSMpNFc4NDAwNGpM"
            + "UD9Xfm+EgnxveneLnMipi5S9lnd6ru2wvc7V4OLgh6f1//PZ/8jb4Nf/2wBDASUn"
            + "JzQuNGY4OGbXj3qP19fX19fX19fX19fX19fX19fX19fX19fX19fX19fX19fX19fX"
            + "19fX19fX19fX19fX19f/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEA"
            + "AAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIh"
            + "MUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6"
            + "Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZ"
            + "mqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx"
            + "8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREA"
            + "AgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAV"
            + "YnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hp"
            + "anN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPE"
            + "xcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwCC"
            + "iiiuM9g//9k=";
        String exifImage =
            "/9j/4QD2RXhpZgAATU0AKgAAAAgACQESAAMAAAABAAEAAAEaAAUAAAABAAAAegEb"
            + "AAUAAAABAAAAggEoAAMAAAABAAIAAAExAAIAAAAQAAAAigEyAAIAAAAUAAAAmgE8"
            + "AAIAAAAOAAAArgITAAMAAAABAAEAAIdpAAQAAAABAAAAvAAAAAAASAAAAAEAAABI"
            + "AAAAAQAAUXVpY2tUaW1lIDcuNy4xADIwMTI6MDg6MjAgMTI6MTE6MTIATWFjIE9T"
            + "IFggMTAuOAAAApAAAAcAAAAEMDIyMJADAAIAAAAUAAAA2gAAAAAyMDEyOjA4OjIw"
            + "IDEyOjEwOjA2AP/+AAxBcHBsZU1hcmsK/9sAQwAjGBoeGhYjHhweJyUjKTRXODQw"
            + "MDRqTFA/V35vhIJ8b3p3i5zIqYuUvZZ3eq7tsL3O1eDi4Ien9f/z2f/I2+DX/9sA"
            + "QwElJyc0LjRmODhm1496j9fX19fX19fX19fX19fX19fX19fX19fX19fX19fX19fX"
            + "19fX19fX19fX19fX19fX19fX/8AAEQgAAQABAwEiAAIRAQMRAf/EAB8AAAEFAQEB"
            + "AQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAE"
            + "EQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2"
            + "Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SV"
            + "lpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn"
            + "6Onq8fLz9PX29/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//E"
            + "ALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXETIjKBCBRCkaGxwQkj"
            + "M1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2Rl"
            + "ZmdoaWpzdHV2d3h5eoKDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5"
            + "usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/aAAwDAQACEQMR"
            + "AD8AgooorjPYP//Z";
        String pngImage =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAMAAAAoyzS7AAAABlBMVEX/JgAAAAAP"
            + "IsinAAAACklEQVQImWNgAAAAAgAB9HFkpgAAAABJRU5ErkJggg==";

        return new Object[][] {
            { ByteString.valueOfBase64(nonImage), false },
            { ByteString.valueOfBase64(jfifImage), true },
            { ByteString.valueOfBase64(exifImage), true },
            { ByteString.valueOfBase64(pngImage), false }
        };
    }

    /** Test acceptable values for this syntax when not allowing malformed JPEG photos. */
    @Test(dataProvider = "acceptableValues")
    public void testAcceptableValues(ByteString value, boolean expectedResult) {
        final Syntax syntax = getSyntax(false);
        final LocalizableMessageBuilder reason = new LocalizableMessageBuilder();

        final boolean result = syntax.valueIsAcceptable(value, reason);
        assertEquals(result, expectedResult,
                syntax + ".valueIsAcceptable gave bad result for " + value + "reason : " + reason);

    }

    /** Test acceptable values for this syntax when allowing malformed JPEG photos. */
    @Test(dataProvider = "acceptableValues")
    public void testAcceptableValuesWhenAllowingMalformedJPEG(ByteString value, boolean notUsedForThisTest) {
        final Syntax syntax = getSyntax(true);
        final LocalizableMessageBuilder reason = new LocalizableMessageBuilder();

        final boolean liveResult = syntax.valueIsAcceptable(value, reason);
        // should always be true
        assertTrue(liveResult, syntax + ".valueIsAcceptable gave bad result for " + value + "reason : " + reason);
    }

    private Syntax getSyntax(boolean allowMalformedJpegPhotos) {
        SchemaBuilder builder =
            new SchemaBuilder(getCoreSchema()).setOption(ALLOW_MALFORMED_JPEG_PHOTOS, allowMalformedJpegPhotos);
        return builder.toSchema().getSyntax(SYNTAX_JPEG_OID);
    }

}
