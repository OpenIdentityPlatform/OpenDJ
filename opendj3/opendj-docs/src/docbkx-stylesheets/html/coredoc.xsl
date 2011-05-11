<?xml version="1.0" encoding="UTF-8"?>
<!--
  ! CCPL HEADER START
  !
  ! This work is licensed under the Creative Commons
  ! Attribution-NonCommercial-NoDerivs 3.0 Unported License.
  ! To view a copy of this license, visit
  ! http://creativecommons.org/licenses/by-nc-nd/3.0/
  ! or send a letter to Creative Commons, 444 Castro Street,
  ! Suite 900, Mountain View, California, 94041, USA.
  !
  ! You can also obtain a copy of the license at
  ! src/main/resources/legal-notices/CC-BY-NC-ND.txt.
  ! See the License for the specific language governing permissions
  ! and limitations under the License.
  !
  ! If applicable, add the following below this CCPL HEADER, with the fields
  ! enclosed by brackets "[]" replaced with your own identifying information:
  !      Portions Copyright [yyyy] [name of copyright owner]
  !
  ! CCPL HEADER END
  !
  !      Copyright 2011 ForgeRock AS
  !    
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 version="1.0">
 <xsl:import href="urn:docbkx:stylesheet" />

<!-- <xsl:param name="chunk.section.depth" select="0" />-->
<!-- <xsl:param name="chunker.output.encoding">UTF-8</xsl:param>-->
<!-- <xsl:param name="chunker.output.indent">yes</xsl:param>-->
 <xsl:param name="generate.legalnotice.link" select="1" />
<!-- <xsl:param name="generate.revhistory.link" select="1" />-->
 <xsl:param name="root.filename">index</xsl:param>
 <xsl:param name="use.id.as.filename" select="1" />
<!-- <xsl:template name="user.footer.content">-->
<!--  <a>-->
<!--   <xsl:attribute name="href">-->
<!--    <xsl:apply-templates select="//legalnotice[1]" mode="chunk-filename" />-->
<!--   </xsl:attribute>-->
<!--   <xsl:apply-templates select="//copyright[1]" mode="titlepage.mode" />-->
<!--  </a>-->
<!-- </xsl:template>-->
 <xsl:param name="generate.toc">
  appendix  nop
  article/appendix  nop
  article   nop
  book      toc,title,figure,table,example,equation
  chapter   nop
  part      toc,title
  preface   nop
  qandadiv  nop
  qandaset  nop
  reference nop
  sect1     nop
  sect2     nop
  sect3     nop
  sect4     nop
  sect5     nop
  section   nop
  set       toc,title
 </xsl:param>
 <xsl:param name="toc.section.depth" select="0" />
</xsl:stylesheet>