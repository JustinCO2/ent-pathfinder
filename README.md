# Ent Pathfinder

Receives live Forestry event calls and draws a Shortest Path straight to a called ent.

## Why this works

Friendly Ent is the one Forestry event worth travelling to. Every other event gates its rewards
behind having been chopping within ~10-30 tiles when it spawned, so arriving afterwards earns
nothing. The ent is different — the wiki is explicit that ineligible players *"can still receive
the egg nest with the same 20% chance per fully pruned entling."* No prior chopping required.

The events last **120 seconds**, which is the entire design constraint.

## Where the data comes from

Not Discord. The call channels are a rendering of an upstream feed: the
[Event Scouting plugin](https://github.com/peanubnutter/scoutingplugin) uploads sightings to a
community-run API every 3 seconds, and the Discord bot reads from there.

This plugin does not read that API itself. Hundreds of installs each polling a community
database would put load on it that grows with every player. Instead, every player holds one
connection to the [call relay](../ent-relay/README.md), which is the API's only reader — once
every 1.5 seconds however many players are connected — and pushes each change out the moment
it sees it.

```
scouting API  <── one read every 1.5s ──  call relay  ── push ──>  every player
```

A call reaches a player a median of ~3s after a scout sees it (2.9s measured at a 1s poll, about a quarter-second more at 1.5s), most of which is the scouting
plugin's own 3s upload batching.

## Keeping the relay free to run

The relay runs on a free plan with a daily allowance of 100,000 connections shared by every
player. Pushes are not billed, so the only thing that grows with players is how often they
connect. The client is built so that stays small:

- **Off by default.** *Receive calls* must be switched on, with the Plugin Hub's third-party
  warning. Until then nothing connects anywhere.
- **Only connects where it is useful.** Logged in, on the configured forestry world, with calls
  on. Anywhere else the plugin holds no connection at all. The world must be a single real world,
  which caps concurrent connections at that world's 2,000-player limit.
- **Rides out hops and relogs.** A connection is kept for a minute after it stops being needed,
  and loading screens and brief connection loss count as still in game, so neither hopping, the
  six-hour relog, nor a region load costs a fresh connection.
- **Backs off on failure.** Reconnects wait 5s, 10s, 20s… up to five minutes, each randomised so
  that a relay restart does not bring every player back in the same instant. A full day of
  failures makes a few hundred attempts, not tens of thousands.
- **Earns its fast reconnects.** The backoff only resets after a connection has stayed up for a
  minute, so a relay that accepts and then immediately drops connections cannot trap clients in
  a tight loop.
- **Pings at the protocol level.** Keep-alives are WebSocket pings, answered by the relay's
  runtime without waking it.
- **Never falls back to the API.** If the relay is unreachable the overlay says *Call relay
  offline*. Quietly polling the API instead would recreate the exact load problem, from every
  player at once, at the worst possible moment.

## Running it without a game client

Everything except the RuneLite wiring runs headlessly, using the plugin's real relay client:

```
cd ../ent-relay && npm run dev           # a local relay, in one terminal
gradlew entWatch                          # all events, in another
gradlew entWatch -Ptype=ENT -Pworld=444   # just ents on 444, as the plugin subscribes
gradlew entWatch -Prelay=wss://.../ws     # a deployed relay
gradlew test                              # clustering, locations, backoff, message parsing
```

`entWatch` prints each event as it arrives, with its place and countdown, plus every connection
state change — stop the relay while it runs to watch the backoff.

## The part that is actually hard

Deduplication. One ent arrives as a dozen or more rows — every scout who sees it uploads its own
sighting from wherever they were standing, scattered over several tiles and up to a minute apart.
A captured real burst was 17 rows across 8 coordinates for a single event. Pathing off raw rows
would alert repeatedly and thrash the pathfinder.

[`EventClusterer`](src/main/java/com/entpathfinder/EventClusterer.java) collapses them using the
upstream plugin's distance rule (same type/world/plane, within 20 tiles). Two details that are
easy to get wrong:

- **The time window is the event's own lifetime.** A report timestamped after an event ended
  cannot be about it, and the forestry world concentrates on a handful of popular trees, so a
  wider window folds the *next* ent at the same tree into the last one and never announces it.
- **Stragglers still need a home.** A report from inside an event can arrive after it ended.
  Finished clusters are kept briefly purely to absorb those, so they never look like a new event.

## Shortest Path integration

Uses the supported plugin-message API — the same route Quest Helper takes. No reflection, no hard
dependency; if Shortest Path is not installed the messages are simply never consumed.

```java
eventBus.post(new PluginMessage("shortestpath", "path",
    Collections.singletonMap("target", worldPoint)));
```

The post is made on the client thread, because Shortest Path's handler reads the player's
location there. See *Which event, and when to move on* for why it is one target at a time.

## Ents only

The plugin follows ents and nothing else: it subscribes to ents on your forestry world, so the relay
never sends it anything else, and it discards any other event type it is somehow sent. An ent lasts
**120s**, stated outright on the wiki, and that is the countdown shown.

`EventType` still knows the feed's other event types, but only so the headless `entWatch` tool can
display them when pointed at the full feed. They have no durations the wiki commits to, so there
they are shown by age rather than with an invented countdown.

## Which event, and when to move on

Events are listed and visited **oldest first** — the order they were called, which is also the
order they expire in. One line each: where the call is, and how long is left.

```
Nemus Retreat                  (88s)
Seers' Village                 (54s)
```

The place name carries a call's identity, so there is no index, event name, distance or leading
marker on the line, and no panel title. With two ents running, "Nemus Retreat" and "Seers' Village"
tell them apart far better than "1." and "2." did. A coordinate matching no known place falls back
to the event's own name so a line is never blank.

State is left entirely to colour:

| State | Colour | Meaning |
|---|---|---|
| Pending | grey | called, not yet your destination |
| Target | white | the path is pointing here |
| Here | cyan | you arrived |

**The place name never changes once a call is listed.** It is resolved once, from the first
sighting, and then fixed. Deriving it from the cluster's live centroid — which moves every time
another scout reports — let a call that started near the edge of a place's radius drift out of it,
at which point the overlay fell back to printing the event's name instead. A call does not move, so
neither should its label.

By default, arriving does **not** remove an event — you are standing in it, which is worth
showing. It stops being a destination and the path moves on, but it stays listed until the game
says you are finished with it:

> *"Well done, you've given 5 entlings haircuts!"*

That message drops it from the list. It is matched loosely (the `Well done,` prefix is optional,
both apostrophe forms are accepted, and singular wording is tolerated) because only the plural
template is documented — missing it would strand an event on screen permanently.

**Remove on arrival** changes that: reaching an event drops it immediately rather than waiting for
the chat message. Useful if you would rather the list only ever show places you have not been to
yet, and it sidesteps the stranding risk above entirely.

**Only one target is sent at a time.** Shortest Path also accepts a `Set`, but it treats the set as
"any of these will do" and clears the *entire* path the moment the player comes within
`reachedDistance` (default 5) of any one of them. With two live events, arriving at the first would
silently cancel the route to the second. Advancing one at a time keeps the path and the list in
agreement.

**Arrival is measured here, not asked of Shortest Path.** It exposes nothing about progress: its
only outbound plugin message is the transport list, and when it does notice arrival it clears the
path without telling anyone. So the plugin measures Chebyshev distance from the player each tick,
and once inside `Arrival distance` (default 10 tiles, configurable) marks the event reached and
retargets. Ten is deliberately wider than Shortest Path's own 5, so the handover happens before it
wipes the path itself.

## Places, and the two islands worth ignoring

[`CallLocation`](src/main/java/com/entpathfinder/CallLocation.java) turns a call's coordinates into
a name for the overlay. Centres come from the wiki's world-map pins, but they mark the centre of an
*area*, not its trees — the Seers' Village maples are ~20 tiles south of the village pin, the Nemus
Retreat trees ~30 south of theirs — so matching is nearest-wins inside a generous radius. A
coordinate matching nothing known shows no name at all, which is the right call: the open ocean
where shoals spawn is not a place, and a wrong name is worse than none.

Two Sailing islands get their own ignore toggles, because both genuinely produce Forestry calls
that most players cannot reach:

| Island | Centre | Gate |
|---|---|---|
| Sunbleak Island | 2209, 2330 | 72 Sailing + adamant helm for the tangled kelp |
| Drumstick Isle | 2146, 3545 | 79 Sailing + adamant keel |

Drumstick Isle holds the only rosewood trees in the game, which is exactly why it shows up. Both
were confirmed against the live feed — the `SAPLING w495 (2150,3538)` and `FLOWERS w444 (2209,2319)`
calls logged while building this are these two islands.

Ignored places are filtered out of the raw rows before clustering, so an ignored island never
becomes a cluster: it cannot alert, cannot be pathed to, and cannot sit at the top of the list as
the oldest call.

## Known limitations

- **Auto-path can clobber a hand-set path.** Shortest Path does not broadcast its current target,
  so a path you set yourself is indistinguishable from no path. The plugin only ever *clears* a
  path it drew itself, but it will overwrite one when a new event lands.
- **Place names cover known spots only.** The table holds 77 places -- effectively every area with
  choppable trees, plus the two Sailing islands. Anywhere else falls back to the event name
  rather than a wrong guess.
- **The target tile is a centroid** of where the scouts were standing, typically a few tiles off
  the actual tree. Fine for pathing — the ent is visible on arrival.
- **Arrival is proximity, not participation.** Getting within ten tiles marks an event as reached,
  whether or not you pruned anything — walking past one en route to another advances the path.
  The chat message is the authority on actually finishing, not proximity.
- **No relay, no calls.** If the relay is down the plugin shows it is offline and waits; it does
  not fall back to the scouting API. See *Keeping the relay free to run*.

## Not yet verified in game

- the Shortest Path plugin-message handshake actually drawing a path. A first in-game run showed
  this was broken: posting the message from a background thread tripped `AssertionError: must be
  called on client thread` inside Shortest Path, 318 times, and no path was ever drawn. The post
  now hops to the client thread via `ClientThread.invokeLater`; needs one confirming run.
- the relay connection inside the real client: connecting on login to the forestry world,
  holding through a hop and relog, disconnecting a minute after leaving
- arrival detection, the completion chat message, and the handover to the next event
- notification and overlay rendering, including the *Call relay offline* line

The relay client itself is verified outside the game: against a local relay it connected,
received and clustered live calls, backed off at 1s, 2s, 4s, 8s, 15s and 59s when the relay was (the schedule has since moved to start at 5s, to spread out a mass reconnect)
killed, and reconnected on its own when it came back.

Plugin instantiation, injection and `startUp()` are confirmed working — the dev client starts with
no errors naming this package.

## Setup

Needs **JDK 11+**; JDK 17 is the safe choice, since the Gradle wrapper here is 7.4 and 21+ will
fail. `gradle.properties` pins it (gitignored — copy `gradle.properties.example` on another
machine).

`gradlew runClient` launches RuneLite with this plugin loaded. `gradlew runClientAll` launches it
with the sibling plugin projects loaded too — RuneLite accepts any number of plugins, but each
project is a separate Gradle build, so the task borrows the siblings' compiled output and
[`DevClient`](src/test/java/com/entpathfinder/DevClient.java) resolves them by name. Build the
siblings first or they are skipped. The desktop `dev.bat` does all of this for you.
