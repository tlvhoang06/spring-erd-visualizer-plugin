package com.github.tlvhoang06.springerdvisualizer.model

data class ErdGraphModel(
    val entities: List<EntityModel> = emptyList(),
    val relationships: List<RelationshipModel> = emptyList()
) {
    fun findEntity(name: String): EntityModel? {
        return entities.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
