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

  Copyright ${year} ForgeRock AS.
-->
<table xml:id="table-global-acis"
       xmlns="http://docbook.org/ns/docbook" version="5.0" xml:lang="${lang}"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://docbook.org/ns/docbook
                           http://docbook.org/xml/5.0/xsd/docbook.xsd"
       pgwide="1">
 <title>${title}</title>

 <textobject>
  <para>
   ${summary}
  </para>
 </textobject>

 <tgroup cols="3">
  <colspec colnum="1" colwidth="1*"/>
  <colspec colnum="2" colwidth="2*" />
  <colspec colnum="3" colwidth="2*" />

  <thead>
   <row>
    <entry>${nameTitle}</entry>
    <entry>${descTitle}</entry>
    <entry>${defTitle}</entry>
   </row>
  </thead>

  <tbody>
   <#list acis?sort_by("name") as aci>
   <row valign="top">
    <entry>
     <para>${aci.name}</para>        <!-- In English in config.ldif by default -->
    </entry>
    <entry>
     <para>${aci.description}</para> <!-- In English in config.ldif by default -->
    </entry>
    <entry>
     <para><literal>${aci.definition}</literal></para>
    </entry>
   </row>
   </#list>
  </tbody>
 </tgroup>
</table>
