# Spring ERD Visualizer IntelliJ Plugin

An IntelliJ IDEA plugin that visualizes JPA/Hibernate Java entities as an interactive ERD directly inside IntelliJ IDEA.

## Features

- **Entity Discovery**: Scans Java JPA annotations (`@Entity`, `@Table`, `@Id`, `@Column`).
- **Relationship Detection**: Discovers `@OneToOne`, `@OneToMany`, `@ManyToOne`, `@ManyToMany` relationships.
- **Interactive ERD**: Visual representation of entity diagrams directly inside IntelliJ IDEA with zoom, pan, drag, and fit-to-screen controls.

## Requirements

- IntelliJ IDEA 2024.3+ (Community or Ultimate)
- Java 17 / 21+

## Building the Project

```bash
./gradlew build
```

## Running the Plugin in Development

```bash
./gradlew runIde
```

## Documentation

See [ARCHITECTURE.md](ARCHITECTURE.md) for architectural details and design principles.
