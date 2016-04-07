${marker}
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
<refentry xml:id="${id}"
          xmlns="http://docbook.org/ns/docbook" version="5.0" xml:lang="${locale}"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://docbook.org/ns/docbook
                              http://docbook.org/xml/5.0/xsd/docbook.xsd"
          xmlns:xlink="http://www.w3.org/1999/xlink"
          xmlns:xinclude="http://www.w3.org/2001/XInclude">

 <info>
  <copyright>
   <year>2011-${year}</year>
   <holder>ForgeRock AS.</holder>
  </copyright>
 </info>

 <refmeta>
  <refentrytitle>${name}</refentrytitle><manvolnum>1</manvolnum>
  <refmiscinfo class="software">OpenDJ</refmiscinfo>
  <refmiscinfo class="version">${r"${project.version}"}</refmiscinfo>
 </refmeta>

 <refnamediv>
  <refname>${name}</refname>
  <refpurpose>${purpose}</refpurpose>
 </refnamediv>

 <refsynopsisdiv>
  <cmdsynopsis>
   <command>${name}</command>
   <arg choice="plain">${args}</arg>
  </cmdsynopsis>
 </refsynopsisdiv>

 <refsect1 xml:id="${id}-description">
  <title>${descTitle}</title>

  <para>
   ${description?ensure_ends_with(".")}
  </para>

  <#if info??>${info}</#if>
 </refsect1>

 <#if options??>
 <refsect1 xml:id="${id}-options">
  <title>${optionsTitle}</title>

  <variablelist>
   <para>
    ${optionsIntro}
   </para>

   <#list options as option>
   <varlistentry>
    <term><option>${option.synopsis?xml}</option></term>
    <listitem>
     <para>
      ${option.description?ensure_ends_with(".")}
     </para>

     <#if option.info??>
       <#if option.info.usage??>${option.info.usage}</#if>

       <#if option.info.default??>
       <para>
        ${option.info.default}
       </para>
       </#if>

       <#if option.info.doc??>${option.info.doc}</#if>
     </#if>
    </listitem>
   </varlistentry>
   </#list>
  </variablelist>
 </refsect1>
 </#if>

 <#if propertiesInfo??>${propertiesInfo}</#if>
</refentry>
