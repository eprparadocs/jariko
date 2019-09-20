package com.smeup.rpgparser.db.sql

import com.smeup.rpgparser.interpreter.*
import java.lang.StringBuilder
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

const val CONVENTIONAL_INDEX_SUFFIX = "_INDEX"

fun ResultSet.joinToString(separator: String = " - "): String {
    val sb = StringBuilder()
    if (this.next()) {
        for (i in 1..this.metaData.columnCount) {
            sb.append("${this.metaData.getColumnName(i)}: ${this.getObject(i)}")
            if (i != this.metaData.columnCount) sb.append(separator)
        }
    }
    return sb.toString()
}

fun PreparedStatement.bind(values: List<Value>) {
    values.forEachIndexed {
        i, value -> this.setObject(i + 1, value.toDBValue())
    }
}

fun Connection.recordFormatName(tableName: String): String? =
    this.metaData.getTables(null, null, tableName, null).use {
        if (it.next()) {
            val remarks = it.getString("REMARKS")
            if (!remarks.isNullOrBlank()) {
                return@use remarks
            }
        }
        return@use tableName
    }

fun Connection.fields(name: String): List<DBField> {
    val result = mutableListOf<DBField>()
    this.metaData.getColumns(null, null, name, null).use {
        while (it.next()) {
            result.add(it.getString("COLUMN_NAME") withType typeFor(it))
        }
    }
    return result
}

private fun typeFor(metadataReultSet: ResultSet): Type {
    val sqlType = metadataReultSet.getString("TYPE_NAME")
    val columnSize = metadataReultSet.getInt("COLUMN_SIZE")
    val decimalDigits = metadataReultSet.getInt("DECIMAL_DIGITS")
    return typeFor(sqlType, columnSize, decimalDigits)
}

fun Connection.primaryKeys(tableName: String): List<String> {
    val result = mutableListOf<String>()
    this.metaData.getPrimaryKeys(null, null, tableName).use {
        while (it.next()) {
            result.add(it.getString("COLUMN_NAME"))
        }
    }
    return result
}

fun Connection.indexes(tableName: String): List<String> {
    val result = mutableListOf<String>()
    this.metaData.getIndexInfo(null, null, tableName + CONVENTIONAL_INDEX_SUFFIX, false, false).use {
        while (it.next()) {
            result.add(it.getString("COLUMN_NAME"))
        }
    }
    return result
}

fun ResultSet?.closeIfOpen() {
    if (this != null) {
        try {
            this.close()
        } catch (t: Throwable) {}
    }
}

fun ResultSet?.toValues(): List<Pair<String, Value>> {
    if (this != null && this.next()) {
        return this.currentRecordToValues()
    }
    return emptyList()
}

fun ResultSet?.currentRecordToValues(): List<Pair<String, Value>> {
    // TODO create a unit test for the isAfterLast condition
    if (this == null || this.isAfterLast) {
        return emptyList()
    }
    val result = mutableListOf<Pair<String, Value>>()
    val metadata = this.metaData
    for (i in 1..metadata.columnCount) {
        val type = typeFor(metadata.getColumnTypeName(i), metadata.getScale(i), metadata.getPrecision(i))
        val value = type.toValue(this, i)
        result.add(Pair(metadata.getColumnName(i), value))
    }
    return result
}
