# Architecture Overview

The Spring ERD Visualizer plugin strictly decouples IntelliJ PSI analysis from rendering and UI components.

## Data Flow Architecture

```text
IntelliJ PSI
     ↓
Source Analyzer (EntityScanner, FieldAnalyzer, RelationshipAnalyzer)
     ↓
Domain Model (EntityModel, FieldModel, RelationshipModel, ErdGraphModel)
     ↓
Graph/Layout Engine (ErdLayoutEngine)
     ↓
ERD Renderer (ErdCanvasPanel)
     ↓
IntelliJ UI ToolWindow / Action (ErdToolWindowFactory, GenerateErdAction)
     ↓
Exporters (MermaidExporter, ImageExporter)
```

## Core Design Principles

1. **Decoupled Domain Model**: `EntityModel`, `FieldModel`, `RelationshipModel`, and `ErdGraphModel` contain pure, immutable domain data and do not depend on IntelliJ PSI or Swing UI classes.
2. **Non-Blocking PSI Analysis**: PSI scanning executes on background threads using project-scoped index search (`GlobalSearchScope.projectScope`), ensuring zero UI thread freezes or read-lock contention.
3. **Hardware-Accelerated Swing Canvas**: `ErdCanvasPanel` uses `Graphics2D` vector painting with perimeter intersection line routing ($O(1)$ edge calculation per node pair).
4. **Multi-Format Export Pipeline**: Domain models can be rendered to Swing UI, exported to standard Mermaid Markdown syntax, or saved as PNG raster images.

## Module Structure

- `action`: IntelliJ Action handlers (`GenerateErdAction`) registered in `ToolsMenu`, `ProjectViewPopupMenu`, and `EditorPopupMenu`.
- `analyzer`: IntelliJ PSI Java source entity scanners (`EntityScanner`, `FieldAnalyzer`, `RelationshipAnalyzer`, `ProjectEntityScanner`).
- `model`: UI-agnostic domain data models (`EntityModel`, `FieldModel`, `RelationshipModel`, `ErdGraphModel`).
- `layout`: Node positioning algorithms (`ErdLayoutEngine`, `NodeBounds`).
- `renderer`: Custom Swing canvas panel for interactive ERD rendering (`ErdCanvasPanel`).
- `toolwindow`: IntelliJ ToolWindow integration (`ErdToolWindowFactory`).
- `exporter`: Export format converters (`MermaidExporter`, `ImageExporter`).

