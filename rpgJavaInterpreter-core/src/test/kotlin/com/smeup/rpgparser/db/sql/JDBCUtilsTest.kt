package com.smeup.rpgparser.db.sql

import org.junit.*
import java.sql.Connection
import kotlin.test.assertEquals

class JDBCUtilsTest {
    @Test
    fun primaryKeysTest() {
        connectionForTest().use {
            it.createStatement().execute("CREATE TABLE TSTTAB00 (TSTFLDCHR CHAR (5) NOT NULL, TSTFLDNBR DECIMAL (7, 2) NOT NULL, TSTFLDNB2 DECIMAL (2, 0) NOT NULL, PRIMARY KEY(TSTFLDCHR, TSTFLDNBR))")
            assertEquals(listOf("TSTFLDCHR", "TSTFLDNBR"), it.primaryKeys("TSTTAB00"))
        }
    }

    @Test
    fun orderingFieldsTest() {
        connectionForTest().use {
            it.createStatement().execute("CREATE TABLE TSTTAB00 (TSTFLDCHR CHAR (5) NOT NULL, TSTFLDNBR DECIMAL (7, 2) NOT NULL, TSTFLDNB2 DECIMAL (2, 0) NOT NULL, PRIMARY KEY(TSTFLDCHR, TSTFLDNBR))")
            it.createStatement().execute("CREATE VIEW TSTVIEW AS SELECT * FROM TSTTAB00 ORDER BY TSTFLDNB2, TSTFLDNBR")
            it.createStatement().execute("CREATE INDEX TSTVIEW$CONVENTIONAL_INDEX_SUFFIX ON TSTTAB00 (TSTFLDNB2, TSTFLDNBR)")
            assertEquals(listOf("TSTFLDNB2", "TSTFLDNBR"), it.orderingFields("TSTVIEW"))
        }
    }

    private fun connectionForTest(): Connection {
        val connection = randomHsqlMemDB().getConnection()
        connection.createStatement().use {
            it.execute("SET DATABASE EVENT LOG SQL LEVEL 3")
        }
        return connection
    }
}