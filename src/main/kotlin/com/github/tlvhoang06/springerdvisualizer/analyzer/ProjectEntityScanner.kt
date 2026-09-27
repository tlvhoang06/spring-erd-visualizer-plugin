package com.github.tlvhoang06.springerdvisualizer.analyzer

import com.github.tlvhoang06.springerdvisualizer.model.EntityModel
import com.github.tlvhoang06.springerdvisualizer.model.ErdGraphModel
import com.github.tlvhoang06.springerdvisualizer.model.RelationshipModel
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

object ProjectEntityScanner {

    fun scanProjectEntities(project: Project): ErdGraphModel {
        val entities = mutableListOf<EntityModel>()
        val allRelationships = mutableListOf<RelationshipModel>()
        val psiManager = PsiManager.getInstance(project)

        val filesToScan = mutableSetOf<VirtualFile>()

        // 1. Fast Index Search: Only project source scope files (ignoring JDK/libraries allScope)
        try {
            val projectScopeFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
            filesToScan.addAll(projectScopeFiles)
        } catch (_: Exception) {}

        // 2. VFS Fallback ONLY if index returns empty (e.g. unindexed / unconfigured JDK project)
        if (filesToScan.isEmpty()) {
            val basePath = project.basePath
            if (basePath != null) {
                val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath)
                if (baseDir != null) {
                    VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Void>() {
                        override fun visitFile(file: VirtualFile): Boolean {
                            if (file.isDirectory) {
                                val name = file.name
                                if (name == "target" || name == "build" || name == ".idea" || name == ".git" || name == "node_modules" || name == ".gradle" || name == "out") {
                                    return false
                                }
                            } else if (file.extension == "java") {
                                filesToScan.add(file)
                            }
                            return true
                        }
                    })
                }
            }
        }

        // 3. Process project Java PSI files
        for (vFile in filesToScan) {
            val path = vFile.path
            if (path.contains("/target/") || path.contains("/build/") || path.contains("/.idea/") || path.contains("/.git/") || path.contains("/.gradle/") || path.contains("/out/")) {
                continue
            }

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
