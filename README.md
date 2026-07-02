# 💰 Finance Tracker — Backend API

<div align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Boot 3.2" />
  <img src="https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Spring_Security_&_JWT-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white" alt="Spring Security & JWT" />
  <img src="https://img.shields.io/badge/IA_Integrated-OpenRouter-412991?style=for-the-badge&logo=openai&logoColor=white" alt="AI Integrated" />
</div>

<br />

> API RESTful robusta, segura e escalável para o sistema **Finance Tracker**, projetada para gerenciar finanças pessoais completas com inteligência artificial integrada para auxílio em decisões de compra.

---

## 📋 Índice

- [✨ Funcionalidades](#-funcionalidades)
- [🚀 Tecnologias e Arquitetura](#-tecnologias-e-arquitetura)
- [🛠️ Pré-requisitos](#-pré-requisitos)
- [⚙️ Configuração do Ambiente (.env)](#-configuração-do-ambiente-env)
- [▶️ Como Executar](#-como-executar)
- [🧠 Funcionalidades de Inteligência Artificial (IA e PLN)](#-funcionalidades-de-inteligência-artificial-ia-e-pln)
- [🧪 Executando Testes Automatizados](#-executando-testes-automatizados)
- [📚 Principais Endpoints](#-principais-endpoints)
- [🤝 Contribuição](#-contribuição)
- [📄 Licença](#-licença)

---

## ✨ Funcionalidades

- 🔒 **Autenticação e Segurança (JWT)**: Cadastro, login, renovação/extensão de sessão e controle de acesso baseado em tokens JWT com Spring Security.
- 🏦 **Gestão Multicontas**: Controle de contas correntes, carteiras físicas, contas de investimento e acompanhamento em tempo real de saldos consolidados.
- 💳 **Cartões de Crédito & Faturas**: Gestão de cartões, monitoramento de limites disponíveis, datas de fechamento, vencimento e controle de faturas mensais.
- 💸 **Transações Financeiras**: Lançamento completo de **Receitas**, **Despesas** e **Transferências** entre contas, com histórico detalhado e paginação.
- 🏷️ **Categorias & Tags Customizadas**: Organização flexível de movimentações financeiras para análises granulares de gastos.
- 🔄 **Assinaturas & Gastos Recorrentes**: Cadastro e controle de serviços por assinatura (streaming, contas fixas, etc.) para projeção de gastos futuros.
- 🎯 **Metas & Orçamentos**: Planejamento de objetivos financeiros de curto, médio e longo prazo, com acompanhamento percentual de progresso.
- 📊 **Relatórios & Dashboard**: Endpoints de agregação de dados para geração de relatórios gráficos de despesas por categoria e evolução patrimonial.
- 🧠 **Inteligência Artificial (IA e PLN)**: Assistente preditivo que oferece categorização automática com aprendizado contínuo, recomendação do melhor cartão para compras no dia, análise de fadiga e custo-benefício de assinaturas, e simulação real de viabilidade financeira para desejos de compra.

---

## 🚀 Tecnologias e Arquitetura

O projeto foi construído seguindo as melhores práticas de Clean Architecture e SOLID no ecossistema Spring:

- **Linguagem**: Java 17
- **Framework Principal**: Spring Boot 3.2.0
- **Persistência & ORM**: Spring Data JPA / Hibernate
- **Banco de Dados**: PostgreSQL (Produção/Dev) e H2 Database (Testes isolados)
- **Segurança**: Spring Security 6 + Auth0 Java JWT
- **Inteligência Artificial**: Integração com APIs LLM (OpenRouter / DeepSeek / OpenAI) via serviço dedicado
- **Utilitários**: Lombok, Spring Dotenv (para gestão de variáveis de ambiente), Spring Boot DevTools
- **Testes**: JUnit 5, Spring Boot Test, JaCoCo (Cobertura de Código)

---

## 🛠️ Pré-requisitos

Antes de começar, você precisará ter instalado em sua máquina:
- [Java Development Kit (JDK) 17+](https://adoptium.net/)
- [Maven 3.8+](https://maven.apache.org/) (ou utilize o wrapper incluído na IDE)
- [PostgreSQL 14+](https://www.postgresql.org/) rodando localmente ou em container Docker

---

## ⚙️ Configuração do Ambiente (`.env`)

Crie um arquivo chamado `.env` na raiz do diretório `Finance-Tracker-Backend` (baseando-se no modelo abaixo) para configurar as variáveis de conexão com o banco de dados, chaves de segurança e integração com IA:

```env
# Configurações do banco de dados PostgreSQL
DB_HOST=localhost
DB_PORT=5432
DB_USER=postgres
DB_PASSWORD=sua_senha_do_postgres

# Configurações de Inteligência Artificial (OpenRouter / OpenAI)
OPENAI_BASE_URL=https://openrouter.ai/api/v1
OPENAI_API_KEY=sua_chave_de_api_openrouter_ou_openai
OPENAI_MODEL=deepseek/deepseek-v4-flash

# Configurações de Segurança (JWT & CORS)
JWT_SECRET=sua_chave_secreta_jwt_muito_segura_e_longa_123456
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

> ⚠️ **Importante**: Nunca compartilhe ou commite seu arquivo `.env` com chaves reais em repositórios públicos! O arquivo `.env` já está listado no `.gitignore`.

---

## ▶️ Como Executar

1. **Clone o repositório principal**:
   ```bash
   git clone https://github.com/seu-usuario/finance-tracker.git
   cd finance-tracker/Finance-Tracker-Backend
   ```

2. **Crie o banco de dados no PostgreSQL**:
   Conecte-se ao seu PostgreSQL e crie o banco de dados (por padrão, o Hibernate criará/atualizará as tabelas automaticamente de acordo com as configurações em `application.properties`):
   ```sql
   CREATE DATABASE finance_tracker;
   ```

3. **Compilar e rodar a aplicação via Maven**:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

4. **Acesse a API**:
   O servidor iniciará por padrão na porta **8080**:
   - URL Base: `http://localhost:8080`

---

## 🧠 Funcionalidades de Inteligência Artificial (IA e PLN)

O grande diferencial do **Finance Tracker** é o seu motor proativo de inteligência financeira integrado via APIs LLM (OpenRouter / DeepSeek). Em vez de respostas genéricas, o sistema analisa o histórico real do usuário para oferecer assistência preditiva em 4 pilares:

### 🏷️ Categorização Automática e Aprendizado Contínuo (PLN)
- **Classificação Inteligente**: Processamento de Linguagem Natural que analisa a descrição de transações e faturas importadas para atribuir automaticamente a categoria e tag mais adequadas (`/api/ia/categorizar`).
- **Loop de Aprendizado (Feedback Loop)**: O sistema aprende o padrão de consumo do usuário a cada correção manual (`/api/ia/correcao`), atualizando seu cache para refinar e personalizar futuras categorizações.
- **Feed Proativo de Insights**: Monitora continuamente as movimentações e gera alertas, dicas e sugestões personalizadas de economia no feed interativo (`/api/ia/insights`).

### 💳 Inteligência Avançada para Cartões de Crédito
- **Recomendação do "Melhor Cartão para Hoje" (`/melhor-cartao`)**: Analisa as datas de fechamento e vencimento de todos os cartões cadastrados e indica exatamente qual cartão deve ser utilizado nas compras do dia para obter o maior prazo possível de pagamento.
- **Projeção Multicartões (`/projecao-cartoes`)**: Cruza todos os parcelamentos ativos na carteira para projetar o impacto financeiro nas faturas futuras e calcula o desvio percentual em relação à média histórica do usuário para alimentar os indicadores visuais de tendência.
- **Aviso de Fechamento Iminente (`/aviso-fechamento`)**: Alerta o usuário sobre faturas que vão fechar entre 0 e 5 dias, prevenindo compras em ciclos desfavoráveis.
- **Alerta de Concentração de Gastos**: Identifica automaticamente quando despesas não-essenciais ultrapassam 50% de uma fatura em aberto.
- **Otimização de Parcelamentos**: Monitora parcelas que terminam no mês atual e notifica sobre a liberação daquela "folga" no orçamento e no limite do cartão.

### 🔄 Diagnóstico de Fadiga e Custo-Benefício de Assinaturas
- **Análise de Essencialidade e Fadiga (`/fadiga-assinatura`)**: Avalia serviços recorrentes contratados (streaming, softwares, clubes) para detectar sobreposições, excessos e gastos subutilizados com base no perfil de uso (diário, regular, pouco frequente).
- **Score de Eficiência e Reajustes (`/assinaturas/inteligencia`)**: Rastreia o histórico de aumentos de preço ao longo do tempo para calcular um Score de Eficiência de custo-benefício para cada assinatura.
- **Prevenção do "Efeito Dominó" (`/efeito-dominio`)**: Deteção proativa de cobranças automáticas que correm risco de falhar devido a limite insuficiente ou proximidade de vencimento no cartão vinculado.

### 🛍️ Simulador Preditivo e Lista de Desejos (Wishlist)
- **Simulador de Viabilidade Real (`/planejador-compras`)**: O usuário informa um desejo de compra (valor, parcelas e categoria) e a IA cruza esse dado com a projeção real de fluxo de caixa dos meses seguintes. O sistema entrega um veredito claro (**Viável / Não Viável**), apresenta justificativas financeiras e indica em qual mês futuro o gasto será 100% seguro sem desequilibrar o orçamento.
- **Monitoramento Contínuo de Desejos**: Permite armazenar itens em uma Lista de Desejos (Wishlist), com a IA reavaliando periodicamente quando o usuário atingirá as condições financeiras ideais para adquiri-los.

---

## 🧪 Executando Testes Automatizados

Para rodar a suíte de testes unitários e de integração com H2 Database em memória (JUnit + JaCoCo):
```bash
mvn test
```

---

## 📚 Principais Endpoints

Aqui estão alguns dos principais endpoints disponibilizados pela API:

| Método | Endpoint | Descrição | Requer Token? |
| :--- | :--- | :--- | :---: |
| `POST` | `/usuarios/register` | Cadastro de novo usuário | ❌ Não |
| `POST` | `/usuarios/login` | Autenticação e retorno de token JWT | ❌ Não |
| `GET` | `/usuarios/me` | Retorna o perfil do usuário logado | ✅ Sim |
| `GET` | `/contas` | Lista todas as contas bancárias/carteiras | ✅ Sim |
| `POST` | `/transacoes` | Cria uma nova transação (receita/despesa/transferência) | ✅ Sim |
| `GET` | `/cartoes` | Lista cartões de crédito e limites | ✅ Sim |
| `GET` | `/assinaturas` | Lista despesas recorrentes e assinaturas | ✅ Sim |
| `GET` | `/dashboard/resumo` | Retorna métricas agregadas e saldos do dashboard | ✅ Sim |
| `POST` | `/ia/categorizar` | Categoriza descrição de transação automaticamente via PLN | ✅ Sim |
| `POST` | `/ia/fadiga-assinatura` | Analisa essencialidade e fadiga de assinaturas ativas | ✅ Sim |
| `POST` | `/ia/melhor-cartao` | Indica o melhor cartão para comprar no dia (maior prazo) | ✅ Sim |
| `POST` | `/ia/planejador-compras` | Consulta o Assistente IA para simular viabilidade e mês ideal de compra | ✅ Sim |

---


## 📄 Licença

Este projeto está sob a licença MIT. Consulte o arquivo de licença para mais detalhes.

---
<div align="center">
  Desenvolvido com ❤️ para transformar a gestão de finanças pessoais.
</div>
