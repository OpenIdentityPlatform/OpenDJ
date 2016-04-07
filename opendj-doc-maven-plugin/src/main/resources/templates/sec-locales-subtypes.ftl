<?xml version="1.0" encoding="UTF-8"?>
<!--
  The contents of this file are subject to the terms of the Common Development and
  Distribution License (the License). You may not use this file except in compliance with the
  License.

  You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
  specific language governing permission and limitations under the License.

  When distributing Covered Software, include this CDDL Header Notice in each file and include
  the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
  Header, with the fields enclosed by brackets [] replaced by your own identifying
  information: "Portions Copyright [year] [name of copyright owner]".

  Copyright 2011-${year} ForgeRock AS.
-->
<section xml:id="sec-locales-subtypes"
         xmlns="http://docbook.org/ns/docbook" version="5.0" xml:lang="${lang}"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://docbook.org/ns/docbook
                             http://docbook.org/xml/5.0/xsd/docbook.xsd">

 <title>${title}</title>

 <para>
  ${info}
 </para>

 <variablelist xml:id="supported-locales">
  <title>${locales.title}</title>
  <indexterm><primary>${locales.indexTerm}</primary></indexterm>

  <#list locales.locales as locale>
  <varlistentry>
   <term>${locale.language}</term>
   <listitem>
    <para>
     ${locale.tag}
    </para>

    <para>
     ${locale.oid}
    </para>
   </listitem>
  </varlistentry>
  </#list>

 </variablelist>

 <itemizedlist xml:id="supported-language-subtypes">
  <title>${subtypes.title}</title>
  <indexterm><primary>${subtypes.indexTerm}</primary></indexterm>

  <#list subtypes.locales?sort_by("language") as subtype>
  <listitem>
   <para>${subtype.language}, ${subtype.tag}</para>
  </listitem>
  </#list>

 </itemizedlist>

</section>
