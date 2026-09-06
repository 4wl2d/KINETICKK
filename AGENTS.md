<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Project instructions

## Mandatory Pokeball skills

- Use the current installed `pokeball` skill for implementation, architecture,
  refactoring, debugging, testing and code review in this project. Read its
  current `SKILL.md` before starting relevant work; do not rely on a remembered
  or archived copy of its instructions.
- Resolve the skill through the active skill catalog and read the location it
  declares. The project's local installation is under `.agents/skills/`.
  Skill updates belong to the maintained skill source, not copied project docs.
- Read the current focused skill when the work touches its boundary:
  `pokeball-binding` for acceptance/dispatch, `pokeball-async` for effects and
  results, `pokeball-composition` for ownership and cross-authority interaction,
  and `pokeball-review` for review. Load only the relevant references.
- If a required skill cannot be found or read, report the missing prerequisite
  and resolve it before the affected work. Do not substitute old architecture
  guides for the required skill.
- Keep general Pokeball guidance in the skills. Do not recreate duplicate local
  development guides or per-task audit documents unless explicitly requested;
  report task findings and checks in the response.

Kotlin contracts, tests and project-specific policy/Assembly records remain the
source of truth for this application's behavior and selected bounds. The pinned
Core snapshot used by Gradle verification is a verification input, not the
source for selecting the current skill. Using an updated skill does not silently
change that baseline or the project's accepted policies.
