Review the following merge request.

## Merge request
Project: ${projectId}
MR: !${mergeRequestIid}
Title: ${title}
Author: ${author}
Branches: ${sourceBranch} -> ${targetBranch}

Description:
${description}

## Requirement context
${jiraContext}

## Automated quality gate
${qualityContext}

## Change
```diff
${diff}
```

## Your task

Classify every real problem you find as a `Finding`, using the severity taxonomy in
your governance section. Report:

- `summary` — what this change does and whether it does it well, in two or three sentences.
- `findings` — the list of problems. Each needs a severity, the file path, a line
  number where you can identify one, a clear description of the defect and its
  consequence, and a category (Security, Bug, CodeSmell, Performance, Test).

Rules that matter more than thoroughness:

1. **Do not state a disposition.** Approval is computed from your severities, not
   from anything you write. Classify accurately and the decision follows.
2. **Report only defects you can point at in this diff.** A finding you cannot tie
   to a specific line is noise, and noise is what makes review agents get switched
   off. If the change is clean, return an empty findings list — that is a valid and
   useful answer.
3. **CRITICAL and MAJOR block the merge.** Use them for security vulnerabilities,
   data loss, production-breaking bugs, significant logic errors, missing error
   handling and performance regressions. Style, naming and documentation are MINOR.
   An improvement idea is a SUGGESTION.
4. **Judge against the stated requirement** where one is supplied above. Code that
   works but does not do what was asked is a MAJOR finding.
5. Do not repeat what the automated quality gate already reports. Add what a tool
   cannot see.
