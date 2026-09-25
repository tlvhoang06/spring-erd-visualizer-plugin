package com.github.tlvhoang06.springerdvisualizer.model

data class FieldModel(
    val name: String,
    val type: String,
    val columnName: String? = null,
    val isPrimaryKey: Boolean = false,
    val nullable: Boolean = true,
    val unique: Boolean = false
)
