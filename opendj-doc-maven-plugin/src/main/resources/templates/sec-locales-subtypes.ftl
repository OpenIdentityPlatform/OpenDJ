<?xml version="1.0" encoding="UTF-8"?>
<!--
  ! CDDL HEADER START
  !
  ! The contents of this file are subject to the terms of the
  ! Common Development and Distribution License, Version 1.0 only
  ! (the "License").  You may not use this file except in compliance
  ! with the License.
  !
  ! You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
  ! or http://forgerock.org/license/CDDLv1.0.html.
  ! See the License for the specific language governing permissions
  ! and limitations under the License.
  !
  ! When distributing Covered Code, include this CDDL HEADER in each
  ! file and include the License file at legal-notices/CDDLv1_0.txt.
  ! If applicable, add the following below this CDDL HEADER, with the
  ! fields enclosed by brackets "[]" replaced with your own identifying
  ! information:
  !      Portions Copyright [yyyy] [name of copyright owner]
  !
  ! CDDL HEADER END
  !
  !      Copyright 2011-${year} ForgeRock AS.
  !
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
