# Spring ERD Visualizer IntelliJ Plugin

An IntelliJ IDEA plugin that visualizes JPA/Hibernate Java entities as an interactive Entity-Relationship Diagram (ERD) directly inside IntelliJ IDEA.

## Features

- **Automatic Entity Discovery**: Scans Java source code using IntelliJ PSI to detect Spring Boot / JPA entities.
- **Full JPA Annotation Support**:
  - Entity & Table: `@Entity`, `@Table(name = "...")`
  - Columns & Keys: `@Id`, `@EmbeddedId`, `@Column(name, nullable, unique)`
  - Component Embedding: `@Embedded`, `@Embeddable`, `@AttributeOverride`, `@AttributeOverrides`
  - Relationships: `@OneToOne`, `@OneToMany`, `@ManyToOne`, `@ManyToMany`, `@JoinColumn`, `@JoinTable`, `mappedBy`
- **Interactive ERD Canvas**:
  - Theme-aware styling matching IntelliJ Light & Dark (Darcula) themes.
  - Highlighted Entity Header boxes with top accent bars.
  - Distinct PK gold badges and colorized cardinality badges (`1` Sapphire Blue, `N`/`M` Amethyst Purple).
  - Horizontal separator lines between attribute rows.
  - Full interaction controls: Zoom, Pan, Drag entity nodes, Fit-to-screen, and Reset view.
- **Export Capabilities**:
  - **Export Mermaid**: Copy standard Mermaid `erDiagram` Markdown syntax with 1 click to clipboard for use in READMEs, Notion, or documentation.
  - **Export PNG**: Export high-resolution PNG images of the ERD canvas.

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

