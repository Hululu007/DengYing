---
name: software-development-workflow
description: >-
  Use when developing features, fixes, or refactoring in an existing or new
  project. Work in small, verified increments: understand the current
  boundaries, agree the outcome and the Definition of Done, build a thin
  vertical slice, prove it with checks you actually ran, then inspect and
  adapt. Applies empirical process control, evolutionary design, and
  risk-scaled rigor while preserving architecture and release gates.
metadata:
  short-description: Incremental, evidence-driven development workflow
---

# Software Development Workflow

Development comes first. Use this skill to deliver software changes in small, usable increments. Working software is the primary measure of progress; each increment must leave the system releasable. Design and quality must keep the next change cheap. Documents earn their place only when they carry a decision the code cannot show or a check the next person must run. It targets one developer and one coding agent, translating agile ideas into executable work units rather than team ceremonies: Product Owner, Scrum Master, daily meeting, and program-planning roles are not added. When several teams share one product, see [Scaling note](#scaling-note).

## Standards references

This skill adapts process ideas from ISO/IEC/IEEE 12207:2026 (life cycle processes), 29148:2018 (requirements engineering), 42010:2022 (architecture description), 29119-2/-3:2021 (test processes), and the SWEBOK Guide V4.0a body of knowledge. ISO/IEC/IEEE 12207:2026 allows life cycle processes to be applied "concurrently, iteratively, and recursively" and does not require a specific life cycle model; it is applicable to agile approaches. This skill does not establish formal conformance or certification, and these references do not prescribe directory names, a layering pattern, or one build module per folder.

## Core principles

1. **Empirical process control:** Make work, evidence, and assumptions transparent; inspect frequently; adapt from what you learn. Inspection without adaptation is pointless.
2. **Working software measures progress:** A usable, verified increment counts. A completed document or an approved design does not.
3. **Small, vertical, valuable increments:** Prefer thin end-to-end slices that deliver observable value. Use INVEST as a slicing aid: Independent, Negotiable, Valuable, Estimable, Small, Testable. Small and Testable matter more than elaborate estimates.
4. **Welcome change:** Put new information in the ordered backlog and re-order it. Do not freeze the whole scope; renegotiate the current increment's scope as more is learned without endangering its goal.
5. **Build quality in and design evolutionarily:** Use one shared Definition of Done, continuous testing, simple design, refactoring, and fast feedback; quality does not decrease as increments accumulate. Keep a little breadth-wise design ahead, then work out details just in time for the slice being delivered. Tests, refactoring, and explicit boundaries keep design reversible; agility is not the absence of architecture.
6. **Understand before you change:** Read the code, imports, public interfaces, and build configuration of everything you touch, plus `AGENTS.md` and repository conventions. An existing project has boundaries whether or not anyone wrote them down.
7. **Empirical verification, risk-based rigor, and context fit:** Claim a check passed only after you ran it and saw the result; state what could not run and why. Scale design depth, checks, and cadence to the effect of the change; do not impose heavy process on a small edit. Empirical evidence for agile methods is limited, so tailor to the project's characteristics rather than claiming one method is universally superior.
8. **Enforced module boundaries:** Apply separation of concerns, high cohesion and low coupling, information hiding, and explicit dependency constraints. Verify contracts, imports, visibility, and build dependencies; an orderly directory tree does not prove architectural separation.
9. **Limit WIP, keep flow, and inspect/adapt:** Keep few items in progress, finish before starting, work in small batches, and maintain a sustainable pace. At regular intervals, improve the process, tools, interactions, and Definition of Done. Simplicity means maximizing the amount of work not done.

## The incremental loop

**Discover, Design, Build, Verify, and Release are activities, not one-pass phases.** They happen concurrently, iteratively, recursively, and incrementally inside each work cycle. Do not run a miniature stage gate inside every increment.

### Entry: one lightweight agreement

At the start of a change, establish only what future increments cannot infer: the intent, desired outcome, and value receiver; the constraints and scope boundary; a boundary-map baseline from the code per [Working in an existing project](#working-in-an-existing-project); the risk tier and decision rights; and one shared Definition of Done with initial acceptance criteria.

Get explicit confirmation of the outcome, value, scope boundary, risk tier, and Definition of Done. Do not treat silence as approval. Review and re-confirm when the confirmed scope materially changes.

### Per increment

Repeat this loop until the outcome is met or the backlog says to stop. Multiple increments may be produced within one work cycle; keep the system releasable throughout. A review is an inspect-and-adapt opportunity, not a gate to releasing value.

1. **Select:** Take the highest-value ready-enough item from the single ordered backlog. Keep the increment small, vertical, and testable. State its goal and acceptance criteria.
2. **Shape:** Do just-enough design for this slice: the four architecture checks, affected contracts, dependency constraints, component placement, risks, and recovery path. Detail only what this increment needs.
3. **Build:** Implement the slice. Follow the established placement, encapsulation, boundaries, and dependency direction. Keep the footprint inside the slice. Synchronize the affected documents as the code changes.
4. **Verify:** Run the checks continuously. Reconcile the change against the acceptance criteria and the design. Record the actual outcomes and the gaps.
5. **Done:** An increment is born only when it meets the Definition of Done. Otherwise it returns to the backlog; do not call it done, release it, or present it as a completed result.
6. **Inspect and adapt:** Demonstrate the usable increment against its acceptance criteria, decide what to do next, adjust the backlog, and record one concrete process improvement. Repeat.

## Backlog and slicing

- Keep one ordered backlog as the single source of work; refine it continuously by breaking items down, making them more precise, and re-ordering them as learning changes their value. Write each item so it is Independent, Negotiable, Valuable, Estimable, Small, and Testable. A story is not an explicit feature contract; details are co-created as work proceeds. Nonfunctional requirements must also be testable. State acceptance criteria before completing the slice; they are the working agreement on the quality an item must meet.
- Slice vertically through the layers rather than delivering one layer at a time; a walking skeleton may be the first increment, and split by behavior, risk, or learning value, not by file type. Use a time-boxed spike when the main unknown is knowledge: it produces a decision, an estimate, or a rejected option, and proof-of-concept code does not become a deliverable increment unless rebuilt to the Definition of Done. A Definition of Ready is optional refinement guidance, not an approval gate; do not weaponize it into a new phase.

## Definition of Done

Maintain one shared Definition of Done. An organization standard is the minimum; teams working on the same product must share the same definition. The moment an item meets it, an increment is born. At minimum, cover:

- The acceptance criteria, automated checks, project's existing test command, and architecture and boundary checks pass, with no new baseline violation.
- The changed behavior and failure paths have checks that fail before the change and pass after it; the affected documents, records, links, and build paths are synchronized.
- The increment is releasable and the unresolved checks are explicitly open.

## Human decision gates

- Confirm the outcome, value, scope boundary, risk tier, and Definition of Done at entry. Do not request a signature for each internal activity. Do not treat silence as approval for the decisions above.
- Get renewed explicit approval when the confirmed scope materially changes, when a decision is irreversible or affects an external system, when a safety or compliance decision is at stake, and before an authorized release.

## Risk tiers

Choose the tier from the effect of the change, not from its size. A file count, a line count, and a keyword match are not tier signals. Judge the change by what it can break, who it affects, whether it is reversible, and what stays unknown. Name the mechanism that produces the effect; a scope word alone does not set the tier.

| Tier | Typical effect | Design depth | Decision point |
|---|---|---|---|
| **1: Major feature or restructuring** | Adds a capability, crosses a component or platform boundary, or replaces a contract that other components or users depend on. | Full chain: requirement, design, component boundaries, risks, migration and recovery, still delivered incrementally. | Confirm the entry agreement and each usable increment. Lengthen the increment and deepen the design only when the risk requires it. |
| **2: Module enhancement** | Adds an interface or a state behavior inside one module and keeps the existing contracts compatible. | Affected design and contracts, the exports, the dependencies, and the verification approach. | Confirm the entry agreement; inspect the verified increment before re-ordering the backlog. |
| **3: Defect fix** | Corrects behavior that is wrong today, including an edge case, a race, or a production defect. | The root cause and the proportional fix. Check the boundary impact. | Reproduce or explain the root cause. Get the regression check before commit. |
| **4: Minor edit** | Alters a typo, a comment, a local name, or formatting, and no behavior or contract changes. | None, unless the edit reveals drift. | Review the diff. |

Do not create paperwork for a trivial change. Tiering does not excuse a boundary violation, an unverified claim, a missing Definition of Done, or a missing authorization. When law, contract, scale, or safety genuinely requires a more predictive path, increase design depth, increment length, and stage confirmation on the same workflow. This is a dial, not a second process. Use the project's characteristics to decide; do not assume agile or predictive methods are automatically superior.

## Architecture: four design checks

Apply these checks to the components the change affects, proportionally to the tier. Establish the current state first, then judge the change.

| Principle | Obligation | What to inspect |
|---|---|---|
| **Separation of concerns** | Name each component's responsibility. Separate business policy, presentation, platform adaptation, and infrastructure where those concerns exist. Place cross-cutting concerns explicitly. | Classes, imports, calls, and tests. Look for mixed responsibilities and for platform or UI details leaking into platform-independent policy. |
| **High cohesion, low coupling** | Group behavior and data that serve one responsibility and change for the same reason. Use narrow contracts. | Dependencies. Look for shared mutable state, cycles, implementation access, and changes that force unrelated components to move together. Explain the change locality. |
| **Information hiding** | Expose the required contracts only. Hide implementation types, internal models, and replaceable mechanisms through real visibility and build controls. | Exported symbols, visibility, public signatures. Consumers must not reach into internal paths, mutable internals, or implementation-only types. |
| **Layering and dependency constraints** | Document which source and build dependencies are allowed and which are forbidden. Distinguish runtime call direction from compile-time dependency direction. | Imports and build metadata. Check reverse dependencies and cycles. Use visibility controls, dependency rules, lint, architecture tests, or build checks. |

### Working in an existing project

An existing project already has boundaries. Establish them before you judge a change.

1. Reconstruct the boundary map from the code, not only from the documents. List the modules, packages, or build units that exist now. Record the import edges. Record the cycles and the forbidden-direction edges that exist now.
2. Set the document precedence per claim class. Current behavior: the code and the configuration decide. Intended behavior: the approved requirement, contract, or decision record decides. When the two disagree, record the disagreement and ask the owner. Do not silently follow a stale document.
3. Record the baseline before you change anything. Write the current violation set to a file in the repository's convention, for example `architecture-baseline.md`. The change must not add a violation. Fixing a baseline violation is separate scope; get approval before you include it. Analyze the impact in the reverse direction: find the consumers of each component you will change and name the affected components, public contracts, and tests.
4. Contain the footprint. Do not add an import that crosses a boundary the change does not need. Do not move a file the change does not own. Report an out-of-scope structural problem; do not fix it here.

If the project has no architecture record, write the boundary map from step 1 as a minimal artifact in the repository's convention, or record it in the change record. Do not invent a full architecture description.

### Component placement

- Place files by responsibility, not by import convenience. Record which kind of boundary the design relies on: a directory, a package, or a build module; they differ, and state what the toolchain can enforce. Do not describe a folder as enforcing isolation when access stays unrestricted.
- Keep tests with the behavior they own, per the repository convention; distinguish unit, integration, and platform tests. Do not dump unrelated responsibilities into `utils`, `common`, or `manager`: a shared module needs a defined purpose and a dependency policy.
- Do not create a build module for every folder or class; justify one by dependency enforcement, ownership, platform isolation, reuse, testing, or release needs. Reuse the existing enforcement tools; if a rule lacks enforcement, report the gap and propose a proportional check for approval. Do not silently install tools or claim a rule is enforced.
- Do not impose Clean Architecture or a fixed domain, data, or UI layout by default. Do not switch a platform runtime without the owner's decision.

### Evolutionary design

Evolutionary design is viable only with enabling practices: simple design, refactoring, fast tests, continuous integration, and patterns used evolutionarily. Uncontrolled evolution degenerates into ad hoc tactical decisions and software entropy.

- Do enough upfront breadth-wise design to expose the main boundaries, then dive into details when delivering a specific slice. Use YAGNI to defer capability, not to skip tests, refactoring, boundary enforcement, or the Definition of Done; keep decisions reversible where learning is still expected and build architectural runway just in time.
- Refactor as part of the increment that needs the change; do not postpone a structural correction to a speculative cleanup phase. Each increment must not add a violation to the recorded baseline.

## Testing and verification

A change is done when a check observed it. Run the checks; do not describe them.

- Give every changed behavior a check that fails before the change and passes after it. If a check cannot exist, record why.
- Give every defect fix a regression check that reproduces the original defect; the check must fail on the old code. Run the project's existing test command and record the exact command and the observed result. Cover the failure paths the change can create, not only the successful path, and name the failure path each check covers.
- Treat a structural check as a test. The import rule, the cycle check, and the boundary check are architecture tests.
- Compare the result with the baseline: report an added violation as a defect and a pre-existing violation as a baseline entry. A check that could not run stays open; state the reason and do not mark it passed. Do not write a test plan document for a small change; put the check list in the change record.

Perform testing continuously across the delivery lifecycle, not in one late phase. Use the Agile Testing Quadrants as a coverage checklist, not a sequence:

| Quadrant | Purpose | Examples |
|---|---|---|
| **Q1: technology-facing, guides development** | Prevent defects before and during coding. | Unit tests, component tests, test-first programming. |
| **Q2: business-facing, guides development** | Turn examples into specifications that guide coding. | Acceptance examples, story tests, functional tests. |
| **Q3: business-facing, critiques the product** | Find missing behavior and discover whether the product works for people. | Exploratory testing, usability testing, user acceptance. |
| **Q4: technology-facing, critiques the product** | Evaluate nonfunctional qualities. | Performance, security, reliability, load, and resilience tests. |

The numbers imply no order. Most small increments use techniques from several quadrants concurrently. Do not defer Q4 "ilities" to a final hardening phase.

Keep automated unit tests as the broad foundation, add service and integration tests where they give faster or more reliable feedback than end-to-end tests, and keep end-to-end tests focused. Seek automated feedback in under ten minutes locally and in CI. Do not declare work development-complete unless automated acceptance tests pass. Testers and developers work alongside one another; manual exploratory, usability, and acceptance testing happen throughout. Remove flaky tests rather than tolerating them. In a brownfield project, start with high-value acceptance tests and require tests for new or changed functionality instead of retrofitting a comprehensive suite at once.

A functional test passing does not prove dependency isolation. Directory appearance alone is not an acceptance criterion. Report each check's method and actual outcome. Never substitute a proposed check for executed evidence.

## Feedback, flow, and continuous delivery

- Visualize the work, set a WIP limit, and stop starting to start finishing; high utilization without slack increases context switching and slows flow. Work in small batches and prefer trunk-based development: few active branches, merges to trunk at least daily, and no long integration freeze. Use synchronous review or pair programming to keep feedback fast, and use continuous integration: keep the build fast, make automated tests comprehensive, and treat a broken build as the highest-priority work.
- Use a deployment pipeline that gives feedback from build, static analysis, automated functional and nonfunctional tests, and manual exploration. Separate code deployment from feature release with feature toggles where useful; constrain their number. Continuous delivery means changes of all kinds can be released on demand quickly, safely, and sustainably; it does not mean continuous deployment, and increasing deployment frequency without improving architecture, process, and skills raises failure rates and burnout.
- Monitor operational signals after release and feed the findings back into the backlog.
- Use DORA metrics as local improvement signals, not targets: change lead time, deployment frequency, failed deployment recovery time, change fail rate, and deployment rework rate. Measure per application or service, keep the context visible, and do not blend unrelated teams. Speed and stability are not a tradeoff; setting a metric as a goal invites gaming.

## Inspect and adapt

- At each increment review, demonstrate the usable increment against its acceptance criteria, discuss what was learned, collaborate on the next most valuable work, and adjust the backlog. At a retrospective, inspect individuals, interactions, processes, tools, assumptions, and the Definition of Done; identify the most helpful change and put it into the next increment. Scale the cadence to the tier: a major change gets a full review and retrospective; a defect fix or minor edit can be one short record.

## Traceability and records

- Reuse the identifiers the repository already uses; if it has none, assign stable IDs to requirements and link each ID to its origin. Keep one authoritative source for each requirement, design, decision, and test record; link supporting notes and do not create conflicting copies.
- Link each increment to its backlog item, its acceptance criteria, its Definition of Done, and the executed evidence. Keep a minimal decision record only when the code cannot show why a choice was made. Follow the repository conventions for source, tests, scripts, configuration, and generated outputs; keep maintained and generated artifacts apart and credentials out of source control.
- Resolve the authority of a document per claim class. Follow the project's own rule first; read the charter, the process document, or `AGENTS.md` for a declared precedence rule. A project may declare that the implementation is authoritative and that documents get corrected to match it. When the project declares no rule, the code and the configuration decide current behavior, and the approved requirement or decision record decides intended behavior.
- Re-check a document's status before you treat it as a source. A document marked draft, superseded, or archived does not decide current behavior. A decision record states why the project made a choice; read it before you propose to reverse that choice.
- Record the change, the commands you ran, the results, and the decisions you made; put the record where the project keeps records. Update the documentation index and the affected links, build paths, scripts, and imports when you move or rename a file. For a structural migration, record the old-to-new paths, the compatibility impact, the order, and the recovery, and get design approval first; separate moves from behavior changes. Do not reorganize unrelated directories during a narrow fix; report an out-of-scope structural problem separately.

## Documentation style

Write technical artifacts in the project's own conventions. When `hai-simplified-technical` is available and permitted, use it as the style layer; it adapts ASD-STE100 ideas to software documentation and is not official ASD-STE100 certification. Do not apply it to marketing or creative text. Requirements and acceptance criteria use the project's fixed vocabulary; if it has none, use `must / must not / should / should not / can / need not` in English, or `应 / 不应 / 宜 / 不宜 / 可 / 不必` in Chinese. Keep one topic per sentence. Preserve obligation strength. Replace a vague qualifier with a known condition; do not invent a threshold the source does not give.

Before a document review gate, run the style checker on the changed artifacts, using a Python executable that exists on the host:

```text
<python executable> <hai-simplified-technical skill dir>/scripts/check.py <file> --lang en
<python executable> <hai-simplified-technical skill dir>/scripts/check.py <file> --lang zh
```

Run it twice on a document that mixes Chinese and Latin text, once per language. In the default auto mode the checker skips a line whose Latin word count is higher than its CJK character count, so a mixed line can pass unchecked. Read the checker's source and run it once with no file argument before you interpret its exit status; it returns `1` for a hard-rule hit and also for a crash, so read its output. Its findings are review candidates, not proof of defects. Keep three checks apart: style (is the wording clear), fact (does the document match the verified code and contracts), and architecture (are the interfaces and dependency constraints enforced); a style checker cannot establish the last two.

## Release and delivery

Release is decoupled from review: when an increment meets the Definition of Done and the required authorization exists, it can be released on demand. Do not wait for a ceremony, and do not publish, deploy, or change external systems without authorization. For release work, decide readiness from the acceptance criteria, the verification evidence, the unresolved defect severity, the residual risks, the operational monitoring, and the recovery plan; do not call a release ready while a required gate is open.

Deliver a reviewable summary:

1. **Scope and behavior:** What changed, and the affected requirement and design records.
2. **Architecture and structure:** The changed responsibilities, public interfaces, dependency constraints, component placement, and migration impact. Omit this section when nothing changed.
3. **Verification:** The checks you ran, the results, the traceability status, and the unverified areas. Keep the style checks apart from the correctness checks.
4. **Risks and recovery:** The residual risks, the blockers, the migration effects, and the rollback guidance.

After an authorized release, review the operational signals and the feedback, record the outcomes, and return the remaining work to the ordered backlog.

## Anti-patterns to avoid

| Anti-pattern | Guard |
|---|---|
| Water-Scrum-Fall: large upfront specification and a large late release phase around short iterations; big batches, long-lived branches, late integration, and deferred nonfunctional testing. | Keep entry lightweight, release on demand, work in small batches, integrate daily, test continuously, and carry verification, operations feedback, and Q4 coverage into each increment. |
| Cargo-cult or zombie ceremonies: roles and events without usable increments, stakeholder collaboration, autonomy, or improvement. Oppressive process that makes developers less safe or less able to deliver. | Require a usable increment, inspect real evidence, adapt at least one concrete thing, and use the workflow to enable developers, not to protect a plan; keep WIP and cadence humane. |
| Agile as "no design"; Definition of Ready as a stage gate, or a miniature waterfall inside each increment. | Keep the four architecture checks, evolutionary design practices, and a recorded baseline; refine continuously and confirm the entry agreement and inspect increments, not internal activity handoffs. |
| Metrics as individual targets; imposing scaling frameworks on small changes, or claiming agile is universally proven. | Use DORA metrics as local, contextual signals for improvement; keep this workflow proportional and use one backlog, one DoD, and one integrated increment only when multiple teams truly share a product. |

## Scaling note

This skill targets one developer and one coding agent. Do not add team ceremonies for a small or medium change. When three or more teams share one product, apply the scaling principle: a single ordered Product Backlog, one shared Definition of Done, one integrated increment, and explicit dependency reduction. Nexus, LeSS, and SAFe provide different starting points for larger settings; tailor them to the organization instead of importing their full structures by default.
