# LuceneFx

## Overview
LuceneFx is a lightweight **JavaFX desktop application** that demonstrates the power of **Apache Lucene and Tika** for document indexing and search. It allows users to quickly index all documents in a folder—including emails and attachments—and perform lightning-fast searches to retrieve relevant files.

![LuceneFx Screenshot](https://github.com/user-attachments/assets/4c7856f3-22ed-4f7f-a27f-c988fb4d9cdd)

## Features
- **Index all documents** in a selected folder, including attachments
- **Fast keyword search** using Apache Lucene
- **Open files instantly** by double-clicking search results
- **Sortable results** by path, modified date, number of attachments, or relevance score
- **Persistent indexes** stored in `.lucene_index`

## Tech Stack
- **Java** (version from `pom.xml`)
- **JavaFX** (Graphical User Interface)
- **Apache Lucene & Tika** (Indexing and document parsing)
- **Maven** (Dependency and build management)

## Installation & Usage

### Prerequisites
- **Java JDK** (version as per `pom.xml`)
- **Maven** installed on your system

### Clone & Build
```sh
git clone https://github.com/lguberan/LuceneFx.git
cd LuceneFx
mvn clean javafx:run
```

### Creating a Fat JAR
```sh
mvn compile package
java -jar target/LuceneFx_0.8.5.jar
```

## License
This project is open-source and provided **as is**, under the **BSD License**.

## Contribution Guidelines
- **Contributions are welcome!** Feel free to submit PRs to enhance the project.
- All **code contributions must be licensed under the BSD License**.

