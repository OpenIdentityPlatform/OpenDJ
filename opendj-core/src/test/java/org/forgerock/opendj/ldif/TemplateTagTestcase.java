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
 *      Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldif;

import static org.fest.assertions.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.TemplateFile.Template;
import org.forgerock.opendj.ldif.TemplateFile.TemplateEntry;
import org.forgerock.opendj.ldif.TemplateFile.TemplateLine;
import org.forgerock.opendj.ldif.TemplateFile.TemplateValue;
import org.forgerock.opendj.ldif.TemplateTag.AttributeValueTag;
import org.forgerock.opendj.ldif.TemplateTag.IfAbsentTag;
import org.forgerock.opendj.ldif.TemplateTag.IfPresentTag;
import org.forgerock.opendj.ldif.TemplateTag.TagResult;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TemplateTagTestcase extends SdkTestCase {

    private static final int LINE_NUMBER = 10;
    private static final TemplateFile NULL_TEMPLATE_FILE = null;
    private static final List<LocalizableMessage> NULL_WARNINGS = null;
    private static final TemplateValue NULL_VALUE = null;
    private static final TemplateLine NULL_LINE = null;

    @Test
    public void testIfAbsentTag() throws Exception {
        TemplateTag tag = new IfAbsentTag();
        String org = "org";
        tagWithArguments(tag, "dc", org); // dc=org should be absent

        // org value is absent
        assertThat(tag.generateValue(templateEntry("v1", "v2"), NULL_VALUE)).isEqualTo(TagResult.SUCCESS);

        // org value is present
        assertThat(tag.generateValue(templateEntry(org, "v"), NULL_VALUE)).isEqualTo(TagResult.FAILURE);
        assertThat(tag.generateValue(templateEntry("v", org), NULL_VALUE)).isEqualTo(TagResult.FAILURE);
        assertThat(tag.generateValue(templateEntry(org), NULL_VALUE)).isEqualTo(TagResult.FAILURE);
    }

    @Test
    public void testIfAbsentTagWithNoValue() throws Exception {
        TemplateTag tag = new IfAbsentTag();
        tagWithArguments(tag, "dc"); // dc should be absent

        assertThat(tag.generateValue(templateEntry("v"), NULL_VALUE)).isEqualTo(TagResult.FAILURE);
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*Invalid number of arguments.*")
    public void testIfAbsentTagTooManyArguments() throws Exception {
        tagWithArguments(new IfAbsentTag(), "dc", "org1", "org2"); // too many args
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*Invalid number of arguments.*")
    public void testIfAbsentTagNotEnoughArguments() throws Exception {
        tagWithArguments(new IfAbsentTag()); // zero args
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*Undefined attribute.*")
    public void testIfAbsentTagNoAttribute() throws Exception {
        tagWithArguments(new IfAbsentTag(), templateWithNoAttribute(), "dc");
    }

    @Test
    public void testDNTagRootDN() throws Exception {
        TemplateTag tag = new TemplateTag.DNTag();
        tagWithArguments(tag);

        TemplateValue value = new TemplateValue(NULL_LINE);
        tag.generateValue(templateEntry(DN.rootDN()), value);

        assertThat(value.getValueAsString()).isEqualTo("");
    }

    @Test
    public void testDNTagZeroComponent() throws Exception {
        TemplateTag tag = new TemplateTag.DNTag();
        tagWithArguments(tag);

        TemplateValue value = new TemplateValue(NULL_LINE);
        tag.generateValue(templateEntry(DN.valueOf("ou=users,dc=example,dc=test")), value);

        assertThat(value.getValueAsString()).isEqualTo("ou=users,dc=example,dc=test");
    }

    @Test
    public void testUnderscoreDNTagZeroComponent() throws Exception {
        TemplateTag tag = new TemplateTag.UnderscoreDNTag();
        tagWithArguments(tag);

        TemplateValue value = new TemplateValue(NULL_LINE);
        tag.generateValue(templateEntry(DN.valueOf("ou=users,dc=example,dc=test")), value);

        assertThat(value.getValueAsString()).isEqualTo("ou=users_dc=example_dc=test");
    }

    @Test
    public void testDNTagOneComponent() throws Exception {
        TemplateTag tag = new TemplateTag.DNTag();
        tagWithArguments(tag, "1");

        TemplateValue value = new TemplateValue(NULL_LINE);
        tag.generateValue(templateEntry(DN.valueOf("ou=users,dc=example,dc=test")), value);

        assertThat(value.getValueAsString()).isEqualTo("ou=users");
    }

    @Test
    public void testDNTagTwoComponent() throws Exception {
        TemplateTag tag = new TemplateTag.DNTag();
        tagWithArguments(tag, "2");

        TemplateValue value = new TemplateValue(NULL_LINE);
        tag.generateValue(templateEntry(DN.valueOf("ou=users,dc=example,dc=test")), value);

        assertThat(value.getValueAsString()).isEqualTo("ou=users,dc=example");
    }

    @Test
    public void testDNTagMinusOneComponent() throws Exception {
        TemplateTag tag = new TemplateTag.DNTag();
        tagWithArguments(tag, "-1");

        TemplateValue value = new TemplateValue(NULL_LINE);
        tag.generateValue(templateEntry(DN.valueOf("ou=users,dc=example,dc=test")), value);

        assertThat(value.getValueAsString()).isEqualTo("dc=test");
    }

    @Test
    public void testDNTagMinusTwoComponents() throws Exception {
        TemplateTag tag = new TemplateTag.DNTag();
        tagWithArguments(tag, "-2");

        TemplateValue value = new TemplateValue(NULL_LINE);
        tag.generateValue(templateEntry(DN.valueOf("ou=users,dc=example,dc=test")), value);

        assertThat(value.getValueAsString()).isEqualTo("dc=example,dc=test");
    }

    @Test
    public void testIfPresentTag() throws Exception {
        TemplateTag tag = new TemplateTag.IfPresentTag();
        String org = "org";
        tagWithArguments(tag, "dc", org); // dc=org should be present

        // org value is absent
        assertThat(tag.generateValue(templateEntry("v1", "v2"), NULL_VALUE)).isEqualTo(TagResult.FAILURE);

        // org value is present
        assertThat(tag.generateValue(templateEntry(org, "v"), NULL_VALUE)).isEqualTo(TagResult.SUCCESS);
        assertThat(tag.generateValue(templateEntry("v", org), NULL_VALUE)).isEqualTo(TagResult.SUCCESS);
        assertThat(tag.generateValue(templateEntry(org), NULL_VALUE)).isEqualTo(TagResult.SUCCESS);
    }

    @Test
    public void testIfPresentTagWithNoValue() throws Exception {
        TemplateTag tag = new TemplateTag.IfPresentTag();
        tagWithArguments(tag, "dc"); // dc=org should be present

        assertThat(tag.generateValue(templateEntry("org"), NULL_VALUE)).isEqualTo(TagResult.SUCCESS);
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*Invalid number of arguments.*")
    public void testIfPresentTagTooManyArguments() throws Exception {
        tagWithArguments(new IfPresentTag(), "1", "2", "3"); // too many args
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*Invalid number of arguments.*")
    public void testIfPresentTagNotEnoughArguments() throws Exception {
        tagWithArguments(new IfPresentTag()); // zero args
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*Undefined attribute.*")
    public void testIfPresentTagNoAttribute() throws Exception {
        tagWithArguments(new IfPresentTag(), templateWithNoAttribute(), "dc");
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*Invalid number of arguments.*")
    public void testAttributeValueTagTooManyArguments() throws Exception {
        tagWithArguments(new AttributeValueTag(), "dc", "2", "3"); // too many args
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*below the lowest allowed value.*")
    public void testAttributeValueTagBelowLowerBound() throws Exception {
        tagWithArguments(new AttributeValueTag(), "dc", "-1");
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*Cannot parse value.*")
    public void testAttributeValueTagNotANumber() throws Exception {
        tagWithArguments(new AttributeValueTag(), "dc", "notanumber");
    }

    @Test(expectedExceptions = DecodeException.class,
            expectedExceptionsMessageRegExp = ".*Undefined attribute.*")
    public void testAttributeValueTagNoAttribute() throws Exception {
        tagWithArguments(new AttributeValueTag(), templateWithNoAttribute(), "dc");
    }

    /** Helper method to initialize tags with template having any attribute and some arguments. */
    private void tagWithArguments(TemplateTag tag, String... arguments) throws DecodeException {
        tagWithArguments(tag, templateWithAnyAttribute(), arguments);
    }

    /** Helper method to initialize tags with template and some arguments. */
    private void tagWithArguments(TemplateTag tag, Template template, String... arguments)
            throws DecodeException {
        tag.initializeForTemplate(Schema.getDefaultSchema(), NULL_TEMPLATE_FILE, template,
                arguments, LINE_NUMBER, NULL_WARNINGS);
    }

    /** Helper method to build a template entry containing the provided values. */
    private TemplateEntry templateEntry(String... values) {
        TemplateEntry templateEntry = mock(TemplateEntry.class);
        List<TemplateValue> templateValues = new ArrayList<>();
        for (String value : values) {
            templateValues.add(templateValue(value));
        }
        when(templateEntry.getValues(any(AttributeType.class))).thenReturn(templateValues);
        return templateEntry;
    }

    /** Helper method to build a template entry with the provided DN. */
    private TemplateEntry templateEntry(DN dn) {
        TemplateEntry templateEntry = mock(TemplateEntry.class);
        when(templateEntry.getDN()).thenReturn(dn);
        return templateEntry;
    }

    /** Helper method to build a template value from provided string. */
    private TemplateValue templateValue(String value) {
        TemplateValue templateVal = new TemplateFile.TemplateValue(null);
        templateVal.append(value);
        return templateVal;
    }

    /** Helper method to build a template that always return true on attribute type check. */
    private Template templateWithAnyAttribute() {
        Template template = mock(Template.class);
        when(template.hasAttribute(any(AttributeType.class))).thenReturn(true);
        return template;
    }

    /** Helper method to build a template that always return false on attribute type check. */
    private Template templateWithNoAttribute() {
        Template template = mock(Template.class);
        when(template.hasAttribute(any(AttributeType.class))).thenReturn(false);
        return template;
    }
}
