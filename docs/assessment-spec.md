# Assessment spec (transcribed from `tech.pdf`)

> A faithful transcription of the Ballast Lane "Java - Technical Interview Exercise". `tech.pdf` remains
> the source of truth. **Bold** marks requirements that are easy to miss.

## Overview
- A RESTful API in **Java + Spring Boot**, built with **Clean Architecture** and **TDD**, backed by a reliable data store.
- Implementation is guided by the Pokémon user stories below, which **must be integrated into the final interview presentation**.
- The service integrates with **PokeAPI** (https://pokeapi.co/docs/v2) for retrieval, local replication, and attribute modification.

## Functional requirements
- **PokeAPI integration:** a Spring Boot REST API that communicates with the external PokeAPI.
- **US01, Pokémon Enumeration:** browse Pokémon via **paginated** results showing each entry's **sprite, category, mass, and a collection of their skills**.
  - Nice to have: caching of service responses.
- **US02, Detailed View:** comprehensive data for a chosen Pokémon: **image, core statistics, narrative description, evolutionary lineage**.
- **US03, Data Synchronization:** persist Pokémon data into a **local relational store**. The replication layer exists to allow **proprietary fields** (localized nomenclature, geographical metadata, internal classification tags).
- **US04, Local Data Modification:** update any Pokémon stored in the local DB, with robust validation: **404 for missing records, 400 for malformed payloads**, plus further defensive logic.

## Technical requirements
- **Mandatory:** public Git repo · tests · proper error handling · front-end that consumes the API. *Nice to have:* a caching layer for PokeAPI responses.
- **Optional:** any additional functionality is welcome.
- **Database:** relational (or appropriate) storage with a **primary entity and a secondary collection for user management**. Records need a **unique primary key and at least two descriptive attributes**.
- **API:** a Java Web API with **comprehensive CRUD** for the dataset, standard HTTP verbs, required parameters, **consistent return structures**. Plus an **auxiliary API for user registration, authentication, and protected vs. public routes**.
- **Data layer:** a specialized data access layer for the persistence store, providing the foundation for the controllers.
- **Core business logic:** a dedicated layer for all domain rules and data validation, **architecturally independent of both the API and the data access components**.
- **Testing:** thorough unit test coverage for **every core component**.

## Frontend
- The primary emphasis is the backend, but **full-stack capability** must be demonstrated with a modern framework (e.g. React, Vue).
- It is evaluated on:
  - a **responsive, user-centric** design
  - **CRUD operations** for the functional use cases
  - clean component organization and **efficient state management**
- Submission: a comprehensive **README** (environment configuration and technical docs), plus **seeded data or mock credentials** for the demo.

## Delivery
- A README with environment setup and technical documentation
- Pre-populated demonstration data
- A **Dockerfile** for containerized execution

## Generative AI exercise (separate from the Pokémon app)
Imagine generating a RESTful API for a **task management system**:
- CRUD on tasks. Each task has `title`, `description`, `status` and `due_date`, and is associated with a user (assume a basic User model exists).

Deliver:
- the **prompt** you would use in your GenAI tool
- the **output code** (or a representative sample)
- a description of how you **validated** the suggestions, **corrected or improved** the output, and handled **edge cases, authentication, and validations**

## Presentation and code review
Present over Meet or Zoom by screen-sharing the repo or IDE. Cover the user stories, design choices, technical architecture and a live demo. A code review with questions follows. It is evaluated on:
- **Clean Architecture:** separation of concerns, independence of components
- **Application testing:** sufficient coverage; **TDD preferred**
- **Code quality:** organized, readable, best practices
- **Functionality:** works as required, without errors or bugs. *Desired:* **no warnings in the browser console.**
- **Presentation:** clear and concise; shows understanding of **backend and frontend best practices**
- **GenAI tools:** fluency with GenAI and prompt engineering, and **critical thinking when evaluating AI-generated code**
