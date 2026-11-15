# Dialogue format

A dialogue file is JSON in `res/dialogue/`.

## Graph form
{ "id": "mayor", "start": "n1", "nodes": [ ... ] }

Node keys:
- id (required, unique, letters/digits/underscore)
- speaker (optional), text (required unless "end": true)
- next: id of the next node (plain line)
- choices: list of choices (instead of next)
- end: true marks a terminal node (no next/choices)
- effects, applied on entering the node: set, clear, stage, coins, exp, item

Choice keys: text, next (both required), if (conditions), default (bool),
plus the same effect keys, applied when the choice is picked.

Conditions (ANDed): "flag", "!flag", "stage:NAME", "!stage:NAME".
Effects: set/clear are lists of flag names; stage is a QuestManager.Stage name;
coins/exp are non-negative integers; item is an ItemType name.

A node with no next and no choices ends the conversation.

## Legacy form
{ "lines": [ {"text": "..."} ] } is read as a straight chain n0 -> n1 -> ...