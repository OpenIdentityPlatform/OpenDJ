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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

/**
 * This package contains LDAP schema syntaxes and matching rules for JSON based attributes.
 * <p>
 * There are two syntaxes, 'Json' and 'Json Query'.
 * <pre>
 * ( 1.3.6.1.4.1.36733.2.1.3.1 DESC 'Json' )
 * ( 1.3.6.1.4.1.36733.2.1.3.2 DESC 'Json Query' )
 * </pre>
 * The first of these, {@link org.forgerock.opendj.rest2ldap.schema.JsonSchema#getJsonSyntax() Json}, is an attribute
 * syntax whose values must conform to the JSON syntax as defined in RFC 7159. The schema option {@link
 * org.forgerock.opendj.rest2ldap.schema.JsonSchema#VALIDATION_POLICY} allows applications to relax the syntax
 * enforcement. For example, to allow single quotes and comments set the following schema option:
 * <pre>
 * SchemaBuilder builder = ...;
 * builder.setOption(JsonSchema.VALIDATION_POLICY, LENIENT);
 * </pre>
 * The second syntax, {@link org.forgerock.opendj.rest2ldap.schema.JsonSchema#getJsonQuerySyntax() Json Query}, is an
 * attribute syntax whose values are {@link org.forgerock.util.query.QueryFilterParser CREST query filters}. This syntax
 * is also the assertion syntax used by the
 * {@link org.forgerock.opendj.rest2ldap.schema.JsonSchema#getCaseIgnoreJsonQueryMatchingRule()
 * caseIgnoreJsonQueryMatch} and
 * {@link org.forgerock.opendj.rest2ldap.schema.JsonSchema#getCaseExactJsonQueryMatchingRule() caseExactJsonQueryMatch}
 * matching rules:
 * <pre>
 * ( 1.3.6.1.4.1.36733.2.1.4.1 NAME 'caseIgnoreJsonQueryMatch' SYNTAX 1.3.6.1.4.1.36733.2.1.3.2 )
 * ( 1.3.6.1.4.1.36733.2.1.4.2 NAME 'caseExactJsonQueryMatch' SYNTAX 1.3.6.1.4.1.36733.2.1.3.2 )
 * </pre>
 * These syntaxes and matching rules are included by default with the OpenDJ server, but may be added to application
 * code as follows:
 * <pre>
 * SchemaBuilder builder = ...;
 * JsonSchema.addJsonSyntaxesAndMatchingRulesToSchema(schemaBuilder);
 * </pre>
 * <p>
 * <b>Trying it out against OpenDJ server</b>
 * <p>
 * After install OpenDJ server add the following schema definition to config/schema/99-user.ldif:
 * <pre>
 * dn: cn=schema
 * objectClass: top
 * objectClass: ldapSubentry
 * objectClass: subschema
 * attributeTypes: ( 1.3.6.1.4.1.36733.2.1.1.999 NAME 'json'
 *   SYNTAX 1.3.6.1.4.1.36733.2.1.3.1 EQUALITY caseIgnoreJsonQueryMatch SINGLE-VALUE )
 * objectClasses: (1.3.6.1.4.1.36733.2.1.2.999 NAME 'jsonObject' SUP top
 *   MUST (cn $ json ) )
 * </pre>
 * Start the server and then add the following entries:
 * <pre>
 * <b>path/to/opendj$ ./bin/ldapmodify -a -h localhost -p 1389 -D cn=directory\ manager -w password</b>
 * dn: cn=bjensen,ou=people,dc=example,dc=com
 * objectClass: top
 * objectClass: jsonObject
 * cn: bjensen
 * json: { "_id":"bjensen", "_rev":"123", "name": { "first": "Babs", "surname": "Jensen" }, "age": 65, "roles": [
 *   "sales", "admin" ] }
 *
 * dn: cn=scarter,ou=people,dc=example,dc=com
 * objectClass: top
 * objectClass: jsonObject
 * cn: scarter
 * json: { "_id":"scarter", "_rev":"456", "name": { "first": "Sam", "surname": "Carter" }, "age": 48, "roles": [
 *   "manager", "eng" ] }
 * </pre>
 * A finally perform some searches:
 * <pre>
 * <b>path/to/opendj$ ./bin/ldapsearch -h localhost -p 1389 -D cn=directory\ manager -w password \
 *   -b ou=people,dc=example,dc=com "(json=age lt 60 and name/first sw 's')"</b>
 * dn: cn=scarter,ou=people,dc=example,dc=com
 * objectClass: jsonObject
 * objectClass: top
 * cn: scarter
 * json: { "_id":"scarter", "_rev":"456", "name": { "first": "Sam", "surname": "Car
 *   ter" }, "age": 48, "roles": [ "manager", "eng" ] }
 * </pre>
 * The JSON query matching rules support indexing which can be enabled using dsconfig against the appropriate
 * attribute index.
 */
package org.forgerock.opendj.rest2ldap.schema;
