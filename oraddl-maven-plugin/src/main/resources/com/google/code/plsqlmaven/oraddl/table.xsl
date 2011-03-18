<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="text" encoding="utf-8" indent="no"/>
	<xsl:template match="table">
	  create table <xsl:value-of select="@name"/>
	  (
	    
	  )
	</xsl:template>
</xsl:stylesheet>