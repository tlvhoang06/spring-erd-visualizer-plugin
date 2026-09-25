# Architecture Overview

The Spring ERD Visualizer plugin strictly decouples IntelliJ PSI analysis from rendering and UI components.

## Data Flow Architecture

```text
IntelliJ PSI
     ↓
Source Analyzer (EntityScanner, FieldAnalyzer, RelationshipAnalyzer)
     ↓
Domain Model (EntityModel, FieldModel, RelationshipModel)
     ↓
Graph/Layout Engine
     ↓
ERD Renderer
     ↓
IntelliJ UI ToolWindow / Action
```

## Module Structure

- `action`: IntelliJ Action handlers (e.g. `GenerateErdAction`)
- `analyzer`: PSI Java entity scanning and relationship parsing
- `model`: Clean, UI-agnostic domain data models
- `renderer`: ERD graph rendering components
- `layout`: Graph layout algorithms
- `toolwindow`: IntelliJ ToolWindow UI integration
- `exporter`: Export utilities (SVG, PNG, Mermaid)
- `util`: Common utilities
