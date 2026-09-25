package com.github.tlvhoang06.springerdvisualizer.model

data class EntityModel(
    val name: String,
    val tableName: String,
    val fields: List<FieldModel> = emptyList(),
    val relationships: List<RelationshipModel> = emptyList()
)
