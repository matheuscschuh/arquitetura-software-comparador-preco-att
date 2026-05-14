# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build / Run

Maven project, but with **non-standard layout**: sources live in `src/` (not `src/main/java`) and resources in `resources/` — both wired up in `pom.xml`'s `<build>` block. Don't move files into `src/main/java`; the build won't find them.

- Build: `mvn compile`
- Run the app: `mvn exec:java` — `mainClass` is `Main` (default package, no qualifier)
- Package: `mvn package`

The compiler is set to `maven.compiler.release=25`. `Main.java` uses Java 25 preview features — instance `void main()` and the implicit `IO.println` — so it requires JDK 25 to compile and run.

There are no tests.

## Persistence

Single SQLite database at `products.db` in the working directory, managed via JPA/Hibernate 6.6.4.

- Persistence unit `default` is declared in [resources/META-INF/persistence.xml](resources/META-INF/persistence.xml).
- `hibernate.hbm2ddl.auto=update` — schema is auto-evolved on startup. Adding a new `@Entity` requires registering it as a `<class>` in `persistence.xml` *and* updating the schema (delete `products.db` if you want a clean rebuild).
- Dialect: `org.hibernate.community.dialect.SQLiteDialect` (from `hibernate-community-dialects`, not `hibernate-core` — SQLite isn't a first-party dialect).
- `hibernate.show_sql=true` is on, so SQL prints to stdout during runs.

## Architecture

Hexagonal/ports-and-adapters layout, three layers:

- [src/domain/](src/domain/) — JPA entities (`Product`, `Price`) plus `EntityInterface` (every entity exposes a `UUID getUUID()`).
- [src/adapters/](src/adapters/) — `PersistInterface` (the port: `save`/`delete`/`listAll`/`findOneById`) and `DatabaseStorage<T extends EntityInterface>` (the JPA adapters).
- [src/service/](src/service/) — `ServiceInterface` and concrete services like `ProductService`.

A few specifics worth knowing before editing:

**`DatabaseStorage<T>` is generic but `PersistInterface` is not.** The class is parameterized by entity type (passed via `Class<T>` to the constructor for `listAll`'s JPQL query and `findOneById`'s `em.find`), but the interface uses `EntityInterface` everywhere. Services wire it as `PersistInterface armazenamento = new DatabaseStorage<>(Product.class);` — see [src/service/ProductService.java:11](src/service/ProductService.java#L11). If you add a new entity service, follow the same pattern.

**`save()` does upsert.** It checks `entity.getUUID() != null && em.find(...) != null` to decide between `merge` and `persist` — see [src/adapters/DatabaseStorage.java:31-36](src/adapters/DatabaseStorage.java#L31-L36). New entities have `uuid == null` until `@GeneratedValue(strategy = GenerationType.UUID)` assigns one on persist.

**`listAll()` and `findOneById()` eagerly initialize lazy collections via reflection.** [DatabaseStorage.initLazyCollections()](src/adapters/DatabaseStorage.java#L87-L98) walks declared fields and calls `Hibernate.initialize()` on any `Collection`. This is why `Product.historicalPrice` (`FetchType.LAZY`) is safely accessible after the `EntityManager` closes. If you add a new lazy association that isn't a `Collection` (e.g., a lazy `@ManyToOne`), this helper won't cover it.

**`Product.setPrice()` has side effects** — it auto-archives the previous price into `historicalPrice` ([src/domain/Product.java:77-86](src/domain/Product.java#L77-L86)). The `Price`/`Product` relationship is `@OneToMany(cascade = ALL, orphanRemoval = true)`, so saving a Product cascades its price history. Don't manually `save()` `Price` entities through a separate adapters — go through `Product`.

**UUIDs are stored as VARCHAR(36)** via `@JdbcTypeCode(SqlTypes.VARCHAR)` because SQLite has no native UUID type. Keep this annotation on any new `UUID` `@Id` columns.

**A new `EntityManagerFactory` is created per `DatabaseStorage` instance.** That's expensive; instances are meant to be long-lived (one per service). `close()` exists but isn't currently called from `Main`.
