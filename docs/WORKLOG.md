# Transakt Worklog

## Day 1 — 19 Jul 2026
**Built:** Dev environment (JDK 21 LTS, IntelliJ CE, Postgres.app on 5432, Postman) + repo initialized (README, .gitignore, docs/)
**Concepts learned:** LTS vs 6-month Java releases and why companies run LTS; markdown raw vs preview mode; .gitignore only blocks untracked files; commit (local) vs push (remote); OAuth in the wild (authorized JetBrains<->GitHub)
**Interview line:** "I chose Java 21 because it's the LTS release production systems and the Spring ecosystem standardize on."
**Mistake & fix:** committed .idea/ before .gitignore existed -> cleaned with git rm -r --cached .idea