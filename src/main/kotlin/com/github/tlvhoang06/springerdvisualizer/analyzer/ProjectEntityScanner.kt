package com.github.tlvhoang06.springerdvisualizer.analyzer

import com.github.tlvhoang06.springerdvisualizer.model.EntityModel
import com.github.tlvhoang06.springerdvisualizer.model.ErdGraphModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipModel
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

object ProjectEntityScanner {

    fun scanProjectEntities(project: Project): ErdGraphModel {
        val entities = mutableListOf<EntityModel>()
        val allRelationships = mutableListOf<RelationshipModel>()

        val scope = GlobalSearchScope.projectScope(project)
        val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)
        val psiManager = PsiManager.getInstance(project)
        val fileIndex = ProjectFileIndex.getInstance(project)

        for (vFile in javaFiles) {
            // Ignore generated or excluded sources
            if (fileIndex.isExcluded(vFile)) continue

            val psiFile = psiManager.findFile(vFile)
            if (psiFile is PsiJavaFile) {
                for (psiClass in psiFile.classes) {
                    val entityModel = EntityScanner.scanEntity(psiClass)
                    if (entityModel != null) {
                        entities.add(entityModel)
                        allRelationships.addAll(entityModel.relationships)
                    }
                }
            }
        }

        return ErdGraphModel(
            entities = entities,
            relationships = allRelationships
        )
    }
}
