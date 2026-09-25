package com.github.tlvhoang06.springerdvisualizer.model

data class RelationshipModel(
    val sourceEntity: String,
    val targetEntity: String,
    val type: RelationshipType,
    val mappedBy: String? = null,
    val joinColumn: String? = null
)
