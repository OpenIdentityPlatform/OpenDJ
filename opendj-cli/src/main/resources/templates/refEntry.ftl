<#--
 # CDDL HEADER START
 #
 # The contents of this file are subject to the terms of the
 # Common Development and Distribution License, Version 1.0 only
 # (the "License").  You may not use this file except in compliance
 # with the License.
 #
 # You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 # or http://forgerock.org/license/CDDLv1.0.html.
 # See the License for the specific language governing permissions
 # and limitations under the License.
 #
 # When distributing Covered Code, include this CDDL HEADER in each
 # file and include the License file at legal-notices/CDDLv1_0.txt.
 # If applicable, add the following below this CDDL HEADER,
 # with the fields enclosed by brackets "[]" replaced
 # with your own identifying information:
 #
 #      Portions Copyright [yyyy] [name of copyright owner]
 #
 # CDDL HEADER END
 #
 #      Copyright 2015 ForgeRock AS.
 #
 #-->
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
<refentry xml:id="${name}-1"
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
  <refpurpose>${shortDesc}</refpurpose>
 </refnamediv>

 <refsynopsisdiv>
  <cmdsynopsis>
   <command>${name}</command>
   <#if args??><arg choice="plain">${args}</arg></#if>
  </cmdsynopsis>
 </refsynopsisdiv>

 <refsect1 xml:id="${name}-description">
   <title>${descTitle}</title>

   <para>
     ${description?ensure_ends_with(".")}
   </para>

   <#if info??>${info}</#if>
 </refsect1>

 <#if optionSection??>
   ${optionSection}
 </#if>

 <#if subcommands??>
   ${subcommands}
 </#if>

 <#if trailingSectionString??>
   ${trailingSectionString}
 </#if>
</refentry>
