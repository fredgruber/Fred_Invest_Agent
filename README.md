# 📈 Fred Invest Agent

> Aplicação **PWA (Progressive Web App)** moderna para Gestão Inteligente de Investimentos desenvolvida em **Java 25 (Spring Boot 3.3.3)**, conteinerizada com **Docker**, com autenticação **OAuth2 (Google, Apple, Microsoft)**, **API de Inteligência Artificial** e sincronização via **Open Finance Brasil**.

---

## 🌟 Funcionalidades Principais

- 📱 **Progressive Web App (PWA)**:
  - Instalável na tela inicial em dispositivos mobile (iOS/Android) e desktop.
  - Suporte a navegação rápida e armazenamento em cache offline via Service Worker (`sw.js`).
- 🔐 **Autenticação Multi-Provedor & Segurança**:
  - Cadastro local seguro por e-mail e senha com hash BCrypt.
  - Login Social OAuth2 / OIDC integrando **Google**, **Apple** e **Microsoft**.
  - Autenticação stateless via tokens **JWT (JSON Web Token)**.
- 📊 **Gestão Completa de Carteiras de Investimentos**:
  - Armazenamento relacional em banco de dados **PostgreSQL**.
  - Suporte às principais classes de ativos: **Ações**, **FIIs**, **Renda Fixa**, **Criptomoedas** e **Outros**.
  - Cálculos consolidados automáticos: Patrimônio Total, Valor Investido, Lucro/Prejuízo (R$ e %) e alocação por categoria.
- 🤖 **Assistente de Inteligência Artificial (AI Financial Agent)**:
  - Análise automatizada da composição da carteira, avaliação de perfil de risco e diagnósticos de diversificação.
  - Recomendações personalizadas de rebalanceamento e estratégias de aportes.
  - Chat financeiro virtual interativo em tempo real.
- 🌐 **Conexão Open Finance Brasil**:
  - Fluxo de gestão de consentimentos de acordo com as normas do Open Finance.
  - Autorização explícita pelo usuário para instituições parceiras (Itaú, Bradesco, Banco do Brasil, Nubank, XP, BTG Pactual).
  - Sincronização automatizada de ativos e saldos bancários sem digitação manual.

---

## 🏗️ Arquitetura do Sistema

```
                     +---------------------------------------+
                     |            PWA Frontend               |
                     |  (Web App Manifest + Service Worker   |
                     |    + Interface Responsiva Dashboard)  |
                     +-------------------+-------------------+
                                         |
                                  HTTP / HTTPS (REST API + JWT)
                                         |
                     +-------------------v-------------------+
                     |      Spring Boot Backend (Java 25)    |
                     |  - Spring Security (OAuth2 + JWT)     |
                     |  - Spring Data JPA (PostgreSQL)       |
                     |  - Engine de Análise por IA           |
                     |  - Gerenciador de Consentimento       |
                     +---------+------------------+----------+
                               |                  |
           +-------------------+--+            +--+-------------------+
           | PostgreSQL Database  |            | Provedores Externos  |
           | (Usuários, Carteiras,|            | - Google/Apple/MS OIDC|
           |  Ativos, Consentos)  |            | - Provider de IA     |
           +----------------------+            | - Open Finance APIs  |
                                               +----------------------+
```

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem / Runtime**: Java 25 (OpenJDK 25)
- **Framework Backend**: Spring Boot 3.3.3 (Spring Web, Spring Security, Spring Data JPA)
- **Banco de Dados**: PostgreSQL 16
- **Autenticação**: Spring Security OAuth2 Client/Resource Server, JJWT 0.12.6
- **Frontend PWA**: HTML5, CSS3 Responsivo, JavaScript ES6, Web App Manifest, Service Worker
- **Conteinerização**: Docker, Docker Compose (Multi-stage build)
- **Gerenciador de Dependências**: Apache Maven 3.9.x

---

## 📁 Estrutura do Projeto

```
Fred_Invest_Agent/
├── docker-compose.yml              # Orquestração de containers (App + PostgreSQL)
├── Dockerfile                      # Multi-stage Docker build com Java 25
├── pom.xml                         # Configurações Maven e dependências
├── .vscode/                        # Perfis de execução e tarefas do VSCode
│   ├── launch.json
│   └── tasks.json
└── src/
    └── main/
        ├── java/com/fredinvest/
        │   ├── FredInvestApplication.java
        │   ├── config/             # SecurityConfig, JwtTokenProvider, Filter
        │   ├── controller/         # Auth, Portfolio, Ai, OpenFinance Controllers
        │   ├── dto/                # DTOs de transporte de dados
        │   ├── model/              # Entidades JPA (User, Portfolio, Asset, Consent)
        │   ├── repository/         # Interfaces Spring Data JPA
        │   └── service/            # Regras de negócio e integrações
        └── resources/
            ├── application.yml     # Configurações da aplicação e OAuth2
            └── static/              # Frontend PWA
                ├── index.html
                ├── manifest.json
                ├── sw.js
                ├── css/app.css
                └── js/app.js
```

---

## 🚀 Como Executar o Projeto

### Pré-requisitos
- [Docker](https://www.docker.com/) e Docker Compose instalados **OU**
- [JDK 25](https://jdk.java.net/25/) e [Maven 3.9+](https://maven.apache.org/) para execução local.

---

### Opção 1: Via Docker Compose (Recomendado)

Na raiz do repositório, execute:

```bash
docker compose up --build
```

A aplicação estará disponível em **`http://localhost:8080`**.

---

### Opção 2: Compilação e Execução Local via Maven

1. Certifique-se de que o PostgreSQL está rodando em `localhost:5432` com o banco `fredinvest`.
2. Compile e empacote o projeto:

```bash
mvn clean package -DskipTests
```

3. Execute o arquivo `.jar` gerado:

```bash
java -jar target/fred-invest-agent-1.0.0-SNAPSHOT.jar
```

Acesse no navegador: **`http://localhost:8080`**.

---

### Opção 3: Executar pelo VSCode

1. Abra a pasta do projeto no **VSCode**.
2. Vá até a aba **Run and Debug** (`Ctrl+Shift+D` / `Cmd+Shift+D`).
3. Selecione a configuração **`🚀 Executar FredInvestApplication (Spring Boot)`** e aperte **F5**.
4. Ou abra a paleta de comandos (`Ctrl+Shift+B`) e selecione **`Spring Boot: Run App`**.

---

## 📌 Principais Endpoints da API REST

| Módulo | Método | Endpoint | Descrição |
| :--- | :--- | :--- | :--- |
| **Auth** | `POST` | `/api/v1/auth/register` | Cadastro de usuário por e-mail e senha |
| **Auth** | `POST` | `/api/v1/auth/login` | Autenticação local e emissão de JWT |
| **Auth** | `POST` | `/api/v1/auth/social` | Login social OAuth2 (Google/Apple/MS) |
| **Carteira** | `GET` | `/api/v1/portfolios/summary` | Resumo consolidado do patrimônio e rentabilidade |
| **Carteira** | `POST` | `/api/v1/portfolios/{id}/assets` | Adição/Atualização de ativo na carteira |
| **Carteira** | `DELETE` | `/api/v1/portfolios/{id}/assets/{assetId}` | Remoção de ativo da carteira |
| **IA** | `POST` | `/api/v1/ai/analyze` | Análise da carteira por Inteligência Artificial |
| **IA** | `POST` | `/api/v1/ai/chat` | Chat financeiro virtual com IA |
| **Open Finance**| `GET` | `/api/v1/open-finance/institutions` | Lista de instituições financeiras suportadas |
| **Open Finance**| `POST` | `/api/v1/open-finance/consent` | Solicitação de consentimento |
| **Open Finance**| `POST` | `/api/v1/open-finance/consent/{id}/authorize` | Autorização e sincronização de ativos |

---

## 👤 Autor

Desenvolvido por **Fred Gruber**.
- Repositório: [https://github.com/fredgruber/Fred_Invest_Agent](https://github.com/fredgruber/Fred_Invest_Agent)

