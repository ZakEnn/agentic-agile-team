# AI Code Review Agent - Architecture DDD avec Spring AI

## ?? Architecture

Ce projet suit une architecture **Domain-Driven Design (DDD)** avec trois couches principales :

```
com.orange.ai.reviewer_agent
??? domain/              # Couche Domaine (Business Logic)
?   ??? gitlab/
?   ?   ??? GitLabService.java (interface)
?   ?   ??? MergeRequestInfo.java (value object)
?   ??? jira/
?   ?   ??? JiraService.java (interface)
?   ?   ??? IssueDetails.java (value object)
?   ??? sonarqube/
?   ?   ??? SonarQubeService.java (interface)
?   ?   ??? QualityGateStatus.java (value object)
?   ?   ??? IssuesSummary.java (value object)
?   ??? review/
?       ??? CodeReviewService.java (interface)
?       ??? ReviewComment.java (entity)
?       ??? ReviewContext.java (aggregate)
?
??? application/         # Couche Application (Orchestration & Use Cases)
?   ??? review/
?   ?   ??? ReviewOrchestrator.java (orchestrateur principal)
?   ?   ??? AiCodeReviewService.java (implémentation avec Spring AI)
?   ??? tools/           # Spring AI Function Calling Tools
?       ??? GitLabTools.java
?       ??? JiraTools.java
?       ??? SonarQubeTools.java
?
??? infrastructure/      # Couche Infrastructure (Détails techniques)
    ??? gitlab/
    ?   ??? GitLabClient.java (implémentation REST)
    ?   ??? GitLabProperties.java (configuration)
    ??? jira/
    ?   ??? JiraClient.java (implémentation REST)
    ?   ??? JiraProperties.java (configuration)
    ??? sonarqube/
    ?   ??? SonarQubeClient.java (implémentation REST)
    ?   ??? SonarQubeProperties.java (configuration)
    ??? config/
        ??? InfrastructureConfig.java
```

## ? Workflow de Revue de Code

### Flux Complet (Pattern RAG - Retrieval Augmented Generation)

```
1. ? Récupération du contexte MR depuis GitLab
   ? (GitLabTools via Spring AI Function Calling)
   
2. ? Extraction du ticket Jira depuis le titre MR
   ? (Regex pattern matching)
   
3. ? Récupération des détails Jira
   ? (JiraTools via Spring AI Function Calling)
   
4. ? Récupération du Quality Gate SonarQube
   ? (SonarQubeTools via Spring AI Function Calling)
   
5. ? Récupération des issues SonarQube
   ? (SonarQubeTools via Spring AI Function Calling)
   
6. ? Analyse IA avec prompt augmenté
   ? (Spring AI ChatClient + Claude Opus)
   
7. ? Génération du ReviewComment structuré
   ? (JSON Schema validation)
   
8. ? Publication du commentaire sur GitLab
   ? (GitLabTools)
   
? Revue de code complète
```

## ? Utilisation

### Configuration

Éditez `application.yaml` ou utilisez des variables d'environnement :

```bash
# GitLab
export GITLAB_TOKEN=glpat-xxxxxxxxxxxx

# Jira
export JIRA_EMAIL=your-email@company.com
export JIRA_API_TOKEN=your-jira-token

# SonarQube
export SONARQUBE_TOKEN=squ_xxxxxxxxxxxx
```

### API Endpoints

#### 1. Récupérer les informations d'une MR (Direct)
```bash
GET /api/mr/info?mrUri=https://gitlab.com/group/project/-/merge_requests/123
```

#### 2. Récupérer les informations d'une MR (via IA)
```bash
GET /api/mr/info/ai?mrUri=https://gitlab.com/group/project/-/merge_requests/123
```

#### 3. Effectuer une revue de code complète
```bash
POST /api/review/perform
  ?mrUri=https://gitlab.com/group/project/-/merge_requests/123
  &sonarProjectKey=group_project
```

#### 4. Webhook GitLab (automatique)
```bash
POST /api/review/webhook/gitlab
Content-Type: application/json

{
  "object_kind": "merge_request",
  "object_attributes": {
    "action": "opened",
    "url": "https://gitlab.com/group/project/-/merge_requests/123"
  },
  "project": {
    "path_with_namespace": "group/project"
  }
}
```

## ? Spring AI - Function Calling

Les **Tools** Spring AI permettent au LLM d'appeler automatiquement les fonctions nécessaires :

### GitLabTools
- `fetchMergeRequestInfo(String mrUri)` - Récupère les infos de la MR
- `postMergeRequestComment(String mrUri, String comment)` - Poste un commentaire

### JiraTools
- `getIssueDetails(String issueKey)` - Récupère les détails du ticket Jira

### SonarQubeTools
- `getQualityGateStatus(String projectKey)` - Récupère le statut Quality Gate
- `getIssuesForBranch(String projectKey, String branchName)` - Récupère les issues

## ? Structure du ReviewComment (Output)

```json
{
  "summary": "Brief overview of changes",
  "issues": [
    {
      "severity": "CRITICAL|MAJOR|MINOR",
      "filePath": "src/main/java/...",
      "lineNumber": 42,
      "description": "Issue description",
      "category": "Security|Bug|CodeSmell|Performance"
    }
  ],
  "suggestions": [
    {
      "filePath": "src/main/java/...",
      "lineNumber": 42,
      "description": "Suggestion description",
      "suggestedCode": "// Code example"
    }
  ],
  "overallAssessment": "Ready to merge | Needs changes | Blocking issues"
}
```

## ? Avantages de l'Architecture DDD

1. **Separation of Concerns** : Chaque couche a sa responsabilité
2. **Testabilité** : Les interfaces du domaine facilitent les tests unitaires
3. **Maintenabilité** : Le code métier est isolé des détails techniques
4. **Évolutivité** : Facile d'ajouter de nouveaux outils ou services
5. **Indépendance** : Le domaine ne dépend pas de l'infrastructure

## ? Sécurité

- Utilisez des **variables d'environnement** pour les tokens
- Les tokens GitLab/Jira/SonarQube doivent avoir des permissions minimales
- Validez les webhooks GitLab avec un secret token (à implémenter)

## ? Dépendances Spring Boot

- `spring-boot-starter-web` - REST APIs
- `spring-ai-anthropic-spring-boot-starter` - Spring AI avec Claude
- `spring-boot-starter-actuator` (optionnel) - Monitoring

## ? Tests

Structure de tests recommandée :
```
src/test/java/
??? domain/              # Tests unitaires du domaine
??? application/         # Tests d'intégration des use cases
??? infrastructure/      # Tests des clients externes (avec Mocks)
```

## ? Notes d'Implémentation

### Pattern RAG (Retrieval Augmented Generation)
1. **Retrieval** : Les Tools récupèrent le contexte (GitLab, Jira, SonarQube)
2. **Augmentation** : Le prompt est enrichi avec toutes les données
3. **Generation** : Le LLM génère une revue structurée

### Spring AI ChatClient
Le `ChatClient` est configuré avec tous les tools disponibles, permettant au LLM de :
- Décider quels outils appeler
- Appeler les outils automatiquement
- Agréger les résultats
- Générer une réponse structurée

## ? Prochaines Étapes

1. Ajouter des tests unitaires et d'intégration
2. Implémenter la validation des webhooks GitLab
3. Ajouter un système de logging (SLF4J)
4. Implémenter un cache pour les appels API externes
5. Ajouter des métriques avec Micrometer
6. Dockeriser l'application

