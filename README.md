# Spring ERD Visualizer IntelliJ Plugin

An IntelliJ IDEA plugin that visualizes JPA/Hibernate Java entities as an interactive Entity-Relationship Diagram (ERD) directly inside IntelliJ IDEA.

## Features

- **Automatic Entity Discovery**: Scans Java source code using IntelliJ PSI to detect Spring Boot / JPA entities asynchronously on background threads.
- **Full JPA Annotation Support**:
  - Entity & Table: `@Entity`, `@Table(name = "...")`
  - Columns & Keys: `@Id`, `@EmbeddedId`, `@Column(name, nullable, unique)`
  - Component Embedding: `@Embedded`, `@Embeddable`, `@AttributeOverride`, `@AttributeOverrides`
  - Relationships: `@OneToOne`, `@OneToMany`, `@ManyToOne`, `@ManyToMany`, `@JoinColumn`, `@JoinTable`, `mappedBy`
  - Object & Collection Fields: Displays fields like `Set<ObjectB>`, `List<Order>`, and `@ManyToOne Department` directly inside Entity cards.
- **Interactive ERD Canvas**:
  - **Pure Dark & Light Themes**: High-contrast theme-aware styling matching IntelliJ Dark (#121212) and Light themes.
  - **Interactive Module Color Legend**: Glassmorphic overlay panel categorizing entities by package/module with color swatches, entity counts, and live hover & click highlighting.
  - **Straight & Minimum-Kink Line Routing**: Unobstructed relationship lines route as 0-kink direct straight lines or 1-kink L-shapes for clutter-free diagrams.
  - **Solid Mask Cardinality Badges**: Colorized badges (`1` Sapphire Blue, `N`/`M` Amethyst Purple) with solid canvas-matching background cutout masks to prevent line bleed.
  - **Real-Time Search & Filtering**: Live search bar to filter entities by name, table name, or field attributes.
  - **Full Canvas Controls**: Zoom, Pan, Drag individual entity nodes, Fit-to-screen, and Reset view.
- **Export Capabilities**:
  - **Export Mermaid**: Copy standard Mermaid `erDiagram` Markdown syntax with 1 click to clipboard for use in READMEs, Notion, or documentation.
  - **Export PNG Image**: Save high-resolution PNG images of the ERD canvas (including the Module Color Legend overlay).

## Supported Annotations

```text
@Entity
@Table
@Id
@EmbeddedId
@Column
@Embedded
@Embeddable
@AttributeOverride
@AttributeOverrides

@OneToOne
@OneToMany
@ManyToOne
@ManyToMany

@JoinColumn
@JoinTable
```

## Requirements

- IntelliJ IDEA 2024.3+ (Community or Ultimate)
- JDK 17 / 21+

## Building & Installation

### Build Plugin ZIP

```bash
./gradlew buildPlugin
```

The compiled plugin zip is generated at:
`build/distributions/spring-erd-visualizer-plugin-0.1.0-SNAPSHOT.zip`

### Install in IntelliJ IDEA

1. Open IntelliJ IDEA -> **`File` -> `Settings`** (or `Ctrl + Alt + S`).
2. Select **`Plugins`** -> Click the Gear Icon **⚙️** -> **`Install Plugin from Disk...`**.
3. Select `spring-erd-visualizer-plugin-0.1.0-SNAPSHOT.zip`.
4. Click **Apply** / **Restart IDE**.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for architectural details and module boundaries.

