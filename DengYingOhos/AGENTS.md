# Agent Instructions (DengYing project)

## Turn Discipline (anti-stall)

1. **Never end a turn on a bare announcement.** If you state an action you are about to take ("Now committing P6-2:", "Let me run spike9", "接下来我去查…"), the SAME turn must carry the tool call that performs it. A turn that ends with prose only — no tool call, no final answer, nothing for the user to decide — strands the session mid-task: it looks like "working… then it just stops".
2. **A turn ends in exactly two ways:** every announced action has been executed via tool calls and you are reporting results, or you genuinely need the user's input. Anything else means you keep calling tools.
3. **Resume before re-planning.** If your previous turn ended without executing what it announced, your first tool call this turn resumes that action — do not restate or re-derive the plan.
