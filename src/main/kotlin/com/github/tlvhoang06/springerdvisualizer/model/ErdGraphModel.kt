package com.github.tlvhoang06.springerdvisualizer.model

data class ErdGraphModel(
    val entities: List<EntityModel> = emptyList(),
    val relationships: List<RelationshipModel> = emptyList()
) {
    fun findEntity(name: String): EntityModel? {
        return entities.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun deduplicatedRelationships(): List<RelationshipModel> {
        val result = mutableListOf<RelationshipModel>()
        val visitedKeys = mutableSetOf<String>()

        for (rel in relationships) {
            val pairKey1 = "${rel.sourceEntity}:${rel.targetEntity}"
            val pairKey2 = "${rel.targetEntity}:${rel.sourceEntity}"

            if (visitedKeys.contains(pairKey1) || visitedKeys.contains(pairKey2)) {
                val existingIndex = result.indexOfFirst {
                    (it.sourceEntity == rel.sourceEntity && it.targetEntity == rel.targetEntity) ||
                    (it.sourceEntity == rel.targetEntity && it.targetEntity == rel.sourceEntity)
                }
                if (existingIndex >= 0 && result[existingIndex].joinColumn == null && rel.joinColumn != null) {
                    result[existingIndex] = result[existingIndex].copy(joinColumn = rel.joinColumn)
                }
                continue
            }

            visitedKeys.add(pairKey1)
            visitedKeys.add(pairKey2)
            result.add(rel)
        }

        return result
    }
}
