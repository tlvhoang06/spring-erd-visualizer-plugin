package com.github.tlvhoang06.springerdvisualizer.exporter

import com.github.tlvhoang06.springerdvisualizer.model.ErdGraphModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipType

object MermaidExporter {

    fun export(graph: ErdGraphModel): String {
        val sb = StringBuilder()
        sb.appendLine("erDiagram")

        val deduplicatedRels = graph.deduplicatedRelationships()
        for (rel in deduplicatedRels) {
            val arrow = when (rel.type) {
                RelationshipType.ONE_TO_ONE -> "||--||"
                RelationshipType.ONE_TO_MANY -> "||--|{"
                RelationshipType.MANY_TO_ONE -> "}|--||"
                RelationshipType.MANY_TO_MANY -> "}|--|{"
            }
            sb.appendLine("    ${rel.sourceEntity} $arrow ${rel.targetEntity} : \"\"")
        }

        for (entity in graph.entities) {
            val safeEntityName = sanitizeIdentifier(entity.name)
            sb.appendLine("    $safeEntityName {")
            for (field in entity.fields) {
                val pkTag = if (field.isPrimaryKey) " PK" else ""
                val cleanType = sanitizeIdentifier(field.type)
                val cleanName = sanitizeIdentifier(field.name)
                sb.appendLine("        $cleanType $cleanName$pkTag")
            }
            sb.appendLine("    }")
        }

        return sb.toString()
    }

    private fun sanitizeIdentifier(text: String): String {
        return text.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }
}
