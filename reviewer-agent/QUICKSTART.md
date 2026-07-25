# ? Guide de Démarrage Rapide - AI Code Review Agent

## ? Implémentation Complète

L'agent de revue de code a été implémenté avec succès en suivant l'architecture DDD (Domain-Driven Design) et intégrant Spring AI avec function calling.

## ? Structure Créée

### Domain Layer (Logique Métier)
```
domain/
??? gitlab/
?   ??? GitLabService.java          ? Interface du service
?   ??? MergeRequestInfo.java       ? Value object
??? jira/
?   ??? JiraService.java             ? Interface du service
?   ??? IssueDetails.java            ? Value object
??? sonarqube/
?   ??? SonarQubeService.java        ? Interface du service
?   ??? QualityGateStatus.java       ? Value object
?   ??? IssuesSummary.java           ? Value object
??? review/
    ??? CodeReviewService.java       ? Interface du service
    ??? ReviewComment.java           ? Entity (structure du résultat)
    ??? ReviewContext.java           ? Aggregate (contexte complet)
```

### Application Layer (Orchestration)
```
application/
??? review/
?   ??? ReviewOrchestrator.java      ? Orchestrateur principal du workflow
?   ??? AiCodeReviewService.java     ? Implémentation avec Spring AI
??? tools/                            ? Spring AI Function Calling Tools
    ??? GitLabTools.java             ? @Tool pour GitLab API
    ??? JiraTools.java               ? @Tool pour Jira API
    ??? SonarQubeTools.java          ? @Tool pour SonarQube API
```

### Infrastructure Layer (Détails Techniques)
```
infrastructure/
??? gitlab/
?   ??? GitLabClient.java            ? RestClient GitLab
?   ??? GitLabProperties.java        ? Configuration
??? jira/
?   ??? JiraClient.java              ? RestClient Jira
?   ??? JiraProperties.java          ? Configuration
??? sonarqube/
?   ??? SonarQubeClient.java         ? RestClient SonarQube
?   ??? SonarQubeProperties.java     ? Configuration
??? config/
    ??? InfrastructureConfig.java    ? Configuration Spring
```

### Web Layer (API REST)
```
web/
??? GitlabAgentController.java       ? Endpoints pour MR info
??? CodeReviewController.java        ? Endpoint de revue complète + webhook
```

## ? Workflow Complet Implémenté

```
1. ? GitLabTools.fetchMergeRequestInfo(mrUri)
   ??> Récupère: titre, description, branches, auteur, diff, ticket Jira
   
2. ? Extraction automatique du ticket Jira depuis le titre
   ??> Pattern regex: [A-Z]+-\d+ (ex: PROJ-123)
   
3. ? JiraTools.getIssueDetails(jiraTicket)
   ??> Récupère: summary, description, acceptance criteria, status, priority
   
4. ? SonarQubeTools.getQualityGateStatus(projectKey)
   ??> Récupère: statut (PASSED/FAILED), conditions
   
5. ? SonarQubeTools.getIssuesForBranch(projectKey, branch)
   ??> Récupère: issues non résolues (CRITICAL, MAJOR, etc.)
   
6. ? AiCodeReviewService.performReview(context)
   ??> Claude Opus analyse avec prompt augmenté
   ??> Génère ReviewComment structuré (JSON Schema)
   
7. ? GitLabTools.postMergeRequestComment(mrUri, formattedComment)
   ??> Poste le commentaire en Markdown formaté
   
? Revue de code complète publiée !
```

## ? Fonctionnalités Clés

### Spring AI Function Calling
Les **@Tool** permettent au LLM d'appeler automatiquement les fonctions :
- ? Le LLM décide quels outils utiliser
- ? Appels automatiques des APIs externes
- ? Agrégation intelligente des résultats
- ? Génération de réponse structurée

### Pattern RAG Implémenté
- **R**etrieval: GitLab + Jira + SonarQube
- **A**ugmented: Prompt enrichi avec toutes les données
- **G**eneration: Claude génère la revue structurée

### Extraction Intelligente Jira
Le `JiraClient` extrait automatiquement les critères d'acceptation depuis la description :
- Pattern "Acceptance Criteria:"
- Format Given/When/Then
- Listes à puces ou numérotées

## ? API Endpoints

### 1. Récupérer les infos d'une MR (Direct)
```bash
curl "http://localhost:8080/api/mr/info?mrUri=https://gitlab.com/group/project/-/merge_requests/123"
```

### 2. Effectuer une revue complète
```bash
curl -X POST "http://localhost:8080/api/review/perform?mrUri=https://gitlab.com/group/project/-/merge_requests/123&sonarProjectKey=group_project"
```

### 3. Webhook GitLab (Automatique)
```bash
curl -X POST http://localhost:8080/api/review/webhook/gitlab \
  -H "Content-Type: application/json" \
  -d '{
    "object_kind": "merge_request",
    "object_attributes": {
      "action": "opened",
      "url": "https://gitlab.com/group/project/-/merge_requests/123"
    },
    "project": {
      "path_with_namespace": "group/project"
    }
  }'
```

## ?? Configuration Requise

Éditez `application.yaml` ou définissez ces variables d'environnement :

```bash
# GitLab
export GITLAB_TOKEN=glpat-xxxxxxxxxxxx

# Jira
export JIRA_EMAIL=your-email@company.com
export JIRA_API_TOKEN=your-jira-api-token

# SonarQube
export SONARQUBE_TOKEN=squ-xxxxxxxxxxxx
```

## ? Démarrage

```bash
# Compiler le projet
mvnw clean compile

# Lancer l'application
mvnw spring-boot:run

# Ou avec Docker
docker build -t reviewer-agent .
docker run -p 8080:8080 \
  -e GITLAB_TOKEN=$GITLAB_TOKEN \
  -e JIRA_EMAIL=$JIRA_EMAIL \
  -e JIRA_API_TOKEN=$JIRA_API_TOKEN \
  -e SONARQUBE_TOKEN=$SONARQUBE_TOKEN \
  reviewer-agent
```

## ? Exemple de ReviewComment Généré

```json
{
  "summary": "The merge request implements user authentication with JWT. Code quality is good overall with minor improvements needed.",
  "issues": [
    {
      "severity": "MAJOR",
      "filePath": "src/main/java/com/example/AuthController.java",
      "lineNumber": 42,
      "description": "Potential SQL injection vulnerability. Use prepared statements.",
      "category": "Security"
    }
  ],
  "suggestions": [
    {
      "filePath": "src/main/java/com/example/UserService.java",
      "lineNumber": 67,
      "description": "Consider using Optional<User> instead of returning null",
      "suggestedCode": "public Optional<User> findById(Long id) { ... }"
    }
  ],
  "overallAssessment": "? Meets Jira acceptance criteria. One security issue must be fixed before merge. Overall architecture is solid."
}
```

## ? Format Markdown sur GitLab

Le commentaire est automatiquement formaté avec :
- ? Titre et émojis
- ? Résumé
- ?? Issues avec sévérité colorée (???)
- ? Suggestions avec code
- ? Évaluation globale

## ? Sécurité

- ? Tokens externalisés (variables d'env)
- ? Gestion des erreurs (try/catch)
- ? Validation des entrées
- ?? TODO: Validation webhook GitLab avec secret token

## ? Documentation

Consultez `README_ARCHITECTURE.md` pour :
- Architecture détaillée DDD
- Diagrammes de flux
- Explication du pattern RAG
- Bonnes pratiques

## ? Prochaines Étapes

1. **Tests** : Ajouter tests unitaires et d'intégration
2. **Logging** : Intégrer SLF4J/Logback
3. **Métriques** : Ajouter Micrometer/Prometheus
4. **Cache** : Cache Redis pour les appels API
5. **Webhook Security** : Valider les webhooks GitLab
6. **CI/CD** : Pipeline GitLab CI

## ? Avantages de Cette Implémentation

1. **Séparation des préoccupations** : DDD avec 3 couches distinctes
2. **Testabilité** : Interfaces facilitent les mocks
3. **Maintenabilité** : Code métier isolé de l'infrastructure
4. **Évolutivité** : Facile d'ajouter de nouveaux outils
5. **Intelligence** : Spring AI avec function calling automatique
6. **Robustesse** : Gestion gracieuse des erreurs (Jira/SonarQube optionnels)

## ? Concepts Spring AI Utilisés

- **ChatClient** : Client conversationnel avec Claude
- **@Tool** : Annotation pour function calling
- **PromptTemplate** : Templates de prompts avec variables
- **Entity Mapping** : Conversion automatique JSON ? Java Record

---

**Votre agent de revue de code est maintenant opérationnel ! ?**

