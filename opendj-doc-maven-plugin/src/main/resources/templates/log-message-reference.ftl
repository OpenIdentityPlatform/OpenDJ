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
  !      Copyright 2012-${year} ForgeRock AS.
  !
-->
<appendix xml:id="appendix-log-messages"
          xmlns="http://docbook.org/ns/docbook" version="5.0" xml:lang="${lang}"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://docbook.org/ns/docbook
                              http://docbook.org/xml/5.0/xsd/docbook.xsd"
          xmlns:xlink="http://www.w3.org/1999/xlink"
          xmlns:xinclude="http://www.w3.org/2001/XInclude">

 <title>${title}</title>

 <indexterm>
  <primary>${indexterm}</primary>
 </indexterm>

 <para>
  ${intro}
 </para>

 <#list categories as section>
 <section xml:id="${section.id}">
  <title>${section.category}</title>

  <variablelist>
  <#list section.entries as entry>
   <varlistentry xml:id="log-ref-${entry.xmlId}">
    <term>${entry.id}</term>
    <listitem>
     <para>
      ${entry.severity}
     </para>

     <para>
      ${entry.message?ensure_ends_with(".")}
     </para>
    </listitem>
   </varlistentry>
  </#list>
  </variablelist>
 </section>
 </#list>
</appendix>
