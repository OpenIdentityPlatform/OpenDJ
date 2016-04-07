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
<#-- Comment text comes from the Javadoc, so the language is English. -->
<appendix xml:id="appendix-ldap-result-codes"
          xmlns="http://docbook.org/ns/docbook" version="5.0" xml:lang="en"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://docbook.org/ns/docbook
                              http://docbook.org/xml/5.0/xsd/docbook.xsd"
          xmlns:xlink="http://www.w3.org/1999/xlink">
 <title>LDAP Result Codes</title>

 <para>
  ${classComment}
 </para>

 <indexterm>
  <primary>LDAP</primary>
  <secondary>Result codes</secondary>
 </indexterm>

 <table pgwide="1">
  <title>OpenDJ LDAP Result Codes</title>
  <tgroup cols="3">
   <colspec colnum="1" colwidth="1*" />
   <colspec colnum="2" colwidth="2*" />
   <colspec colnum="3" colwidth="3*" />

   <thead>
    <row>
     <entry>Result Code</entry>
     <entry>Name</entry>
     <entry>Description</entry>
    </row>
   </thead>

   <tbody>
    <#list resultCodes as resultCode>
    <row valign="top">
     <entry>
      <para>
       ${resultCode.intValue}
      </para>
     </entry>
     <entry>
      <para>
       ${resultCode.name}
      </para>
     </entry>
     <entry>
      <para>
       ${resultCode.comment}
      </para>
     </entry>
    </row>
    </#list>
   </tbody>

  </tgroup>
 </table>
</appendix>
